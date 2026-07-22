const { defineConfig } = require('vite')
const { createVuePlugin } = require('vite-plugin-vue2')
const path = require('path')

module.exports = defineConfig({
  plugins: [
    createVuePlugin({
      include: /\.vue$/,
      template: {
        transformAssetUrls: false
      }
    })
  ],
  root: __dirname,
  base: './',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  define: {
    __APP_VERSION__: JSON.stringify(process.env.APP_VERSION || '1.0.0')
  },
  build: {
    outDir: path.resolve(__dirname, '..', 'src', 'main', 'resources', 'web'),
    emptyOutDir: true,
    assetsInlineLimit: 0,
    target: 'es2015',
    cssCodeSplit: false,
    rollupOptions: {
      output: {
        assetFileNames: 'assets/[name].[hash][extname]',
        chunkFileNames: 'assets/[name].[hash].js',
        entryFileNames: 'assets/[name].[hash].js'
      }
    }
  },
  css: {
    extract: {
      filename: 'assets/[name].[hash].css'
    }
  }
})
