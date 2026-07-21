# NetDebugger

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A cross-platform TCP/UDP debugging tool with a modern web-based UI powered by Chromium Embedded Framework.

> [中文文档](./README_zh.md)

---

## Interface

![Interface](./interface/interface.png)
![Interface Dark](./interface/interface_dark.png)

---

## Features

- **TCP Server** — listen on a port, accept multiple clients, send/receive messages, support broadcast and targeted sending
- **TCP Client** — connect to remote TCP servers, send/receive messages
- **UDP Server** — bind a local port, receive datagrams, track known clients, send to specific clients or broadcast
- **UDP Client** — bind local port, send datagrams to target hosts
- **Multi-session** — create and manage multiple server/client instances simultaneously
- **HEX support** — send and receive data in either text (UTF-8/GBK/ASCII) or hexadecimal format
- **Dark/Light theme** — supports light mode, dark mode, and auto (follow system)
- **Chinese / English i18n** — full bilingual UI with dynamic language switching
- **Session persistence** — automatically saves and restores session configurations across restarts
- **Log display** — color-coded sent/received/system messages, click to copy content

---

## Tech Stack

| Layer | Technology |
|---|---|
| Shell | Java AWT/Swing (undecorated window) |
| Browser Engine | JCEF (Java Chromium Embedded Framework) |
| Frontend | Vue 2.7 + Element UI |
| Build | Maven + maven-shade-plugin (fat jar) |
| Packaging | jpackage (app-image) |
| Networking | Java NIO (java.net standard API) |

---

## Prerequisites

- **JDK 17+** (development & building)
- **Maven 3.6+** (building fat jar)
- **Windows** (current runtime supports `windows-amd64`; other platforms require corresponding JCEF runtime binaries)

---

## Quick Start

### 1. Run in Development Mode

```bash
# Build the fat jar
mvn clean package

# Run
java -jar target/tcp-udp-debug-tool-1.0.0.jar
```

> The program internally implements automatic JCEF environment discovery (`App.findRuntimesDir` method), so no additional environment specification is needed at runtime: `-Djava.library.path="./runtimes/windows-amd64"`

On Windows, you can also simply double-click `run.bat` after building.
> You need to configure your JDK17 path in `run.bat`.

---

### 2. Package as Distributable App

Use `jpackage` to create a self-contained app-image — end users do **not** need a JDK to run it.

#### Run the Package Script

Edit the `JDK_HOME` path in `package.sh` to point to your JDK 17+ installation, then:

```bash
bash package.sh
```

The output will be in `installer-output/NetDebugger/`. Users can launch `NetDebugger.exe` directly from that directory — no JDK required.

> On Windows, please install Git Bash to support shell script execution. After installation, run the `package.sh` script in Git Bash.

#### Customizing the Package Script

```bash
# package.sh key parameters:
--type app-image          # Creates a self-contained directory (no installer)
--name "NetDebugger"      # Application name
--app-version "1.0.0"     # Version number
--vendor "DebugTool"      # Vendor/publisher name
--java-options "-Xms128m" # Minimum heap
--java-options "-Xmx512m" # Maximum heap
```

To create an installer (`.msi`/`.exe` on Windows, `.dmg` on macOS, `.deb`/`.rpm` on Linux), change `--type app-image` to `--type msi` or `--type exe` (requires WiX Toolset on Windows).

---

## Project Structure

```
JavaFxCEF/
├── src/
│   └── main/
│       ├── java/com/debugtool/
│       │   ├── App.java                        # Entry point (AWT window + JCEF + HTTP server)
│       │   ├── handler/
│       │   │   └── JSBridgeHandler.java        # JS ↔ Java bridge
│       │   ├── model/
│       │   │   └── LogEntry.java               # Log data model
│       │   ├── service/
│       │   │    ├── TcpServerService.java      # TCP server logic
│       │   │    ├── TcpClientService.java      # TCP client logic
│       │   │    ├── UdpServerService.java      # UDP server logic
│       │   │    ├── UdpClientService.java      # UDP client logic
│       │   │    └── PersistenceService.java    # Session persistence I/O
│       │   └── util/
│       │       ├── HexUtil.java                # HEX encode/decode utility
│       │       └── I18n.java                   # Internationalization utility
│       └── resources/
│           ├── web/                            # Vue + Element UI frontend
│           │   ├── css/
│           │   ├── img/
│           │   ├── js/
│           │   └── index.html
│           ├── i18n/                           # Language resource files
│           │   ├── messages.properties
│           │   └── messages_zh_CN.properties
│           └── logo/                           # Logo resources
│               ├── icon.ico                    # Windows app icon
│               └── icon.png                    # Window icon
├── pom.xml                                     # Maven build config
├── package.sh                                  # jpackage build script
├── run.bat                                     # Windows dev-mode launcher
├── LICENSE                                     # MIT License
└── THIRD-PARTY                                 # Third-party license notices
```

---

## Third-Party Dependencies

See [THIRD-PARTY](./THIRD-PARTY) for full license details.

| Dependency | License | Purpose |
|---|---|---|
| JCEF / CEF | BSD | Embedded Chromium browser engine |
| Vue.js 2.7 | MIT | Frontend reactive framework |
| Element UI | MIT | UI component library |
| Gson 2.10 | Apache 2.0 | JSON serialization |

---

## License

This project is licensed under the MIT License — see [LICENSE](./LICENSE) for details.
