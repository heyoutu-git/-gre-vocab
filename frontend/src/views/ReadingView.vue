<template>
  <div class="detail reading" v-loading="loading">
    <div class="topbar">
      <div class="bar">
        <div>
          <el-button text @click="goBackLessons">← 课时目录</el-button>
          <h2 style="display:inline; margin-left:8px;">{{ title }}</h2>
        </div>
        <div class="tools">
          <el-button :type="showZh ? 'primary' : 'default'" @click="showZh = !showZh" :disabled="ttsState.active || !zhReady">
            {{ showZh ? '隐藏中文' : '显示中文' }}
          </el-button>
          <el-button @click="speakReading" :disabled="!supported || ttsState.active || !enSentences.length">🔊 跟读全文</el-button>
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
      title="当前浏览器不支持语音朗读（SpeechSynthesis），请使用 Chrome / Edge / Safari。" />

    <el-empty v-if="!loading && !enSentences.length" description="暂无阅读内容" />

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
import { ElMessage } from 'element-plus'
import { lessonApi, learningApi } from '../api'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '../store/user'
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
const engineList = ref([{ code: 'auto', name: '自动' }])
function buildEngineList() {
  const rest = getAvailableEngines()
    .slice()
    .sort((a, b) => (a.code === 'kokoro' ? -1 : b.code === 'kokoro' ? 1 : 0))
    .map((e) => ({ code: e.code, name: e.name }))
  engineList.value = [{ code: 'auto', name: '自动' }, ...rest]
}
function onEngineChange() { setEngine(engineSel.value) }

const loading = ref(false)
const supported = speechSupported()
const ttsState = ref({ active: false, paused: false, index: -1, total: 0, track: false })
onTTSState((s) => { ttsState.value = s })

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

// ---- 自动滚屏：记录每句 DOM，当前句变化时把句子顶部对齐到 topbar 下方 ----
const autoScroll = ref(true)
const enEls = {}
function setEnRef(i, el) {
  if (el) enEls[i] = el
  else delete enEls[i]
}
function scrollToCurrent(force = false) {
  if (!ttsState.value.active || !ttsState.value.track || !autoScroll.value) return
  const idx = ttsState.value.index
  if (idx < 0) return
  const el = enEls[idx]
  if (!el || typeof el.scrollIntoView !== 'function') return
  const topbar = document.querySelector('.topbar')
  const topbarBottom = topbar ? topbar.getBoundingClientRect().bottom : 120
  const viewportH = window.innerHeight
  const rect = el.getBoundingClientRect()
  const margin = 20
  const visibleTop = topbarBottom + margin
  const visibleBottom = viewportH - margin
  // 非强制调用（如 resize）时，若当前句已在合理可视区内则不跳，避免频繁跳动
  if (!force && rect.top >= visibleTop && rect.bottom <= visibleBottom) return
  // 把句子顶部固定对齐到 topbar 下方 20px，确保长句从头可见，不会滚过顶
  const targetY = window.scrollY + rect.top - visibleTop
  window.scrollTo({ top: Math.max(0, targetY), behavior: 'smooth' })
}
watch(
  () => [ttsState.value.active, ttsState.value.index, ttsState.value.track],
  () => nextTick(() => scrollToCurrent(true))
)

// 屏幕尺寸变化（横竖屏切换 / 窗口缩放）后，若高亮句被遮挡再滚回可视区
let resizeTimer = null
function onResize() {
  clearTimeout(resizeTimer)
  // 延时稍长，等 Element Plus 按钮组换行/重排完成
  resizeTimer = setTimeout(() => nextTick(() => scrollToCurrent(false)), 250)
}
onMounted(() => {
  window.addEventListener('resize', onResize)
  window.addEventListener('orientationchange', onResize)
  buildEngineList()
  // /api/tts/engines 为异步拉取，就绪后刷新引擎列表
  window.addEventListener('gre-engines-updated', buildEngineList)
  window.addEventListener('kokoro-progress', onKokoroProgress)
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('orientationchange', onResize)
  window.removeEventListener('gre-engines-updated', buildEngineList)
  window.removeEventListener('kokoro-progress', onKokoroProgress)
  clearTimeout(resizeTimer)
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
    ElMessage.error('阅读内容加载失败，请确认已登录')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadPassage(props.lessonId)
  refreshDone()
  // 本书循环跨课自动播放：阅读书自己消费续播标记
  consumePendingAutoPlay(props.lessonId)
})

watch(() => props.lessonId, async (newId) => {
  await loadPassage(newId)
  refreshDone()
  consumePendingAutoPlay(newId)
})

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
  top: 0;
  z-index: 20;
  margin: -18px -18px 14px;
  padding: 14px 18px 0;
  background: var(--gre-bg);
  border-bottom: 1px solid var(--gre-border);
  box-shadow: 0 4px 10px -6px rgba(0, 0, 0, .18);
}
.bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.done-row { margin-top: 18px; text-align: center; }
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
  .topbar { margin: -12px -12px 10px; padding: 10px 12px 0; }
  .reading-body { font-size: 15px; }
}
</style>
