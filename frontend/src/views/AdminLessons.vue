<template>
  <div class="admin">
    <el-tabs v-model="tab">
      <!-- ===== PDF 导入 ===== -->
      <el-tab-pane :label="$t('admin.tabImport')" name="import">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
          <template #title>{{ $t('admin.flowTitle') }}</template>
          {{ $t('admin.flow1') }}<br/>
          {{ $t('admin.flow2') }}<br/>
          {{ $t('admin.flow3') }}
        </el-alert>
        <el-form :inline="true" style="margin-bottom:10px">
          <el-form-item :label="$t('lessons.pickBook')" required>
            <el-select v-model="importBookId" :placeholder="$t('lessons.pickBookPh')" style="width:220px" clearable>
              <el-option v-for="b in books" :key="b.id" :label="b.title" :value="b.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('admin.wordsPerLesson')">
            <el-input-number v-model="batchSize" :min="1" :max="500" />
          </el-form-item>
          <el-form-item>
            <input ref="pdfInput" type="file" accept="application/pdf" style="display:none" @change="onPdfSelected" />
            <el-button :icon="Document" @click="$refs.pdfInput.click()">{{ $t('admin.choosePdf') }}</el-button>
            <el-button type="primary" :loading="parsing" :disabled="!pdfFile" @click="parsePdf">{{ $t('admin.parsePdf') }}</el-button>
            <el-button type="success" :loading="importing" :disabled="!importBookId" @click="doImport">{{ $t('lessons.startImport') }}</el-button>
            <el-button @click="loadSample">{{ $t('admin.fillSample') }}</el-button>
          </el-form-item>
          <el-form-item :label="$t('admin.parsed')">
            <el-tag>{{ $t('admin.parsedCount', { n: parsedCount }) }}</el-tag>
          </el-form-item>
        </el-form>
        <el-input v-model="jsonText" type="textarea" :rows="14" :placeholder="$t('admin.jsonPh')" />
      </el-tab-pane>

      <!-- ===== 书本管理 ===== -->
      <el-tab-pane :label="$t('admin.tabBooks')" name="books">
        <el-button type="primary" style="margin-bottom:10px" @click="addBook">+ {{ $t('admin.addBook') }}</el-button>
        <el-table :data="books" border stripe>
          <el-table-column prop="sortNo" label="#" width="60" />
          <el-table-column prop="title" :label="$t('admin.colTitle')" min-width="180" />
          <el-table-column prop="description" :label="$t('admin.colDesc')" min-width="200" />
          <el-table-column prop="bookType" :label="$t('admin.colType')" width="90">
            <template #default="{ row }">
              <template v-if="row.bookType === 2">{{ $t('admin.typeReading') }}</template>
              <template v-else-if="row.bookType === 1">{{ $t('admin.typeVocab') }}</template>
              <template v-else><el-tag type="info" size="small">{{ $t('admin.typeUnset') }}</el-tag></template>
            </template>
          </el-table-column>
          <el-table-column :label="$t('admin.colScope')" width="150">
            <template #default="{ row }">
              <el-tag v-if="row.isPublic === 1" type="success" size="small">{{ $t('admin.scopePublic') }}</el-tag>
              <el-tag v-else type="warning" size="small">{{ $t('admin.scopePrivate') }}</el-tag>
              <el-tag v-if="row.browsePublic === 1" type="info" size="small" style="margin-left:4px">{{ $t('lessons.tagShowcase') }}</el-tag>
              <div v-if="row.ownerName" style="margin-top:4px;color:var(--gre-text-soft);font-size:12px">{{ row.ownerName }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="status" :label="$t('users.status')" width="80">
            <template #default="{ row }">{{ row.status === 1 ? $t('admin.statusPublished') : $t('admin.statusDraft') }}</template>
          </el-table-column>
          <el-table-column :label="$t('users.action')" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="editBook(row)">{{ $t('common.edit') }}</el-button>
              <el-button size="small" type="danger" @click="delBook(row)">{{ $t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ===== 课时管理 ===== -->
      <el-tab-pane :label="$t('admin.tabLessons')" name="lessons">
        <el-form :inline="true" style="margin-bottom:10px">
          <el-form-item :label="$t('admin.filterBook')">
            <el-select v-model="lessonBookFilter" :placeholder="$t('admin.allBooksPh')" clearable style="width:220px" @change="reloadLessons">
              <el-option v-for="b in books" :key="b.id" :label="b.title" :value="b.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-table :data="lessons" border stripe>
          <el-table-column prop="sortNo" label="#" width="60" />
          <el-table-column prop="title" :label="$t('admin.colLessonTitle')" min-width="180" />
          <el-table-column :label="$t('admin.colBelong')" min-width="160">
            <template #default="{ row }">
              <el-select v-model="row.bookId" size="small" style="width:160px" @change="(val) => assignBook(row, val)">
                <el-option v-for="b in books" :key="b.id" :label="b.title" :value="b.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="wordCount" :label="$t('admin.colWordCount')" width="90" />
          <el-table-column prop="description" :label="$t('admin.colDesc')" min-width="160" />
          <el-table-column :label="$t('users.action')" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="editLesson(row)">{{ $t('common.edit') }}</el-button>
              <el-button size="small" type="danger" @click="delLesson(row)">{{ $t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ===== TTS 设置 ===== -->
      <el-tab-pane :label="$t('admin.tabTts')" name="tts">
        <h4>{{ $t('admin.engineSwitch') }}</h4>
        <el-table :data="ttsEngines" border stripe style="margin-bottom:20px">
          <el-table-column prop="sortNo" label="#" width="60" />
          <el-table-column prop="name" :label="$t('admin.colEngine')" min-width="180" />
          <el-table-column prop="code" :label="$t('admin.colCode')" width="120" />
          <el-table-column :label="$t('users.status')" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="(v) => toggleEngine(row, v)" />
            </template>
          </el-table-column>
        </el-table>

        <h4>{{ $t('admin.tencentAcc') }}</h4>
        <el-button type="primary" style="margin-bottom:10px" @click="addProvider">+ {{ $t('admin.addProvider') }}</el-button>
        <el-table :data="ttsProviders" border stripe>
          <el-table-column prop="provider" :label="$t('admin.colProvider')" width="100" />
          <el-table-column prop="appId" label="AppID" width="120" />
          <el-table-column prop="region" :label="$t('admin.colRegion')" width="130" />
          <el-table-column prop="endpoint" :label="$t('admin.colEndpoint')" min-width="180" />
          <el-table-column :label="$t('users.status')" width="90">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" @change="(v) => saveProviderStatus(row, v)" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('users.action')" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="editProvider(row)">{{ $t('common.edit') }}</el-button>
              <el-button size="small" type="danger" @click="delProvider(row)">{{ $t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <h4 style="margin-top:20px">{{ $t('admin.usageMonitor') }}</h4>
        <el-form :inline="true">
          <el-form-item :label="$t('admin.colProvider')">
            <el-select v-model="usageProvider" :placeholder="$t('admin.selectPh')" style="width:160px">
              <el-option label="tencent" value="tencent" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="loadUsage">{{ $t('admin.query') }}</el-button>
          </el-form-item>
        </el-form>
        <div v-if="ttsUsage">
          {{ $t('admin.usageLine', { date: ttsUsage.usageDate, chars: ttsUsage.charCount, reqs: ttsUsage.requestCount, quota: ttsUsage.quota || $t('admin.noLimit') }) }}
          <el-form :inline="true" style="margin-top:8px">
            <el-form-item :label="$t('admin.setQuota')">
              <el-input-number v-model="quotaInput" :min="0" :step="1000" />
            </el-form-item>
            <el-form-item>
              <el-button @click="saveQuota">{{ $t('admin.saveQuota') }}</el-button>
            </el-form-item>
          </el-form>
        </div>
        <el-empty v-else :description="$t('admin.noUsage')" />
      </el-tab-pane>

      <!-- ===== 阅读篇章对齐调整 ===== -->
      <el-tab-pane :label="$t('admin.tabAlign')" name="align">
        <el-form :inline="true" style="margin-bottom:10px">
          <el-form-item :label="$t('admin.alignBook')">
            <el-select v-model="alignBookId" :placeholder="$t('admin.pickReadingPh')" clearable style="width:240px" @change="onAlignBookChange">
              <el-option v-for="b in readingBooks" :key="b.id" :label="b.title" :value="b.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('admin.lessonLabel')">
            <el-select v-model="alignLessonId" :placeholder="$t('admin.pickLessonPh')" clearable style="width:280px" @change="onAlignLessonChange">
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
        <el-empty v-else :description="$t('admin.pickFirst')" />
      </el-tab-pane>

      <!-- ===== 词汇管理 ===== -->
      <el-tab-pane :label="$t('admin.tabVocabs')" name="vocabs">
        <el-form :inline="true" style="margin-bottom:10px">
          <el-form-item :label="$t('admin.pickLesson')">
            <el-select v-model="selLesson" placeholder="选择课时" style="width:280px" @change="loadVocabs">
              <el-option v-for="l in lessons" :key="l.id" :label="`${l.title}（${l.wordCount}词）`" :value="l.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('admin.moveTo')" v-if="selLesson">
            <el-select v-model="moveTo" :placeholder="$t('admin.pickTargetPh')" style="width:240px">
              <el-option v-for="l in lessons" :key="l.id" :label="l.title" :value="l.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-table :data="vocabs" border stripe v-loading="vocabLoading">
          <el-table-column :label="$t('admin.colWord')" width="150">
            <template #default="{ row }"><el-input v-model="row.word" size="small" /></template>
          </el-table-column>
          <el-table-column :label="$t('admin.colPhonetic')" width="120">
            <template #default="{ row }"><el-input v-model="row.phonetic" size="small" /></template>
          </el-table-column>
          <el-table-column :label="$t('admin.colIpa')" width="120">
            <template #default="{ row }"><el-input v-model="row.phoneticIpa" size="small" /></template>
          </el-table-column>
          <el-table-column :label="$t('admin.colPos')" width="80">
            <template #default="{ row }"><el-input v-model="row.pos" size="small" /></template>
          </el-table-column>
          <el-table-column :label="$t('admin.colInflection')" width="140">
            <template #default="{ row }"><el-input v-model="row.inflection" size="small" /></template>
          </el-table-column>
          <el-table-column :label="$t('admin.colDef')" min-width="180">
            <template #default="{ row }"><el-input v-model="row.definition" size="small" /></template>
          </el-table-column>
          <el-table-column :label="$t('admin.colExample')" min-width="200">
            <template #default="{ row }"><el-input v-model="row.example" size="small" type="textarea" :rows="1" /></template>
          </el-table-column>
          <el-table-column :label="$t('users.action')" width="200" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="saveVocab(row)">{{ $t('common.save') }}</el-button>
              <el-button size="small" v-if="moveTo && moveTo !== selLesson" @click="moveVocab(row)">{{ $t('admin.moveBtn') }}</el-button>
              <el-button size="small" type="danger" @click="delVocab(row)">{{ $t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 课时编辑弹窗 -->
    <el-dialog v-model="lessonDlg" :title="$t('admin.editLessonTitle')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$t('admin.labelTitle')"><el-input v-model="lessonForm.title" /></el-form-item>
        <el-form-item :label="$t('admin.colBelong')">
          <el-select v-model="lessonForm.bookId" :placeholder="$t('lessons.pickBookPh')" style="width:100%">
            <el-option v-for="b in books" :key="b.id" :label="b.title" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('admin.labelSort')"><el-input-number v-model="lessonForm.sortNo" :min="1" /></el-form-item>
        <el-form-item :label="$t('admin.colDesc')"><el-input v-model="lessonForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lessonDlg = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveLesson">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 书本编辑弹窗 -->
    <el-dialog v-model="bookDlg" :title="$t('admin.bookDlgTitle')" width="420px">
      <el-form label-width="80px">
        <el-form-item :label="$t('admin.colTitle')"><el-input v-model="bookForm.title" /></el-form-item>
        <el-form-item :label="$t('admin.labelSort')"><el-input-number v-model="bookForm.sortNo" :min="0" /></el-form-item>
        <el-form-item :label="$t('admin.colDesc')"><el-input v-model="bookForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item :label="$t('admin.colType')">
          <el-radio-group v-model="bookForm.bookType">
            <el-radio :label="1">{{ $t('admin.typeVocab') }}</el-radio>
            <el-radio :label="2">{{ $t('admin.typeReading') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('users.status')">
          <el-radio-group v-model="bookForm.status">
            <el-radio :label="1">{{ $t('admin.statusPublished') }}</el-radio>
            <el-radio :label="0">{{ $t('admin.statusDraft') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('admin.labelPublic')">
          <el-switch v-model="bookForm.isPublic" :active-value="1" :inactive-value="0" :active-text="$t('admin.publicOn')" :inactive-text="$t('admin.publicOff')" />
          <span style="margin-left:8px;color:var(--gre-text-soft);font-size:12px">{{ $t('admin.publicHint') }}</span>
        </el-form-item>
        <el-form-item v-if="bookForm.isPublic === 0" :label="$t('admin.labelOwner')">
          <el-select
            v-model="bookForm.userId"
            filterable
            remote
            clearable
            reserve-keyword
            :placeholder="$t('admin.ownerSearchPh')"
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
          <span style="margin-left:8px;color:var(--gre-text-soft);font-size:12px">{{ $t('admin.ownerHint') }}</span>
        </el-form-item>
        <el-form-item :label="$t('admin.labelShowcase')">
          <el-switch v-model="bookForm.browsePublic" :active-value="1" :inactive-value="0" />
          <span style="margin-left:8px;color:var(--gre-text-soft);font-size:12px">{{ $t('admin.showcaseHint') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bookDlg = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveBook">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- TTS 账号编辑弹窗 -->
    <el-dialog v-model="ttsDlg" :title="$t('admin.tencentAcc')" width="520px">
      <el-form label-width="100px">
        <el-form-item :label="$t('admin.labelEngineCode')">
          <el-select v-model="ttsForm.engineCode" style="width:100%">
            <el-option label="tencent" value="tencent" />
          </el-select>
        </el-form-item>
        <el-form-item label="AppID"><el-input v-model="ttsForm.appId" :placeholder="$t('admin.appIdPh')" /></el-form-item>
        <el-form-item label="SecretId"><el-input v-model="ttsForm.secretId" :placeholder="$t('admin.keepBlank')" /></el-form-item>
        <el-form-item label="SecretKey"><el-input v-model="ttsForm.secretKey" type="password" :placeholder="$t('admin.keepBlank')" show-password /></el-form-item>
        <el-form-item label="Region"><el-input v-model="ttsForm.region" /></el-form-item>
        <el-form-item label="Endpoint"><el-input v-model="ttsForm.endpoint" /></el-form-item>
        <el-form-item :label="$t('admin.labelExtra')">
          <el-input v-model="ttsForm.extraJson" type="textarea" :rows="3" :placeholder="$t('admin.extraPh')" />
        </el-form-item>
        <el-form-item :label="$t('admin.labelSort')"><el-input-number v-model="ttsForm.sortNo" :min="0" /></el-form-item>
        <el-form-item :label="$t('admin.labelEnable')">
          <el-switch v-model="ttsForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ttsDlg = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveProvider">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { adminApi, lessonApi } from '../api'
import ReadingAlignEditor from './ReadingAlignEditor.vue'

const { t } = useI18n()
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
  if (!pdfFile.value) return ElMessage.warning(t('admin.needPdf'))
  parsing.value = true
  try {
    const { data } = await adminApi.parsePdf(pdfFile.value)
    jsonText.value = JSON.stringify(data || [], null, 2)
    ElMessage.success(t('admin.parseOk', { n: (data || []).length }))
  } finally { parsing.value = false }
}

async function doImport() {
  if (!importBookId.value) return ElMessage.warning(t('lessons.needTitle') === t('lessons.needTitle') ? t('admin.needBook') : t('admin.needBook'))
  let items
  try { items = JSON.parse(jsonText.value) } catch (e) { return ElMessage.error(t('admin.badJson')) }
  if (!Array.isArray(items) || !items.length) return ElMessage.warning(t('admin.needJson'))
  importing.value = true
  try {
    const { data } = await adminApi.importVocabularies(batchSize.value, importBookId.value, items)
    ElMessage.success(t('lessons.importOk', { l: data.lessonCount, w: data.vocabCount }))
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
  ElMessage.success(t('common.saved'))
  bookDlg.value = false
  await reloadBooks()
}
async function delBook(row) {
  await ElMessageBox.confirm(t('admin.delBookConfirm', { name: row.title }), t('admin.tip'), { type: 'warning' })
  await adminApi.removeBook(row.id)
  ElMessage.success(t('common.deleted'))
  await reloadBooks()
  await reloadLessons()
}

async function editLesson(row) {
  lessonForm.value = { id: row.id, bookId: row.bookId, title: row.title, sortNo: row.sortNo, description: row.description }
  lessonDlg.value = true
}
async function saveLesson() {
  await adminApi.saveLesson(lessonForm.value)
  ElMessage.success(t('common.saved'))
  lessonDlg.value = false
  await reloadLessons()
}
async function delLesson(row) {
  await ElMessageBox.confirm(t('admin.delLessonConfirm', { name: row.title }), t('admin.tip'), { type: 'warning' })
  await adminApi.removeLesson(row.id)
  ElMessage.success(t('common.deleted'))
  await reloadLessons()
}
async function assignBook(row, bookId) {
  await adminApi.assignLessonBook(row.id, bookId)
  ElMessage.success(t('admin.movedOwner'))
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
  ElMessage.success(t('common.saved'))
}
async function moveVocab(row) {
  await adminApi.reassignVocab(row.id, moveTo.value, row.sortNo)
  ElMessage.success(t('admin.moved'))
  await loadVocabs()
  await reloadLessons()
}
async function delVocab(row) {
  await ElMessageBox.confirm(t('admin.delVocabConfirm'), t('admin.tip'), { type: 'warning' })
  await adminApi.removeVocab(row.id)
  ElMessage.success(t('common.deleted'))
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
  ElMessage.success(t('common.saved'))
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
  ElMessage.success(t('common.saved'))
  ttsDlg.value = false
  await loadTtsProviders()
}
async function saveProviderStatus(row, enabled) {
  const payload = { ...row, enabled }
  await adminApi.saveTtsProvider(payload)
  ElMessage.success(t('common.saved'))
  await loadTtsProviders()
}
async function delProvider(row) {
  await ElMessageBox.confirm('确认删除该账号？', '提示', { type: 'warning' })
  await adminApi.removeTtsProvider(row.id)
  ElMessage.success(t('common.deleted'))
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
