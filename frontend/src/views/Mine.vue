<template>
  <div>
    <el-card>
      <template #header><div class="header">👤 我的</div></template>
      <div v-if="user" class="profile">
        <el-avatar :size="64" :icon="UserFilled" />
        <div class="info">
          <div class="name">{{ user.nickname || user.username }}</div>
          <div class="meta">账号：{{ user.username }}</div>
          <div class="meta" v-if="user.role">角色：{{ user.role }}</div>
        </div>
      </div>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header><div class="header">🔊 语音朗读说明</div></template>
      <p class="tip">单词与例句的朗读使用浏览器内置的语音合成（Web Speech API），无需联网或密钥。</p>
      <p class="tip">建议使用 Chrome / Edge / Safari 打开；若按钮置灰，说明当前浏览器不支持该能力。</p>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'

const userStore = useUserStore()
const user = computed(() => userStore.user)
</script>

<style scoped>
.header { font-weight: 600; }
.profile { display: flex; align-items: center; gap: 16px; }
.info { line-height: 1.8; }
.name { font-size: 16px; font-weight: 700; }
.meta { color: #888; font-size: 13px; }
.tip { color: var(--gre-text-soft); line-height: 1.7; margin: 6px 0; }
</style>
