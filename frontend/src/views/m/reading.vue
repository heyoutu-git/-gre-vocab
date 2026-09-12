<template>
  <div class="m-reading">
    <div class="toolbar">
      <van-field
        v-model="engineLabel"
        readonly
        is-link
        :label="$t('lesson.engine')"
        class="engine-field"
        @click="showEngine = true"
      />
      <van-popup v-model:show="showEngine" position="bottom" round>
        <van-picker
          :columns="engineColumns"
          @confirm="onEnginePick"
          @cancel="showEngine = false"
          show-toolbar
          :title="$t('lesson.engine')"
        />
      </van-popup>

      <div class="actions">
        <van-button size="small" :type="showZh ? 'primary' : 'default'" :disabled="!zhReady || ttsState.active" @click="showZh = !showZh">
          {{ showZh ? $t('mobile.hideZh') : $t('mobile.showZh') }}
        </van-button>
        <van-button size="small" type="primary" :disabled="!supported || ttsState.active || !enSentences.length" @click="speakReading">{{ $t('mobile.speakAll') }}</van-button>
      </div>
      <div class="actions">
        <van-button size="small" :type="autoScroll ? 'primary' : 'default'" @click="autoScroll = !autoScroll">{{ $t('lesson.autoScroll') }}</van-button>
        <van-button size="small" v-for="m in loopOptions" :key="m.value"
          :type="loopMode === m.value ? 'primary' : 'default'" @click="loopMode = m.value">{{ m.label }}</van-button>
      </div>
    </div>

    <div v-if="ttsState.active" class="tts-bar">
      <span class="status">{{ ttsState.paused ? $t('lesson.paused') : $t('lesson.playing') }} {{ Math.max(ttsState.index + 1, 0) }}/{{ ttsState.total }}<template v-if="ttsState.detail"> · {{ $t('lesson.' + ttsState.detail) }}</template></span>
      <div class="tts-btns">
        <van-button size="small" @click="togglePause">{{ ttsState.paused ? '▶' : '⏸' }}</van-button>
        <van-button size="small" @click="restartTTS">↺</van-button>
        <van-button size="small" @click="stopTTS">⏹</van-button>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { lessonApi, learningApi } from '../../api'
import { showSuccessToast } from 'vant'
import 'vant/es/toast/style'
import { useUserStore } from '../../store/user'
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
  bookId: { type: [String, Number], default: null }
})

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

// ---- 自动滚屏 ----
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
  if (!el) return
  if (!force) {
    const rect = el.getBoundingClientRect()
    const vh = window.innerHeight
    if (rect.top >= 80 && rect.bottom <= vh - 40) return
  }
  // 部分安卓浏览器 scrollIntoView(smooth) 静默失效，改用手动计算滚动
  const rect = el.getBoundingClientRect()
  const top = rect.top + window.pageYOffset - window.innerHeight / 2 + rect.height / 2
  window.scrollTo({ top, behavior: 'smooth' })
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

// TTS 进度变化时滚动到当前句（顶层 watch，避免重复注册）
watch(
  () => [ttsState.value.active, ttsState.value.index, ttsState.value.track],
  () => nextTick(() => scrollToCurrent(true))
)

// 屏幕尺寸变化后重新对齐
let resizeTimer = null
function onResize() {
  clearTimeout(resizeTimer)
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
    passage.value = null
  } finally {
    loading.value = false
  }
  refreshDone()
}

onMounted(async () => {
  await loadPassage(props.lessonId)
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
  consumePendingAutoPlay(newId)
})
</script>

<style scoped>
.m-reading { padding-bottom: 12px; }
.toolbar { padding: 10px 12px 0; display: flex; flex-direction: column; gap: 8px; }
.engine-field { padding: 4px 10px; }
.engine-field :deep(.van-field__label) { width: 36px; }
.actions { display: flex; gap: 8px; }
.actions .van-button { flex: 1 1 0; margin-left: 0; }
.tts-bar {
  display: flex; align-items: center; justify-content: space-between;
  margin: 10px 12px; padding: 8px 12px; background: #fff;
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
