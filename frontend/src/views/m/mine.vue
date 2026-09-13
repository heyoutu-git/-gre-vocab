<template>
  <div class="m-mine">
    <div class="profile">
      <van-icon name="manager-o" class="avatar" />
      <div class="info" v-if="userStore.isLogin">
        <div class="name">{{ userStore.user?.nickname || userStore.user?.username }}</div>
        <div class="role">{{ roleText }}</div>
      </div>
      <div class="info" v-else>
        <div class="name">{{ $t('mine.notLogin') }}</div>
        <div class="role">{{ $t('mine.loginToSync') }}</div>
      </div>
    </div>

    <van-cell-group inset>
      <van-cell v-if="resumeItem" :title="$t('mine.continueLearn')" :label="resumeItem.lessonTitle" icon="clock-o" is-link @click="goResume" />
      <van-cell :title="$t('mine.progress')" :label="progressLabel" icon="bar-chart-o" :to="userStore.isLogin ? '/m/lessons' : undefined" is-link />
      <van-cell :title="$t('mine.engine')" :label="engineName" icon="volume-o" @click="showEngine = true" is-link />
      <van-cell :title="$t('mine.voiceMode')" :label="kokoroPathName" icon="music-o" @click="showPath = true" is-link />
      <van-cell :title="$t('nav.lang')" :label="currentLangLabel" icon="exchange" @click="showLang = true" is-link />
      <van-cell :title="$t('mine.useDesktop')" :label="$t('mine.useDesktopTip')" icon="desktop-o" @click="useDesktop" is-link />
      <van-cell v-if="userStore.isLogin" :title="$t('mine.logout')" icon="logout" @click="onLogout" is-link />
      <van-cell v-else :title="$t('mine.login')" icon="user-circle-o" @click="goLogin" is-link />
    </van-cell-group>

    <van-popup v-model:show="showEngine" position="bottom" round>
      <van-picker
        :columns="engineColumns"
        :model-value="[engineSel]"
        @confirm="onEnginePick"
        @cancel="showEngine = false"
        show-toolbar
        :title="$t('mine.engineTitle')"
      />
    </van-popup>

    <van-popup v-model:show="showPath" position="bottom" round>
      <van-picker
        :columns="pathColumns"
        :model-value="[kokoroPath]"
        @confirm="onPathPick"
        @cancel="showPath = false"
        show-toolbar
        :title="$t('mine.voiceModeTitle')"
      />
    </van-popup>

    <van-popup v-model:show="showLang" position="bottom" round>
      <van-picker
        :columns="langColumns"
        :model-value="[current]"
        @confirm="onLangPick"
        @cancel="showLang = false"
        show-toolbar
        :title="$t('nav.lang')"
      />
    </van-popup>

    <div class="tip">
      {{ $t('mine.tip') }}
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'
import { lessonApi, bookApi, learningApi } from '../../api'
import { getEngine, getAvailableEngines, setEngine } from '../../utils/tts'
import { useLang } from '../../composables/useLang'
import { useI18n } from 'vue-i18n'

const router = useRouter()
const userStore = useUserStore()

// ---- 继续学习：最近学的未完成一课 ----
const resumeItem = ref(null)
onMounted(async () => {
  if (!userStore.isLogin) return
  try { const { data } = await learningApi.resume(); resumeItem.value = data || null } catch (e) { /* ignore */ }
})
function goResume() {
  if (resumeItem.value) router.push(`/m/lesson-detail/${resumeItem.value.lessonId}`)
}

const knownKey = 'gre-known'
const favKey = 'gre-fav'
const knownCount = ref(0)
const favCount = ref(0)
const doneCount = ref(0)
const progressLabel = computed(() => {
  const known = `${t('mine.known')} ${knownCount.value}`
  const fav = `${t('mine.fav')} ${favCount.value}`
  const done = `${t('mine.doneLessons')} ${doneCount.value}`
  return `${done} · ${known} · ${fav}`
})

const engineSel = ref(getEngine())
const { t } = useI18n()
const autoEngineName = computed(() => t('lesson.auto'))
const engineList = ref([{ code: 'auto', name: '自动' }])
const showEngine = ref(false)
const engineColumns = computed(() => engineList.value.map((e) => ({ text: e.code === 'auto' ? autoEngineName.value : e.name, value: e.code })))
const engineName = computed(() => {
  const e = engineList.value.find((x) => x.code === engineSel.value)
  return e ? (e.code === 'auto' ? autoEngineName.value : e.name) : autoEngineName.value
})
function onEnginePick({ selectedValues }) {
  engineSel.value = selectedValues[0]
  setEngine(engineSel.value)
  showEngine.value = false
}

// Kokoro 合成方式：''=跟随设备（桌面离线优先/手机服务器优先）、'offline'=离线优先、'server'=服务器优先
const kokoroPath = ref(localStorage.getItem('ttsKokoroPath') || '')
const showPath = ref(false)
const pathColumns = computed(() => [
  { text: t('mine.voiceFollow'), value: '' },
  { text: t('mine.voiceOffline'), value: 'offline' },
  { text: t('mine.voiceServer'), value: 'server' },
])
const kokoroPathName = computed(() => {
  const p = pathColumns.value.find((x) => x.value === kokoroPath.value)
  return p ? p.text : pathColumns.value[0].text
})
function onPathPick({ selectedValues }) {
  kokoroPath.value = selectedValues[0]
  localStorage.setItem('ttsKokoroPath', kokoroPath.value)
  showPath.value = false
}

// 语言切换
const { current, list: langList, setLang } = useLang()
const showLang = ref(false)
const langColumns = langList.map((l) => ({ text: l.label, value: l.code }))
const currentLangLabel = computed(() => langList.find((l) => l.code === current.value)?.label || '简体中文')
function onLangPick({ selectedValues }) {
  setLang(selectedValues[0])
  showLang.value = false
}

const roleText = computed(() => {
  const r = userStore.user?.role
  return r === 'admin' ? t('mine.roleAdmin') : t('mine.roleStudent')
})

function goLogin() { router.push({ name: 'Login', query: { redirect: '/m/mine' } }) }
function onLogout() {
  userStore.logout()
  router.replace('/m/home')
}
function useDesktop() {
  localStorage.setItem('viewMode', 'desktop')
  // 刷新后路由守卫不再跳移动端
  router.replace('/home')
}

onMounted(() => {
  engineList.value = [
    { code: 'auto', name: '自动' },
    ...getAvailableEngines().map((e) => ({ code: e.code, name: e.name }))
  ]
  try {
    knownCount.value = (JSON.parse(localStorage.getItem(knownKey) || '[]')).length
    favCount.value = (JSON.parse(localStorage.getItem(favKey) || '[]')).length
  } catch (e) { /* ignore */ }
  // 服务端学习进度（跨端同步）：已完成课时数
  if (userStore.isLogin) {
    learningApi.progressCount().then(({ data }) => { doneCount.value = data || 0 }).catch(() => {})
  }
})
</script>

<style scoped>
.m-mine { padding: 12px; }
.profile {
  display: flex; align-items: center; gap: 14px;
  background: linear-gradient(135deg, #4f6df5, #6f8bff);
  color: #fff; border-radius: 12px; padding: 18px 16px; margin-bottom: 12px;
}
.profile .avatar { font-size: 44px; }
.profile .name { font-size: 18px; font-weight: 700; }
.profile .role { font-size: 12px; opacity: .9; margin-top: 2px; }
.tip {
  margin: 14px 12px; font-size: 12px; color: #969799; line-height: 1.7;
  background: #fff; border-radius: 10px; padding: 12px 14px;
}
</style>
