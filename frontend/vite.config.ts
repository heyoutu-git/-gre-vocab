import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from '@vant/auto-import-resolver'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    base: '/gre-vocab/',
    plugins: [
      vue(),
      // Vant 按需引入：移动端组件（van-*）自动导入并附带样式，不增大 PC 首屏
      Components({
        resolvers: [VantResolver()],
        dts: 'src/components.d.ts'
      })
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      host: '127.0.0.1',
      port: 5173,
      proxy: {
        // 开发期把 API 请求代理到后端（context-path=/gre-vocab）；页面本身由 vite serve
        '/gre-vocab/api': {
          target: 'http://localhost:8080',
          changeOrigin: true
        }
      }
    }
  }
})
