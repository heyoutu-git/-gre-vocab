<template>
  <div class="m-detail" ref="rootEl">
    <div class="head">
    <div class="title-row">
      <van-icon name="arrow-left" class="sbtn" @click="goBack" />
      <div class="title">{{ lesson?.title || $t('lessons.lessons') }}</div>
      <van-icon v-if="bookType == null || bookType !== 2" name="setting-o" class="sbtn" @click="showSettings = true" />
    </div>

    <template v-if="bookType != null && bookType !== 2">
    <div class="chipbar">
      <span class="sq-btn" :class="{ disabled: !prevLesson }" @click="prevLesson && goNavLesson(prevLesson)">
        <van-icon name="arrow-left" />
      </span>
      <span class="sq-btn" :class="{ disabled: !nextLesson }" @click="nextLesson && goNavLesson(nextLesson)">
        <van-icon name="arrow" />
      </span>
      <span class="chip" :class="{ on: autoScroll }" @click="autoScroll = !autoScroll">{{ $t('lesson.autoScroll') }}</span>
      <span class="chip" @click="showLoop = true">{{ loopLabel }} ▾</span>
      <van-button size="small" type="primary" class="play-btn" :disabled="!supported || ttsState.active" @click="playWords">{{ $t('lesson.speakWords') }}</van-button>
    </div>

    <div v-if="ttsState.active" class="tts-bar">
      <span class="status">{{ ttsState.paused ? $t('lesson.paused') : $t('lesson.playing') }} {{ Math.max(ttsState.index + 1, 0) }}/{{ ttsState.total }}<template v-if="ttsState.detail"> · {{ $t('lesson.' + ttsState.detail) }}</template></span>
      <div class="tts-btns">
        <van-button size="small" @click="togglePause">{{ ttsState.paused ? '▶' : '⏸' }}</van-button>
        <van-button size="small" @click="restartTTS">↺</van-button>
        <van-button size="small" @click="stopTTS">⏹</van-button>
      </div>
      </div>
    </template>
    </div>

    <ReadingView v-if="bookType === 2" :lesson-id="route.params.id" :title="lesson?.title || ''" :book-id="lesson?.bookId" :prev="prevLesson" :next="nextLesson" @nav="goNavLesson" />

    <template v-else-if="bookType != null">
    <van-notice-bar v-if="kokoroLoading" wrapable :scrollable="false" class="kokoro-tip"
      :text="$t('lesson.kokoroTip', { pct: kokoroPct })" />

    <van-cell-group inset>
      <van-cell
        v-for="(v, i) in filteredVocabs"
        :key="v.id"
        :ref="(el) => setCardRef(v.id, el)"
        :class="{ active: ttsState.active && ttsState.index === i, done: isKnown(v.id) }"
      >
        <template #title>
          <div class="vocab">
            <span class="word">{{ v.word }}</span>
            <span class="phon">/{{ v.phoneticIpa || v.phonetic }}/</span>
            <span class="pos" v-if="v.pos">{{ v.pos }}.</span>
          </div>
          <div class="def">{{ v.definition }}</div>
          <div class="def-cn" v-if="v.meaningCn">{{ v.meaningCn }}</div>
          <div class="ex" v-if="v.example">{{ v.example }}</div>
        </template>
        <template #right-icon>
          <div class="row-actions">
            <van-icon name="volume-o" @click="speakOne(v.word)" />
            <van-icon name="comment-o" v-if="v.example" @click="speakOne(v.example)" />
            <van-icon :name="isFav(v.id) ? 'star' : 'star-o'" :class="{ faved: isFav(v.id) }" @click="toggleFav(v)" />
            <van-icon :name="isKnown(v.id) ? 'passed' : 'circle'" :class="{ known: isKnown(v.id) }" @click="toggleKnown(v.id)" />
          </div>
        </template>
      </van-cell>
      <van-empty v-if="!loading && !vocabs.length" :description="$t('lesson.noWords')" />
    </van-cell-group>

    <van-loading v-if="loading" class="center" />
    <van-notice-bar v-if="!supported" :text="$t('lesson.noSupportShort')" />
    </template>
    <van-loading v-else class="center" />

    <!-- 设置弹层（低频操作收纳）：发音引擎 / 跟读全文 / 隐藏已掌握 / 完成本课 -->
    <van-popup v-model:show="showSettings" position="bottom" round class="settings-sheet">
      <div class="sheet-title">{{ $t('lesson.settings') }}</div>
      <van-field
        v-model="engineLabel"
        readonly
        is-link
        :label="$t('lesson.engine')"
        class="engine-field"
        @click="showEngine = true"
      />
      <div class="sheet-row">
        <van-button size="small" block :disabled="!supported || ttsState.active" @click="playAll">{{ $t('lesson.speakPage') }}</van-button>
        <van-button size="small" block :type="hideKnown ? 'primary' : 'default'" :disabled="ttsState.active" @click="hideKnown = !hideKnown">
          {{ hideKnown ? $t('lesson.showAll') : $t('lesson.hideKnown') }}
        </van-button>
      </div>
      <div class="sheet-row">
        <van-button size="small" block :type="lessonDone ? 'success' : 'default'" :icon="lessonDone ? 'success' : ''" :disabled="!isLogin || lessonDone" @click="markDone">
          {{ lessonDone ? $t('lesson.done') : $t('lesson.markDone') }}
        </van-button>
      </div>
    </van-popup>

    <!-- 发音引擎选择 -->
    <van-popup v-model:show="showEngine" position="bottom" round>
      <van-picker
        :columns="engineColumns"
        @confirm="onEnginePick"
        @cancel="showEngine = false"
        show-toolbar
        :title="$t('lesson.engine')"
      />
    </van-popup>

    <!-- 循环模式选择 -->
    <van-popup v-model:show="showLoop" position="bottom" round>
      <van-picker
        :columns="loopColumns"
        @confirm="onLoopPick"
        @cancel="showLoop = false"
        show-toolbar
        :title="$t('lesson.settings')"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { lessonApi, bookApi, learningApi } from '../../api'
import { showSuccessToast, showConfirmDialog } from 'vant'
import 'vant/es/toast/style'
import 'vant/es/dialog/style'
import { useUserStore } from '../../store/user'
import ReadingView from './reading.vue'
import { useProgressReport, checkLessonResume } from '../../composables/useProgressReport'
import { createScrollTracker } from '../../utils/scroll-track'
import {
  speechSupported,
  onTTSState,
  playItems,
  pauseTTS,
  resumeTTS,
  stopTTS,
  restartTTS,
  getEngine,
  getAvailableEngines,
  setEngine,
  getLoopMode,
  setLoopMode,
  onBookLoop,
  getCurrentOpts
} from '../../utils/tts'

const route = useRoute()
const router = useRouter()
const lesson = ref(null)
const vocabs = ref([])
const loading = ref(false)
const bookType = ref(null)
const supported = speechSupported()

const ttsState = ref({ active: false, paused: false, index: -1, total: 0, track: false })
onTTSState((s) => { ttsState.value = s })
// ---- 学习进度自动上报（10s 节流，跨端存服务端）----
const { flush: flushProgress } = useProgressReport(() => route.params.id, ttsState)

// 学习进度（服务端存储，电脑/手机跨端同步）
const userStore = useUserStore()
const isLogin = computed(() => userStore.isLogin)
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
  if (lessonDone.value || !route.params.id) return
  try {
    await learningApi.markProgress(route.params.id)
    lessonDone.value = true
    showSuccessToast(t('lesson.doneTip'))
  } catch (e) { /* http 拦截器已提示 */ }
}

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

// 引擎选择（auto + 后端开关列表；Kokoro 优先排前；engines 异步就绪后事件驱动刷新）
const autoEngineName = computed(() => t('lesson.auto'))
const engineSel = ref(getEngine())
const engineList = ref([{ code: 'auto', name: '自动' }])
const showEngine = ref(false)
const engineColumns = computed(() => engineList.value.map((e) => ({ text: e.code === 'auto' ? autoEngineName.value : e.name, value: e.code })))
const engineLabel = computed(() => {
  const e = engineList.value.find((x) => x.code === engineSel.value)
  return e ? (e.code === 'auto' ? autoEngineName.value : e.name) : autoEngineName.value
})
function buildEngineList() {
  const rest = getAvailableEngines()
    .slice()
    .sort((a, b) => (a.code === 'kokoro' ? -1 : b.code === 'kokoro' ? 1 : 0))
    .map((e) => ({ code: e.code, name: e.name }))
  engineList.value = [{ code: 'auto', name: '自动' }, ...rest]
}
function onEnginePick({ selectedValues }) {
  engineSel.value = selectedValues[0]
  setEngine(engineSel.value)
  showEngine.value = false
}

function goBack() {
  const bid = lesson.value?.bookId
  router.push(bid ? { path: '/m/lessons', query: { bookId: String(bid) } } : { path: '/m/lessons' })
}

function playWords() {
  const list = filteredVocabs.value
  if (!list.length) return
  const items = list.map((v, i) => ({ text: v.word, wordIndex: i }))
  playItems(items, { totalWords: list.length, track: true, speakMode: 'words' })
}
function playAll() {
  const list = filteredVocabs.value
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
function playWordsFrom(n) {
  const list = filteredVocabs.value
  if (!list.length) return
  const items = list.map((v, i) => ({ text: v.word, wordIndex: i }))
  const start = items.findIndex((it) => it.wordIndex >= n)
  playItems(items, { totalWords: list.length, track: true, speakMode: 'words', startIndex: start < 0 ? 0 : start })
}

// 进入页面/换课时：有历史位置且未完成 → 提示继续
// prompt=false：页内跳课（上一课/下一课）只静默记录访问，不弹窗
async function promptResume(lid, prompt = true) {
  const n = await checkLessonResume(() => lid, userStore, prompt)
  if (!n || bookType.value === 2) return // 阅读课由内嵌 ReadingView 处理
  showConfirmDialog({
    title: t('lesson.resumeTitle'),
    message: t('lesson.resumeBody', { n }),
    confirmButtonText: t('lesson.resumeYes'),
    cancelButtonText: t('lesson.resumeNo')
  }).then(() => playWordsFrom(n)).catch(() => {})
}
function speakOne(text) {
  if (!text) return
  playItems([text])
}
function togglePause() {
  if (ttsState.value.paused) resumeTTS()
  else pauseTTS()
}

// ---- 与电脑版对齐：已掌握/收藏 ----
const favKey = 'gre-fav'
const knownKey = 'gre-known'
const favorites = ref(new Set(safeParse(favKey)))
const known = ref(new Set(safeParse(knownKey)))
const hideKnown = ref(false)
const filteredVocabs = computed(() =>
  vocabs.value.filter((v) => !(hideKnown.value && known.value.has(v.id)))
)
function safeParse(key) {
  try { return JSON.parse(localStorage.getItem(key) || '[]') } catch (e) { return [] }
}
function isKnown(id) { return known.value.has(id) }
function toggleKnown(id) {
  if (known.value.has(id)) known.value.delete(id)
  else known.value.add(id)
  localStorage.setItem(knownKey, JSON.stringify([...known.value]))
}
function isFav(id) { return favorites.value.has(id) }
function toggleFav(v) {
  if (favorites.value.has(v.id)) favorites.value.delete(v.id)
  else favorites.value.add(v.id)
  localStorage.setItem(favKey, JSON.stringify([...favorites.value]))
}

// ---- 循环模式（与电脑版一致：不循环/本课循环/本书循环） ----
const loopMode = ref(getLoopMode())
const { t } = useI18n()
const loopOptions = computed(() => [
  { value: 'none', label: t('lesson.loopNone') },
  { value: 'lesson', label: t('lesson.loopLesson') },
  { value: 'book', label: t('lesson.loopBook') }
])
watch(loopMode, (m) => setLoopMode(m))
// 设置弹层 / 循环模式选择器
const showSettings = ref(false)
const showLoop = ref(false)
const loopLabel = computed(() => loopOptions.value.find((o) => o.value === loopMode.value)?.label || '')
const loopColumns = computed(() => loopOptions.value.map((o) => ({ text: o.label, value: o.value })))
function onLoopPick({ selectedValues }) {
  loopMode.value = selectedValues[0]
  showLoop.value = false
}

// ---- 自动滚屏：跟读时把当前词条滚到可视区中间 ----
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
  const v = filteredVocabs.value[ttsState.value.index]
  if (!v) return
  // van-cell 组件 ref 的真实 DOM 在 $el 上，ensureVisible 内部已兼容
  const headerEl = rootEl.value ? rootEl.value.querySelector('.head') : null
  tracker.ensureVisible(cardEls[v.id], headerEl, {
    isActive: () => ttsState.value.active && ttsState.value.track && autoScroll.value
  })
}
watch(
  () => [ttsState.value.active, ttsState.value.index, ttsState.value.track],
  () => nextTick(scrollToCurrent)
)
watch(autoScroll, (on) => { if (!on) tracker.stop() })

// ---- 本书循环：播完本书最后一课跳下一课并自动续播（跨课经 sessionStorage 传递） ----
const bookLessons = ref([])
let bookLessonsBookId = null
async function loadBookLessons() {
  const bid = lesson.value?.bookId
  if (!bid) return []
  if (bookLessonsBookId === bid && bookLessons.value.length) return bookLessons.value
  try {
    const { data: ls } = await lessonApi.list(bid)
    bookLessons.value = ls || []
    bookLessonsBookId = bid
  } catch (e) { /* ignore */ }
  return bookLessons.value
}
async function handleBookLoop() {
  const lessons = await loadBookLessons()
  if (!lessons.length) { stopTTS(); return }
  const currentId = Number(route.params.id)
  const idx = lessons.findIndex((l) => Number(l.id) === currentId)
  const next = lessons[idx + 1] || lessons[0]
  if (!next) { stopTTS(); return }
  const opts = getCurrentOpts()
  try {
    sessionStorage.setItem('gre-book-loop', JSON.stringify({
      lessonId: next.id,
      speakMode: opts.speakMode || 'words'
    }))
  } catch (e) { /* ignore */ }
  stopTTS()
  router.push(`/m/lesson-detail/${next.id}`)
}
function autoPlayCurrent(mode) {
  // 等 DOM 渲染完再开始，避免 auto-scroll 找不到卡片
  nextTick(() => {
    if (mode === 'words') playWords()
    else if (mode === 'all') playAll()
    // reading 模式由 m/reading.vue 自己消费续播标记
  })
}
function consumePendingAutoPlay(lid) {
  if (bookType.value === 2) return
  try {
    const raw = sessionStorage.getItem('gre-book-loop')
    if (!raw) return
    const { lessonId, speakMode } = JSON.parse(raw)
    if (String(lessonId) !== String(lid)) return
    sessionStorage.removeItem('gre-book-loop')
    setTimeout(() => autoPlayCurrent(speakMode), 350)
  } catch (e) { /* ignore */ }
}
// 路由参数变化（本书循环跳下一课）时组件实例被复用，onMounted 不再执行，必须显式重载
watch(() => route.params.id, async (newId) => {
  // 组件复用（/m/lesson/:id 只变参数）不触发卸载，必须显式终止旧课会话
  stopTTS()
  // prompt=false：页内跳课不弹「继续上次学习」
  await loadLesson(newId, false)
  consumePendingAutoPlay(newId)
})
// 注册本书循环处理器
onBookLoop(handleBookLoop)

async function loadLesson(lid, prompt = true) {
  loading.value = true
  try {
    const [l, vs] = await Promise.all([lessonApi.get(lid), lessonApi.vocabularies(lid)])
    lesson.value = l.data
    vocabs.value = vs.data || []
    // 预取本书课时列表（上一课/下一课导航用），不阻塞主流程
    loadBookLessons()
    // 取书本类型：1=词汇书 2=阅读书（类型固定，决定走词表还是阅读视图）
    if (l.data?.bookId) {
      try {
        const { data: b } = await bookApi.get(l.data.bookId)
        bookType.value = b.bookType
      } catch (e) { /* 忽略，默认按词汇书处理 */ }
    }
  } catch (e) { vocabs.value = [] }
  finally { loading.value = false }
  refreshDone(lid)
  promptResume(lid, prompt)
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
  router.push(`/m/lesson-detail/${l.id}`)
  // 换课后回到页面顶部
  nextTick(() => window.scrollTo(0, 0))
}

onMounted(async () => {
  // 清掉上个页面残留的播放会话，避免「继续」播到别页内容
  if (ttsState.value.active) stopTTS()
  buildEngineList()
  // /api/tts/engines 为异步拉取，就绪后刷新引擎列表
  window.addEventListener('gre-engines-updated', buildEngineList)
  window.addEventListener('kokoro-progress', onKokoroProgress)
  await loadLesson(route.params.id)
})
onUnmounted(() => {
  stopTTS()
  flushProgress()
  tracker.stop()
  window.removeEventListener('gre-engines-updated', buildEngineList)
  window.removeEventListener('kokoro-progress', onKokoroProgress)
})
</script>

<style scoped>
.m-detail { padding-bottom: 12px; }
.head {
  position: sticky; top: 0; z-index: 10;
  background: #fff; box-shadow: 0 2px 8px rgba(100, 101, 102, 0.12);
}
.title-row {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; background: #fff; border-bottom: 1px solid #ebedf0;
}
.title-row .sbtn { font-size: 20px; color: #4f6df5; flex: 0 0 auto; }
.title-row .title {
  flex: 1 1 auto; font-size: 16px; font-weight: 600; text-align: center;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.chipbar {
  display: flex; align-items: center; gap: 6px;
  padding: 7px 10px; background: #fff; border-bottom: 1px solid #ebedf0;
}
.chipbar .play-btn { margin-left: auto; border-radius: 15px; }
.chip {
  font-size: 12px; padding: 5px 10px; border-radius: 14px;
  border: 1px solid #c8c9cc; color: #323233; white-space: nowrap;
}
.chip.on { background: #4f6df5; border-color: #4f6df5; color: #fff; }
.sq-btn {
  width: 30px; height: 30px; flex: 0 0 auto;
  display: flex; align-items: center; justify-content: center;
  border-radius: 8px; border: 1px solid #c8c9cc; color: #4f6df5; font-size: 15px;
}
.sq-btn.disabled { color: #c8c9cc; }
.settings-sheet { padding: 14px 14px 24px; }
.sheet-title { text-align: center; font-weight: 600; font-size: 15px; margin-bottom: 12px; }
.sheet-row { display: flex; gap: 8px; margin-top: 10px; }
.sheet-row .van-button { flex: 1 1 0; margin-left: 0; }
.engine-field { padding: 4px 10px; border: 1px solid #ebedf0; border-radius: 8px; }
.engine-field :deep(.van-field__label) { width: 64px; white-space: nowrap; }
.tts-bar {
  display: flex; align-items: center; justify-content: space-between;
  margin: 0 12px 8px; padding: 8px 12px; background: #fff;
  border: 1px solid #ebedf0; border-radius: 8px;
}
.tts-bar .status { font-size: 13px; color: #4f6df5; font-weight: 600; }
.tts-btns { display: flex; gap: 6px; }
.vocab { display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; }
.vocab .word { font-size: 16px; font-weight: 700; color: #323233; }
.vocab .phon { font-size: 13px; color: #969799; }
.vocab .pos { font-size: 13px; color: #969799; }
.def { font-size: 14px; color: #323233; margin-top: 2px; line-height: 1.5; }
.def-cn { font-size: 14px; color: #4f6df5; margin-top: 2px; line-height: 1.5; font-weight: 500; white-space: pre-wrap; }
.ex { font-size: 13px; color: #969799; margin-top: 2px; line-height: 1.5; font-style: italic; }
.row-actions { display: flex; gap: 12px; align-items: center; }
.row-actions .van-icon { font-size: 20px; color: #4f6df5; }
.row-actions .van-icon.faved { color: #ff976a; }
.row-actions .van-icon.known { color: #07c160; }
:deep(.van-cell.done .word) { color: #c8c9cc; text-decoration: line-through; }
.center { display: flex; justify-content: center; margin-top: 24px; }
:deep(.van-cell.active) { background: #f0f5ff; }
</style>
