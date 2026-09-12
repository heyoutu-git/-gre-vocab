// 浏览器语音合成 —— 多引擎播放控制器
//   system  : Web Speech API（桌面 Chrome/Edge、iOS Safari 音质好）
//   mespeak : 纯前端离线 eSpeak（不依赖系统 TTS，华为/缺英文语音的安卓可用）
//   tencent : 腾讯云在线 TTS（后台管理账号与免费额度熔断）
//   auto    : 默认。按后端开关与本地能力自动选择：
//             桌面端 system > mespeak > tencent；移动端 tencent > system > mespeak（手机优先云端真人音，解决安卓缺英文包）
// 对外提供统一的播放控制：playItems / pauseTTS / resumeTTS / stopTTS / restartTTS，
// 并通过 onTTSState 订阅播放状态（active / paused / index / total），供 UI 置灰与控件栏使用。
// 关键：用 sessionId（会话令牌）隔离每次播放，所有回调校验 sid===sessionId，
// 避免「停止/重新开始」后旧 audio 的 onended 闭包串台导致回声或停止失效。
import { ttsApi } from '../api'

let cachedVoices = []
let voicesReady = false
let unlocked = false
let mespeakReady = false
let engine = 'auto' // 'auto' | 'system' | 'mespeak' | 'tencent' | 'kokoro'
let availableEngines = [] // 从后端 /api/tts/engines 拉取

// ---- 播放状态（对外通过 onTTSState 订阅）----
const player = { active: false, paused: false, engine: null, index: -1, total: 0, track: false, detail: '' }
let stateListener = null
function emitState() { if (stateListener) stateListener({ ...player }) }
export function onTTSState(cb) {
  stateListener = cb
  emitState()
  return () => { if (stateListener === cb) stateListener = null }
}

// ---- 播放内部状态 ----
let currentItems = []
let currentIndex = 0
let currentOpts = {}
let audioEl = null
let sessionId = 0 // 每次 playItems/stopTTS 自增，用于让过期回调失效
let triedEngines = new Set() // 本次播放已尝试过的引擎，避免回退死循环

// ---- 循环播放 ----
let loopMode = 'none' // 'none' | 'lesson' | 'book'
const bookLoopHandlers = [] // 本书循环切换下一课时由页面注册（支持多处组件同时订阅）
export function setLoopMode(mode) {
  loopMode = ['none', 'lesson', 'book'].includes(mode) ? mode : 'none'
}
export function getLoopMode() { return loopMode }
export function onBookLoop(handler) {
  if (typeof handler === 'function' && !bookLoopHandlers.includes(handler)) {
    bookLoopHandlers.push(handler)
  }
  return () => {
    const idx = bookLoopHandlers.indexOf(handler)
    if (idx !== -1) bookLoopHandlers.splice(idx, 1)
  }
}
export function getCurrentOpts() { return { ...currentOpts } }

// ---- meSpeak 动态加载（约 4.9MB，首屏异步加载，不阻塞渲染）----
function loadMespeak() {
  if (mespeakReady || typeof window === 'undefined') return
  if (window.meSpeak) { mespeakReady = true; return }
  const base = (import.meta.env && import.meta.env.BASE_URL) || '/'
  const s = document.createElement('script')
  s.src = base + 'mespeak/mespeak.js'
  s.onload = () => { mespeakReady = true }
  s.onerror = () => { /* 加载失败则回退 system */ }
  document.head.appendChild(s)
}

function loadVoices() {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) return
  const vs = window.speechSynthesis.getVoices() || []
  if (vs.length) { cachedVoices = vs; voicesReady = true }
}

export function initVoices() {
  if (typeof window === 'undefined') return
  loadVoices()
  loadMespeak()
  if ('speechSynthesis' in window && 'onvoiceschanged' in window.speechSynthesis) {
    window.speechSynthesis.onvoiceschanged = loadVoices
  }
  // 异步加载后端引擎开关，失败则保持本地默认
  refreshEngines().catch(() => {})
}

export async function refreshEngines() {
  try {
    const { data } = await ttsApi.engines()
    availableEngines = Array.isArray(data) ? data : []
    // 通知各页面引擎列表已就绪（页面 onMounted 早于本请求返回，需事件驱动刷新）
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('gre-engines-updated'))
    }
  } catch (e) {
    availableEngines = []
    throw e
  }
}

export function speechSupported() {
  return typeof window !== 'undefined' && 'speechSynthesis' in window
}

export function isMespeakReady() { return mespeakReady }

export function setEngine(e) {
  if (['auto', 'system', 'mespeak', 'tencent', 'kokoro'].includes(e)) {
    engine = e
    // 仅离线优先路径预加载 92MB 语音包；服务器优先时由 playServerKokoro 失败兜底按需加载
    if ((e === 'kokoro' || e === 'auto') && kokoroOfflineFirst()) loadKokoro().catch(() => {})
  }
}

// Kokoro 路径选择：true=浏览器离线推理优先（首次下载 92MB 语音包，之后离线可用）
// false=服务器端合成优先（188 本机推理，秒级真人音，无需下载）
// 默认：桌面离线优先（实测音质速度均佳）；手机服务器优先（92MB 模型+wasm 在手机端不可控）。
// 用户可在「我的」页强制指定：localStorage.ttsKokoroPath = 'offline' | 'server'
function kokoroOfflineFirst() {
  try {
    const forced = localStorage.getItem('ttsKokoroPath')
    if (forced === 'offline') return true
    if (forced === 'server') return false
  } catch (e) { /* ignore */ }
  return !isMobile()
}
export function getEngine() { return engine }
export function getAvailableEngines() { return availableEngines }
export function isEngineEnabled(code) {
  return availableEngines.some((en) => en.code === code && en.enabled === 1)
}

function hasEnglishVoice() {
  return cachedVoices.some((v) => (v.lang || '').toLowerCase().startsWith('en'))
}

// 移动端检测：手机优先走腾讯云真人音（解决 Android 国产机缺英文语音包问题）
function isMobile() {
  if (typeof navigator === 'undefined') return false
  const ua = navigator.userAgent || ''
  if (/Mobi|Android|iPhone|iPad|iPod|Windows Phone|HarmonyOS/i.test(ua)) return true
  // iPadOS 13+ 的 Safari UA 伪装成桌面 Mac，用触摸点数兜底识别
  if (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1) return true
  return false
}

// 回退顺序：与初始优先级一致（去掉当前引擎后的剩余链）
function fallbackOrder() {
  // kokoro 为纯前端离线推理（较重），作为最终兜底
  return isMobile()
    ? ['tencent', 'system', 'mespeak', 'kokoro']
    : ['system', 'mespeak', 'tencent', 'kokoro']
}

// 按后端开关 + 本地能力决定实际引擎
function resolveEngine() {
  if (engine === 'system' && isEngineEnabled('system')) return 'system'
  if (engine === 'mespeak' && isEngineEnabled('mespeak')) return 'mespeak'
  if (engine === 'tencent' && isEngineEnabled('tencent')) return 'tencent'
  if (engine !== 'auto') return engine // 用户强制指定时即使后端关闭也尝试

  // auto 模式：Kokoro（离线神经网络真人音质）优先；中文文本 playKokoroItem 内部会自动回退
  if (isEngineEnabled('kokoro')) return 'kokoro'

  if (isMobile()) {
    // 移动端：优先云端真人音，其次系统本地真人音，再次离线 eSpeak
    if (isEngineEnabled('tencent')) return 'tencent'
    if (isEngineEnabled('system') && hasEnglishVoice()) return 'system'
    if (isEngineEnabled('mespeak')) return 'mespeak'
    if (isEngineEnabled('system')) return 'system'
    return 'system'
  }
  // 桌面端：优先系统真人音，其次离线 eSpeak，再次云端
  if (isEngineEnabled('system') && hasEnglishVoice()) return 'system'
  if (isEngineEnabled('mespeak')) return 'mespeak'
  if (isEngineEnabled('system')) return 'system' // 无英文 voice 但开关开着，仍尝试 system
  if (isEngineEnabled('tencent')) return 'tencent'
  return 'system'
}

// 解锁移动端 Web Audio（Android Chrome 等要求在用户手势内 resume AudioContext）
function unlockAudio() {
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext
    if (!Ctx) return
    const ctx = new Ctx()
    if (ctx.state === 'suspended') ctx.resume()
  } catch (e) { /* ignore */ }
}

// 解锁 iOS Web Speech（首次用户手势内须有一次 speak 激活音频会话）
function ensureUnlockedSystem() {
  if (unlocked || !speechSupported()) return
  unlocked = true
  try {
    window.speechSynthesis.cancel()
    const u = new SpeechSynthesisUtterance('')
    u.volume = 0
    window.speechSynthesis.speak(u)
  } catch (e) { /* ignore */ }
}

function pickVoice() {
  if (!cachedVoices.length) return null
  const en = cachedVoices.filter((v) => (v.lang || '').toLowerCase().startsWith('en'))
  if (!en.length) return null
  // 本地合成音色（设备自带，通常是真人的 Samantha / Google TTS 英文包）优先于云端 voice
  const local = en.filter((v) => v.localService !== false)
  const remote = en.filter((v) => v.localService === false)
  const order = ['en-us', 'en-gb', 'en']
  const firstMatch = (pool) => {
    for (const w of order) {
      const hit = pool.find((v) => (v.lang || '').toLowerCase() === w)
      if (hit) return hit
    }
    return null
  }
  return firstMatch(local) || local[0] || firstMatch(remote) || remote[0] || en[0]
}

const MESPEAK_OPTS = { amplitude: 100, pitch: 50, speed: 150, variant: 'f5' }

function cleanup() {
  if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
    try { window.speechSynthesis.cancel() } catch (e) { /* ignore */ }
  }
  if (audioEl) {
    try {
      audioEl.onended = null
      audioEl.onerror = null
      audioEl.pause()
      audioEl.src = ''
    } catch (e) { /* ignore */ }
    audioEl = null
  }
}

function advance(sid) {
  if (sid !== sessionId) return // 过期会话（已被停止/重开）直接丢弃
  currentIndex++
  if (currentIndex >= currentItems.length) {
    if (loopMode === 'lesson') {
      // 本课循环：回到第一句重新播放
      currentIndex = 0
      playCurrent()
      return
    }
    if (loopMode === 'book' && bookLoopHandlers.length) {
      // 本书循环：把切换权交给页面（加载下一课并继续播放）
      bookLoopHandlers.forEach((h) => { try { h() } catch (e) { /* ignore */ } })
      return
    }
    finish()
    return
  }
  playCurrent()
}

function finish() {
  player.active = false
  player.paused = false
  player.index = -1
  cleanup()
  emitState()
}

// ---- 对外播放控制 ----
// items 支持两种形式：
//   1) 字符串数组：['word1', 'example1', 'word2', ...]（旧接口兼容）
//   2) 对象数组：[{ text: 'word1', wordIndex: 0 }, { text: 'example1', wordIndex: 0 }, ...]
//      wordIndex 用于进度条按"单词数"而不是"utterance 数"显示。
export function playItems(items, opts = {}) {
  const list = (items || []).filter(Boolean)
  if (!list.length) return false
  sessionId++ // 让上一次播放的所有回调（onended/onerror/等待定时器）立即失效
  cleanup()
  currentItems = list
  currentOpts = opts || {}
  currentIndex = 0
  triedEngines = new Set() // 新一轮播放，重置已尝试引擎
  player.active = true
  player.paused = false
  player.total = opts.totalWords || list.length
  player.index = -1
  player.engine = resolveEngine()
  player.detail = ''
  player.track = !!opts.track
  emitState()
  playCurrent()
  return true
}

function itemText(item) { return typeof item === 'object' && item !== null ? (item.text || '') : String(item || '') }
function itemWordIndex(item) {
  if (typeof item === 'object' && item !== null && typeof item.wordIndex === 'number') return item.wordIndex
  return currentIndex
}

function playCurrent() {
  const sid = sessionId
  if (currentIndex >= currentItems.length) { finish(); return }
  player.index = itemWordIndex(currentItems[currentIndex])
  emitState()
  const text = itemText(currentItems[currentIndex])
  unlockAudio()
  if (player.engine === 'mespeak') playMespeakItem(text, sid)
  else if (player.engine === 'tencent') playTencentItem(text, sid)
  else if (player.engine === 'kokoro') playKokoroItem(text, sid)
  else playSystemItem(text, sid)
}

// 引擎失败后尝试下一个可用引擎（按已尝试集合去重，避免死循环）
function fallbackAndPlay(text, sid) {
  triedEngines.add(player.engine)
  const order = fallbackOrder()
  const next = order.find((c) => isEngineEnabled(c) && !triedEngines.has(c))
  if (!next) { advance(sid); return } // 所有可用引擎都已失败，结束本轮
  player.engine = next
  emitState()
  if (next === 'mespeak') playMespeakItem(text, sid)
  else if (next === 'tencent') playTencentItem(text, sid)
  else if (next === 'kokoro') playKokoroItem(text, sid)
  else playSystemItem(text, sid)
}

function playSystemItem(text, sid) {
  if (!speechSupported()) {
    if (mespeakReady) { player.engine = 'mespeak'; playMespeakItem(text, sid); return }
    advance(sid); return
  }
  ensureUnlockedSystem()
  if (!voicesReady) loadVoices()
  const en = pickVoice()
  const u = new SpeechSynthesisUtterance(text)
  u.lang = (en && en.lang) || 'en-US'
  u.rate = currentOpts.rate || 0.92
  if (en) u.voice = en
  player.detail = 'sysVoice'
  emitState()
  const speakStart = Date.now()
  u.onend = () => {
    if (sid !== sessionId || player.paused) return
    // 移动端/部分浏览器下 Web Speech 会「静默立即 onend」（无音频），视为失败，
    // 回退到下一个可用引擎（离线 meSpeak 必定出声），避免进度条秒跳完却没声音。
    if (Date.now() - speakStart < 300) { fallbackAndPlay(text, sid); return }
    advance(sid)
  }
  u.onerror = () => { if (sid === sessionId && !player.paused) fallbackAndPlay(text, sid) }
  try { window.speechSynthesis.speak(u) } catch (e) { fallbackAndPlay(text, sid) }
}

function playMespeakItem(text, sid) {
  if (!mespeakReady || !window.meSpeak) {
    // meSpeak 尚未加载完（首屏 4.9MB），最多等待约 8s
    let tries = 0
    const t = setInterval(() => {
      if (sid !== sessionId) { clearInterval(t); return } // 已被停止/重开
      if (mespeakReady && window.meSpeak) { clearInterval(t); doPlayMespeak(text, sid) }
      else if (++tries > 80) { clearInterval(t); fallbackAndPlay(text, sid) }
    }, 100)
    return
  }
  doPlayMespeak(text, sid)
}

function doPlayMespeak(text, sid) {
  try {
    const dataUrl = window.meSpeak.speak(String(text), { ...MESPEAK_OPTS, rawdata: 'data-url' })
    if (!dataUrl) { fallbackAndPlay(text, sid); return }
    player.detail = 'espeak'
    emitState()
    const a = new Audio(dataUrl)
    audioEl = a
    a.onended = () => { if (sid === sessionId && !player.paused) advance(sid) }
    a.onerror = () => { if (sid === sessionId && !player.paused) fallbackAndPlay(text, sid) }
    const p = a.play()
    if (p && p.catch) p.catch(() => { if (sid === sessionId && !player.paused) fallbackAndPlay(text, sid) })
  } catch (e) { fallbackAndPlay(text, sid) }
}

// ---- Kokoro 离线神经网络 TTS（浏览器端 onnxruntime-web 推理）----
// 默认指向本地托管的模型（public/kokoro/...，随 WAR 部署到 188），实现完全内网离线。
// 可用 VITE_KOKORO_MODEL_ID 覆盖为其它本地/远程路径。
let kokoroReady = false
let kokoroLoading = false
let kokoroInstance = null
let kokoroError = null
let kokoroDevice = 'wasm'
const BASE_URL = (import.meta.env && import.meta.env.BASE_URL) || '/'
const KOKORO_MODEL_ID = (import.meta.env && import.meta.env.VITE_KOKORO_MODEL_ID) || (BASE_URL + 'kokoro/Kokoro-82M-v1.0-ONNX')
const KOKORO_EN_VOICE = 'af_heart' // 美式女声（英文，kokoro 当前版本仅内置英文 voice）

// 中文判断
function isCJK(text) { return /[一-鿿]/.test(text || '') }

// 离线关键：kokoro-js 在浏览器里会"硬编码"从 huggingface.co 拉取 voices/*.bin。
// 把这类请求透明重定向到本地静态目录，实现完全内网离线（无需任何外网）。
;(function patchVoiceFetch() {
  if (typeof window === 'undefined' || window.__greKokoroPatched) return
  window.__greKokoroPatched = true
  const orig = window.fetch.bind(window)
  const localBase = BASE_URL.replace(/\/$/, '') + '/kokoro/Kokoro-82M-v1.0-ONNX/voices/'
  window.fetch = function (input, init) {
    const url = typeof input === 'string' ? input : (input && input.url) || ''
    const m = url.match(/huggingface\.co\/onnx-community\/Kokoro-82M-v1\.0-ONNX\/resolve\/main\/voices\/([^?]+)/)
    if (m) return orig(localBase + m[1], init)
    return orig(input, init)
  }
})()

async function loadKokoro() {
  if (kokoroReady || kokoroLoading || typeof window === 'undefined') return kokoroReady
  kokoroLoading = true
  kokoroError = null
  try {
    // 让 onnxruntime-web 的 wasm 走本地（离线），而不是默认 jsdelivr CDN。
    // 注意 transformers v3 的 wasm 配置在 env.backends.onnx.wasm（env.wasm 是 v2 API，不存在）
    try {
      const hf = await import('@huggingface/transformers')
      if (hf && hf.env) {
        const wasmCfg = (hf.env.backends && hf.env.backends.onnx) ? hf.env.backends.onnx.wasm : hf.env.wasm
        if (wasmCfg) wasmCfg.wasmPaths = BASE_URL + 'kokoro-ort/'
        // 浏览器默认 allowLocalModels=false，会跳过本地路径直接报 invalid model ID，必须显式开启
        hf.env.allowLocalModels = true
        hf.env.allowRemoteModels = false // 内网禁远端，失败立即报错而非挂在外网超时
      }
    } catch (e) { /* 忽略，退回到默认 CDN（仅在线时有效） */ }
    const mod = await import('kokoro-js')
    const fromPretrained = (device) => mod.KokoroTTS.from_pretrained(KOKORO_MODEL_ID, {
      // q8 对应本地托管的 model_quantized.onnx（92MB），保证离线可用
      dtype: 'q8',
      device,
      progress_callback: (p) => {
        if (p && typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('kokoro-progress', { detail: p }))
        }
      },
    })
    // 强制 wasm 推理：WebGPU 初始化成功不代表推理正确——q8 量化模型在部分设备的
    // WebGPU 上会"跑通"但输出失真音频（机械音）且不报错，音质不可控。
    // wasm 路径已经过端到端实测（音色正常），离线可用性优先。
    kokoroInstance = await fromPretrained('wasm')
    kokoroDevice = 'wasm'
    if (!kokoroInstance) throw new Error('kokoro load failed')
    kokoroReady = true
    return true
  } catch (e) {
    kokoroError = e
    kokoroReady = false
    kokoroInstance = null
    return false
  } finally {
    kokoroLoading = false
  }
}

// Float32(-1..1) PCM → 16bit WAV Blob
function pcmToWavBlob(pcm, sampleRate) {
  const dataLen = pcm.length * 2
  const buffer = new ArrayBuffer(44 + dataLen)
  const view = new DataView(buffer)
  const writeStr = (off, s) => { for (let i = 0; i < s.length; i++) view.setUint8(off + i, s.charCodeAt(i)) }
  writeStr(0, 'RIFF'); view.setUint32(4, 36 + dataLen, true); writeStr(8, 'WAVE')
  writeStr(12, 'fmt '); view.setUint32(16, 16, true); view.setUint16(20, 1, true)
  view.setUint16(22, 1, true); view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true); view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeStr(36, 'data'); view.setUint32(40, dataLen, true)
  let off = 44
  for (let i = 0; i < pcm.length; i++) {
    const s = Math.max(-1, Math.min(1, pcm[i] || 0))
    view.setInt16(off, s < 0 ? s * 0x8000 : s * 0x7fff, true)
    off += 2
  }
  return new Blob([buffer], { type: 'audio/wav' })
}

// 中文文本无法用 kokoro（当前仅英文引擎）朗读，回退到其它可用引擎
function fallbackToNonKokoro(text, sid) {
  triedEngines.add(player.engine)
  const order = (isMobile()
    ? ['tencent', 'system', 'mespeak']
    : ['system', 'mespeak', 'tencent'])
    .filter((c) => isEngineEnabled(c) && !triedEngines.has(c))
  const next = order[0]
  if (!next) { advance(sid); return }
  player.engine = next
  emitState()
  if (next === 'mespeak') playMespeakItem(text, sid)
  else if (next === 'tencent') playTencentItem(text, sid)
  else playSystemItem(text, sid)
}

export function getKokoroState() {
  return { ready: kokoroReady, loading: kokoroLoading, error: kokoroError, device: kokoroDevice }
}

function playKokoroItem(text, sid) {
  // kokoro 当前仅支持英文；中文文本直接回退，避免用英文音色乱读
  if (isCJK(text)) { fallbackToNonKokoro(text, sid); return }
  // 手机默认服务器优先：188 本机推理秒级真人音，不受手机端 92MB 模型/wasm 制约
  if (!kokoroOfflineFirst()) { playServerKokoro(text, sid, true); return }
  // 桌面离线优先：浏览器 wasm 推理（已实测音质正常），失败回服务器合成
  if (kokoroReady && kokoroInstance) { doPlayKokoro(text, sid); return }
  let tries = 0
  const t = setInterval(() => {
    if (sid !== sessionId) { clearInterval(t); return }
    if (kokoroReady && kokoroInstance) { clearInterval(t); doPlayKokoro(text, sid); return }
    if (kokoroError) { clearInterval(t); playServerKokoro(text, sid); return }
    if (++tries > 300) { clearInterval(t); playServerKokoro(text, sid); return }
    loadKokoro().catch(() => {})
  }, 1000)
}

// 服务器端 Kokoro 合成（188 本机原生推理，返回 base64 wav）
// fallbackBrowser=true：服务器不可用时回退浏览器离线推理（等待模型加载），仍失败再回退其它引擎
function playServerKokoro(text, sid, fallbackBrowser) {
  const serverFail = () => {
    if (sid !== sessionId || player.paused) return
    if (!fallbackBrowser) { fallbackAndPlay(text, sid); return }
    // 回浏览器离线推理兜底
    if (kokoroReady && kokoroInstance) { doPlayKokoro(text, sid); return }
    let tries = 0
    const t = setInterval(() => {
      if (sid !== sessionId) { clearInterval(t); return }
      if (kokoroReady && kokoroInstance) { clearInterval(t); doPlayKokoro(text, sid); return }
      if (kokoroError || ++tries > 300) { clearInterval(t); fallbackAndPlay(text, sid); return }
      loadKokoro().catch(() => {})
    }, 1000)
  }
  ttsApi.kokoroSynthesize(text, currentOpts.rate ? currentOpts.rate : 1.0)
    .then((res) => {
      if (sid !== sessionId || player.paused) return
      // http.js 拦截器已返回后端 Result：{ code, message, data=base64 wav }
      if (res && res.code === 0 && res.data) {
        player.detail = 'serverVoice'
        emitState()
        const a = new Audio('data:audio/wav;base64,' + res.data)
        audioEl = a
        a.onended = () => { if (sid === sessionId && !player.paused) advance(sid) }
        a.onerror = () => { if (sid === sessionId && !player.paused) serverFail() }
        const p = a.play()
        if (p && p.catch) p.catch(() => { if (sid === sessionId && !player.paused) serverFail() })
      } else {
        serverFail()
      }
    })
    .catch(serverFail)
}

// 浏览器端推理：加载等待已并入 playKokoroItem 主路径（浏览器优先、服务器兜底）
async function doPlayKokoro(text, sid, retried) {
  // 防御：中文不应进入 kokoro 推理（当前版本仅英文 voice）
  if (isCJK(text)) { fallbackToNonKokoro(text, sid); return }
  try {
    const voices = Array.isArray(kokoroInstance.voices) ? kokoroInstance.voices : []
    const voiceId = voices.includes(KOKORO_EN_VOICE) ? KOKORO_EN_VOICE : voices[0]
    if (!voiceId) { fallbackAndPlay(text, sid); return }
    const out = await kokoroInstance.generate(String(text), {
      voice: voiceId,
      speed: currentOpts.rate ? currentOpts.rate : 1.0,
    })
    if (sid !== sessionId) return
    const blob = pcmToWavBlob(out.audio, out.sampling_rate || 24000)
    player.detail = 'offlineVoice'
    emitState()
    const url = URL.createObjectURL(blob)
    const a = new Audio(url)
    audioEl = a
    a.onended = () => { if (sid === sessionId && !player.paused) { URL.revokeObjectURL(url); advance(sid) } }
    a.onerror = () => { if (sid === sessionId && !player.paused) playServerKokoro(text, sid) }
    const p = a.play()
    if (p && p.catch) p.catch(() => { if (sid === sessionId && !player.paused) playServerKokoro(text, sid) })
  } catch (e) {
    // 浏览器 wasm 推理最终失败 → 服务器端合成兜底
    if (sid === sessionId && !player.paused) playServerKokoro(text, sid)
  }
}

function playTencentItem(text, sid) {
  ttsApi.tencentSynthesize(text)
    .then((res) => {
      if (sid !== sessionId || player.paused) return
      // http.js 拦截器已返回后端 Result：{ code, message, data=base64 mp3 }
      if (!res || res.code !== 0 || !res.data) {
        // 已熔断或失败，回退
        fallbackAndPlay(text, sid)
        return
      }
      player.detail = 'tencentVoice'
      emitState()
      const a = new Audio('data:audio/mp3;base64,' + res.data)
      audioEl = a
      a.onended = () => { if (sid === sessionId && !player.paused) advance(sid) }
      a.onerror = () => { if (sid === sessionId && !player.paused) fallbackAndPlay(text, sid) }
      const p = a.play()
      if (p && p.catch) p.catch(() => { if (sid === sessionId && !player.paused) fallbackAndPlay(text, sid) })
    })
    .catch(() => {
      if (sid === sessionId && !player.paused) fallbackAndPlay(text, sid)
    })
}

export function pauseTTS() {
  if (!player.active || player.paused) return
  if (player.engine === 'kokoro' || player.engine === 'mespeak' || player.engine === 'tencent') {
    if (audioEl) { try { audioEl.pause() } catch (e) { /* ignore */ } }
  } else if (speechSupported()) {
    try { window.speechSynthesis.pause() } catch (e) { /* ignore */ }
  }
  player.paused = true
  emitState()
}

export function resumeTTS() {
  if (!player.active || !player.paused) return
  if (player.engine === 'kokoro' || player.engine === 'mespeak' || player.engine === 'tencent') {
    if (audioEl) { const p = audioEl.play(); if (p && p.catch) p.catch(() => {}) }
  } else if (speechSupported()) {
    try { window.speechSynthesis.resume() } catch (e) { /* ignore */ }
  }
  player.paused = false
  emitState()
}

export function stopTTS() {
  sessionId++ // 让任何在途回调（onended/等待定时器）立即失效，保证停止生效
  triedEngines = new Set()
  cleanup()
  player.active = false
  player.paused = false
  player.index = -1
  currentIndex = 0
  emitState()
}

export function restartTTS() {
  if (!currentItems.length) return
  playItems(currentItems, currentOpts)
}

// 兼容旧调用
export function speak(text, opts) { return playItems([text], opts) }
export function speakSequence(items, opts) { return playItems(items, opts) }
export function stopSpeak() { stopTTS() }
