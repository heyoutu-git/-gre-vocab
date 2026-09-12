import { defineStore } from 'pinia'

const THEME_KEY = 'sc-theme'
const SIDEBAR_KEY = 'sc-sidebar-collapsed'

const MOBILE_MAX = 768

export const useUiStore = defineStore('ui', {
  state: () => ({
    theme: localStorage.getItem(THEME_KEY) || 'light',
    sidebarCollapsed: localStorage.getItem(SIDEBAR_KEY) === '1',
    isMobile: false
  }),
  getters: {
    // 小屏（<=768px）强制只显示图标的收起态；其余尺寸由用户手动控制
    effectiveCollapsed: (s) => s.isMobile || s.sidebarCollapsed
  },
  actions: {
    applyTheme() {
      const el = document.documentElement
      el.setAttribute('data-theme', this.theme)
      el.classList.toggle('dark', this.theme === 'dark')
    },
    init() {
      this.applyTheme()
      this.isMobile = window.innerWidth <= MOBILE_MAX
    },
    toggleTheme() {
      this.theme = this.theme === 'dark' ? 'light' : 'dark'
      localStorage.setItem(THEME_KEY, this.theme)
      this.applyTheme()
    },
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      localStorage.setItem(SIDEBAR_KEY, this.sidebarCollapsed ? '1' : '0')
    },
    setMobile(v) {
      this.isMobile = v
    }
  }
})
