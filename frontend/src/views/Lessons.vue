<template>
  <div class="lessons">
    <div class="bar">
      <h2>{{ $t('lessons.title') }}</h2>
      <div>
        <el-input v-model="kw" :placeholder="$t('lessons.searchTitle')" clearable style="width: 200px" />
        <el-button v-if="userStore.isLogin" type="primary" style="margin-left:8px" @click="openUpload">{{ $t('lessons.upload') }}</el-button>
        <el-button v-else type="primary" style="margin-left:8px" @click="goLogin">{{ $t('lessons.uploadLogin') }}</el-button>
      </div>
    </div>

    <!-- 书本选择：封面网格 -->
    <div class="book-grid">
      <div
        v-for="b in bookOptions"
        :key="b.id ?? 'all'"
        class="book-card"
        :class="{ active: selectedBook === b.id }"
        @click="selectBook(b.id)"
      >
        <BookCover :title="b.title" :all="b.id === null" />
        <div class="bc-body">
          <div class="bc-title" :title="b.title">{{ b.title }}</div>
          <div class="bc-tags">
            <el-tag v-if="b.bookType === 2" size="small" type="primary" effect="plain">{{ $t('lessons.tagReading') }}</el-tag>
            <el-tag v-if="b.mine" size="small" type="warning" effect="plain">{{ $t('lessons.tagMine') }}</el-tag>
            <el-tag v-else-if="b.browsePublic" size="small" type="success" effect="plain">{{ $t('lessons.tagShowcase') }}</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">{{ $t('lessons.tagPublic') }}</el-tag>
            <span class="bc-words">{{ $t('lessons.totalWordsOf', { n: planOf(b.id)?.totalWords || 0 }) }}</span>
          </div>
          <el-progress
            v-if="planOf(b.id)"
            :percentage="planOf(b.id).percent"
            :stroke-width="4"
            :show-text="false"
          />
          <div class="bc-actions" v-if="userStore.isLogin && (b.mine || b.bookType !== 2)" @click.stop>
            <!-- 学习计划：登录用户对任何词汇书都可设自己的计划；重切分/删除仍限书主 -->
            <el-button v-if="b.bookType !== 2" size="small" text type="primary" @click="openSettings(b)">{{ $t('lessons.settingsPlan') }}</el-button>
            <el-button v-if="b.mine" size="small" text type="danger" @click="removeBook(b)">{{ $t('common.delete') }}</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-alert
      v-if="!userStore.isLogin"
      type="info"
      :closable="false"
      show-icon
      :title="$t('lessons.anonTitle')"
      :description="$t('lessons.anonDesc')"
      style="margin-bottom:16px"
    />
    <el-empty v-if="!loading && !filtered.length" :description="userStore.isLogin ? $t('lessons.emptyOwner') : $t('lessons.emptyAnon')" />
    <div v-else class="lesson-grid">
      <div v-for="(l, i) in filtered" :key="l.id" class="lesson-item" :title="l.description || l.title" @click="open(l)">
        <div class="lesson-num">{{ String(i + 1).padStart(2, '0') }}</div>
        <div class="li-body">
          <div class="li-title">{{ l.title }}</div>
          <div class="li-meta">
            <span class="li-words">{{ l.wordCount }} {{ $t('lessons.words') }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 上传书本对话框 -->
    <el-dialog v-model="uploadVisible" :title="$t('lessons.uploadDialogTitle')" width="480px">
      <el-form label-width="96px">
        <el-form-item :label="$t('lessons.pickBook')">
          <el-select v-model="upload.target" :placeholder="$t('lessons.pickBookPh')" style="width:100%">
            <el-option :label="$t('lessons.newBook')" :value="0" />
            <el-option v-for="b in myBooks" :key="b.id" :label="b.title" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('lessons.bookName')" v-if="!upload.target">
          <el-input v-model="upload.title" :placeholder="$t('lessons.bookNamePh')" />
        </el-form-item>
        <el-form-item :label="$t('lessons.wordsPerLesson')">
          <el-input-number v-model="upload.wordsPerLesson" :min="1" :max="500" />
          <span class="hint">{{ $t('lessons.splitHint') }}</span>
        </el-form-item>
        <el-form-item :label="$t('lessons.pdfFile')">
          <input type="file" accept=".pdf" @change="onFileChange" />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="uploading"
        :title="$t('lessons.ocrWait')"
        type="info"
        :closable="false"
        show-icon
      />
      <template #footer>
        <el-button @click="uploadVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">{{ $t('lessons.startImport') }}</el-button>
      </template>
    </el-dialog>

    <!-- 设置 / 计划对话框 -->
    <el-dialog v-model="settingsVisible" :title="$t('lessons.settingsTitle')" width="520px">
      <template v-if="current">
        <el-form label-width="96px">
          <el-form-item :label="$t('lessons.bookName')">
            <span>{{ current.title }}</span>
            <el-tag v-if="current.mine" size="small" type="warning" effect="plain" style="margin-left:8px">{{ $t('lessons.tagMine') }}</el-tag>
          </el-form-item>
        </el-form>

        <!-- 阅读书：单词计划不适用 -->
        <template v-if="current.bookType === 2">
          <el-alert
            :title="$t('lessons.readingNoPlan')"
            type="info"
            :closable="false"
            show-icon
            :description="$t('lessons.readingNoPlanDesc')"
          />
        </template>

        <!-- 词汇书：正常显示计划 -->
        <template v-else>
          <el-form label-width="96px">
            <!-- 每课词数影响课时切分，仅书主可改 -->
            <el-form-item v-if="current.mine" :label="$t('lessons.wordsPerLesson')">
              <el-input-number v-model="planForm.wordsPerLesson" :min="1" :max="500" />
              <el-button style="margin-left:8px" :loading="resplitting" @click="doResplit">{{ $t('lessons.resplitBtn') }}</el-button>
            </el-form-item>
            <el-divider>{{ $t('lessons.planSection') }}</el-divider>
            <el-form-item :label="$t('lessons.dailyGoal')">
              <el-input-number v-model="planForm.dailyGoal" :min="1" :max="2000" />
            </el-form-item>
            <el-form-item :label="$t('lessons.startDate')">
              <el-date-picker v-model="planForm.startDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('lessons.startDate')" />
            </el-form-item>
            <el-form-item :label="$t('lessons.endDate')">
              <el-date-picker v-model="planForm.endDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('lessons.endDate')" />
            </el-form-item>
          </el-form>
          <!-- 非书主提示：计划只影响自己 -->
          <el-alert
            v-if="!current.mine"
            :title="$t('lessons.planUserScope')"
            type="info"
            :closable="false"
            show-icon
            style="margin-top:4px"
          />

          <el-card class="plan-card" shadow="never">
            <div class="plan-row"><span>{{ $t('lessons.progressTotal') }}</span><b>{{ plan.totalWords }}</b></div>
            <div class="plan-row"><span>{{ $t('lessons.progressLearned') }}</span><b>{{ plan.learnedWords }}</b></div>
            <div class="plan-row"><span>{{ $t('lessons.progressRemain') }}</span><b>{{ plan.remainWords }}</b></div>
            <div class="plan-row"><span>{{ $t('lessons.expectedFinish') }}</span><b>{{ plan.expectedFinishDate || $t('lessons.noGoal') }}</b></div>
            <el-progress :percentage="plan.percent" :stroke-width="10" style="margin-top:8px" />
          </el-card>
        </template>
      </template>
      <template #footer>
        <el-button @click="settingsVisible = false">{{ $t('common.close') }}</el-button>
        <el-button v-if="current && current.bookType !== 2" type="primary" :loading="saving" @click="savePlan">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { lessonApi, bookApi } from '../api'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()
const books = ref([])
const selectedBook = ref(null)
const lessons = ref([])
const loading = ref(false)
const kw = ref('')
const plans = ref({}) // bookId -> plan

const bookOptions = computed(() => {
  return [{ id: null, title: t('lessons.allBooks'), description: t('lessons.allBooksDesc') }, ...books.value]
})
const myBooks = computed(() => books.value.filter((b) => b.mine))
const filtered = computed(() =>
  lessons.value.filter((l) => !kw.value || l.title.toLowerCase().includes(kw.value.toLowerCase()))
)

function planOf(id) { return plans.value[id] || null }

async function loadPlans() {
  const map = {}
  await Promise.all(books.value.map(async (b) => {
    try { const { data } = await bookApi.plan(b.id); map[b.id] = data } catch (e) {}
  }))
  plans.value = map
}

async function refreshBooks() {
  const { data } = await bookApi.list()
  books.value = data || []
  await loadPlans()
}

function open(l) {
  if (!userStore.isLogin) {
    router.push({ name: 'Login', query: { redirect: `/lessons/${l.id}` } })
    return
  }
  router.push(`/lessons/${l.id}`)
}

function goLogin() {
  router.push({ name: 'Login', query: { redirect: '/lessons' } })
}

async function selectBook(bookId) {
  selectedBook.value = bookId
  loading.value = true
  try {
    const { data } = await lessonApi.list(bookId)
    lessons.value = data || []
  } catch (e) { /* 需登录 */ } finally { loading.value = false }
}

// ===== 上传 =====
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadFile = ref(null)
const upload = reactive({ target: 0, title: '', wordsPerLesson: 50 })

function openUpload() { upload.target = 0; upload.title = ''; upload.wordsPerLesson = 50; uploadFile.value = null; uploadVisible.value = true }
function onFileChange(e) { uploadFile.value = e.target.files && e.target.files[0] }

async function submitUpload() {
  if (!uploadFile.value) { ElMessage.warning(t('lessons.needPdf')); return }
  if (!upload.target && !upload.title.trim()) { ElMessage.warning(t('lessons.needTitle')); return }
  uploading.value = true
  try {
    const { data } = await bookApi.importBook(uploadFile.value, {
      bookTitle: upload.target ? null : upload.title.trim(),
      bookId: upload.target || null,
      wordsPerLesson: upload.wordsPerLesson
    })
    ElMessage.success(t('lessons.importOk', { l: data.lessonCount, w: data.vocabCount }))
    uploadVisible.value = false
    await refreshBooks()
    if (data.bookId) await selectBook(data.bookId)
  } catch (e) { /* 错误已提示 */ } finally { uploading.value = false }
}

// ===== 设置 / 计划 =====
const settingsVisible = ref(false)
const saving = ref(false)
const resplitting = ref(false)
const current = ref(null)
const plan = reactive({ totalWords: 0, learnedWords: 0, remainWords: 0, percent: 0, expectedFinishDate: null })
const planForm = reactive({ wordsPerLesson: 50, dailyGoal: null, startDate: null, endDate: null })

function openSettings(b) {
  current.value = b
  planForm.wordsPerLesson = b.wordsPerLesson || 50
  // 计划从「我在这本书上的计划」初始化（后端已按当前用户返回）
  const p = plans.value[b.id]
  planForm.dailyGoal = (p && p.dailyGoal) || b.planDailyWords || null
  planForm.startDate = (p && p.startDate) || b.planStartDate || null
  planForm.endDate = (p && p.endDate) || b.planEndDate || null
  if (p) Object.assign(plan, p)
  settingsVisible.value = true
}

async function savePlan() {
  if (!current.value) return
  saving.value = true
  try {
    // 非书主不传每课词数（书级设置仅书主可改）
    await bookApi.updatePlan(current.value.id, {
      wordsPerLesson: current.value.mine ? planForm.wordsPerLesson : null,
      planDailyWords: planForm.dailyGoal,
      planStartDate: planForm.startDate,
      planEndDate: planForm.endDate
    })
    ElMessage.success(t('common.saved'))
    await refreshBooks()
    const p = await bookApi.plan(current.value.id)
    Object.assign(plan, p.data)
  } catch (e) {} finally { saving.value = false }
}

async function doResplit() {
  if (!current.value) return
  resplitting.value = true
  try {
    const { data } = await bookApi.resplit(current.value.id, planForm.wordsPerLesson)
    ElMessage.success(t('lessons.resplitOk', { n: planForm.wordsPerLesson, l: data.lessonCount }))
    await refreshBooks()
  } catch (e) {} finally { resplitting.value = false }
}

async function removeBook(b) {
  try {
    await ElMessageBox.confirm(t('lessons.deleteConfirm', { name: b.title }), t('lessons.deleteTitle'), { type: 'warning' })
  } catch (e) { return }
  try {
    await bookApi.remove(b.id)
    ElMessage.success(t('common.deleted'))
    await refreshBooks()
  } catch (e) {}
}

onMounted(async () => {
  loading.value = true
  try {
    await refreshBooks()
    // 优先使用 URL ?bookId= 恢复用户进入课时前选中的书本（来自课时详情页回退）
    const queryBid = Number(route.query.bookId)
    const matched = Number.isFinite(queryBid) && books.value.find((b) => b.id === queryBid)
    if (matched) {
      await selectBook(queryBid)
    } else if (books.value.length) {
      // 默认选中第一个有课时的可见书本（跳过"全部"）
      const first = books.value.find((b) => (plans.value[b.id]?.totalWords || 0) > 0) || books.value[0]
      await selectBook(first.id)
    } else {
      await selectBook(null)
    }
  } catch (e) {
    await selectBook(null)
  } finally { loading.value = false }
})
</script>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; flex-wrap: wrap; gap: 10px; }
.bar h2 { margin: 0; }

/* 书架网格：自动换行，无需横向滚动 */
.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(252px, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}
.book-card {
  display: flex;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  cursor: pointer;
  transition: all .18s;
  background: #fff;
}
.book-card:hover {
  border-color: var(--gre-primary);
  transform: translateY(-2px);
  box-shadow: 0 4px 14px rgba(79, 109, 245, .13);
}
.book-card.active {
  border-color: var(--gre-primary);
  background: linear-gradient(135deg, #eef2ff, #f5f7ff);
  box-shadow: 0 2px 10px rgba(79, 109, 245, .16);
}
.bc-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.bc-title {
  font-weight: 700;
  color: var(--gre-text);
  font-size: 14px;
  line-height: 1.35;
  /* 固定两行高度，卡片对齐 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.7em;
}
.bc-tags {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.bc-words {
  margin-left: auto;
  color: var(--gre-text-soft);
  font-size: 12px;
  white-space: nowrap;
}
.bc-actions { display: flex; gap: 4px; margin-top: -2px; }
.bc-actions .el-button { margin: 0; padding: 4px 6px; }
.hint { color: var(--gre-text-soft); font-size: 12px; margin-left: 8px; }

/* 课时网格：自动换行，无需横向滚动 */
.lesson-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(225px, 1fr));
  gap: 12px;
}
.lesson-item {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 12px;
  background: var(--gre-surface, #fff);
  border: 1px solid var(--gre-border, #e6e9ef);
  border-radius: 12px;
  cursor: pointer;
  overflow: hidden;
  transition: transform .16s ease, box-shadow .16s ease, border-color .16s ease;
}
/* 左侧主色竖条：默认隐形，悬停浮现 */
.lesson-item::before {
  content: '';
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 3px;
  background: var(--gre-primary, #4f6df5);
  opacity: 0;
  transition: opacity .16s ease;
}
.lesson-item:hover {
  transform: translateY(-3px);
  border-color: var(--gre-primary, #4f6df5);
  box-shadow: 0 6px 18px rgba(79, 109, 245, .13);
}
.lesson-item:hover::before { opacity: 1; }
.lesson-num {
  flex: 0 0 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gre-primary-soft, #eef1fe);
  color: var(--gre-primary, #4f6df5);
  border-radius: 10px;
  font-weight: 700;
  font-size: 15px;
  letter-spacing: .5px;
  transition: background .16s ease, color .16s ease;
}
.lesson-item:hover .lesson-num { background: var(--gre-primary, #4f6df5); color: #fff; }
.li-body { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 4px; }
.li-title {
  font-weight: 600;
  color: var(--gre-text, #1f2d3d);
  font-size: 14px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.li-meta { display: flex; align-items: center; gap: 8px; }
.li-words { color: var(--gre-text-soft, #5b6b7f); font-size: 12px; }

.plan-card { margin-top: 12px; background: #f8faff; }
.plan-row { display: flex; justify-content: space-between; padding: 3px 0; font-size: 14px; color: var(--gre-text); }
.plan-row b { color: var(--gre-primary); }
</style>
