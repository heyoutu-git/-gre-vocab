import { ref, onMounted, onUnmounted, watch } from 'vue'
import { adminApi } from '../api'
import { useUserStore } from '../store/user'

// 管理员待审核用户提醒（模块级单例：手机/桌面布局与用户管理页共享同一状态）
// - 闪动提示条跟随待审数：>0 显示，=0 停止（审核后立即刷新）
// - 叮咚音（+振动）仅在待审数量新增时响
// - 音频需用户手势解锁（浏览器自动播放策略），注册全局 pointerdown 解锁
const pendingCount = ref(0)
const flash = ref(false)
let timer = null
let audioCtx = null
let started = false

function isAdminNow() {
  const userStore = useUserStore()
  return userStore.isLogin && userStore.user?.role === 'admin'
}

// 手势解锁音频：pointerdown 里创建/resume AudioContext（无手势创建会 suspended 且无法恢复）
function unlockAudio() {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)()
    if (audioCtx.state === 'suspended') audioCtx.resume().catch(() => {})
  } catch (e) { /* 不支持则跳过，仍有振动+闪动 */ }
}

// 叮咚音：Web Audio 合成两声正弦（880Hz→660Hz），无需音频文件
function dingdong() {
  unlockAudio()
  if (!audioCtx || audioCtx.state !== 'running') return
  try {
    const play = (freq, start, dur) => {
      const osc = audioCtx.createOscillator()
      const gain = audioCtx.createGain()
      osc.type = 'sine'
      osc.frequency.value = freq
      gain.gain.setValueAtTime(0.0001, audioCtx.currentTime + start)
      gain.gain.exponentialRampToValueAtTime(0.35, audioCtx.currentTime + start + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + start + dur)
      osc.connect(gain).connect(audioCtx.destination)
      osc.start(audioCtx.currentTime + start)
      osc.stop(audioCtx.currentTime + start + dur + 0.05)
    }
    play(880, 0, 0.25)   // 叮
    play(660, 0.28, 0.4) // 咚
    // 振动兜底（手机端，即使无声也能感知）
    try { navigator.vibrate && navigator.vibrate([120, 80, 160]) } catch (e) { /* ignore */ }
  } catch (e) { /* ignore */ }
}

async function fetchPending() {
  const { data } = await adminApi.listUsers(null, 2)
  const rows = Array.isArray(data) ? data : (data?.records || data?.list || [])
  return rows.length
}

async function check(allowNotify = true) {
  if (!isAdminNow()) return
  try {
    const prev = pendingCount.value
    pendingCount.value = await fetchPending()
    if (allowNotify && pendingCount.value > prev) dingdong()
    // 闪动条跟随待审数：>0 闪，=0 停
    flash.value = pendingCount.value > 0
  } catch (e) { /* 轮询失败静默，下轮重试 */ }
}

// 审核操作（同意/拒绝/禁用/启用）后立即刷新，不等下一轮轮询
async function afterReview() {
  await check(false)
}

function onVisible() {
  // 回前台立即补查：熄屏/后台时 setInterval 被系统冻结或节流
  if (document.visibilityState === 'visible' && isAdminNow()) check(true)
}

function start() {
  if (started || !isAdminNow()) return
  started = true
  check(false) // 首拉建基线（不响铃）
  timer = setInterval(() => check(true), 30000)
  document.addEventListener('visibilitychange', onVisible)
  document.addEventListener('pointerdown', unlockAudio, { passive: true })
}
function stop() {
  started = false
  if (timer) { clearInterval(timer); timer = null }
  document.removeEventListener('visibilitychange', onVisible)
  document.removeEventListener('pointerdown', unlockAudio)
}

export function useAdminNotify() {
  const userStore = useUserStore()

  watch(() => [userStore.isLogin, userStore.user?.role], () => {
    if (isAdminNow()) start()
    else { stop(); pendingCount.value = 0; flash.value = false }
  })

  onMounted(() => { if (isAdminNow()) start() })
  onUnmounted(stop)

  return { pendingCount, flash, checkNow: check, afterReview, clearFlash: () => {} }
}
