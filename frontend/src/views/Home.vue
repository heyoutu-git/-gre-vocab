<template>
  <div class="home">
    <el-card class="hero" shadow="never">
      <h1>📚 GRE 词汇知识分享</h1>
      <p class="sub">把《美国学生用的 GRE 重要词汇及例句表》拆成课时，自动识别单词 / 音标 / 词性 / 释义 / 例句，点一下就能听发音。</p>
      <div class="actions">
        <el-button type="primary" size="large" @click="goLessons">{{ $t('home.start') }}</el-button>
        <el-button v-if="isAdmin" size="large" @click="goAdmin">{{ $t('nav.admin') }}</el-button>
      </div>
    </el-card>

    <el-row :gutter="16" class="stats">
      <el-col :xs="12" :sm="8">
        <el-card shadow="hover"><div class="num">{{ stats.lessonCount }}</div><div class="lbl">课时</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="8">
        <el-card shadow="hover"><div class="num">{{ stats.vocabCount }}</div><div class="lbl">词汇总量</div></el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover"><div class="num">🔊</div><div class="lbl">单词 / 例句 语音朗读</div></el-card>
      </el-col>
    </el-row>

    <el-card class="feat" shadow="never">
      <h3>功能</h3>
      <ul>
        <li>📑 按固定词数自动切分课时（默认每课时 50 词）</li>
        <li>🔍 自动识别：单词、音标（Barron's + 标准 IPA）、词性、释义、例句</li>
        <li>🔊 浏览器内置语音朗读单词与例句，手机电脑自适应</li>
        <li>🛠 后台可调整每课时包含哪些词、编辑任意字段</li>
      </ul>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { lessonApi } from '../api'

const router = useRouter()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.user?.role === 'admin')
const stats = reactive({ lessonCount: '-', vocabCount: '-' })

function goLessons() { router.push('/lessons') }
function goAdmin() { router.push('/admin/lessons') }

onMounted(async () => {
  try {
    const { data } = await lessonApi.list()
    stats.lessonCount = (data || []).length
    stats.vocabCount = (data || []).reduce((s, l) => s + (l.wordCount || 0), 0)
  } catch (e) { /* 公开接口，失败静默 */ }
})
</script>

<style scoped>
.home { max-width: 960px; margin: 0 auto; }
.hero { background: linear-gradient(135deg, #4f6df5, #6f8bff); color: #fff; border: none; }
.hero h1 { margin: 6px 0 10px; }
.hero .sub { opacity: .92; line-height: 1.7; }
.hero .actions { margin-top: 16px; }
.stats { margin-top: 16px; }
.num { font-size: 28px; font-weight: 700; color: var(--gre-primary); }
.lbl { color: var(--gre-text-soft); font-size: 13px; margin-top: 4px; }
.feat { margin-top: 16px; }
.feat ul { line-height: 2; color: var(--gre-text); }
.feat h3 { margin: 4px 0 10px; }
</style>
