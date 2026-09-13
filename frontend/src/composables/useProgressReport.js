// 学习进度自动上报：播放/跟读期间节流记录位置（跨端存服务端）
// - watch ttsState 变化，10 秒节流调 POST /learning/progress/visit
// - flush()：卸载/离开前强制落一次最后位置（force 绕过节流）
// - wordIndex=-1 仅记一次进入（后端保留已有位置不覆盖）
import { watch } from 'vue'
import { learningApi } from '../api'
import { useUserStore } from '../store/user'

export function useProgressReport(getLessonId, ttsState) {
  const userStore = useUserStore()
  let lastSent = 0
  let lastPos = -1

  function send(pos, force) {
    if (!userStore.isLogin) return
    const lid = getLessonId()
    if (!lid) return
    if (pos >= 0) lastPos = pos
    const now = Date.now()
    if (!force && now - lastSent < 10000) return
    lastSent = now
    learningApi.visit(lid, pos).catch(() => { /* 静默失败，不打扰学习 */ })
  }

  watch(
    () => [ttsState.value.active, ttsState.value.index],
    () => {
      const s = ttsState.value
      if (s.active && s.index >= 0) send(s.index, false)
    }
  )

  return { flush: () => { if (lastPos >= 0) { send(lastPos, true); lastPos = -1 } } }
}

// 进入课时页：记一次访问（last_visit_at/visit_count），并返回上次学到的位置（未完成且有记录时）
export async function checkLessonResume(getLessonId, userStore) {
  const lid = getLessonId()
  if (!lid || !userStore.isLogin) return 0
  learningApi.visit(lid, -1).catch(() => {})
  try {
    const { data } = await learningApi.progressDetail(lid)
    if (data && data.finished !== 1 && (data.lastWordIndex || 0) > 0) return data.lastWordIndex
  } catch (e) { /* ignore */ }
  return 0
}
