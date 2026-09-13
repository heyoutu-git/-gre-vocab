<template>
  <div class="detail reading" v-loading="loading" ref="rootEl">
    <div class="topbar">
      <div class="bar">
        <div>
          <el-button text @click="goBackLessons">{{ $t('lesson.back') }}</el-button>
          <h2 style="display:inline; margin-left:8px;">{{ title }}</h2>
          <span v-if="prevLesson || nextLesson" class="lesson-nav-top">
            <el-button v-if="prevLesson" size="small" @click="goNavLesson(prevLesson)">{{ $t('lesson.prevLesson') }}</el-button>
            <el-button v-if="nextLesson" size="small" type="primary" @click="goNavLesson(nextLesson)">{{ $t('lesson.nextLesson') }}</el-button>
          </span>
        </div>
        <div class="tools">
          <el-button :type="showZh ? 'primary' : 'default'" @click="showZh = !showZh" :disabled="ttsState.active || !zhReady">
            {{ showZh ? $t('mobile.hideZh') : $t('mobile.showZh') }}
          </el-button>
          <el-button @click="speakReading" :disabled="!supported || ttsState.active || !enSentences.length">{{ $t('mobile.speakAll') }}</el-button>
          <el-select v-model="engineSel" style="width: 120px" :disabled="ttsState.active" :title="$t('lesson.engine')" @change="onEngineChange">
            <el-option v-for="e in engineList" :key="e.code" :label="e.name" :value="e.code" />
          </el-select>
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
        <el-button size="small" type="danger" @click="stopTTS">{{ $t('lesson.stop') }}</el-button>
      </div>
    </div>

    <el-alert v-if="kokoroLoading" type="info" :closable="false" style="margin-bottom:12px"
      :title="$t('lesson.kokoroTip', { pct: kokoroPct })" />

    <el-alert v-if="!supported" type="warning" :closable="false" style="margin-bottom:12px"
      :title="$t('lesson.noSupport')" />

    <el-empty v-if="!loading && !enSentences.length" :description="$t('lesson.noReading')" />

    <div v-else class="reading-body" :class="{ 'with-zh': showZh }">
      <div
        v-for="(row, i) in rows"
        :key="i"
        class="para"
        :class="{ active: ttsState.active && ttsState.track && ttsState.index === i }"
        :ref="(el) => setEnRef(i, el)"
      >
        <p class="en">{{ row.en }}</p>
        <p v-if="showZh && row.zh" class="zh">{{ row.zh }}</p>
      </div>

      <div class="done-row">
        <el-button :type="lessonDone ? 'success' : 'default'" :disabled="!userStore.isLogin || lessonDone" @click="markDone">
          {{ lessonDone ? $t('lesson.done') : $t('lesson.markDone') }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { lessonApi, learningApi } from '../api'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '../store/user'
import { createScrollTracker } from '../utils/scroll-track'
import { useProgressReport, checkLessonResume } from '../composables/useProgressReport'
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
  getEngine,
  setEngine,
  getAvailableEngines,
} from '../utils/tts'

const props = defineProps({
  lessonId: { type: [String, Number], required: true },
  title: { type: String, default: '' },
  bookId: { type: [String, Number], default: null },
})

// 学习进度（服务端存储，电脑/手机跨端同步）
const { t } = useI18n()
const userStore = useUserStore()
const lessonDone = ref(false)
async function refreshDone() {
  lessonDone.value = false
  if (!userStore.isLogin || !props.lessonId) return
  try {
    const { data } = await learningApi.getProgress(props.lessonId)
    lessonDone.value = !!data
  } catch (e) { /* ignore */ }
}
async function markDone() {
  if (lessonDone.value || !props.lessonId) return
  try {
    await learningApi.markProgress(props.lessonId)
    lessonDone.value = true
    ElMessage.success(t('lesson.doneTip'))
  } catch (e) { /* http 拦截器已提示 */ }
}

const router = useRouter()
// 返回课时目录，带上当前 bookId 让列表页恢复选中的书本
function goBackLessons() {
  const bid = props.bookId
  router.push(bid ? { name: 'Lessons', query: { bookId: String(bid) } } : { name: 'Lessons' })
}

const loopMode = ref(getLoopMode())
function onLoopChange(mode) {
  setLoopMode(mode)
}
watch(loopMode, (mode) => {
  setLoopMode(mode)
})

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

const loading = ref(false)
const supported = speechSupported()
const ttsState = ref({ active: false, paused: false, index: -1, total: 0, track: false })
onTTSState((s) => { ttsState.value = s })
// ---- 学习进度自动上报（10s 节流，跨端存服务端）----
const { flush: flushProgress } = useProgressReport(() => props.lessonId, ttsState)

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

const passage = ref(null)
const showZh = ref(false)

const enText = computed(() => passage.value?.enText || '')
const zhText = computed(() => passage.value?.zhText || '')
const zhReady = computed(() => zhText.value.trim().length > 0)

// 人工对齐结果（管理员在后台调整后保存，优先于自动对齐）
const manualAlignment = computed(() => {
  const raw = passage.value?.alignment
  if (!raw) return null
  try {
    const arr = JSON.parse(raw)
    if (Array.isArray(arr) && arr.length) return arr
  } catch (e) { /* 解析失败回退自动 */ }
  return null
})

const enSentences = computed(() => computeEnSentences(enText.value).map((s) => s.text))
// 中英逐句配对：优先使用人工对齐；否则按英文句子在原文中的字符位置比例
// 把完整中文句归到对应英文句下，绝不截断中文整句（原按字符比例硬切会切断译文）。
const rows = computed(() => {
  if (manualAlignment.value) {
    return manualAlignment.value
      .map((r) => ({ en: r.en || '', zh: r.zh || '' }))
      .filter((r) => r.en || r.zh)
  }
  const enSents = computeEnSentences(enText.value)
  const zhSents = computeZhSentences(zhText.value)
  const enLen = enText.value.length
  const zhLen = zhText.value.length
  if (!enSents.length) return []
  let zhPtr = 0
  const rows = enSents.map((s) => {
    const targetEnd = enLen ? Math.round((s.end / enLen) * zhLen) : 0
    const collected = []
    while (zhPtr < zhSents.length) {
      const zs = zhSents[zhPtr]
      if (zs.end <= targetEnd) {
        collected.push(zs.text)
        zhPtr++
      } else if (zs.start < targetEnd) {
        // 目标位置落在这个中文句内部：整个句子归给当前英文句，避免截断
        collected.push(zs.text)
        zhPtr++
        break
      } else {
        break
      }
    }
    return { en: s.text, zh: collected.join('').trim() }
  })
  // 剩余中文句全部归到最后一个英文句，避免漏字
  const last = rows[rows.length - 1]
  while (zhPtr < zhSents.length) {
    last.zh += zhSents[zhPtr].text
    zhPtr++
  }
  return rows
})

// ---- 中文断句：返回 [{text, start, end}]，用于整句分配给英文句 ----
function computeZhSentences(text) {
  if (!text) return []
  const re = /[。！？；…]+[”』』]?/g
  const out = []
  let start = 0
  let m
  while ((m = re.exec(text)) !== null) {
    const end = m.index + m[0].length
    const seg = text.slice(start, end).trim()
    if (seg) out.push({ text: seg, start, end })
    start = end
  }
  const last = text.slice(start).trim()
  if (last) out.push({ text: last, start, end: text.length })
  return out
}

// ---- 英文断句：返回 [{text, start, end}]，保留在原文中的字符偏移，用于按比例映射中文 ----
function computeEnSentences(text) {
  if (!text) return []
  const abbrSet = new Set(['Mr', 'Mrs', 'Ms', 'Dr', 'St', 'Vs', 'Prof', 'Gen', 'Sen', 'Capt', 'Lt', 'Sgt', 'Jr', 'Sr', 'Inc', 'Ltd', 'Co', 'etc', 'e.g', 'i.e', 'vs', 'No'])
  const re = /(?<=[.!?]["'’»”]?)\s+(?=[A-Z"'‘(（])/g
  const out = []
  let start = 0
  let m
  while ((m = re.exec(text)) !== null) {
    const boundaryStart = m.index // 空白分隔符起点，其前一位为 [.!?]
    const before = text.slice(0, boundaryStart)
    const wm = before.match(/([A-Za-z.]+)\s*$/)
    let isAbbr = false
    if (wm) {
      const w = wm[1].replace(/\.$/, '')
      if (abbrSet.has(w)) isAbbr = true
    }
    if (isAbbr) continue // 缩写后的点不算句末，跳过该边界
    const seg = text.slice(start, boundaryStart).trim()
    if (seg) out.push({ text: seg, start, end: boundaryStart })
    let s = boundaryStart
    while (s < text.length && /\s/.test(text[s])) s++
    start = s
  }
  const last = text.slice(start).trim()
  if (last) out.push({ text: last, start, end: text.length })
  return out
}

// ---- 跟读：按句子逐句播放 + 高亮 + 自动滚屏 ----
function speakReading() {
  // 优先使用当前显示的行（人工对齐时即按对齐后的英文句朗读，保持与画面一致）
  const list = rows.value.map((r) => r.en).filter(Boolean)
  if (!list.length) return
  const items = list.map((s, i) => ({ text: s, wordIndex: i }))
  playItems(items, { totalWords: list.length, track: true, speakMode: 'reading' })
}

// 从第 n 句续播（「继续上次学习」）
function speakReadingFrom(n) {
  const list = rows.value.map((r) => r.en).filter(Boolean)
  if (!list.length) return
  const items = list.map((s, i) => ({ text: s, wordIndex: i }))
  const start = Math.min(Math.max(n, 0), items.length - 1)
  playItems(items, { totalWords: list.length, track: true, speakMode: 'reading', startIndex: start })
}

// 进入页面/换课时：有历史位置且未完成 → 提示继续
async function promptResume(lid) {
  const n = await checkLessonResume(() => lid, userStore)
  if (!n) return
  ElMessageBox.confirm(t('lesson.resumeBody', { n }), t('lesson.resumeTitle'), {
    confirmButtonText: t('lesson.resumeYes'),
    cancelButtonText: t('lesson.resumeNo'),
    type: 'info'
  }).then(() => speakReadingFrom(n)).catch(() => {})
}

// ---- 自动滚屏：记录每句 DOM，当前句变化时把句子垂直居中显示（超长句顶部对齐） ----
const autoScroll = ref(true)
const rootEl = ref(null)
const enEls = {}
const tracker = createScrollTracker()
function setEnRef(i, el) {
  if (el) enEls[i] = el
  else delete enEls[i]
}
function scrollToCurrent() {
  if (!ttsState.value.active || !ttsState.value.track || !autoScroll.value) { tracker.stop(); return }
  const idx = ttsState.value.index
  if (idx < 0) return
  const headerEl = rootEl.value ? rootEl.value.querySelector('.topbar') : null
  tracker.ensureVisible(enEls[idx], headerEl, {
    isActive: () => ttsState.value.active && ttsState.value.track && autoScroll.value
  })
}
watch(
  () => [ttsState.value.active, ttsState.value.index, ttsState.value.track],
  () => nextTick(scrollToCurrent)
)
watch(autoScroll, (on) => { if (!on) tracker.stop() })

// 屏幕尺寸变化（横竖屏切换 / 窗口缩放）后，若高亮句被遮挡再滚回可视区
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

function togglePause() {
  if (ttsState.value.paused) resumeTTS()
  else pauseTTS()
}

async function loadPassage(lid) {
  loading.value = true
  try {
    const { data } = await lessonApi.passage(lid)
    passage.value = data
  } catch (e) {
    ElMessage.error(t('lesson.readLoadFail'))
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadPassage(props.lessonId)
  promptResume(props.lessonId)
  refreshDone()
  loadBookLessons()
  // 本书循环跨课自动播放：阅读书自己消费续播标记
  consumePendingAutoPlay(props.lessonId)
})

watch(() => props.lessonId, async (newId) => {
  await loadPassage(newId)
  refreshDone()
  promptResume(newId)
  loadBookLessons()
  consumePendingAutoPlay(newId)
})

// ---- 上一课 / 下一课导航（顶栏悬浮；按本书课时列表顺序，首课无上一课、末课无下一课） ----
const bookLessons = ref([])
let bookLessonsBookId = null
async function loadBookLessons() {
  const bid = props.bookId
  if (!bid) return
  if (bookLessonsBookId === bid && bookLessons.value.length) return
  try {
    const { data: ls } = await lessonApi.list(bid)
    bookLessons.value = ls || []
    bookLessonsBookId = bid
  } catch (e) { /* ignore */ }
}
function navNeighbor(offset) {
  const ls = bookLessons.value
  const idx = ls.findIndex((l) => String(l.id) === String(props.lessonId))
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
  // 换课后回到页面顶部
  nextTick(() => {
    const main = document.querySelector('.el-main')
    if (main) main.scrollTop = 0
    window.scrollTo(0, 0)
  })
}

function consumePendingAutoPlay(lid) {
  try {
    const raw = sessionStorage.getItem('gre-book-loop')
    if (!raw) return
    const { lessonId, speakMode } = JSON.parse(raw)
    if (String(lessonId) !== String(lid) || speakMode !== 'reading') return
    sessionStorage.removeItem('gre-book-loop')
    setTimeout(() => speakReading(), 350)
  } catch (e) { /* ignore */ }
}
</script>

<style scoped>
.topbar {
  position: sticky;
  /* 吸顶点用负值抵消负 margin：sticky 的 top 按外边距盒对齐，
     top:0 会让边框盒停在 18px 下方，滑动文字会从空隙穿出 */
  top: -18px;
  z-index: 20;
  margin: -18px -18px 14px;
  padding: 14px 18px 0;
  background: var(--gre-bg);
  border-bottom: 1px solid var(--gre-border);
  box-shadow: 0 4px 10px -6px rgba(0, 0, 0, .18);
}
.bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.done-row { margin-top: 18px; text-align: center; }
.lesson-nav-top { display: inline-flex; gap: 6px; margin-left: 10px; vertical-align: middle; }
.tools { display: flex; gap: 8px; flex-wrap: wrap; }
.tts-controls { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; padding: 8px 12px; background: var(--gre-surface); border: 1px solid var(--gre-border); border-radius: 8px; }
.auto-scroll-toggle { margin-right: 4px; }
.tts-status { font-size: 13px; color: var(--gre-primary); font-weight: 600; }

.reading-body { line-height: 1.9; font-size: 16px; padding-top: 24px; }
.para { padding: 10px 12px; border-radius: 8px; transition: background .2s; scroll-margin-top: 150px; }
.para.active { background: var(--gre-primary-soft); box-shadow: inset 3px 0 0 var(--gre-primary); padding-top: 18px; padding-bottom: 18px; }
.para .en { margin: 0; color: var(--gre-text); text-align: justify; }
.para .zh { margin: 8px 0 0; color: var(--gre-text-soft); text-align: justify; font-size: 15px; }

@media (max-width: 768px) {
  .topbar { margin: -12px -12px 10px; padding: 10px 12px 0; top: -12px; }
  .reading-body { font-size: 15px; }
}
</style>
