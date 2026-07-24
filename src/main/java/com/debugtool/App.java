package com.debugtool;

import com.debugtool.handler.JSBridgeHandler;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.*;

import com.debugtool.util.I18n;
import com.debugtool.service.PersistenceService;
import com.debugtool.service.SshClientService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

/**
 * NetDebugger - TCP/UDP Debug Tool.
 * AWT/Swing window + JCEF Chromium + Element UI frontend.
 * Zero VM options required.
 */
public class App {

    private static String APP_TITLE = "NetDebugger";
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;
    private static final int SINGLE_INSTANCE_PORT = 43127;
    private static ServerSocket singleInstanceLock;

    private JSBridgeHandler bridgeHandler;
    private PersistenceService persistenceService;
    private CefApp cefApp;
    private CefClient cefClient;
    private CefBrowser cefBrowser;
    private JFrame frame;
    private JPanel wrapper;
    private HttpServer httpServer;

    // Window drag state
    private volatile boolean dragging = false;

    // Window resize state
    private static final int RESIZE_N = 1, RESIZE_S = 2, RESIZE_E = 4, RESIZE_W = 8;
    private volatile boolean resizing = false;
    private int resizeEdge = 0;

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        new App().run();
    }

    private void run() {
        // Prevent multiple instances
        if (!acquireSingleInstanceLock()) {
            JOptionPane.showMessageDialog(null,
                "NetDebugger is already running.",
                "NetDebugger",
                JOptionPane.WARNING_MESSAGE);
            System.exit(0);
            return;
        }

        persistenceService = new PersistenceService();

        // Load persisted language preference, fallback to system locale
        Map<String, String> savedConfig = persistenceService.loadConfig();
        String savedLang = savedConfig.get("language");
        if (savedLang != null && !savedLang.isEmpty()) {
            I18n.setLocaleTag(savedLang);
        }

        APP_TITLE = I18n.get("app.title");
        initCef();

        SwingUtilities.invokeLater(() -> {
            frame = new JFrame(APP_TITLE);
            frame.setUndecorated(true);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setSize(WIDTH, HEIGHT);
            frame.setMinimumSize(new Dimension(1280, 860));
            frame.setLocationRelativeTo(null);
            frame.setLayout(new BorderLayout());

            // Window icon (taskbar + title bar)
            try {
                java.net.URL iconUrl = App.class.getResource("/logo/logo.png");
                if (iconUrl != null) {
                    Image icon = Toolkit.getDefaultToolkit().getImage(iconUrl);
                    frame.setIconImage(icon);
                    System.out.println("[NetDebugger] Window icon loaded");
                }
            } catch (Exception e) {
                System.err.println("[NetDebugger] Failed to load icon: " + e.getMessage());
            }

            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    shutdown();
                }
            });

            // Show frame first so native peer exists before GLCanvas.addNotify()
            frame.setVisible(true);

            // Create CEF browser after frame is visible (GLCanvas needs valid peer)
            SwingUtilities.invokeLater(() -> createCefBrowser());
        });
    }

    // ==================== CEF Initialization ====================

    private void initCef() {
        CefAppBuilder builder = new CefAppBuilder();
        builder.getCefSettings().windowless_rendering_enabled = false;

        File installDir = new File(findRuntimesDir(), getPlatformDir());
        System.out.println("[NetDebugger] CEF install dir: " + installDir.getAbsolutePath());
        builder.setInstallDir(installDir);
        builder.setSkipInstallation(true);

        try {
            cefApp = builder.build();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("[NetDebugger] CEF initialized");
    }

    // ==================== Browser Creation ====================

    private void createCefBrowser() {
        cefClient = cefApp.createClient();

        // JS→Java message router
        CefMessageRouter.CefMessageRouterConfig config =
            new CefMessageRouter.CefMessageRouterConfig("cefQuery", "cefQueryCancel");
        CefMessageRouter msgRouter = CefMessageRouter.create(config);
        msgRouter.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                                   String request, boolean persistent, CefQueryCallback callback) {
                if (handleWindowCommand(request, callback)) {
                    return true;
                }
                bridgeHandler.handleCefQuery(request, callback);
                return true;
            }
        }, true);
        cefClient.addMessageRouter(msgRouter);

        // Load handler
        cefClient.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                                             boolean canGoBack, boolean canGoForward) {
                if (!isLoading) {
                    SwingUtilities.invokeLater(() -> notifyBridgeReady(browser));
                }
            }
        });

        // Create bridgeHandler before HTTP server (upload endpoint needs it)
        bridgeHandler = new JSBridgeHandler();
        bridgeHandler.setPersistence(persistenceService);

        String htmlUrl = startHttpServer();
        System.out.println("[NetDebugger] Loading: " + htmlUrl);

        cefBrowser = cefClient.createBrowser(htmlUrl, false, false);

        bridgeHandler.setBrowser(cefBrowser);

        // Direct AWT embedding (no SwingNode, no JavaFX modules)
        Component ui = cefBrowser.getUIComponent();
        ui.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        // Wrap in a panel with an AWT border so it never disappears during resize
        wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
        wrapper.add(ui, BorderLayout.CENTER);
        frame.add(wrapper, BorderLayout.CENTER);
        frame.revalidate();

        // Set initial border color based on persisted theme
        Map<String, String> savedCfg = persistenceService.loadConfig();
        updateThemeBorder(savedCfg.getOrDefault("theme", "auto"));

        // Listen for theme changes from frontend
        bridgeHandler.setThemeCallback(this::updateThemeBorder);
    }

    // ==================== HTTP Server (proper UTF-8 encoding) ====================

    private String startHttpServer() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(0), 0);

            // Upload endpoint for SFTP file uploads
            httpServer.createContext("/upload/", (HttpExchange exchange) -> {
                try {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, 0);
                        exchange.close();
                        return;
                    }
                    String path = exchange.getRequestURI().getPath();
                    // /upload/sessionId/fileName?relpath=subdir/...
                    String[] parts = path.substring(8).split("/", 2);
                    if (parts.length < 2) {
                        exchange.sendResponseHeaders(400, 0);
                        exchange.close();
                        return;
                    }
                    String sessionId = parts[0];
                    String fileName = java.net.URLDecoder.decode(parts[1], "UTF-8");

                    // Extract optional relpath query parameter for folder uploads
                    String relpath = null;
                    String query = exchange.getRequestURI().getQuery();
                    if (query != null) {
                        for (String pair : query.split("&")) {
                            String[] kv = pair.split("=", 2);
                            if ("relpath".equals(kv[0]) && kv.length > 1) {
                                relpath = java.net.URLDecoder.decode(kv[1], "UTF-8");
                                break;
                            }
                        }
                    }

                    SshClientService svc = bridgeHandler.getSshClient(sessionId);
                    if (svc == null || !svc.isConnected()) {
                        String body = "Session not found or not connected";
                        exchange.sendResponseHeaders(404, body.length());
                        try (OutputStream os = exchange.getResponseBody()) { os.write(body.getBytes()); }
                        return;
                    }
                    byte[] data = exchange.getRequestBody().readAllBytes();
                    svc.sftpUploadStream(new ByteArrayInputStream(data), fileName, data.length, relpath);
                    exchange.sendResponseHeaders(200, 0);
                    exchange.close();
                } catch (Exception e) {
                    try {
                        String body = "Upload failed: " + e.getMessage();
                        exchange.sendResponseHeaders(500, body.length());
                        try (OutputStream os = exchange.getResponseBody()) { os.write(body.getBytes()); }
                    } catch (Exception ignored) {}
                }
            });

            httpServer.createContext("/", (HttpExchange exchange) -> {
                try {
                    String path = exchange.getRequestURI().getPath();
                    if (path == null || path.equals("/")) path = "/index.html";
                    String resourcePath = "/web" + path;
                    InputStream in = getClass().getResourceAsStream(resourcePath);
                    if (in == null) {
                        String body = "404 Not Found: " + path;
                        exchange.sendResponseHeaders(404, body.getBytes().length);
                        try (OutputStream os = exchange.getResponseBody()) { os.write(body.getBytes()); }
                        return;
                    }
                    byte[] bytes = in.readAllBytes();
                    in.close();
                    String ct = getContentType(path);
                    exchange.getResponseHeaders().set("Content-Type", ct);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                } catch (Exception e) {
                    String body = "500 Error";
                    exchange.sendResponseHeaders(500, body.length());
                    try (OutputStream os = exchange.getResponseBody()) { os.write(body.getBytes()); }
                }
            });
            httpServer.start();

            int port = httpServer.getAddress().getPort();
            System.out.println("[NetDebugger] HTTP server on port " + port);
            return "http://localhost:" + port + "/";
        } catch (IOException e) {
            System.err.println("[NetDebugger] HTTP server failed: " + e.getMessage());
            System.exit(1);
            return null;
        }
    }

    private static String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".ttf")) return "font/truetype";
        return "application/octet-stream";
    }

    // ==================== Bridge ====================

    private void notifyBridgeReady(CefBrowser browser) {
        // Push locale before anything else
        String localeTag = I18n.getLocaleTag();
        browser.executeJavaScript(
            "if(window.setLocale) window.setLocale('" + localeTag + "');",
            browser.getURL(), 0);

        browser.executeJavaScript(
            "if(window.onBridgeReady) window.onBridgeReady();",
            browser.getURL(), 0);
        System.out.println("[NetDebugger] Bridge ready, locale=" + localeTag);

        // Push persisted state after a short delay (let frontend Vue initialize)
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            bridgeHandler.pushRestoreState();
        }, "restore-state").start();
    }

    // ==================== Window Commands ====================

    private boolean handleWindowCommand(String reqJson, CefQueryCallback callback) {
        try {
            JsonObject req = JsonParser.parseString(reqJson).getAsJsonObject();
            String method = req.get("method").getAsString();
            switch (method) {
                case "windowMinimize":
                    SwingUtilities.invokeLater(() -> frame.setState(JFrame.ICONIFIED));
                    if (callback != null) callback.success("ok");
                    return true;
                case "windowMaximize":
                    SwingUtilities.invokeLater(() -> {
                        if ((frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) {
                            frame.setExtendedState(JFrame.NORMAL);
                        } else {
                            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                        }
                    });
                    if (callback != null) callback.success("ok");
                    return true;
                case "windowClose":
                    SwingUtilities.invokeLater(() -> shutdown());
                    return true;
                case "windowDragStart":
                    startWindowDrag();
                    if (callback != null) callback.success("ok");
                    return true;
                case "windowDragEnd":
                    dragging = false;
                    if (callback != null) callback.success("ok");
                    return true;
                case "windowResizeStart":
                    int edge = req.getAsJsonArray("args").get(0).getAsInt();
                    startWindowResize(edge);
                    if (callback != null) callback.success("ok");
                    return true;
                case "windowResizeEnd":
                    resizing = false;
                    resizeEdge = 0;
                    if (callback != null) callback.success("ok");
                    return true;
            }
        } catch (Exception e) {
            // Not a window command, let bridgeHandler handle it
        }
        return false;
    }

    private void startWindowDrag() {
        dragging = false; // kill any previous drag thread
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) return;
        final Point[] prevMouse = {pi.getLocation()};
        dragging = true;
        new Thread(() -> {
            while (dragging) {
                PointerInfo p = MouseInfo.getPointerInfo();
                if (p != null) {
                    Point cur = p.getLocation();
                    int dx = cur.x - prevMouse[0].x;
                    int dy = cur.y - prevMouse[0].y;
                    Point loc = frame.getLocation();
                    frame.setLocation(loc.x + dx, loc.y + dy);
                    prevMouse[0] = cur;
                }
                try { Thread.sleep(5); } catch (InterruptedException e) { break; }
            }
        }, "win-drag").start();
    }

    // ==================== Window Resize (border drag via JS bridge) ====================

    private void startWindowResize(int edge) {
        resizing = false; // kill any previous resize thread
        resizeEdge = edge;
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) return;
        final Point[] prevMouse = {pi.getLocation()};
        resizing = true;
        new Thread(() -> {
            while (resizing) {
                PointerInfo p = MouseInfo.getPointerInfo();
                if (p == null) { try { Thread.sleep(5); } catch (InterruptedException e) { break; } continue; }
                Point cur = p.getLocation();
                int dx = cur.x - prevMouse[0].x;
                int dy = cur.y - prevMouse[0].y;
                prevMouse[0] = cur;

                Rectangle bounds = frame.getBounds();
                int x = bounds.x, y = bounds.y;
                int w = bounds.width, h = bounds.height;
                Dimension min = frame.getMinimumSize();

                if ((resizeEdge & RESIZE_N) != 0) {
                    int newH = h - dy;
                    if (newH >= min.height) { y += dy; h = newH; }
                }
                if ((resizeEdge & RESIZE_S) != 0) {
                    h = Math.max(min.height, h + dy);
                }
                if ((resizeEdge & RESIZE_W) != 0) {
                    int newW = w - dx;
                    if (newW >= min.width) { x += dx; w = newW; }
                }
                if ((resizeEdge & RESIZE_E) != 0) {
                    w = Math.max(min.width, w + dx);
                }
                frame.setBounds(x, y, w, h);
                try { Thread.sleep(5); } catch (InterruptedException e) { break; }
            }
        }, "win-resize").start();
    }

    // ==================== Native Library Path ====================

    /**
     * Detect platform-specific subdirectory name (e.g. windows-amd64, linux-amd64, macosx-amd64).
     */
    private static String getPlatformDir() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String platform;
        if (os.contains("win")) {
            platform = "windows";
        } else if (os.contains("mac") || os.contains("darwin")) {
            platform = "macosx";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            platform = "linux";
        } else {
            platform = os.replace(" ", "");
        }

        // Normalize arch to match JCEF naming
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            arch = "amd64";
        } else if (arch.contains("aarch64") || arch.contains("arm64")) {
            arch = "arm64";
        }

        return platform + "-" + arch;
    }

    /**
     * Locate the runtimes directory from multiple possible sources,
     * supporting IDE dev, jpackage, and CWD-based launch.
     */
    private static String findRuntimesDir() {
        // 1) Try relative to JAR/code location (works for jpackage, CWD launch)
        try {
            java.security.CodeSource cs = App.class.getProtectionDomain().getCodeSource();
            if (cs != null) {
                File jarLoc = new File(cs.getLocation().toURI().getPath());
                File parent = jarLoc.isDirectory() ? jarLoc : jarLoc.getParentFile();
                File rt = new File(parent, "runtimes");
                if (rt.isDirectory()) return rt.getAbsolutePath();
            }
        } catch (Exception ignored) {}

        // 2) Try $APPDIR/app/runtimes (jpackage app-image layout)
        File javaHomeParent = new File(System.getProperty("java.home")).getParentFile();
        if (javaHomeParent != null) {
            File rt = new File(new File(javaHomeParent, "app"), "runtimes");
            if (rt.isDirectory()) return rt.getAbsolutePath();
        }

        // 3) Fallback: current working directory (IDE / run.bat)
        File cwdRuntimes = new File("runtimes");
        if (cwdRuntimes.isDirectory()) return cwdRuntimes.getAbsolutePath();

        return new File("runtimes").getAbsolutePath(); // last resort — will fail fast downstream
    }

    private static void addNativeLibraryPath(String path) {
        try {
            java.lang.reflect.Field f = ClassLoader.class.getDeclaredField("usr_paths");
            f.setAccessible(true);
            String[] paths = (String[]) f.get(null);
            for (String p : paths) {
                if (p != null && p.equals(path)) return;
            }
            String[] np = new String[paths.length + 1];
            System.arraycopy(paths, 0, np, 0, paths.length);
            np[paths.length] = path;
            f.set(null, np);
            System.out.println("[NetDebugger] Native path injected: " + path);
        } catch (Exception e) {
            System.err.println("[NetDebugger] Warning: native path injection failed. "
                + "Use -Djava.library.path=./runtimes/windows-amd64 as fallback.");
        }
    }

    private void updateThemeBorder(String theme) {
        if (wrapper == null) return;
        SwingUtilities.invokeLater(() -> {
            if ("dark".equals(theme)) {
                wrapper.setBorder(BorderFactory.createLineBorder(new Color(42, 165, 255), 1));
            } else {
                wrapper.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
            }
        });
    }

    private static boolean acquireSingleInstanceLock() {
        try {
            singleInstanceLock = new ServerSocket(SINGLE_INSTANCE_PORT, 0, InetAddress.getLoopbackAddress());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ==================== Shutdown ====================

    private void shutdown() {
        System.out.println("[NetDebugger] Shutting down...");
        if (httpServer != null) httpServer.stop(0);
        if (bridgeHandler != null) bridgeHandler.shutdown();
        if (cefBrowser != null) cefBrowser.close(true);
        if (cefClient != null) cefClient.dispose();
        if (cefApp != null) cefApp.dispose();
        if (frame != null) frame.dispose();
        try { if (singleInstanceLock != null) singleInstanceLock.close(); } catch (IOException ignored) {}
        System.exit(0);
    }
}
