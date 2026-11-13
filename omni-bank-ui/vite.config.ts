import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 网关已 StripPrefix=1，开发态直连 agent 服务（8109）时同样剥离 /api
      '/api': {
        target: 'http://localhost:8109',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  test: {
    environment: 'node'
  }
})
