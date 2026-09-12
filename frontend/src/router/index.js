import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { public: true } },
  {
    path: '/', component: () => import('../views/Layout.vue'), redirect: '/home', children: [
      { path: 'home', name: 'Home', component: () => import('../views/Home.vue'), meta: { public: true } },
      { path: 'lessons', name: 'Lessons', component: () => import('../views/Lessons.vue'), meta: { public: true } },
      { path: 'lessons/:id', name: 'LessonDetail', component: () => import('../views/LessonDetail.vue') },
      { path: 'mine', name: 'Mine', component: () => import('../views/Mine.vue') },
      { path: 'record-guide', name: 'RecordGuide', component: () => import('../views/RecordGuide.vue') },
      { path: 'admin/users', name: 'AdminUsers', component: () => import('../views/AdminUsers.vue'), meta: { admin: true } },
      { path: 'admin/lessons', name: 'AdminLessons', component: () => import('../views/AdminLessons.vue'), meta: { admin: true } }
    ]
  },
  // 移动端独立路由树（Vant 界面，与 CRM 的 /m 架构一致）
  {
    path: '/m', component: () => import('../layouts/MobileLayout.vue'), children: [
      { path: 'home', name: 'MHome', component: () => import('../views/m/home.vue'), meta: { public: true, titleKey: 'mobile.library' } },
      { path: 'lessons', name: 'MLessons', component: () => import('../views/m/lessons.vue'), meta: { public: true, titleKey: 'mobile.study' } },
      { path: 'lesson-detail/:id', name: 'MLessonDetail', component: () => import('../views/m/lesson-detail.vue'), meta: { titleKey: 'mobile.study', hideTabbar: true } },
      { path: 'mine', name: 'MMine', component: () => import('../views/m/mine.vue'), meta: { titleKey: 'mobile.me' } },
      { path: 'admin/users', name: 'MAdminUsers', component: () => import('../views/m/admin-users.vue'), meta: { admin: true, titleKey: 'users.title' } }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 手机 UA 判断（与 tts.js 保持一致）
function isMobileUA() {
  if (typeof navigator === 'undefined') return false
  const ua = navigator.userAgent || ''
  if (/Mobi|Android|iPhone|iPad|iPod|Windows Phone|HarmonyOS/i.test(ua)) return true
  if (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1) return true
  return false
}

// 设备综合判断：UA 之外，再兜底「触屏 + 窄屏」——
// 部分手机浏览器开了「电脑模式/极速模式」后 UA 不含任何移动关键字
function isMobileDevice() {
  if (isMobileUA()) return true
  try {
    if ((navigator.maxTouchPoints || 0) > 1 && window.innerWidth < 700) return true
  } catch (e) { /* ignore */ }
  return false
}

// PC 路由 → 移动端路由映射（仅对未改造页面做跳转，不影响后台/admin）
const MOBILE_MAP = {
  '/': '/m/home',
  '/home': '/m/home',
  '/lessons': '/m/lessons',
  '/mine': '/m/mine'
}

router.beforeEach((to) => {
  const user = useUserStore()
  // 1) 登录校验（移动端未登录访问受限页 → PC 登录页，登录后回跳）
  if (!to.meta.public && !user.isLogin) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && user.user?.role !== 'admin') {
    return { name: 'Home' }
  }
  // 2) 移动端自动跳转 /m（用户在「我的」页主动选过「使用电脑版」则尊重，桌面版有悬浮按钮切回）
  if (isMobileDevice() && localStorage.getItem('viewMode') !== 'desktop') {
    if (to.path.startsWith('/m/')) return true
    if (to.name === 'LessonDetail') return { path: `/m/lesson-detail/${to.params.id}`, query: to.query }
    const m = MOBILE_MAP[to.path]
    if (m) return { path: m, query: to.query }
  }
  return true
})

export default router
