<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { initVoices } from './utils/tts'

onMounted(() => {
  initVoices() // 尽早加载语音列表，规避首次朗读竞态
})
</script>
