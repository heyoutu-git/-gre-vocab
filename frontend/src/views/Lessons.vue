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
        <el-tag v-if="b.bookType === 2" size="small" type="primary" effect="plain" style="margin-right:4px">阅读</el-tag>
        <el-tag v-if="b.mine" size="small" type="warning" effect="plain">我的</el-tag>
        <el-tag v-else-if="b.browsePublic" size="small" type="success" effect="plain">展示</el-tag>
        <el-tag v-else size="small" type="info" effect="plain">公共</el-tag>
        <div class="book-meta">共 {{ planOf(b.id)?.totalWords || 0 }} 词</div>
        <el-progress
          v-if="planOf(b.id)"
          :percentage="planOf(b.id).percent"
          :stroke-width="6"
          style="margin-top:6px"
        />
        <div class="book-actions" v-if="b.mine" @click.stop>
          <el-button size="small" text type="primary" @click="openSettings(b)">设置/计划</el-button>
          <el-button size="small" text type="danger" @click="removeBook(b)">删除</el-button>
        </div>
      </div>
    </div>

    <el-alert
      v-if="!userStore.isLogin"
      type="info"
      :closable="false"
      show-icon
      title="未登录仅可浏览书本目录"
      description="登录后即可查看课时内容、开始学习与制定计划。"
      style="margin-bottom:16px"
    />
    <el-empty v-if="!loading && !filtered.length" :description="userStore.isLogin ? '该书本无课时，请到后台导入 PDF 词汇，或上传你自己的书本' : '登录后可查看课时内容并开始学习'" />
    <el-row v-else :gutter="16">
      <el-col v-for="l in filtered" :key="l.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="lesson-card" shadow="hover" @click="open(l)">
          <div class="title">{{ l.title }}</div>
          <div class="meta">{{ l.wordCount }} 词</div>
          <div class="desc">{{ l.description }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 上传书本对话框 -->
    <el-dialog v-model="uploadVisible" title="上传我的书本（PDF）" width="480px">
      <el-form label-width="96px">
        <el-form-item label="目标书本">
          <el-select v-model="upload.target" placeholder="选择目标" style="width:100%">
            <el-option label="＋ 新建一本新书" :value="0" />
            <el-option v-for="b in myBooks" :key="b.id" :label="b.title" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="书本名称" v-if="!upload.target">
          <el-input v-model="upload.title" placeholder="如：我的 GRE 核心词" />
        </el-form-item>
        <el-form-item label="每课单词数">
          <el-input-number v-model="upload.wordsPerLesson" :min="1" :max="500" />
          <span class="hint">导入后按此数量自动切分课时</span>
        </el-form-item>
        <el-form-item label="PDF 文件">
          <input type="file" accept=".pdf" @change="onFileChange" />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="uploading"
        title="正在识别 PDF 并导入，扫描版可能需要 3-5 分钟，请勿关闭窗口"
        type="info"
        :closable="false"
        show-icon
      />
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 设置 / 计划对话框 -->
    <el-dialog v-model="settingsVisible" title="书本设置与学习计划" width="520px">
      <template v-if="current">
        <el-form label-width="96px">
          <el-form-item label="书本名称">
            <span>{{ current.title }}</span>
            <el-tag size="small" type="warning" effect="plain" style="margin-left:8px">我的</el-tag>
          </el-form-item>
        </el-form>

        <!-- 阅读书：单词计划不适用 -->
        <template v-if="current.bookType === 2">
          <el-alert
            title="阅读书暂不支持单词学习计划"
            type="info"
            :closable="false"
            show-icon
            description="当前书本为阅读片段，不统计词汇数。如需设定阅读进度，可直接进入课时学习。"
          />
        </template>

        <!-- 词汇书：正常显示计划 -->
        <template v-else>
          <el-form label-width="96px">
            <el-form-item label="每课单词数">
              <el-input-number v-model="planForm.wordsPerLesson" :min="1" :max="500" />
              <el-button style="margin-left:8px" :loading="resplitting" @click="doResplit">按此重切分课时</el-button>
            </el-form-item>
            <el-divider>学习计划</el-divider>
            <el-form-item label="每日目标词数">
              <el-input-number v-model="planForm.dailyGoal" :min="1" :max="2000" />
            </el-form-item>
            <el-form-item label="计划开始">
              <el-date-picker v-model="planForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" />
            </el-form-item>
            <el-form-item label="计划结束">
              <el-date-picker v-model="planForm.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" />
            </el-form-item>
          </el-form>

          <el-card class="plan-card" shadow="never">
            <div class="plan-row"><span>总词数</span><b>{{ plan.totalWords }}</b></div>
            <div class="plan-row"><span>已学</span><b>{{ plan.learnedWords }}</b></div>
            <div class="plan-row"><span>剩余</span><b>{{ plan.remainWords }}</b></div>
            <div class="plan-row"><span>预计完成</span><b>{{ plan.expectedFinishDate || '未设目标' }}</b></div>
            <el-progress :percentage="plan.percent" :stroke-width="10" style="margin-top:8px" />
          </el-card>
        </template>
      </template>
      <template #footer>
        <el-button @click="settingsVisible = false">关闭</el-button>
        <el-button v-if="current && current.bookType !== 2" type="primary" :loading="saving" @click="savePlan">保存设置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { lessonApi, bookApi } from '../api'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const books = ref([])
const selectedBook = ref(null)
const lessons = ref([])
const loading = ref(false)
const kw = ref('')
const plans = ref({}) // bookId -> plan

const bookOptions = computed(() => {
  return [{ id: null, title: '全部', description: '显示所有书本的课时' }, ...books.value]
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
  if (!uploadFile.value) { ElMessage.warning('请选择 PDF 文件'); return }
  if (!upload.target && !upload.title.trim()) { ElMessage.warning('请填写书本名称'); return }
  uploading.value = true
  try {
    const { data } = await bookApi.importBook(uploadFile.value, {
      bookTitle: upload.target ? null : upload.title.trim(),
      bookId: upload.target || null,
      wordsPerLesson: upload.wordsPerLesson
    })
    ElMessage.success(`导入成功：${data.lessonCount} 课时 / ${data.vocabCount} 词`)
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
    ElMessage.success('已保存')
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
    ElMessage.success(`已按每课 ${planForm.wordsPerLesson} 词重切分：${data.lessonCount} 课时`)
    await refreshBooks()
  } catch (e) {} finally { resplitting.value = false }
}

async function removeBook(b) {
  try {
    await ElMessageBox.confirm(`确定删除书本「${b.title}」？其下课时与词汇将一并删除（不可恢复）。`, '删除确认', { type: 'warning' })
  } catch (e) { return }
  try {
    await bookApi.remove(b.id)
    ElMessage.success('已删除')
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
