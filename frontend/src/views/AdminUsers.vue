<template>
  <div class="users-page">
    <div class="bar">
      <h2>用户管理</h2>
      <div class="tools">
        <el-select v-model="statusFilter" style="width: 130px" @change="load">
          <el-option label="全部状态" :value="null" />
          <el-option label="待审核" :value="2" />
          <el-option label="正常" :value="1" />
          <el-option label="已禁用" :value="0" />
          <el-option label="已拒绝" :value="3" />
        </el-select>
        <el-input v-model="kw" placeholder="搜索用户名/昵称/手机号" clearable style="width: 220px" @keyup.enter="load" />
        <el-button type="primary" @click="load">搜索</el-button>
      </div>
    </div>

    <el-table :data="users" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="64" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="100" />
      <el-table-column prop="phone" label="手机号" width="120" />
      <el-table-column label="注册 IP:端口" width="160">
        <template #default="{ row }">
          <span v-if="row.registerIp">{{ row.registerIp }}<template v-if="row.registerPort">:{{ row.registerPort }}</template></span>
          <span v-else class="dim">—</span>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 2" type="warning">待审核</el-tag>
          <el-tag v-else-if="row.status === 1" type="success">正常</el-tag>
          <el-tag v-else-if="row.status === 3" type="danger">已拒绝</el-tag>
          <el-tag v-else type="danger">已禁用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 2">
            <el-button size="small" type="success" @click="setStatus(row, 1)">通过审核</el-button>
            <el-button size="small" type="danger" plain @click="setStatus(row, 3)">拒绝</el-button>
          </template>
          <el-button v-if="row.status === 1" size="small" type="danger" @click="setStatus(row, 0)">禁用</el-button>
          <el-button v-if="row.status === 0 || row.status === 3" size="small" type="success" @click="setStatus(row, 1)">启用</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api'
import { useAdminNotify } from '../composables/useAdminNotify'

// 审核操作后立即刷新全局待审核提示（数量更新，为 0 停止闪动）
const { afterReview } = useAdminNotify()

const users = ref([])
const loading = ref(false)
const kw = ref('')
const statusFilter = ref(2) // 默认看待审核

function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    const { data } = await adminApi.listUsers(kw.value, statusFilter.value)
    users.value = data || []
  } catch (e) { /* http 拦截器已提示 */ } finally { loading.value = false }
}

async function setStatus(row, status) {
  const action = status === 1 ? (row.status === 2 ? '通过审核' : '启用') : status === 3 ? '拒绝' : '禁用'
  try {
    await ElMessageBox.confirm(`确认对用户「${row.username}」执行「${action}」？`, '确认', { type: 'warning' })
  } catch (e) { return }
  try {
    await adminApi.setUserStatus(row.id, status)
    ElMessage.success('操作成功')
    load()
    afterReview()
  } catch (e) { /* http 拦截器已提示 */ }
}

onMounted(load)
</script>

<style scoped>
.users-page { max-width: 1100px; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 10px; }
.bar h2 { margin: 0; }
.tools { display: flex; gap: 8px; align-items: center; }
.dim { color: #aaa; }
</style>
