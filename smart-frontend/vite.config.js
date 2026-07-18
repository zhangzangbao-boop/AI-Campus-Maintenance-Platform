import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { nodePolyfills } from 'vite-plugin-node-polyfills'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    nodePolyfills({
      // 解决 sockjs-client 等依赖 Node 全局变量的问题
      globals: {
        global: true,
        process: true,
        Buffer: true,
      },
    }),
  ],

  build: {
    // Keep production assets inside smart-frontend.
    outDir: path.resolve(__dirname, 'dist'),
    emptyOutDir: true, // 构建前清空输出目录
    sourcemap: false, // 生产环境不生成 sourcemap
  },

  server: {
    // 开发服务器配置
    port: 5173,

    proxy: {
      // API 请求代理到 Gateway 网关
      '/api': {
        target: 'http://localhost:8070',
        changeOrigin: true,
      },
    },
  },
})
