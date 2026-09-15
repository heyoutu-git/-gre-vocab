<template>
  <div class="m-reading" ref="rootEl">
    <div class="head">
    <div class="chipbar">
      <span v-if="prev" class="sq-btn" @click="$emit('nav', prev)"><van-icon name="arrow-left" /></span>
      <span v-if="next" class="sq-btn" @click="$emit('nav', next)"><van-icon name="arrow" /></span>
      <span class="chip" :class="{ on: autoScroll }" @click="autoScroll = !autoScroll">{{ $t('lesson.autoScroll') }}</span>
      <span class="chip" @click="showLoop = true">{{ loopLabel }} ▾</span>
      <van-button size="small" type="primary" class="play-btn" :disabled="!supported || ttsState.active || !enSentences.length" @click="speakReading">{{ $t('mobile.speakAll') }}</van-button>
      <van-icon name="setting-o" class="sbtn" @click="showSettings = true" />
    </div>

    <div v-if="ttsState.active" class="tts-bar">
      <span class="status">{{ ttsState.paused ? $t('lesson.paused') : $t('lesson.playing') }} {{ Math.max(ttsState.index + 1, 0) }}/{{ ttsState.total }}<template v-if="ttsState.detail"> · {{ $t('lesson.' + ttsState.detail) }}</template></span>
      <div class="tts-btns">
        <van-button size="small" @click="togglePause">{{ ttsState.paused ? '▶' : '⏸' }}</van-button>
        <van-button size="small" @click="restartTTS">↺</van-button>
        <van-button size="small" @click="stopTTS">⏹</van-button>
      </div>
    </div>
    </div>
    <van-notice-bar v-if="kokoroLoading" wrapable :scrollable="false" class="kokoro-tip"
      :text="$t('lesson.kokoroTip', { pct: kokoroPct })" />

    <van-empty v-if="!loading && !enSentences.length" :description="$t('lesson.noReading')" />
    <div v-else class="reading-body" :class="{ 'with-zh': showZh }">
      <div
        v-for="(row, i) in rows"
        :key="i"
        :ref="(el) => setEnRef(i, el)"
        class="para"
        :class="{ active: ttsState.active && ttsState.track && ttsState.index === i }"
      >
        <p class="en">{{ row.en }}</p>
        <p v-if="showZh && row.zh" class="zh">{{ row.zh }}</p>
      </div>

      <div class="done-row">
        <van-button size="small" :type="lessonDone ? 'success' : 'default'" :icon="lessonDone ? 'success' : ''" :disabled="!userStore.isLogin || lessonDone" @click="markDone">
          {{ lessonDone ? $t('lesson.done') : $t('lesson.markDone') }}
        </van-button>
      </div>
    </div>

    <van-loading v-if="loading" class="center" />
    <van-notice-bar v-if="!supported" :text="$t('lesson.noSupportShort')" />

    <!-- 设置弹层（低频操作收纳）：发音引擎 / 显示中文 -->
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
      <div class="sheet-row between">
        <span class="sheet-label" :class="{ dim: !zhReady }">{{ showZh ? $t('mobile.hideZh') : $t('mobile.showZh') }}</span>
        <van-switch v-model="showZh" size="22px" :disabled="!zhReady" />
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
import { useI18n } from 'vue-i18n'
import { lessonApi, learningApi } from '../../api'
import { showSuccessToast, showConfirmDialog } from 'vant'
import 'vant/es/toast/style'
import 'vant/es/dialog/style'
import { useUserStore } from '../../store/user'
import { createScrollTracker } from '../../utils/scroll-track'
import { useProgressReport, checkLessonResume } from '../../composables/useProgressReport'
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
  setLoopMode
} from '../../utils/tts'

const props = defineProps({
  lessonId: { type: [String, Number], required: true },
  title: { type: String, default: '' },
  bookId: { type: [String, Number], default: null },
  // 上一课/下一课（父级 lesson-detail 按本书课时顺序传入，点击时 emit nav）
  prev: { type: Object, default: null },
  next: { type: Object, default: null }
})
const emit = defineEmits(['nav'])

// 学习进度（服务端存储，电脑/手机跨端同步）
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
    showSuccessToast(t('lesson.doneTip'))
  } catch (e) { /* http 拦截器已提示 */ }
}

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

// ---- 阅读内容 ----
const passage = ref(null)
const showZh = ref(false)

const enText = computed(() => passage.value?.enText || '')
const zhText = computed(() => passage.value?.zhText || '')
const zhReady = computed(() => zhText.value.trim().length > 0)

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
  const out = enSents.map((s) => {
    const targetEnd = enLen ? Math.round((s.end / enLen) * zhLen) : 0
    const collected = []
    while (zhPtr < zhSents.length) {
      const zs = zhSents[zhPtr]
      if (zs.end <= targetEnd) {
        collected.push(zs.text)
        zhPtr++
      } else if (zs.start < targetEnd) {
        collected.push(zs.text)
        zhPtr++
        break
      } else {
        break
      }
    }
    return { en: s.text, zh: collected.join('').trim() }
  })
  const last = out[out.length - 1]
  while (zhPtr < zhSents.length) {
    last.zh += zhSents[zhPtr].text
    zhPtr++
  }
  return out
})

// ---- 中文断句 ----
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

// ---- 英文断句（保留字符偏移，按比例映射中文） ----
function computeEnSentences(text) {
  if (!text) return []
  const abbrSet = new Set(['Mr', 'Mrs', 'Ms', 'Dr', 'St', 'Vs', 'Prof', 'Gen', 'Sen', 'Capt', 'Lt', 'Sgt', 'Jr', 'Sr', 'Inc', 'Ltd', 'Co', 'etc', 'e.g', 'i.e', 'vs', 'No'])
  const re = /(?<=[.!?]["'’»”]?)\s+(?=[A-Z"'‘(（])/g
  const out = []
  let start = 0
  let m
  while ((m = re.exec(text)) !== null) {
    const boundaryStart = m.index
    const before = text.slice(0, boundaryStart)
    const wm = before.match(/([A-Za-z.]+)\s*$/)
    let isAbbr = false
    if (wm) {
      const w = wm[1].replace(/\.$/, '')
      if (abbrSet.has(w)) isAbbr = true
    }
    if (isAbbr) continue
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

// ---- 跟读全文 ----
function speakReading() {
  const list = rows.value.map((r) => r.en).filter(Boolean)
  if (!list.length) return
  const items = list.map((s, i) => ({ text: s, wordIndex: i }))
  playItems(items, { totalWords: list.length, track: true, speakMode: 'reading' })
}

// 从第 n 句续播（「继续上次学习」）
// prompt=false：页内跳课（上一课/下一课）只静默记录访问，不弹窗
async function promptResume(lid, prompt = true) {
  const n = await checkLessonResume(() => lid, userStore, prompt)
  if (!n) return
  showConfirmDialog({
    title: t('lesson.resumeTitle'),
    message: t('lesson.resumeBody', { n }),
    confirmButtonText: t('lesson.resumeYes'),
    cancelButtonText: t('lesson.resumeNo')
  }).then(() => {
    const all = rows.value.map((r) => r.en).filter(Boolean)
    const start = Math.min(Math.max(n, 0), all.length - 1)
    const items = all.map((s, i) => ({ text: s, wordIndex: i }))
    playItems(items, { totalWords: all.length, track: true, speakMode: 'reading', startIndex: start })
  }).catch(() => {})
}

// ---- 自动滚屏 ----
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
  const headerEl = rootEl.value ? rootEl.value.querySelector('.head') : null
  tracker.ensureVisible(enEls[idx], headerEl, {
    isActive: () => ttsState.value.active && ttsState.value.track && autoScroll.value
  })
}

// ---- 循环模式（与电脑版一致：不循环/本课循环/本书循环；本书循环跳课由父级 lesson-detail 处理） ----
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

// TTS 进度变化时滚动到当前句（顶层 watch，避免重复注册）
watch(
  () => [ttsState.value.active, ttsState.value.index, ttsState.value.track],
  () => nextTick(scrollToCurrent)
)
watch(autoScroll, (on) => { if (!on) tracker.stop() })

// 屏幕尺寸变化后重新对齐
let resizeTimer = null
function onResize() {
  clearTimeout(resizeTimer)
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
    passage.value = null
  } finally {
    loading.value = false
  }
  refreshDone()
}

onMounted(async () => {
  await loadPassage(props.lessonId)
  promptResume(props.lessonId)
  // 本书循环跨课续播标记（父级写入，本组件按 speakMode==='reading' 消费）
  consumePendingAutoPlay(props.lessonId)
})

// 本书循环跳到下一课时，父级复用组件实例，靠 lessonId 变化重载并续播
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

watch(() => props.lessonId, async (newId) => {
  await loadPassage(newId)
  promptResume(newId, false) // 页内跳课不弹「继续上次学习」
  consumePendingAutoPlay(newId)
})
</script>

<style scoped>
.m-reading { padding-bottom: 12px; }
.head {
  position: sticky; top: 0; z-index: 10;
  background: #fff; box-shadow: 0 2px 8px rgba(100, 101, 102, 0.12);
}
.chipbar {
  display: flex; align-items: center; gap: 6px;
  padding: 7px 10px; background: #fff; border-bottom: 1px solid #ebedf0;
}
.chipbar .play-btn { margin-left: auto; border-radius: 15px; }
.chipbar .sbtn { font-size: 20px; color: #4f6df5; flex: 0 0 auto; margin-left: 2px; }
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
.settings-sheet { padding: 14px 14px 24px; }
.sheet-title { text-align: center; font-weight: 600; font-size: 15px; margin-bottom: 12px; }
.sheet-row { display: flex; gap: 8px; margin-top: 10px; }
.sheet-row.between { align-items: center; justify-content: space-between; }
.sheet-label { font-size: 14px; color: #323233; }
.sheet-label.dim { color: #c8c9cc; }
.engine-field { padding: 4px 10px; border: 1px solid #ebedf0; border-radius: 8px; }
.engine-field :deep(.van-field__label) { width: 64px; white-space: nowrap; }
.tts-bar {
  display: flex; align-items: center; justify-content: space-between;
  margin: 0 12px 8px; padding: 8px 12px; background: #fff;
  border: 1px solid #ebedf0; border-radius: 8px;
}
.tts-bar .status { font-size: 13px; color: #4f6df5; font-weight: 600; }
.tts-btns { display: flex; gap: 6px; }
.reading-body { padding: 12px 14px; }
.para { padding: 10px 12px; border-radius: 8px; transition: background .2s; }
.para.active { background: #f0f5ff; box-shadow: inset 3px 0 0 #4f6df5; }
.para .en { margin: 0; font-size: 16px; line-height: 1.9; color: #323233; text-align: justify; }
.para .zh { margin: 8px 0 0; font-size: 15px; line-height: 1.8; color: #969799; text-align: justify; }
.center { display: flex; justify-content: center; margin-top: 24px; }
.done-row { margin-top: 18px; display: flex; justify-content: center; }
</style>
