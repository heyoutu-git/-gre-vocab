<template>
  <div class="m-lessons">
    <van-cell-group inset :title="$t('lessons.pickBook')">
      <van-field
        :model-value="selectedBookTitle"
        readonly
        is-link
        :placeholder="$t('lessons.pickBookPh')"
        @click="showBookPicker = true"
      />
    </van-cell-group>

    <van-popup v-model:show="showBookPicker" position="bottom" round>
      <van-picker
        :columns="bookColumns"
        @confirm="onPickBook"
        @cancel="showBookPicker = false"
        show-toolbar
        :title="$t('lessons.pickBook')"
      />
    </van-popup>

    <!-- 当前书学习进度（跨端同步） -->
    <div class="progress-card" v-if="bookId && plan.totalWords > 0">
      <div class="plan-row">
        <span>{{ $t('lessons.progressTotal') }}</span><b>{{ plan.totalWords }}</b>
        <span>{{ $t('lessons.progressLearned') }}</span><b>{{ plan.learnedWords }}</b>
        <span>{{ $t('lessons.progressRemain') }}</span><b>{{ plan.remainWords }}</b>
      </div>
      <van-progress :percentage="plan.percent" stroke-width="8" />
      <div class="plan-pct">{{ plan.percent }}%</div>
    </div>

    <van-cell-group inset :title="$t('lessons.lessons')" v-if="bookId">
      <van-cell
        v-for="l in lessons"
        :key="l.id"
        :title="l.title"
        :label="l.wordCount != null ? `${l.wordCount} ${$t('lessons.words')}` : ''"
        is-link
        @click="openLesson(l)"
      >
        <template #icon>
          <van-icon name="label-o" class="cell-icon" />
        </template>
      </van-cell>
      <van-empty v-if="!loading && bookId && !lessons.length" :description="$t('lessons.noLessons')" />
    </van-cell-group>

    <van-loading v-if="loading" class="center" />
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { bookApi, lessonApi } from '../../api'

const route = useRoute()
const router = useRouter()

const books = ref([])
const bookId = ref(route.query.bookId || '')
const lessons = ref([])
const loading = ref(false)
const showBookPicker = ref(false)
const plan = reactive({ totalWords: 0, learnedWords: 0, remainWords: 0, percent: 0 })

const bookColumns = computed(() => books.value.map((b) => ({ text: b.title, value: String(b.id) })))
const selectedBookTitle = computed(() => {
  const b = books.value.find((x) => String(x.id) === String(bookId.value))
  return b ? b.title : ''
})

async function loadBooks() {
  try {
    const { data } = await bookApi.list()
    books.value = data || []
    if (!bookId.value && books.value.length) bookId.value = String(books.value[0].id)
  } catch (e) { books.value = [] }
}

async function loadLessons() {
  if (!bookId.value) { lessons.value = []; return }
  loading.value = true
  try {
    const { data } = await lessonApi.list(bookId.value)
    lessons.value = data || []
  } catch (e) { lessons.value = [] }
  finally { loading.value = false }
  // 学习进度（登录后服务端返回；未登录 plan 接口按匿名计算，进度为 0）
  try {
    const { data } = await bookApi.plan(bookId.value)
    if (data) Object.assign(plan, data)
  } catch (e) { /* ignore */ }
}

function onPickBook({ selectedValues }) {
  bookId.value = selectedValues[0]
  showBookPicker.value = false
}

function openLesson(l) {
  router.push({ path: `/m/lesson-detail/${l.id}` })
}

onMounted(async () => {
  await loadBooks()
  await loadLessons()
})

watch(bookId, () => {
  // 同步到 URL，便于回退恢复选中书本
  if (String(route.query.bookId || '') !== String(bookId.value)) {
    router.replace({ path: '/m/lessons', query: bookId.value ? { bookId: String(bookId.value) } : {} })
  }
  loadLessons()
})
</script>

<style scoped>
.cell-icon { margin-right: 10px; color: #4f6df5; font-size: 18px; }
.center { display: flex; justify-content: center; margin-top: 24px; }
.progress-card {
  margin: 12px 16px 0; padding: 12px 14px;
  background: #fff; border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0,0,0,.05);
}
.plan-row { display: flex; gap: 8px; align-items: center; font-size: 12.5px; color: #999; margin-bottom: 8px; }
.plan-row b { color: #4f6df5; margin-right: 8px; }
.plan-pct { margin-top: 6px; text-align: right; font-size: 12.5px; color: #4f6df5; font-weight: 600; }
</style>
