<template>
  <div class="m-layout">
    <van-nav-bar
      :title="navTitle"
      fixed
      placeholder
      :left-arrow="!!route.meta.hideTabbar"
      @click-left="onBack"
    />

    <!-- 待审核用户闪动提示（仅管理员，点击进入用户管理） -->
    <div v-if="flash && pendingCount > 0" class="pending-flash" @click="goAdminUsers">
      <span class="pending-text blink">🔔 {{ $t('mobile.pendingTip', { n: pendingCount }) }}</span>
      <van-icon name="arrow" />
    </div>

    <div class="m-body">
      <router-view v-slot="{ Component }">
        <keep-alive include="MHome,MLessons,MMine">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </div>

    <van-tabbar v-if="!route.meta.hideTabbar" route fixed placeholder>
      <van-tabbar-item to="/m/home" icon="books-o">{{ libraryLabel }}</van-tabbar-item>
      <van-tabbar-item to="/m/lessons" icon="orders-o">{{ studyLabel }}</van-tabbar-item>
      <van-tabbar-item v-if="isAdmin" to="/m/admin/users" icon="manager-o">{{ adminLabel }}</van-tabbar-item>
      <van-tabbar-item to="/m/mine" icon="user-o">{{ meLabel }}</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '../store/user'
import { useAdminNotify } from '../composables/useAdminNotify'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()

// 管理员：待审核轮询 + 叮咚音 + 闪动提示
const isAdmin = computed(() => userStore.isLogin && userStore.user?.role === 'admin')
const { pendingCount, flash, clearFlash } = useAdminNotify()
function goAdminUsers() {
  clearFlash()
  router.push('/m/admin/users')
}

// 路由 meta.title 为 i18n key（mobile.*），跟随语言切换
const navTitle = computed(() => {
  const key = route.meta && route.meta.titleKey
  return key ? t(key) : (route.meta.title || t('app.title'))
})
const libraryLabel = computed(() => t('mobile.library'))
const studyLabel = computed(() => t('mobile.study'))
const meLabel = computed(() => t('mobile.me'))
const adminLabel = computed(() => t('nav.admin'))

const onBack = () => {
  if (window.history.length > 1) router.back()
  else router.replace('/m/home')
}
</script>

<style scoped>
.m-layout {
  min-height: 100vh;
  background: #f7f8fa;
}
.m-body {
  /* 让内容不被底部 tabbar 遮挡 */
  padding-bottom: 60px;
}
.pending-flash {
  display: flex; align-items: center; justify-content: space-between;
  margin: 8px 12px 0; padding: 10px 14px;
  background: #fff7e6; border: 1px solid #ffd591; border-radius: 10px;
  color: #d46b08; font-size: 14px; cursor: pointer;
}
.pending-text.blink {
  animation: blink 1s step-start infinite;
  font-weight: 600;
}
@keyframes blink {
  50% { opacity: 0.15; }
}
</style>
