<template>
  <el-container class="layout">
    <el-aside :width="effectiveCollapsed ? '64px' : '220px'" class="aside">
      <div class="logo">{{ effectiveCollapsed ? '📚' : '📚 GRE 词汇' }}</div>
      <el-menu
        :default-active="activeMenu"
        router
        :collapse="effectiveCollapsed"
        :collapse-transition="false"
        class="menu"
      >
        <el-menu-item index="/home"><el-icon><HomeFilled/></el-icon><span>首页</span></el-menu-item>
        <el-menu-item index="/lessons"><el-icon><Reading/></el-icon><span>课时目录</span></el-menu-item>
        <el-menu-item index="/mine"><el-icon><User/></el-icon><span>我的</span></el-menu-item>
        <el-menu-item index="/record-guide"><el-icon><Document/></el-icon><span>录制指引</span></el-menu-item>
        <el-menu-item v-if="isAdmin" index="/admin/lessons"><el-icon><Setting/></el-icon><span>后台管理</span></el-menu-item>
        <el-menu-item v-if="isAdmin" index="/admin/users"><el-icon><UserFilled/></el-icon><span>用户管理</span></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="left">
          <el-button text class="collapse-btn" @click="ui.toggleSidebar()" :title="effectiveCollapsed ? '展开菜单' : '收起菜单'">
            <el-icon size="20"><component :is="effectiveCollapsed ? Expand : Fold"/></el-icon>
          </el-button>
          <span class="title">{{ $t('app.title') }}</span>
        </div>
        <div class="user">
          <el-dropdown trigger="click" @command="setLang">
            <el-button text class="lang-btn">🌐 {{ currentLangLabel }}</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="l in langList" :key="l.code" :command="l.code" :class="{ active: l.code === current }">{{ l.label }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button
            v-if="showMobileSwitch"
            class="mobile-switch"
            size="small"
            round
            color="#ff6699"
            @click="backToMobile"
          >📱 手机版</el-button>
          <el-button text class="theme-btn" :title="ui.theme === 'dark' ? '切换到浅色' : '切换到深色'" @click="ui.toggleTheme()">
            <el-icon size="18"><component :is="ui.theme === 'dark' ? Sunny : Moon"/></el-icon>
          </el-button>
          <el-tag v-if="user" size="small" type="info">{{ user.nickname || user.username }}</el-tag>
          <el-button text @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <!-- 待审核用户闪动提示（仅管理员，点击进入用户管理） -->
        <div v-if="flash && pendingCount > 0" class="pending-flash" @click="goAdminUsers">
          <span class="pending-text blink">🔔 {{ $t('mobile.pendingTip', { n: pendingCount }) }}</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { useUiStore } from '../store/ui'
import { useLang } from '../composables/useLang'
import { useAdminNotify } from '../composables/useAdminNotify'
import { computed as vueComputed } from 'vue'
import {
  HomeFilled, Reading, User, Setting, Expand, Fold, Sunny, Moon, Document, UserFilled, ArrowRight
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const ui = useUiStore()
const user = computed(() => userStore.user)
const isAdmin = computed(() => user.value?.role === 'admin')
const activeMenu = computed(() => '/' + (route.path.split('/')[1] || 'home'))
const effectiveCollapsed = computed(() => ui.effectiveCollapsed)

// 管理员：待审核轮询 + 叮咚音 + 闪动提示（与手机版共用逻辑）
const { pendingCount, flash, clearFlash } = useAdminNotify()

// 多语言切换
const { current, list: langList, setLang } = useLang()
const currentLangLabel = computed(() => langList.find((l) => l.code === current.value)?.label || '中文')

function onResize() {
  ui.setMobile(window.innerWidth <= 768)
}
onMounted(() => {
  onResize()
  window.addEventListener('resize', onResize)
})
onUnmounted(() => window.removeEventListener('resize', onResize))

function logout() {
  userStore.logout()
  router.push('/login')
}

function goAdminUsers() {
  clearFlash()
  router.push('/admin/users')
}

// 手机设备访问却停在电脑版（viewMode=desktop）时，显示切回手机版的悬浮按钮
const showMobileSwitch = ref(false)
function checkMobileSwitch() {
  const ua = /Mobi|Android|iPhone|iPad|iPod|Windows Phone|HarmonyOS/i.test(navigator.userAgent || '')
    || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
    || ((navigator.maxTouchPoints || 0) > 1 && window.innerWidth < 700)
  showMobileSwitch.value = ua && localStorage.getItem('viewMode') === 'desktop'
}
function backToMobile() {
  localStorage.removeItem('viewMode')
  router.replace('/m/home')
}
onMounted(() => { checkMobileSwitch() })
</script>

<style scoped>
.layout { height: 100%; }
.pending-flash {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 12px; padding: 10px 14px;
  background: #fff7e6; border: 1px solid #ffd591; border-radius: 8px;
  color: #d46b08; font-size: 14px; cursor: pointer; user-select: none;
}
.pending-text.blink {
  animation: pending-blink 1s step-start infinite;
  font-weight: 600;
}
@keyframes pending-blink {
  50% { opacity: 0.15; }
}
.aside {
  background: var(--gre-sidebar-bg);
  color: var(--gre-sidebar-text);
  transition: width .2s ease;
  overflow-x: hidden;
}
.logo {
  font-size: 18px;
  font-weight: 700;
  padding: 18px 16px;
  border-bottom: 1px solid var(--gre-sidebar-border);
  white-space: nowrap;
}
.menu { background: var(--gre-sidebar-bg); border-right: none; font-size: calc(var(--el-font-size-base, 14px) * 1.08); }
.menu :deep(.el-menu-item) { color: var(--gre-sidebar-text); font-size: inherit; }
.menu :deep(.el-menu-item.is-active) { background: var(--gre-sidebar-active); color: #fff; }
.menu :deep(.el-sub-menu__title) { font-size: inherit; }
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--gre-surface);
  border-bottom: 1px solid var(--gre-border);
}
.left { display: flex; align-items: center; gap: 8px; }
.title { font-weight: 600; color: var(--gre-text); white-space: nowrap; }
.user { display: flex; align-items: center; gap: 10px; }
.main { padding: 18px; background: var(--gre-bg); position: relative; }
.mobile-switch { font-weight: 600; letter-spacing: .5px; }
</style>
