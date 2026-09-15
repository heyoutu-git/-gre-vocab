<template>
  <div class="users-page">
    <div class="bar">
      <h2>{{ $t('users.title') }}</h2>
      <div class="tools">
        <el-select v-model="statusFilter" style="width: 130px" @change="load">
          <el-option :label="$t('users.allStatus')" :value="null" />
          <el-option :label="$t('users.pending')" :value="2" />
          <el-option :label="$t('users.active')" :value="1" />
          <el-option :label="$t('users.disabled')" :value="0" />
          <el-option :label="$t('users.rejected')" :value="3" />
        </el-select>
        <el-input v-model="kw" :placeholder="$t('users.search')" clearable style="width: 220px" @keyup.enter="load" />
        <el-button type="primary" @click="load">{{ $t('common.searchBtn') }}</el-button>
      </div>
    </div>

    <el-table :data="users" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="64" />
      <el-table-column prop="username" :label="$t('users.username')" min-width="120" />
      <el-table-column prop="nickname" :label="$t('users.nickname')" min-width="100" />
      <el-table-column prop="phone" :label="$t('users.phone')" width="120" />
      <el-table-column :label="$t('users.regAddr')" width="160">
        <template #default="{ row }">
          <span v-if="row.registerIp">{{ row.registerIp }}<template v-if="row.registerPort">:{{ row.registerPort }}</template></span>
          <span v-else class="dim">—</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('users.regTime')" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="$t('users.status')" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 2" type="warning">{{ $t('users.pending') }}</el-tag>
          <el-tag v-else-if="row.status === 1" type="success">{{ $t('users.active') }}</el-tag>
          <el-tag v-else-if="row.status === 3" type="danger">{{ $t('users.rejected') }}</el-tag>
          <el-tag v-else type="danger">{{ $t('users.disabled') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('users.action')" width="320" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 2">
            <el-button size="small" type="success" @click="setStatus(row, 1)">{{ $t('users.approve') }}</el-button>
            <el-button size="small" type="danger" plain @click="setStatus(row, 3)">{{ $t('users.reject') }}</el-button>
          </template>
          <el-button v-if="row.status === 1" size="small" type="danger" @click="setStatus(row, 0)">{{ $t('users.disable') }}</el-button>
          <el-button v-if="row.status === 0 || row.status === 3" size="small" type="success" @click="setStatus(row, 1)">{{ $t('users.enable') }}</el-button>
          <el-button size="small" type="warning" plain @click="resetPwd(row)">{{ $t('users.resetPwd') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { adminApi } from '../api'
import { useAdminNotify } from '../composables/useAdminNotify'

// 审核操作后立即刷新全局待审核提示（数量更新，为 0 停止闪动）
const { afterReview } = useAdminNotify()
const { t } = useI18n()

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
  const action = status === 1 ? (row.status === 2 ? t('users.approve') : t('users.enable')) : status === 3 ? t('users.reject') : t('users.disable')
  try {
    await ElMessageBox.confirm(t('users.confirmMsg', { name: row.username, action }), t('users.confirm'), { type: 'warning' })
  } catch (e) { return }
  try {
    await adminApi.setUserStatus(row.id, status)
    ElMessage.success(t('users.ok'))
    load()
    afterReview()
  } catch (e) { /* http 拦截器已提示 */ }
}

// 重置密码为默认密码 123456（用户登录后应自行修改）
async function resetPwd(row) {
  try {
    await ElMessageBox.confirm(t('users.resetPwdConfirm', { name: row.username }), t('users.resetPwd'), {
      type: 'warning',
      confirmButtonText: t('users.confirm'),
      cancelButtonText: t('common.cancel')
    })
  } catch (e) { return }
  try {
    await adminApi.resetUserPassword(row.id)
    ElMessage.success(t('users.resetPwdOk', { name: row.username }))
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
