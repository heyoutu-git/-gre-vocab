<template>
  <div class="admin">
    <el-tabs v-model="tab">
      <!-- ===== PDF 导入 ===== -->
      <el-tab-pane label="PDF 词汇导入" name="import">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
          <template #title>导入流程</template>
          1) 选择目标书本与 PDF 文件，点击「识别 PDF」；<br/>
          2) 系统自动调用 <code>tools/ingest_pdf.py</code> 解析，把识别结果填入下方文本框；<br/>
          3) 设置每课时词数，点击「导入」即按词数自动切分课时并入库（会重置所选书本内的旧课时和词汇）。
        </el-alert>
        <el-form :inline="true" style="margin-bottom:10px">
          <el-form-item label="目标书本" required>
            <el-select v-model="importBookId" placeholder="选择书本" style="width:220px" clearable>
              <el-option v-for="b in books" :key="b.id" :label="b.title" :value="b.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="每课时词数">
            <el-input-number v-model="batchSize" :min="1" :max="500" />
          </el-form-item>
          <el-form-item>
            <input ref="pdfInput" type="file" accept="application/pdf" style="display:none" @change="onPdfSelected" />
            <el-button :icon="Document" @click="$refs.pdfInput.click()">选择 PDF 文件</el-button>
            <el-button type="primary" :loading="parsing" :disabled="!pdfFile" @click="parsePdf">识别 PDF</el-button>
            <el-button type="success" :loading="importing" :disabled="!importBookId" @click="doImport">导入</el-button>
            <el-button @click="loadSample">填入示例</el-button>
          </el-form-item>
          <el-form-item label="已识别">
            <el-tag>{{ parsedCount }} 条</el-tag>
          </el-form-item>
        </el-form>
        <el-input v-model="jsonText" type="textarea" :rows="14" placeholder='解析后的词汇 JSON 会出现在这里，格式：[{"word":"abase","phonetic":"E5beis","pos":"v","inflection":"abased; abasing","definition":"lower; degrade","example":"..."}]' />
      </el-tab-pane>

      <!-- ===== 书本管理 ===== -->
      <el-tab-pane label="书本管理" name="books">
        <el-button type="primary" style="margin-bottom:10px" @click="addBook">+ 新增书本</el-button>
        <el-table :data="books" border stripe>
          <el-table-column prop="sortNo" label="#" width="60" />
          <el-table-column prop="title" label="书名" min-width="180" />
          <el-table-column prop="description" label="说明" min-width="200" />
          <el-table-column prop="bookType" label="类型" width="90">
            <template #default="{ row }">
              <template v-if="row.bookType === 2">阅读书</template>
              <template v-else-if="row.bookType === 1">词汇书</template>
              <template v-else><el-tag type="info" size="small">未设置</el-tag></template>
            </template>
          </el-table-column>
          <el-table-column label="范围" width="150">
            <template #default="{ row }">
              <el-tag v-if="row.isPublic === 1" type="success" size="small">公共书</el-tag>
              <el-tag v-else type="warning" size="small">私有书</el-tag>
              <el-tag v-if="row.browsePublic === 1" type="info" size="small" style="margin-left:4px">展示</el-tag>
              <div v-if="row.ownerName" style="margin-top:4px;color:var(--gre-text-soft);font-size:12px">{{ row.ownerName }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">{{ row.status === 1 ? '发布' : '草稿' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="editBook(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="delBook(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ===== 课时管理 ===== -->
      <el-tab-pane label="课时管理" name="lessons">
        <el-form :inline="true" style="margin-bottom:10px">
          <el-form-item label="筛选书本">
            <el-select v-model="lessonBookFilter" placeholder="全部书本" clearable style="width:220px" @change="reloadLessons">
              <el-option v-for="b in books" :key="b.id" :label="b.title" :value="b.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-table :data="lessons" border stripe>
          <el-table-column prop="sortNo" label="#" width="60" />
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column label="所属书本" min-width="160">
            <template #default="{ row }">
              <el-select v-model="row.bookId" size="small" style="width:160px" @change="(val) => assignBook(row, val)">
                <el-option v-for="b in books" :key="b.id" :label="b.title" :value="b.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="wordCount" label="词数" width="90" />
          <el-table-column prop="description" label="说明" min-width="160" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="editLesson(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="delLesson(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ===== TTS 设置 ===== -->
      <el-tab-pane label="TTS 设置" name="tts">
        <h4>引擎开关</h4>
        <el-table :data="ttsEngines" border stripe style="margin-bottom:20px">
          <el-table-column prop="sortNo" label="#" width="60" />
          <el-table-column prop="name" label="引擎" min-width="180" />
          <el-table-column prop="code" label="标识" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="(v) => toggleEngine(row, v)" />
            </template>
          </el-table-column>
        </el-table>

        <h4>腾讯云 TTS 账号</h4>
        <el-button type="primary" style="margin-bottom:10px" @click="addProvider">+ 新增账号</el-button>
        <el-table :data="ttsProviders" border stripe>
          <el-table-column prop="provider" label="服务商" width="100" />
          <el-table-column prop="appId" label="AppID" width="120" />
          <el-table-column prop="region" label="区域" width="130" />
          <el-table-column prop="endpoint" label="接入点" min-width="180" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="(v) => saveProviderStatus(row, v)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="editProvider(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="delProvider(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <h4 style="margin-top:20px">当日用量监控</h4>
        <el-form :inline="true">
          <el-form-item label="服务商">
            <el-select v-model="usageProvider" placeholder="选择" style="width:160px">
              <el-option label="tencent" value="tencent" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="loadUsage">查询</el-button>
          </el-form-item>
        </el-form>
        <div v-if="ttsUsage">
          日期：{{ ttsUsage.usageDate }} &nbsp; 字符数：{{ ttsUsage.charCount }} &nbsp; 请求数：{{ ttsUsage.requestCount }} &nbsp; 限额：{{ ttsUsage.quota || '无限制' }}
          <el-form :inline="true" style="margin-top:8px">
            <el-form-item label="设置当日限额（0=无限制）">
              <el-input-number v-model="quotaInput" :min="0" :step="1000" />
            </el-form-item>
            <el-form-item>
              <el-button @click="saveQuota">保存限额</el-button>
            </el-form-item>
          </el-form>
        </div>
        <el-empty v-else description="暂无用量数据" />
      </el-tab-pane>

      <!-- ===== 阅读篇章对齐调整 ===== -->
      <el-tab-pane label="阅读对齐" name="align">
        <el-form :inline="true" style="margin-bottom:10px">
          <el-form-item label="阅读书本">
            <el-select v-model="alignBookId" placeholder="选择阅读书" clearable style="width:240px" @change="onAlignBookChange">
              <el-option v-for="b in readingBooks" :key="b.id" :label="b.title" :value="b.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="课时">
            <el-select v-model="alignLessonId" placeholder="选择课时" clearable style="width:280px" @change="onAlignLessonChange">
              <el-option v-for="l in alignLessons" :key="l.id" :label="l.title" :value="l.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <ReadingAlignEditor
          v-if="alignLessonId"
          :key="alignLessonId"
          :lesson-id="alignLessonId"
          :lesson-title="alignLessonTitle"
        />
        <el-empty v-else description="请先选择阅读书本与课时" />
      </el-tab-pane>

      <!-- ===== 词汇管理 ===== -->
      <el-tab-pane label="词汇管理" name="vocabs">
        <el-form :inline="true" style="margin-bottom:10px">
          <el-form-item label="选择课时">
            <el-select v-model="selLesson" placeholder="选择课时" style="width:280px" @change="loadVocabs">
              <el-option v-for="l in lessons" :key="l.id" :label="`${l.title}（${l.wordCount}词）`" :value="l.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="移动到" v-if="selLesson">
            <el-select v-model="moveTo" placeholder="选择目标课时" style="width:240px">
              <el-option v-for="l in lessons" :key="l.id" :label="l.title" :value="l.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-table :data="vocabs" border stripe v-loading="vocabLoading">
          <el-table-column label="单词" width="150">
            <template #default="{ row }"><el-input v-model="row.word" size="small" /></template>
          </el-table-column>
          <el-table-column label="音标(源)" width="120">
            <template #default="{ row }"><el-input v-model="row.phonetic" size="small" /></template>
          </el-table-column>
          <el-table-column label="标准IPA" width="120">
            <template #default="{ row }"><el-input v-model="row.phoneticIpa" size="small" /></template>
          </el-table-column>
          <el-table-column label="词性" width="80">
            <template #default="{ row }"><el-input v-model="row.pos" size="small" /></template>
          </el-table-column>
          <el-table-column label="变形" width="140">
            <template #default="{ row }"><el-input v-model="row.inflection" size="small" /></template>
          </el-table-column>
          <el-table-column label="释义" min-width="180">
            <template #default="{ row }"><el-input v-model="row.definition" size="small" /></template>
          </el-table-column>
          <el-table-column label="例句" min-width="200">
            <template #default="{ row }"><el-input v-model="row.example" size="small" type="textarea" :rows="1" /></template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="saveVocab(row)">保存</el-button>
              <el-button size="small" v-if="moveTo && moveTo !== selLesson" @click="moveVocab(row)">移动</el-button>
              <el-button size="small" type="danger" @click="delVocab(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 课时编辑弹窗 -->
    <el-dialog v-model="lessonDlg" title="编辑课时" width="420px">
      <el-form label-width="80px">
        <el-form-item label="标题"><el-input v-model="lessonForm.title" /></el-form-item>
        <el-form-item label="所属书本">
          <el-select v-model="lessonForm.bookId" placeholder="选择书本" style="width:100%">
            <el-option v-for="b in books" :key="b.id" :label="b.title" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="序号"><el-input-number v-model="lessonForm.sortNo" :min="1" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="lessonForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lessonDlg = false">取消</el-button>
        <el-button type="primary" @click="saveLesson">保存</el-button>
      </template>
    </el-dialog>

    <!-- 书本编辑弹窗 -->
    <el-dialog v-model="bookDlg" title="书本" width="420px">
      <el-form label-width="80px">
        <el-form-item label="书名"><el-input v-model="bookForm.title" /></el-form-item>
        <el-form-item label="序号"><el-input-number v-model="bookForm.sortNo" :min="0" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="bookForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="bookForm.bookType">
            <el-radio :label="1">词汇书</el-radio>
            <el-radio :label="2">阅读书</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="bookForm.status">
            <el-radio :label="1">发布</el-radio>
            <el-radio :label="0">草稿</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="公开书">
          <el-switch v-model="bookForm.isPublic" :active-value="1" :inactive-value="0" active-text="公开" inactive-text="私有" />
          <span style="margin-left:8px;color:var(--gre-text-soft);font-size:12px">公开书所有人可见；私有书仅归属用户可见</span>
        </el-form-item>
        <el-form-item v-if="bookForm.isPublic === 0" label="所有者">
          <el-select
            v-model="bookForm.userId"
            filterable
            remote
            clearable
            reserve-keyword
            placeholder="输入用户名或昵称搜索"
            :remote-method="searchOwners"
            :loading="ownerSearchLoading"
            style="width:260px"
          >
            <el-option
              v-for="u in ownerOptions"
              :key="u.id"
              :label="formatOwner(u)"
              :value="u.id"
            />
          </el-select>
          <span style="margin-left:8px;color:var(--gre-text-soft);font-size:12px">私有书归属的用户</span>
        </el-form-item>
        <el-form-item label="展示书">
          <el-switch v-model="bookForm.browsePublic" :active-value="1" :inactive-value="0" />
          <span style="margin-left:8px;color:var(--gre-text-soft);font-size:12px">开启后未登录也能在目录中浏览</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bookDlg = false">取消</el-button>
        <el-button type="primary" @click="saveBook">保存</el-button>
      </template>
    </el-dialog>

    <!-- TTS 账号编辑弹窗 -->
    <el-dialog v-model="ttsDlg" title="腾讯云 TTS 账号" width="520px">
      <el-form label-width="100px">
        <el-form-item label="引擎标识">
          <el-select v-model="ttsForm.engineCode" style="width:100%">
            <el-option label="tencent" value="tencent" />
          </el-select>
        </el-form-item>
        <el-form-item label="AppID"><el-input v-model="ttsForm.appId" placeholder="腾讯云 AppID" /></el-form-item>
        <el-form-item label="SecretId"><el-input v-model="ttsForm.secretId" placeholder="留空则保留原值" /></el-form-item>
        <el-form-item label="SecretKey"><el-input v-model="ttsForm.secretKey" type="password" placeholder="留空则保留原值" show-password /></el-form-item>
        <el-form-item label="Region"><el-input v-model="ttsForm.region" /></el-form-item>
        <el-form-item label="Endpoint"><el-input v-model="ttsForm.endpoint" /></el-form-item>
        <el-form-item label="额外配置(JSON)">
          <el-input v-model="ttsForm.extraJson" type="textarea" :rows="3" placeholder='{"quota": 10000}  当日字符限额，0=无限制' />
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="ttsForm.sortNo" :min="0" /></el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="ttsForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ttsDlg = false">取消</el-button>
        <el-button type="primary" @click="saveProvider">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { adminApi, lessonApi } from '../api'
import ReadingAlignEditor from './ReadingAlignEditor.vue'

const tab = ref('import')
const batchSize = ref(50)
const jsonText = ref('')
const importing = ref(false)
const parsing = ref(false)
const pdfFile = ref(null)
const importBookId = ref(null)

const books = ref([])
const lessons = ref([])
const lessonBookFilter = ref(null)

// ---- 阅读对齐 ----
const alignBookId = ref(null)
const alignLessonId = ref(null)
const alignLessons = ref([])
const alignLessonTitle = ref('')
const readingBooks = computed(() => (books.value || []).filter((b) => b.bookType === 2))

const selLesson = ref(null)
const moveTo = ref(null)
const vocabs = ref([])
const vocabLoading = ref(false)

const lessonDlg = ref(false)
const lessonForm = ref({ id: null, bookId: null, title: '', sortNo: 1, description: '' })

const bookDlg = ref(false)
const bookForm = ref({ id: null, title: '', sortNo: 0, description: '', status: 1, bookType: 1, isPublic: 1, browsePublic: 0, userId: null })
const ownerOptions = ref([])
const ownerSearchLoading = ref(false)
let ownerSearchTimer = null

const ttsEngines = ref([])
const ttsProviders = ref([])
const ttsDlg = ref(false)
const ttsForm = ref({ id: null, engineCode: 'tencent', provider: 'tencent', appId: '', secretId: '', secretKey: '', region: 'ap-guangzhou', endpoint: 'tts.tencentcloudapi.com', extraJson: '', enabled: 1, sortNo: 0 })
const ttsUsage = ref(null)
const usageProvider = ref('tencent')
const quotaInput = ref(0)

const parsedCount = computed(() => {
  try { return Array.isArray(JSON.parse(jsonText.value || '[]')) ? JSON.parse(jsonText.value).length : 0 }
  catch { return 0 }
})

async function reloadBooks() {
  const { data } = await adminApi.listBooks()
  books.value = data || []
}

async function reloadLessons() {
  const { data } = await adminApi.listLessons(lessonBookFilter.value)
  lessons.value = data || []
}

async function onAlignBookChange(bookId) {
  alignLessonId.value = null
  alignLessons.value = []
  if (!bookId) return
  const { data } = await adminApi.listLessons(bookId)
  alignLessons.value = data || []
}
function onAlignLessonChange(lessonId) {
  const l = alignLessons.value.find((x) => x.id === lessonId)
  alignLessonTitle.value = l ? l.title : ''
}

function onPdfSelected(e) {
  const f = e.target.files?.[0]
  if (f) pdfFile.value = f
}

async function parsePdf() {
  if (!pdfFile.value) return ElMessage.warning('请先选择 PDF 文件')
  parsing.value = true
  try {
    const { data } = await adminApi.parsePdf(pdfFile.value)
    jsonText.value = JSON.stringify(data || [], null, 2)
    ElMessage.success(`识别完成：${(data || []).length} 条词汇`)
  } finally { parsing.value = false }
}

async function doImport() {
  if (!importBookId.value) return ElMessage.warning('请选择目标书本')
  let items
  try { items = JSON.parse(jsonText.value) } catch (e) { return ElMessage.error('JSON 格式错误') }
  if (!Array.isArray(items) || !items.length) return ElMessage.warning('请先粘贴或识别词汇 JSON')
  importing.value = true
  try {
    const { data } = await adminApi.importVocabularies(batchSize.value, importBookId.value, items)
    ElMessage.success(`导入成功：${data.lessonCount} 课时 / ${data.vocabCount} 词`)
    tab.value = 'lessons'
    await reloadLessons()
  } catch (e) { /* interceptor 已提示 */ } finally { importing.value = false }
}

function loadSample() {
  jsonText.value = JSON.stringify([
    { word: 'abase', phonetic: 'E5beis', pos: 'v', inflection: 'abased; abasing', definition: 'lower; degrade; humiliate', example: 'He refused to abase himself.' },
    { word: 'abate', phonetic: 'E5beit', pos: 'v', inflection: 'abated; abating', definition: 'subside or moderate', example: 'They waited for the storm to abate.' }
  ], null, 2)
}

function formatOwner(u) {
  if (!u) return ''
  if (u.nickname && u.nickname !== u.username) return `${u.username}（${u.nickname}）`
  return u.username
}

async function searchOwners(keyword) {
  if (!keyword || keyword.trim().length < 1) {
    ownerOptions.value = []
    return
  }
  if (ownerSearchTimer) clearTimeout(ownerSearchTimer)
  ownerSearchTimer = setTimeout(async () => {
    ownerSearchLoading.value = true
    try {
      const { data } = await adminApi.searchUsers(keyword.trim())
      ownerOptions.value = data || []
    } finally {
      ownerSearchLoading.value = false
    }
  }, 300)
}

function addBook() {
  bookForm.value = { id: null, title: '', sortNo: 0, description: '', status: 1, bookType: 1, isPublic: 1, browsePublic: 0, userId: null }
  ownerOptions.value = []
  bookDlg.value = true
}
function editBook(row) {
  // 保持原 bookType（含 null），不要默认填充为 1；配合后端 COALESCE 保留未设置书本的原类型
  bookForm.value = { id: row.id, title: row.title, sortNo: row.sortNo, description: row.description, status: row.status, bookType: row.bookType, isPublic: row.isPublic ?? 1, browsePublic: row.browsePublic ?? 0, userId: row.userId ?? null }
  // 预置当前所有者，确保 el-select 能正确回显
  ownerOptions.value = []
  if (row.userId && row.ownerName) {
    ownerOptions.value = [{ id: row.userId, username: row.ownerName, nickname: '' }]
  }
  bookDlg.value = true
}
async function saveBook() {
  await adminApi.saveBook(bookForm.value)
  ElMessage.success('已保存')
  bookDlg.value = false
  await reloadBooks()
}
async function delBook(row) {
  await ElMessageBox.confirm(`确认删除书本「${row.title}」？其课时将变为未归属。`, '提示', { type: 'warning' })
  await adminApi.removeBook(row.id)
  ElMessage.success('已删除')
  await reloadBooks()
  await reloadLessons()
}

async function editLesson(row) {
  lessonForm.value = { id: row.id, bookId: row.bookId, title: row.title, sortNo: row.sortNo, description: row.description }
  lessonDlg.value = true
}
async function saveLesson() {
  await adminApi.saveLesson(lessonForm.value)
  ElMessage.success('已保存')
  lessonDlg.value = false
  await reloadLessons()
}
async function delLesson(row) {
  await ElMessageBox.confirm(`确认删除「${row.title}」？该课时的词汇也会一并删除。`, '提示', { type: 'warning' })
  await adminApi.removeLesson(row.id)
  ElMessage.success('已删除')
  await reloadLessons()
}
async function assignBook(row, bookId) {
  await adminApi.assignLessonBook(row.id, bookId)
  ElMessage.success('已调整书本归属')
  await reloadLessons()
}

async function loadVocabs() {
  if (!selLesson.value) return
  vocabLoading.value = true
  try {
    const lid = selLesson.value
    const resp = await (await import('../api')).lessonApi.vocabularies(lid)
    vocabs.value = resp.data || []
  } finally { vocabLoading.value = false }
}
async function saveVocab(row) {
  await adminApi.saveVocab(row)
  ElMessage.success('已保存')
}
async function moveVocab(row) {
  await adminApi.reassignVocab(row.id, moveTo.value, row.sortNo)
  ElMessage.success('已移动')
  await loadVocabs()
  await reloadLessons()
}
async function delVocab(row) {
  await ElMessageBox.confirm('确认删除该词汇？', '提示', { type: 'warning' })
  await adminApi.removeVocab(row.id)
  ElMessage.success('已删除')
  await loadVocabs()
  await reloadLessons()
}

// ---- TTS 管理 ----
async function loadTtsEngines() {
  const { data } = await adminApi.listTtsEngines()
  ttsEngines.value = data || []
}
async function loadTtsProviders() {
  const { data } = await adminApi.listTtsProviders()
  ttsProviders.value = data || []
}
async function toggleEngine(row, enabled) {
  await adminApi.setTtsEngineEnabled(row.id, enabled === 1)
  ElMessage.success('已更新')
  await loadTtsEngines()
}

function addProvider() {
  ttsForm.value = { id: null, engineCode: 'tencent', provider: 'tencent', appId: '', secretId: '', secretKey: '', region: 'ap-guangzhou', endpoint: 'tts.tencentcloudapi.com', extraJson: JSON.stringify({ quota: 0 }, null, 2), enabled: 1, sortNo: 0 }
  ttsDlg.value = true
}
function editProvider(row) {
  ttsForm.value = { ...row, secretId: '', secretKey: '' }
  ttsDlg.value = true
}
async function saveProvider() {
  const payload = { ...ttsForm.value }
  // 未填写 secret 时不传，保留原值
  if (!payload.secretId) delete payload.secretId
  if (!payload.secretKey) delete payload.secretKey
  await adminApi.saveTtsProvider(payload)
  ElMessage.success('已保存')
  ttsDlg.value = false
  await loadTtsProviders()
}
async function saveProviderStatus(row, enabled) {
  const payload = { ...row, enabled }
  await adminApi.saveTtsProvider(payload)
  ElMessage.success('已更新')
  await loadTtsProviders()
}
async function delProvider(row) {
  await ElMessageBox.confirm('确认删除该账号？', '提示', { type: 'warning' })
  await adminApi.removeTtsProvider(row.id)
  ElMessage.success('已删除')
  await loadTtsProviders()
}
async function loadUsage() {
  const { data } = await adminApi.getTtsUsage(usageProvider.value)
  ttsUsage.value = data
  quotaInput.value = data?.quota || 0
}
async function saveQuota() {
  await adminApi.setTtsQuota(usageProvider.value, quotaInput.value)
  ElMessage.success('限额已更新')
  await loadUsage()
}

onMounted(async () => {
  await reloadBooks()
  await reloadLessons()
  await loadTtsEngines()
  await loadTtsProviders()
})
</script>

<style scoped>
.admin { max-width: 1200px; }
</style>
