<template>
  <div class="detail" v-loading="loading" ref="rootEl">
    <ReadingView v-if="bookType === 2" :lesson-id="route.params.id" :title="lesson?.title || ''" :book-id="lesson?.bookId" />

    <template v-else>
    <div class="topbar">
      <div class="bar">
        <div>
          <el-button text @click="goBackLessons">{{ $t('lesson.back') }}</el-button>
          <h2 style="display:inline; margin-left:8px;">{{ lesson?.title }}</h2>
          <span v-if="prevLesson || nextLesson" class="lesson-nav-top">
            <el-button v-if="prevLesson" size="small" @click="goNavLesson(prevLesson)">{{ $t('lesson.prevLesson') }}</el-button>
            <el-button v-if="nextLesson" size="small" type="primary" @click="goNavLesson(nextLesson)">{{ $t('lesson.nextLesson') }}</el-button>
          </span>
        </div>
        <div class="tools">
          <el-input v-model="kw" :placeholder="$t('lesson.search')" clearable style="width: 200px" :disabled="ttsState.active" />
          <el-select v-model="engineSel" style="width: 120px" :disabled="ttsState.active" :title="$t('lesson.engine')">
            <el-option v-for="e in engineList" :key="e.code" :label="e.name" :value="e.code" />
          </el-select>
          <div class="btn-group">
            <el-button :type="hideKnown ? 'primary' : 'default'" @click="hideKnown = !hideKnown" :disabled="ttsState.active">
              <span class="long">{{ hideKnown ? $t('lesson.showAll') : $t('lesson.hideKnown') }}</span>
              <span class="short">{{ hideKnown ? $t('lesson.allShort') : $t('lesson.hideShort') }}</span>
            </el-button>
            <el-button @click="speakWords" :disabled="!supported || ttsState.active">
              <span class="long">{{ $t('lesson.speakWords') }}</span>
              <span class="short">{{ $t('lesson.wordsShort') }}</span>
            </el-button>
            <el-button @click="speakAll" :disabled="!supported || ttsState.active">
              <span class="long">{{ $t('lesson.speakPage') }}</span>
              <span class="short">{{ $t('lesson.pageShort') }}</span>
            </el-button>
            <el-button :type="lessonDone ? 'success' : 'default'" :disabled="!userStore.isLogin || lessonDone" @click="markDone">
              {{ lessonDone ? $t('lesson.done') : $t('lesson.markDone') }}
            </el-button>
          </div>
        </div>
      </div>

      <div v-if="ttsState.active" class="tts-controls">
        <el-checkbox v-model="autoScroll" size="small" class="auto-scroll-toggle" @click.stop @mousedown.stop>{{ $t('lesson.autoScroll') }}</el-checkbox>
        <el-radio-group v-model="loopMode" size="small" @change="onLoopChange" @click.stop @mousedown.stop>
          <el-radio-button label="none">{{ $t('lesson.loopNone') }}</el-radio-button>
          <el-radio-button label="lesson">{{ $t('lesson.loopLesson') }}</el-radio-button>
          <el-radio-button label="book">{{ $t('lesson.loopBook') }}</el-radio-button>
        </el-radio-group>
        <span class="tts-status">{{ ttsState.paused ? $t('lesson.paused') : $t('lesson.playing') }} {{ Math.max(ttsState.index + 1, 0) }}/{{ ttsState.total }}<template v-if="ttsState.detail"> · {{ $t('lesson.' + ttsState.detail) }}</template></span>
        <el-button size="small" @click="togglePause">{{ ttsState.paused ? $t('lesson.resume') : $t('lesson.pause') }}</el-button>
        <el-button size="small" @click="restartTTS">{{ $t('lesson.restart') }}</el-button>
        <el-button size="small" type="danger" @click="stopTTSAndClear">{{ $t('lesson.stop') }}</el-button>
      </div>
    </div>

    <el-alert v-if="kokoroLoading" type="info" :closable="false" style="margin-bottom:12px"
      :title="$t('lesson.kokoroTip', { pct: kokoroPct })" />

    <el-alert v-if="!supported" type="warning" :closable="false" style="margin-bottom:12px"
      :title="$t('lesson.noSupport')" />

    <div v-for="(v, i) in filtered" :key="v.id" :ref="(el) => setCardRef(v.id, el)" class="vocab-card"
      :class="{ done: isKnown(v.id), active: ttsState.active && ttsState.track && ttsState.index === i }">
      <div>
        <span class="vocab-word">{{ v.word }}</span>
        <span class="vocab-phon">/{{ v.phoneticIpa || v.phonetic }}/</span>
        <span class="vocab-pos" v-if="v.pos">{{ v.pos }}.</span>
        <span class="vocab-inflect" v-if="v.inflection">{{ v.inflection }}</span>
      </div>
      <div class="vocab-def">{{ v.definition }}</div>
      <div class="vocab-def-cn" v-if="v.meaningCn">{{ v.meaningCn }}</div>
      <div class="vocab-ex" v-if="v.example">{{ v.example }}</div>
      <div class="vocab-actions">
        <el-button size="small" :icon="Microphone" :disabled="!supported || ttsState.active" @click="speakOne(v.word)">{{ $t('lesson.readWord') }}</el-button>
        <el-button size="small" :icon="Microphone" v-if="v.example" :disabled="!supported || ttsState.active" @click="speakOne(v.example)">{{ $t('lesson.readExample') }}</el-button>
        <el-button size="small" :type="isKnown(v.id) ? 'success' : 'default'"
          @click="toggleKnown(v.id)">{{ isKnown(v.id) ? $t('lesson.known') : $t('lesson.markKnown') }}</el-button>
        <el-button size="small" text type="danger" @click="toggleFav(v)">
          {{ isFav(v.id) ? '★ ' + $t('lesson.favorite') : '☆ ' + $t('lesson.favorite') }}
        </el-button>
      </div>
    </div>

    <el-empty v-if="!loading && !filtered.length" :description="$t('lesson.noMatch')" />
    </template>

    <!-- 上一课 / 下一课导航（词汇课与阅读课共用；首课无上一课，末课无下一课） -->
    <div v-if="prevLesson || nextLesson" class="lesson-nav">
      <el-button v-if="prevLesson" @click="goNavLesson(prevLesson)">{{ $t('lesson.prevLesson') }}</el-button>
      <el-button v-if="nextLesson" type="primary" @click="goNavLesson(nextLesson)">{{ $t('lesson.nextLesson') }}</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Microphone } from '@element-plus/icons-vue'
import { lessonApi, bookApi, learningApi } from '../api'
import { useUserStore } from '../store/user'
import ReadingView from './ReadingView.vue'
import { useProgressReport, checkLessonResume } from '../composables/useProgressReport'
import { createScrollTracker } from '../utils/scroll-track'
import {
  speechSupported,
  onTTSState,
  playItems,
  pauseTTS,
  resumeTTS,
  stopTTS,
  restartTTS,
  setLoopMode,
  getLoopMode,
  onBookLoop,
  getCurrentOpts,
  getEngine,
  setEngine,
  getAvailableEngines,
} from '../utils/tts'

const route = useRoute()
const { t } = useI18n()
const router = useRouter()
const lesson = ref(null)
const vocabs = ref([])
const loading = ref(false)
const kw = ref('')
const hideKnown = ref(false)
const supported = speechSupported()
const bookType = ref(null)
const bookLessons = ref([])
const loopMode = ref(getLoopMode())
const ttsState = ref({ active: false, paused: false, index: -1, total: 0, track: false })
onTTSState((s) => { ttsState.value = s })
// ---- 学习进度自动上报（10s 节流，跨端存服务端）----
const { flush: flushProgress } = useProgressReport(() => route.params.id, ttsState)

// Kokoro 语音包加载进度提示（首次 92MB 下载 + wasm 推理需要等待，给用户明确反馈）
const kokoroLoading = ref(false)
const kokoroPct = ref(0)
function onKokoroProgress(e) {
  const d = e.detail || {}
  if (d.status === 'ready' || d.status === 'done') { kokoroLoading.value = false; return }
  if (d.status === 'progress' && d.file && d.file.includes('.onnx')) {
    kokoroLoading.value = true
    kokoroPct.value = Math.round(d.progress || 0)
  } else if (d.status === 'initiate' || d.status === 'download') {
    kokoroLoading.value = true
  }
}

// 发音引擎选择器（自动 / system / mespeak / tencent / kokoro；Kokoro 优先排前）
const engineSel = ref(getEngine())
const engineList = ref([{ code: 'auto', name: t('lesson.auto') }])
function buildEngineList() {
  const rest = getAvailableEngines()
    .slice()
    .sort((a, b) => (a.code === 'kokoro' ? -1 : b.code === 'kokoro' ? 1 : 0))
    .map((e) => ({ code: e.code, name: e.name }))
  engineList.value = [{ code: 'auto', name: t('lesson.auto') }, ...rest]
}
function onEngineChange() { setEngine(engineSel.value) }

// 返回课时目录，带上当前 bookId 让列表页恢复选中的书本
function goBackLessons() {
  const bid = lesson.value?.bookId
  router.push(bid ? { name: 'Lessons', query: { bookId: String(bid) } } : { name: 'Lessons' })
}

// 跟读自动滚屏：记录每张卡片 DOM，当前词条变化时平滑滚动到可视区中央
const autoScroll = ref(true)
const rootEl = ref(null)
const tracker = createScrollTracker()
const cardEls = {}
function setCardRef(id, el) {
  if (el) cardEls[id] = el
  else delete cardEls[id]
}
function scrollToCurrent() {
  if (!ttsState.value.active || !ttsState.value.track || !autoScroll.value) { tracker.stop(); return }
  const idx = ttsState.value.index
  if (idx < 0) return
  const v = filtered.value[idx]
  if (!v) return
  // 词汇卡片可能是组件 ref（$el 取真实 DOM），ensureVisible 内部已兼容
  const headerEl = rootEl.value ? rootEl.value.querySelector('.topbar') : null
  tracker.ensureVisible(cardEls[v.id], headerEl, {
    isActive: () => ttsState.value.active && ttsState.value.track && autoScroll.value
  })
}
watch(
  () => [ttsState.value.active, ttsState.value.index, ttsState.value.track],
  () => nextTick(scrollToCurrent)
)
watch(autoScroll, (on) => { if (!on) tracker.stop() })

// 屏幕尺寸变化（横竖屏切换 / 窗口缩放）后，强制把高亮项滚回中间
let resizeTimer = null
function onResize() {
  clearTimeout(resizeTimer)
  // 延时稍长，等 Element Plus 按钮组换行/重排完成
  resizeTimer = setTimeout(() => nextTick(scrollToCurrent), 250)
}
onMounted(() => {
  // 清掉上个页面残留的播放会话（如 A 页暂停后切到本页），避免「继续」播到别页内容
  if (ttsState.value.active) stopTTS()
  window.addEventListener('resize', onResize)
  window.addEventListener('orientationchange', onResize)
  buildEngineList()
  // /api/tts/engines 为异步拉取，就绪后刷新引擎列表
  window.addEventListener('gre-engines-updated', buildEngineList)
  window.addEventListener('kokoro-progress', onKokoroProgress)
})
onUnmounted(() => {
  // 播放中/暂停中离开页面一律终止会话，防止串台
  stopTTS()
  flushProgress()
  window.removeEventListener('resize', onResize)
  window.removeEventListener('orientationchange', onResize)
  window.removeEventListener('gre-engines-updated', buildEngineList)
  window.removeEventListener('kokoro-progress', onKokoroProgress)
  clearTimeout(resizeTimer)
  tracker.stop()
})

const favKey = 'gre-fav'
const knownKey = 'gre-known'
const favorites = ref(new Set(JSON.parse(localStorage.getItem(favKey) || '[]')))
const known = ref(new Set(JSON.parse(localStorage.getItem(knownKey) || '[]')))

const filtered = computed(() =>
  vocabs.value.filter((v) => {
    if (hideKnown.value && known.value.has(v.id)) return false
    if (!kw.value) return true
    const k = kw.value.toLowerCase()
    return (v.word || '').toLowerCase().includes(k) || (v.definition || '').toLowerCase().includes(k)
  })
)

function speakWords() {
  const list = filtered.value
  if (!list.length) return
  const items = list.map((v, i) => ({ text: v.word, wordIndex: i }))
  playItems(items, { totalWords: list.length, track: true, speakMode: 'words' })
}

function speakAll() {
  const list = filtered.value
  if (!list.length) return
  const items = []
  for (let i = 0; i < list.length; i++) {
    const v = list[i]
    items.push({ text: v.word, wordIndex: i })
    if (v.example) items.push({ text: v.example, wordIndex: i })
  }
  playItems(items, { totalWords: list.length, track: true, speakMode: 'all' })
}

// 从第 n 个词续播（「继续上次学习」）
function speakWordsFrom(n) {
  const list = filtered.value
  if (!list.length) return
  const items = list.map((v, i) => ({ text: v.word, wordIndex: i }))
  const start = items.findIndex((it) => it.wordIndex >= n)
  playItems(items, { totalWords: list.length, track: true, speakMode: 'words', startIndex: start < 0 ? 0 : start })
}

// 进入页面/换课时：有历史位置且未完成 → 提示继续
async function promptResume(lid) {
  const n = await checkLessonResume(() => lid, userStore)
  if (!n || bookType.value === 2) return // 阅读课由内嵌 ReadingView 处理
  ElMessageBox.confirm(t('lesson.resumeBody', { n }), t('lesson.resumeTitle'), {
    confirmButtonText: t('lesson.resumeYes'),
    cancelButtonText: t('lesson.resumeNo'),
    type: 'info'
  }).then(() => speakWordsFrom(n)).catch(() => {})
}

function onLoopChange(mode) {
  setLoopMode(mode)
}

// 用 watch 兜底同步：防止 el-radio-group change 事件在某些场景下未触发
watch(loopMode, (mode) => {
  setLoopMode(mode)
})

function autoPlayCurrent(mode) {
  // 等 DOM 渲染完再开始，避免 auto-scroll 找不到卡片
  nextTick(() => {
    if (mode === 'words') speakWords()
    else if (mode === 'all') speakAll()
  })
}

async function loadBookLessons() {
  if (bookLessons.value.length) return bookLessons.value
  const bid = lesson.value?.bookId
  if (!bid) return []
  try {
    const { data: ls } = await lessonApi.list(bid)
    bookLessons.value = ls || []
  } catch (e) { /* ignore */ }
  return bookLessons.value
}

// ---- 上一课 / 下一课导航（按本书课时列表顺序） ----
function navNeighbor(offset) {
  const ls = bookLessons.value
  const idx = ls.findIndex((l) => String(l.id) === String(route.params.id))
  if (idx < 0) return null
  return ls[idx + offset] || null
}
const prevLesson = computed(() => navNeighbor(-1))
const nextLesson = computed(() => navNeighbor(1))
function goNavLesson(l) {
  stopTTS()
  // 清掉可能的跨课续播标记，避免误触发自动播放
  try { sessionStorage.removeItem('gre-book-loop') } catch (e) { /* ignore */ }
  router.push(`/lessons/${l.id}`)
  // 换课后回到页面顶部，避免停留在底部导航位置看新课内容
  nextTick(() => {
    const main = document.querySelector('.el-main')
    if (main) main.scrollTop = 0
    window.scrollTo(0, 0)
  })
}

async function handleBookLoop() {
  // 本书循环：找下一课；没有则回到本书第一课
  const lessons = await loadBookLessons()
  if (!lessons.length) { stopTTS(); return }
  const currentId = Number(route.params.id)
  const idx = lessons.findIndex((l) => Number(l.id) === currentId)
  const next = lessons[idx + 1] || lessons[0]
  if (!next) { stopTTS(); return }
  const opts = getCurrentOpts()
  // 保存 loop 状态，供下一课页面读取后继续播放
  try {
    sessionStorage.setItem('gre-book-loop', JSON.stringify({
      lessonId: next.id,
      speakMode: opts.speakMode || 'words'
    }))
  } catch (e) { /* ignore */ }
  // 清理当前播放状态（停掉旧 audio / 重置 sessionId），新课时再自动续播
  stopTTS()
  router.push(`/lessons/${next.id}`)
}

// 重新加载某一课时数据（本课 + 词表 + 本书课时列表）
async function loadLesson(lid) {
  loading.value = true
  try {
    const [l, vs] = await Promise.all([lessonApi.get(lid), lessonApi.vocabularies(lid)])
    lesson.value = l.data
    vocabs.value = vs.data || []
    if (l.data?.bookId) {
      try {
        const [{ data: b }, { data: ls }] = await Promise.all([
          bookApi.get(l.data.bookId),
          lessonApi.list(l.data.bookId)
        ])
        bookType.value = b.bookType
        bookLessons.value = ls || []
      } catch (e) { /* 忽略 */ }
    }
  } catch (e) { /* 公开接口 */ } finally { loading.value = false }
  refreshDone(lid)
  promptResume(lid)
}

// 学习进度（服务端存储，电脑/手机跨端同步）
const userStore = useUserStore()
const lessonDone = ref(false)
async function refreshDone(lid) {
  lessonDone.value = false
  if (!userStore.isLogin || !lid) return
  try {
    const { data } = await learningApi.getProgress(lid)
    lessonDone.value = !!data
  } catch (e) { /* ignore */ }
}
async function markDone() {
  const lid = route.params.id
  if (lessonDone.value || !lid) return
  try {
    await learningApi.markProgress(lid)
    lessonDone.value = true
    ElMessage.success(t('lesson.doneTip'))
  } catch (e) { /* http 拦截器已提示 */ }
}

// 消费跨课续播标记（同步移除，避免 onMounted 与 watch 重复消费）
function consumePendingAutoPlay(lid) {
  // 阅读书由 ReadingView 自己消费跨课续播标记
  if (bookType.value === 2) return
  try {
    const raw = sessionStorage.getItem('gre-book-loop')
    if (!raw) return
    const { lessonId, speakMode } = JSON.parse(raw)
    if (String(lessonId) !== String(lid)) return
    sessionStorage.removeItem('gre-book-loop')
    // 等 DOM 渲染完成再出声，避免被浏览器拦截 / 找不到卡片
    setTimeout(() => autoPlayCurrent(speakMode), 350)
  } catch (e) { /* ignore */ }
}

function speakOne(text) {
  if (!text) return
  playItems([text])
}

function togglePause() {
  if (ttsState.value.paused) resumeTTS()
  else pauseTTS()
}

function stopTTSAndClear() {
  try { sessionStorage.removeItem('gre-book-loop') } catch (e) { /* ignore */ }
  stopTTS()
}

function isKnown(id) { return known.value.has(id) }
function toggleKnown(id) {
  if (known.value.has(id)) known.value.delete(id); else known.value.add(id)
  localStorage.setItem(knownKey, JSON.stringify([...known.value]))
}
function isFav(id) { return favorites.value.has(id) }
function toggleFav(v) {
  if (favorites.value.has(v.id)) { favorites.value.delete(v.id); ElMessage.info(t('lesson.unfavorited')) }
  else { favorites.value.add(v.id); ElMessage.success(t('lesson.favorited') + v.word) }
  localStorage.setItem(favKey, JSON.stringify([...favorites.value]))
}

onMounted(async () => {
  const lid = route.params.id
  await loadLesson(lid)
  // 本书循环跨课自动播放：检查 sessionStorage 中是否有待播放标记
  consumePendingAutoPlay(lid)
})

// 路由参数变化（如本书循环跳到下一课）时 Vue 会复用组件实例，onMounted 不再执行，
// 必须在此显式重载新课时数据并续播，否则页面停留在旧课、播放停住。
watch(
  () => route.params.id,
  async (newId) => {
    // 组件复用（/lessons/:id 只变参数）不触发卸载，必须显式终止旧课会话
    stopTTS()
    await loadLesson(newId)
    consumePendingAutoPlay(newId)
  }
)

// 注册本书循环处理器
onBookLoop(handleBookLoop)
</script>

<style scoped>
.topbar {
  position: sticky;
  /* 吸顶点用负值抵消负 margin：sticky 的 top 按外边距盒对齐，
     top:0 会让边框盒停在 18px 下方，滑动文字会从空隙穿出 */
  top: -18px;
  z-index: 20;
  /* 负 margin 抵消 .el-main 的内边距，使控制条通栏贴顶、始终在屏幕顶部 */
  margin: -18px -18px 14px;
  padding: 0 18px 0;
  background: var(--gre-bg);
  border-bottom: 1px solid var(--gre-border);
  box-shadow: 0 4px 10px -6px rgba(0, 0, 0, .18);
}
.bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.tools { display: flex; gap: 8px; flex-wrap: wrap; }
.btn-group { display: flex; gap: 8px; flex-wrap: nowrap; }
.btn-group .short { display: none; }
.tts-controls { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; padding: 8px 12px; background: var(--gre-surface); border: 1px solid var(--gre-border); border-radius: 8px; }
.auto-scroll-toggle { margin-right: 4px; }
.tts-status { font-size: 13px; color: var(--gre-primary); font-weight: 600; }
.lesson-nav { display: flex; justify-content: space-between; gap: 12px; margin: 22px 0 8px; }
.lesson-nav .el-button { min-width: 132px; }
.lesson-nav-top { display: inline-flex; gap: 6px; margin-left: 10px; vertical-align: middle; }
.done { opacity: .55; }
.vocab-def-cn {
  font-size: 14px;
  color: var(--gre-primary, #5b6ea0);
  margin: 4px 0 2px;
  line-height: 1.5;
  /* 中文读感：浅一档色调，与上方英文释义行对照 */
  font-weight: 500;
  /* ECDICT 用 \n 分隔多义项；这里当文本里的真换行渲染 */
  white-space: pre-wrap;
}
@media (prefers-color-scheme: dark) {
  .vocab-def-cn { color: #93a3c4; }
}
/* 小屏：.el-main 内边距为 12px，对应更小的负 margin 抵消 */
@media (max-width: 768px) {
  .topbar { margin: -12px -12px 10px; padding: 0 12px 0; top: -12px; }

  /* 标题与工具分行，避免标题折行和工具按钮挤在一行 */
  .bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    margin-bottom: 10px;
  }
  .bar > div:first-child {
    width: 100%;
  }
  .bar h2 {
    display: block;
    margin-left: 0;
    margin-top: 4px;
    font-size: 1.05rem;
    line-height: 1.35;
  }
  .bar .el-button:first-child {
    font-size: 13px;
    padding-left: 0;
  }

  /* 工具行：搜索框独占一行，三个按钮单行紧凑排列 */
  .tools {
    width: 100%;
    gap: 6px;
  }
  .tools .el-input {
    width: 100% !important;
    flex: 1 1 100%;
  }
  .tools .el-input__inner {
    height: 32px;
    line-height: 32px;
  }
  .btn-group {
    width: 100%;
    gap: 4px;
  }
  .btn-group .el-button {
    flex: 1 1 0;
    padding: 4px 6px;
    font-size: 11px;
  }
  .btn-group .long { display: none; }
  .btn-group .short { display: inline; }

  /* 朗读控制条更紧凑，避免挡住高亮卡片 */
  .tts-controls {
    gap: 4px;
    padding: 4px 6px;
    margin-bottom: 8px;
  }
  .tts-status {
    font-size: 11px;
  }
  .auto-scroll-toggle {
    margin-right: 0;
  }
  .tts-controls .el-button,
  .tts-controls .el-radio-button__inner {
    padding: 3px 6px;
    font-size: 11px;
  }
}
</style>
