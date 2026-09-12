<template>
  <div class="m-home">
    <div class="hero">
      <h1>📚 {{ $t('home.mTitle') }}</h1>
      <p class="sub">{{ $t('home.mSub') }}</p>
    </div>

    <div class="stats">
      <div class="stat">
        <div class="num">{{ stats.lessonCount }}</div>
        <div class="lbl">{{ $t('home.statLessons') }}</div>
      </div>
      <div class="stat">
        <div class="num">{{ stats.vocabCount }}</div>
        <div class="lbl">{{ $t('home.statWords') }}</div>
      </div>
      <div class="stat">
        <div class="num">🔊</div>
        <div class="lbl">{{ $t('home.statTts') }}</div>
      </div>
    </div>

    <van-cell-group inset :title="$t('mobile.library')">
      <van-cell
        v-for="b in books"
        :key="b.id"
        :title="b.title"
        :label="bookLabel(b)"
        is-link
        @click="openBook(b)"
      >
        <template #icon>
          <van-icon name="book-o" class="cell-icon" />
        </template>
      </van-cell>
      <van-empty v-if="!loading && !books.length" :description="$t('home.noBooks')" />
    </van-cell-group>

    <van-loading v-if="loading" class="center" />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { bookApi, lessonApi } from '../../api'

const router = useRouter()
const { t } = useI18n()
const books = ref([])
const loading = ref(false)
const stats = reactive({ lessonCount: '-', vocabCount: '-' })

function bookLabel(b) {
  const parts = []
  if (b.lessonCount != null) parts.push(`${b.lessonCount} ${t('lessons.lessons')}`)
  if (b.wordCount != null) parts.push(`${b.wordCount} ${t('lessons.words')}`)
  return parts.join(' · ') || t('home.tapStart')
}

function openBook(b) {
  router.push({ path: '/m/lessons', query: { bookId: String(b.id) } })
}

onMounted(async () => {
  loading.value = true
  try {
    const [{ data: bs }, { data: ls }] = await Promise.all([
      bookApi.list(),
      lessonApi.list()
    ])
    books.value = bs || []
    const list = ls || []
    stats.lessonCount = list.length
    stats.vocabCount = list.reduce((s, l) => s + (l.wordCount || 0), 0)
  } catch (e) {
    books.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.m-home { padding: 12px; }
.hero {
  background: linear-gradient(135deg, #4f6df5, #6f8bff);
  color: #fff;
  border-radius: 12px;
  padding: 18px 16px;
}
.hero h1 { margin: 0 0 6px; font-size: 20px; }
.hero .sub { opacity: .92; font-size: 13px; line-height: 1.6; margin: 0; }
.stats { display: flex; gap: 10px; margin: 12px 0; }
.stat {
  flex: 1;
  background: #fff;
  border-radius: 10px;
  padding: 12px 8px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,.04);
}
.stat .num { font-size: 20px; font-weight: 700; color: #4f6df5; }
.stat .lbl { font-size: 12px; color: #969799; margin-top: 2px; }
.cell-icon { margin-right: 10px; color: #4f6df5; font-size: 18px; }
.center { display: flex; justify-content: center; margin-top: 24px; }
</style>
