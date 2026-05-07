import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import viteCompression from 'vite-plugin-compression'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:5680'

  return {
    base: mode === 'production' ? '/ui/' : '/',
    plugins: [
      vue(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
        imports: ['vue', 'vue-router', 'pinia'],
        dts: 'src/auto-imports.d.ts',
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/components.d.ts',
      }),
      // 生产 build 顺手生成 .gz,nginx gzip_static / Spring Boot 部署都能直接用
      viteCompression({ threshold: 1024, algorithm: 'gzip', ext: '.gz' }),
    ],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
      },
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
        '/mcp': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      target: 'es2020',
      rollupOptions: {
        output: {
          // 大库单独成 chunk,避免 vendor.js 一坨几 MB 拖首屏
          manualChunks: {
            monaco: ['monaco-editor'],
            x6: ['@antv/x6', '@antv/x6-vue-shape'],
            'element-plus': ['element-plus', '@element-plus/icons-vue'],
          },
        },
      },
    },
    // Vite 5+ 默认 esbuild minify,无须额外依赖即可 drop console/debugger
    esbuild: {
      drop: mode === 'production' ? ['console', 'debugger'] : [],
    },
  }
})
