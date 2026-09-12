<template>
  <div class="align-editor" v-loading="loading">
    <div class="topbar">
      <div class="info">
        <span class="title">{{ lessonTitle }}</span>
        <el-tag v-if="hasManual" type="success" size="small">已人工对齐</el-tag>
        <el-tag v-else type="info" size="small">自动对齐</el-tag>
        <span class="hint">
          共 {{ rows.length }} 行 / 英文 {{ enSentences.length }} 句 / 中文 {{ zhSentences.length }} 句
        </span>
      </div>
      <div class="actions">
        <el-button @click="resetAuto">重置为自动对齐</el-button>
        <el-button @click="previewVisible = true">预览</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存对齐</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" style="margin-bottom:10px">
      <template #default>
        错位通常是「中文整体偏移一格」：用行内 <b>⇧ / ⇩</b> 从该行起整体上移/下移中文，一次修好后续所有行，
        不必逐句拖。个别句子对不上时，再从两侧原句池拖到中间格。上移会把本行中文并入上一行，下移会插入空行，均不丢内容。
      </template>
    </el-alert>

    <div class="toolbar">
      <el-input v-model="jumpTo" placeholder="跳到行号" style="width:120px" size="small" @keyup.enter="doJump">
        <template #append><el-button size="small" @click="doJump">跳转</el-button></template>
      </el-input>
      <el-checkbox v-model="onlyEmptyZh" size="small">只看中文为空的行</el-checkbox>
      <span class="spacer" />
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="filteredRows.length"
        layout="prev, pager, next, total"
        size="small"
        background
      />
    </div>

    <div class="layout">
      <!-- 英文原句池 -->
      <div class="pool">
        <div class="pool-title">英文原句</div>
        <el-input v-model="enFilter" placeholder="过滤英文" size="small" clearable style="margin-bottom:8px" />
        <div
          v-for="it in enPoolShown"
          :key="'e' + it.i"
          class="chip en"
          draggable="true"
          @dragstart="onDragStart($event, it.text)"
        >
          <span class="idx">{{ it.i + 1 }}</span>{{ it.text }}
        </div>
        <div v-if="enPoolMore > 0" class="more">另有 {{ enPoolMore }} 句未显示，请用过滤词缩小范围</div>
        <el-empty v-if="!enPoolShown.length" description="无匹配" :image-size="40" />
      </div>

      <!-- 中间对齐行 -->
      <div class="center">
        <div class="rows-head">
          中英对齐结果（显示第 {{ pageStartLabel }} - {{ pageEndLabel }} 行，共 {{ filteredRows.length }} 行）
        </div>
        <div class="rows">
          <div class="row" v-for="it in pagedRows" :key="it.abs">
            <div class="row-no">{{ it.abs + 1 }}</div>
            <div class="row-actions">
              <el-button size="small" circle title="从此行起中文整体上移（本行中文并入上一行）" @click="shiftZhUp(it.abs)">⇧</el-button>
              <el-button size="small" circle title="从此行起中文整体下移（本行插入空中文）" @click="shiftZhDown(it.abs)">⇩</el-button>
              <el-button size="small" circle title="在上方插入空行" @click="insertRow(it.abs)">＋</el-button>
              <el-button size="small" circle type="danger" title="删除本行" @click="removeRow(it.abs)">✕</el-button>
            </div>
            <div
              class="cell en"
              :class="{ over: dragOverCell === it.abs + '-en' }"
              @dragover.prevent="dragOverCell = it.abs + '-en'"
              @dragleave="dragOverCell === it.abs + '-en' && (dragOverCell = null)"
              @drop="onDrop($event, it.abs, 'en')"
            >
              <textarea v-model="rows[it.abs].en" rows="2" placeholder="拖入英文句" />
            </div>
            <div
              class="cell zh"
              :class="{ over: dragOverCell === it.abs + '-zh', empty: !rows[it.abs].zh }"
              @dragover.prevent="dragOverCell = it.abs + '-zh'"
              @dragleave="dragOverCell === it.abs + '-zh' && (dragOverCell = null)"
              @drop="onDrop($event, it.abs, 'zh')"
            >
              <textarea v-model="rows[it.abs].zh" rows="2" placeholder="拖入中文句" />
            </div>
          </div>

          <div class="add-row" @dragover.prevent @drop="onDropNew($event)">
            ＋ 把句子拖到此处，在末尾新增一行
          </div>
        </div>
      </div>

      <!-- 中文原句池 -->
      <div class="pool">
        <div class="pool-title">中文原句</div>
        <el-input v-model="zhFilter" placeholder="过滤中文" size="small" clearable style="margin-bottom:8px" />
        <div
          v-for="it in zhPoolShown"
          :key="'z' + it.i"
          class="chip zh"
          draggable="true"
          @dragstart="onDragStart($event, it.text)"
        >
          <span class="idx">{{ it.i + 1 }}</span>{{ it.text }}
        </div>
        <div v-if="zhPoolMore > 0" class="more">另有 {{ zhPoolMore }} 句未显示，请用过滤词缩小范围</div>
        <el-empty v-if="!zhPoolShown.length" description="无匹配" :image-size="40" />
      </div>
    </div>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" title="预览（阅读端效果）" width="760px">
      <div class="reading-body">
        <div class="para" v-for="(r, i) in previewRows" :key="i">
          <p class="en">{{ r.en }}</p>
          <p class="zh" v-if="r.zh">{{ r.zh }}</p>
        </div>
        <div v-if="previewMore > 0" class="more">仅预览前 {{ previewRows.length }} 行，另有 {{ previewMore }} 行未显示</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api'

const props = defineProps({
  lessonId: { type: [String, Number], required: true },
  lessonTitle: { type: String, default: '' }
})

const POOL_LIMIT = 200
const PREVIEW_LIMIT = 200

const loading = ref(false)
const saving = ref(false)
const enText = ref('')
const zhText = ref('')
const enSentences = ref([])
const zhSentences = ref([])
const rows = ref([])
const hasManual = ref(false)
const dragOverCell = ref(null)
const previewVisible = ref(false)
const dragText = ref('')

const page = ref(1)
const pageSize = ref(30)
const jumpTo = ref('')
const onlyEmptyZh = ref(false)
const enFilter = ref('')
const zhFilter = ref('')

// ---- 英文断句：与阅读端一致，返回 [{text,start,end}] ----
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

// ---- 中文断句：按句末标点切分 ----
function splitZh(text) {
  if (!text) return []
  return text.split(/(?<=[。！？!?])/).map((s) => s.trim()).filter(Boolean)
}

// ---- 按比例自动对齐（与阅读端同算法，用于初始化/重置） ----
function autoAlign() {
  const en = computeEnSentences(enText.value)
  const zh = zhText.value
  const enLen = enText.value.length
  const zhLen = zh.length
  if (!en.length) return []
  return en.map((s, i) => {
    const zs = enLen ? Math.round((s.start / enLen) * zhLen) : 0
    const ze = i === en.length - 1 ? zhLen : Math.round((en[i + 1].start / enLen) * zhLen)
    return { en: s.text, zh: zh.slice(zs, ze).trim() }
  })
}

// ---- 行筛选 / 分页 ----
const filteredRows = computed(() => {
  const out = []
  rows.value.forEach((r, i) => {
    if (onlyEmptyZh.value && (r.zh || '').trim()) return
    out.push({ abs: i })
  })
  return out
})
const pagedRows = computed(() => {
  const s = (page.value - 1) * pageSize.value
  return filteredRows.value.slice(s, s + pageSize.value)
})
const pageStartLabel = computed(() => (filteredRows.value.length ? (page.value - 1) * pageSize.value + 1 : 0))
const pageEndLabel = computed(() => Math.min(page.value * pageSize.value, filteredRows.value.length))

watch([onlyEmptyZh, () => rows.value.length], () => {
  const maxPage = Math.max(1, Math.ceil(filteredRows.value.length / pageSize.value))
  if (page.value > maxPage) page.value = maxPage
})

function doJump() {
  const n = parseInt(jumpTo.value, 10)
  if (!n || n < 1) return ElMessage.warning('请输入有效行号')
  const pos = filteredRows.value.findIndex((x) => x.abs === n - 1)
  if (pos < 0) return ElMessage.warning('该行不在当前筛选结果中')
  page.value = Math.floor(pos / pageSize.value) + 1
}

// ---- 句子池（过滤 + 限量，避免上千 DOM 节点） ----
function buildPool(list, kw) {
  const k = (kw || '').trim().toLowerCase()
  const matched = []
  list.forEach((text, i) => {
    if (!k || text.toLowerCase().includes(k)) matched.push({ i, text })
  })
  return matched
}
const enPoolAll = computed(() => buildPool(enSentences.value, enFilter.value))
const zhPoolAll = computed(() => buildPool(zhSentences.value, zhFilter.value))
const enPoolShown = computed(() => enPoolAll.value.slice(0, POOL_LIMIT))
const zhPoolShown = computed(() => zhPoolAll.value.slice(0, POOL_LIMIT))
const enPoolMore = computed(() => Math.max(0, enPoolAll.value.length - POOL_LIMIT))
const zhPoolMore = computed(() => Math.max(0, zhPoolAll.value.length - POOL_LIMIT))

const validRows = computed(() => rows.value.filter((r) => (r.en && r.en.trim()) || (r.zh && r.zh.trim())))
const previewRows = computed(() => validRows.value.slice(0, PREVIEW_LIMIT))
const previewMore = computed(() => Math.max(0, validRows.value.length - PREVIEW_LIMIT))

async function load() {
  loading.value = true
  try {
    const { data } = await adminApi.getAlignment(props.lessonId)
    enText.value = data.enText || ''
    zhText.value = data.zhText || ''
    enSentences.value = computeEnSentences(enText.value).map((s) => s.text)
    zhSentences.value = splitZh(zhText.value)
    hasManual.value = !!data.hasManualAlignment
    if (data.rows && data.rows.length) {
      rows.value = data.rows.map((r) => ({ en: r.en || '', zh: r.zh || '' }))
    } else {
      rows.value = autoAlign()
    }
    page.value = 1
  } catch (e) {
    ElMessage.error('加载篇章失败')
  } finally {
    loading.value = false
  }
}

// ---- 拖拽 ----
function onDragStart(ev, text) {
  dragText.value = text
  ev.dataTransfer.setData('text/plain', text)
  ev.dataTransfer.effectAllowed = 'copy'
}
function onDrop(ev, abs, kind) {
  const t = ev.dataTransfer.getData('text/plain') || dragText.value
  if (t) rows.value[abs][kind] = t
  dragOverCell.value = null
}
function onDropNew(ev) {
  const t = ev.dataTransfer.getData('text/plain') || dragText.value
  if (t) rows.value.push({ en: t, zh: '' })
}

// ---- 行操作（绝对索引） ----
function insertRow(abs) {
  rows.value.splice(abs, 0, { en: '', zh: '' })
}
function removeRow(abs) {
  rows.value.splice(abs, 1)
}

// 从 abs 行起，中文整体下移一格：本行留空，后续依次接收上一行的中文；
// 末尾溢出的中文追加为新行，保证不丢内容。
function shiftZhDown(abs) {
  const arr = rows.value
  let carry = ''
  for (let i = abs; i < arr.length; i++) {
    const cur = arr[i].zh || ''
    arr[i].zh = carry
    carry = cur
  }
  if (carry.trim()) arr.push({ en: '', zh: carry })
}

// 从 abs 行起，中文整体上移一格：本行中文并入上一行（避免丢内容），
// 之后每行接收下一行的中文，末行清空。
function shiftZhUp(abs) {
  const arr = rows.value
  const cur = (arr[abs].zh || '').trim()
  if (cur) {
    if (abs === 0) {
      return ElMessage.warning('第 1 行中文无法上移（上方无行），请先在上方插入一行')
    }
    const prev = (arr[abs - 1].zh || '').trim()
    arr[abs - 1].zh = prev ? prev + cur : cur
  }
  for (let i = abs; i < arr.length - 1; i++) {
    arr[i].zh = arr[i + 1].zh || ''
  }
  arr[arr.length - 1].zh = ''
}

function resetAuto() {
  rows.value = autoAlign()
  hasManual.value = false
  page.value = 1
  ElMessage.info('已重置为自动对齐（尚未保存）')
}

async function save() {
  saving.value = true
  try {
    const payload = validRows.value.map((r) => ({ en: (r.en || '').trim(), zh: (r.zh || '').trim() }))
    await adminApi.saveAlignment(props.lessonId, payload)
    hasManual.value = true
    ElMessage.success(`已保存 ${payload.length} 行对齐`)
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.align-editor { border: 1px solid var(--gre-border); border-radius: 10px; padding: 14px; background: var(--gre-surface); }
.topbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.info { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.title { font-weight: 600; }
.hint { color: var(--gre-text-soft); font-size: 12px; }
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.toolbar .spacer { flex: 1; }
.layout { display: grid; grid-template-columns: 1fr 1.6fr 1fr; gap: 12px; align-items: start; }
.pool { max-height: 62vh; overflow: auto; border: 1px dashed var(--gre-border); border-radius: 8px; padding: 8px; background: var(--gre-bg); }
.pool-title { font-size: 12px; color: var(--gre-text-soft); margin-bottom: 8px; font-weight: 600; }
.chip { padding: 6px 8px; margin-bottom: 6px; border-radius: 6px; font-size: 13px; line-height: 1.5; cursor: grab; border: 1px solid transparent; }
.chip.en { background: #eef4ff; border-color: #cfe0ff; }
.chip.zh { background: #fdeef2; border-color: #f6d0db; }
.chip .idx { display: inline-block; min-width: 20px; font-size: 11px; color: var(--gre-text-soft); margin-right: 6px; }
.more { font-size: 12px; color: var(--gre-text-soft); padding: 6px 4px; }
.center { max-height: 62vh; overflow: auto; }
.rows-head { font-size: 12px; color: var(--gre-text-soft); margin-bottom: 8px; font-weight: 600; }
.row { display: grid; grid-template-columns: auto auto 1fr 1fr; gap: 6px; align-items: start; margin-bottom: 8px; padding: 6px; border: 1px solid var(--gre-border); border-radius: 8px; background: var(--gre-bg); }
.row-no { font-size: 11px; color: var(--gre-text-soft); min-width: 26px; text-align: right; padding-top: 8px; }
.row-actions { display: flex; flex-direction: column; gap: 3px; }
.cell textarea { width: 100%; border: 1px solid var(--gre-border); border-radius: 6px; padding: 6px; font-size: 13px; resize: vertical; font-family: inherit; box-sizing: border-box; }
.cell.zh.empty textarea { background: #fff8f0; border-color: #f3d19e; }
.cell.over { outline: 2px dashed var(--gre-primary); outline-offset: 1px; border-radius: 6px; }
.add-row { border: 1px dashed var(--gre-border); border-radius: 8px; padding: 14px; text-align: center; color: var(--gre-text-soft); font-size: 13px; }
.reading-body { line-height: 1.9; font-size: 16px; max-height: 60vh; overflow: auto; }
.para { padding: 8px 10px; border-radius: 8px; }
.para .en { margin: 0; color: var(--gre-text); }
.para .zh { margin: 6px 0 0; color: var(--gre-text-soft); font-size: 15px; }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
}
</style>
