<template>
  <div class="bcover" :style="{ background: coverBg }" aria-hidden="true">
    <div class="bc-spine"></div>
    <div class="bc-pages"></div>
    <!-- 「全部」卡片：堆叠书本图形 -->
    <div v-if="all" class="bc-stack"><i></i><i></i><i></i></div>
    <div v-else class="bc-text" :class="{ vert: vertical }">{{ short }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  all: { type: Boolean, default: false }
})

// 深色系渐变调色板（白字可读），按书名哈希确定性取色 —— 同一本书永远同款封面
const PALETTE = [
  'linear-gradient(160deg,#667eea,#764ba2)',
  'linear-gradient(160deg,#f5576c,#c31432)',
  'linear-gradient(160deg,#2b32b2,#1488cc)',
  'linear-gradient(160deg,#c31432,#240b36)',
  'linear-gradient(160deg,#8e2de2,#4a00e0)',
  'linear-gradient(160deg,#11998e,#0f5f4f)',
  'linear-gradient(160deg,#fc4a1a,#b86a10)',
  'linear-gradient(160deg,#0f2027,#2c5364)',
  'linear-gradient(160deg,#ff512f,#dd2476)',
  'linear-gradient(160deg,#1d976c,#143d2e)',
  'linear-gradient(160deg,#3a1c71,#d76d77)',
  'linear-gradient(160deg,#41295a,#2f0743)'
]

const coverBg = computed(() => {
  if (props.all) return 'linear-gradient(160deg,#5b7fa6,#31456b)'
  let h = 0
  for (const c of props.title || '') h = (h * 31 + c.codePointAt(0)) >>> 0
  return PALETTE[h % PALETTE.length]
})

// 封面短标题：中文取前 4 字（竖排），拉丁字母开头取前 8 字符（横排）
const short = computed(() => {
  const t = (props.title || '').trim()
  if (!t) return ''
  if (/[A-Za-z0-9]/.test(t[0])) return t.replace(/ /g, '').slice(0, 8)
  return t.slice(0, 4)
})

const vertical = computed(() => /[\u4e00-\u9fff]/.test((props.title || '')[0] || ''))
</script>

<style scoped>
.bcover {
  position: relative;
  width: 58px;
  height: 80px;
  flex: 0 0 58px;
  border-radius: 3px 7px 7px 3px;
  overflow: hidden;
  color: #fff;
  user-select: none;
  box-shadow: 0 2px 6px rgba(15, 35, 95, .22), inset 0 0 0 1px rgba(255, 255, 255, .12);
}
/* 书脊 */
.bc-spine {
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 6px;
  background: linear-gradient(90deg, rgba(0,0,0,.30), rgba(255,255,255,.18) 55%, rgba(0,0,0,0));
  border-right: 1px solid rgba(255,255,255,.25);
}
/* 右侧页边 */
.bc-pages {
  position: absolute;
  right: 0; top: 3px; bottom: 3px;
  width: 2px;
  background: repeating-linear-gradient(180deg, rgba(255,255,255,.6) 0 1px, rgba(0,0,0,.10) 1px 2px);
}
.bc-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px 7px 6px 10px;
  font-weight: 700;
  font-size: 14px;
  line-height: 1.3;
  letter-spacing: 1px;
  text-align: center;
  text-shadow: 0 1px 3px rgba(0,0,0,.35);
  word-break: break-all;
}
/* 中文封面竖排（传统书装） */
.bc-text.vert {
  writing-mode: vertical-rl;
  text-orientation: upright;
  letter-spacing: 3px;
  padding-top: 8px;
}
/* 「全部」：三本堆叠的书 */
.bc-stack {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
}
.bc-stack i {
  display: block;
  width: 32px;
  height: 5px;
  border-radius: 2px;
  background: rgba(255,255,255,.88);
  box-shadow: 0 1px 2px rgba(0,0,0,.25);
}
.bc-stack i:nth-child(2) { width: 26px; opacity: .72; transform: translateX(-2px); }
.bc-stack i:nth-child(3) { width: 29px; opacity: .5; transform: translateX(2px); }
</style>
