import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // 构建输出目录指向后端的静态资源目录
    outDir: path.resolve(__dirname, '../backend/src/main/resources/static'),
    emptyOutDir: true, // 构建前清空输出目录
    sourcemap: false, // 生产环境不生成 sourcemap
  },
  server: {
    // 开发服务器配置
    port: 3000,
    proxy: {
      // API 请求代理到后端服务器
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
