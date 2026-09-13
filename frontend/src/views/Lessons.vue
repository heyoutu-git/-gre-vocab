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

    <!-- 书本选择 -->
    <div class="book-list">
      <div
        v-for="b in bookOptions"
        :key="b.id"
        class="book-card"
        :class="{ active: selectedBook === b.id }"
        @click="selectBook(b.id)"
      >
        <div class="book-title">{{ b.title }}</div>
        <el-tag v-if="b.bookType === 2" size="small" type="primary" effect="plain" style="margin-right:4px">{{ $t('lessons.tagReading') }}</el-tag>
        <el-tag v-if="b.mine" size="small" type="warning" effect="plain">{{ $t('lessons.tagMine') }}</el-tag>
        <el-tag v-else-if="b.browsePublic" size="small" type="success" effect="plain">{{ $t('lessons.tagShowcase') }}</el-tag>
        <el-tag v-else size="small" type="info" effect="plain">{{ $t('lessons.tagPublic') }}</el-tag>
        <div class="book-meta">{{ $t('lessons.totalWordsOf', { n: planOf(b.id)?.totalWords || 0 }) }}</div>
        <el-progress
          v-if="planOf(b.id)"
          :percentage="planOf(b.id).percent"
          :stroke-width="6"
          style="margin-top:6px"
        />
        <div class="book-actions" v-if="b.mine" @click.stop>
          <el-button size="small" text type="primary" @click="openSettings(b)">{{ $t('lessons.settingsPlan') }}</el-button>
          <el-button size="small" text type="danger" @click="removeBook(b)">{{ $t('common.delete') }}</el-button>
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
    <el-row v-else :gutter="16">
      <el-col v-for="l in filtered" :key="l.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="lesson-card" shadow="hover" @click="open(l)">
          <div class="title">{{ l.title }}</div>
          <div class="meta">{{ l.wordCount }} {{ $t('lessons.words') }}</div>
          <div class="desc">{{ l.description }}</div>
        </el-card>
      </el-col>
    </el-row>

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
            <el-tag size="small" type="warning" effect="plain" style="margin-left:8px">{{ $t('lessons.tagMine') }}</el-tag>
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
            <el-form-item :label="$t('lessons.wordsPerLesson')">
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
  planForm.dailyGoal = b.planDailyWords || null
  planForm.startDate = b.planStartDate || null
  planForm.endDate = b.planEndDate || null
  const p = plans.value[b.id]
  if (p) Object.assign(plan, p)
  settingsVisible.value = true
}

async function savePlan() {
  if (!current.value) return
  saving.value = true
  try {
    await bookApi.updatePlan(current.value.id, {
      wordsPerLesson: planForm.wordsPerLesson,
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

.book-list {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 12px;
  margin-bottom: 16px;
}
.book-card {
  min-width: 175px;
  max-width: 220px;
  flex: 0 0 auto;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  cursor: pointer;
  transition: all .2s;
  background: #fff;
}
.book-card:hover { border-color: var(--gre-primary); }
.book-card.active {
  border-color: var(--gre-primary);
  background: linear-gradient(135deg, #eef2ff, #f5f7ff);
  box-shadow: 0 2px 8px rgba(79, 109, 245, .12);
}
.book-title { font-weight: 700; color: var(--gre-text); font-size: 15px; }
.book-meta { color: var(--gre-text-soft); font-size: 12px; margin-top: 6px; }
.book-actions { margin-top: 8px; display: flex; gap: 4px; }
.hint { color: var(--gre-text-soft); font-size: 12px; margin-left: 8px; }

.lesson-card { margin-bottom: 4px; }
.title { font-weight: 700; color: var(--gre-text); }
.meta { color: var(--gre-primary); font-size: .82rem; margin-top: 4px; }
.desc { color: var(--gre-text-soft); font-size: .8rem; margin-top: 6px; line-height: 1.5; }

.plan-card { margin-top: 12px; background: #f8faff; }
.plan-row { display: flex; justify-content: space-between; padding: 3px 0; font-size: 14px; color: var(--gre-text); }
.plan-row b { color: var(--gre-primary); }
</style>
