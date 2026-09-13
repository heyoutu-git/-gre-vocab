<template>
  <div>
    <el-card>
      <template #header><div class="header">👤 {{ $t('mine.title') }}</div></template>
      <div v-if="user" class="profile">
        <el-avatar :size="64" :icon="UserFilled" />
        <div class="info">
          <div class="name">{{ user.nickname || user.username }}</div>
          <div class="meta">{{ $t('mine.account') }}{{ user.username }}</div>
          <div class="meta" v-if="user.role">{{ $t('mine.role') }}{{ user.role === 'admin' ? $t('mine.roleAdmin') : $t('mine.roleStudent') }}</div>
        </div>
      </div>
    </el-card>

    <el-card v-if="resumeItem" style="margin-top:16px">
      <template #header><div class="header">⏯ {{ $t('mine.continueLearn') }}</div></template>
      <div class="resume-row" @click="goResume">
        <div>
          <div class="name">{{ resumeItem.lessonTitle }}</div>
          <div class="meta">{{ $t('mine.continueAt', { n: (resumeItem.lastWordIndex || 0) + 1 }) }}</div>
        </div>
        <el-button type="primary" size="small">{{ $t('lesson.resumeYes') }}</el-button>
      </div>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header><div class="header">🔊 {{ $t('mine.ttsHelp') }}</div></template>
      <p class="tip">{{ $t('mine.ttsHelp1') }}</p>
      <p class="tip">{{ $t('mine.ttsHelp2') }}</p>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import { learningApi } from '../api'

const userStore = useUserStore()
const user = computed(() => userStore.user)

// ---- 继续学习：最近学的未完成一课 ----
const router = useRouter()
const resumeItem = ref(null)
onMounted(async () => {
  if (!userStore.isLogin) return
  try { const { data } = await learningApi.resume(); resumeItem.value = data || null } catch (e) { /* ignore */ }
})
function goResume() {
  if (resumeItem.value) router.push(`/lessons/${resumeItem.value.lessonId}`)
}
</script>

<style scoped>
.header { font-weight: 600; }
.profile { display: flex; align-items: center; gap: 16px; }
.info { line-height: 1.8; }
.resume-row { display: flex; justify-content: space-between; align-items: center; cursor: pointer; gap: 12px; }
.name { font-size: 16px; font-weight: 700; }
.meta { color: #888; font-size: 13px; }
.tip { color: var(--gre-text-soft); line-height: 1.7; margin: 6px 0; }
</style>
