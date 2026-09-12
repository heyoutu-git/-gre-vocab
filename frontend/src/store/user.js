import { defineStore } from 'pinia'
import { authApi } from '../api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  getters: {
    isLogin: (s) => !!s.token
  },
  actions: {
    async login(username, password, agreedTerms, captcha, captchaToken) {
      const body = { username, password, agreedTerms }
      if (captcha) body.captcha = captcha
      if (captchaToken) body.captchaToken = captchaToken
      const { data } = await authApi.login(body)
      this.token = data.token
      this.user = { userId: data.userId, username: data.username, nickname: data.nickname, role: data.role }
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(this.user))
      return data
    },
    async register(payload) {
      // 注册后进入待审核状态，不自动登录（data 为提示文案）
      const { data } = await authApi.register(payload)
      return data
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
