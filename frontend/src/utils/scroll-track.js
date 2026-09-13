// 跟读高亮自动滚屏共享工具
// 设计要点：
// 1. 自动探测滚动容器（.el-main / 窗口），桌面端内容实际滚在 .el-main 里，window.scrollTo 是无效操作
// 2. rAF 闭环追踪：每帧读取元素实时位置并朝目标纠偏（指数趋近），彻底规避
//    behavior:'smooth' 的竞态（连续调用被浏览器静默取消、滚动中途被布局变化带偏）
// 3. 可视区计算计入 sticky 悬浮控件头（.topbar / .head）高度，避免高亮被悬浮头遮挡
// 4. 短句在剩余可视区垂直居中；超长句顶部对齐保证从头可读；已完整可见则不滚动，避免每句抖动

// 自 el 向上找最近的可滚动祖先；找不到返回 null（= window 滚动）
export function findScroller(el) {
  let n = el && el.parentElement
  while (n && n !== document.body) {
    if (n.nodeType === 1) {
      const oy = getComputedStyle(n).overflowY
      if ((oy === 'auto' || oy === 'scroll' || oy === 'overlay') && n.scrollHeight > n.clientHeight + 1) return n
    }
    n = n.parentElement
  }
  return null
}

export function createScrollTracker() {
  let raf = 0
  function stop() {
    if (raf) { cancelAnimationFrame(raf); raf = 0 }
  }

  /**
   * 确保 el 进入可视区（必要时平滑滚动）
   * @param {Element|{el: Element}} target 元素或组件 ref（组件实例自动取 $el）
   * @param {Element|null} headerEl sticky 悬浮控件头（如 .topbar / .head），可为 null
   * @param {{isActive?: Function}} opts 每帧调用的续跑条件（返回 false 立即停止）
   */
  function ensureVisible(target, headerEl, opts = {}) {
    if (!target) return
    const el = target.$el || target
    if (!el || typeof el.getBoundingClientRect !== 'function') return

    const scroller = findScroller(el)
    const sRect = scroller
      ? scroller.getBoundingClientRect()
      : { top: 0, bottom: window.innerHeight }
    let hBottom = sRect.top
    if (headerEl && typeof headerEl.getBoundingClientRect === 'function') {
      hBottom = Math.max(hBottom, headerEl.getBoundingClientRect().bottom)
    }
    const visTop = hBottom + 12
    const visBottom = sRect.bottom - 12

    const rect = el.getBoundingClientRect()
    // 已完整落在可视区内则不滚，避免逐句跳动
    if (rect.top >= visTop - 4 && rect.bottom <= visBottom + 4) { stop(); return }

    const visH = visBottom - visTop
    if (visH <= 40) return
    // 目标：句顶应停留的视口 y。短句居中，超长句顶部对齐（保证从头可读）
    const topTarget = rect.height >= visH - 24
      ? visTop
      : visTop + Math.max(8, (visH - rect.height) / 2)

    stop()
    let lastT = performance.now()
    const step = (now) => {
      if (!el.isConnected || (opts.isActive && !opts.isActive())) { raf = 0; return }
      const dt = Math.min(64, now - lastT); lastT = now
      const err = el.getBoundingClientRect().top - topTarget
      if (Math.abs(err) < 1) { raf = 0; return }
      // 时间常数 80ms 的指数趋近：~250ms 内收敛，帧率无关、目标实时纠偏
      const k = 1 - Math.exp(-dt / 80)
      const d = err * k
      if (scroller) scroller.scrollTop += d
      else window.scrollTo(0, window.scrollY + d)
      raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
  }

  return { ensureVisible, stop }
}
