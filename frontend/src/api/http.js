import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/gre-vocab/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers['Authorization'] = 'Bearer ' + token
  return config
})

http.interceptors.response.use(
  (resp) => {
    const data = resp.data
    if (data && data.code !== 0) {
      // 后端以 code=401 表示未登录/令牌失效（HTTP 状态仍为 200）
      if (data.code === 401) {
        // 仅当原本持有 token（已登录但过期）才跳登录；匿名浏览公开页面时静默拒绝，不强制跳转
        if (localStorage.getItem('token')) {
          localStorage.removeItem('token')
          localStorage.removeItem('user')
          ElMessage.error(data.message || '登录已过期，请重新登录')
          if (location.hash !== '#/login') location.hash = '#/login'
        }
        return Promise.reject(new Error(data.message))
      }
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      if (localStorage.getItem('token')) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        ElMessage.error('登录已过期，请重新登录')
        if (location.hash !== '#/login') location.hash = '#/login'
      }
    } else {
      ElMessage.error(err.message || '网络错误')
    }
    return Promise.reject(err)
  }
)

export default http
