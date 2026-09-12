<template>
  <div class="m-admin-users">
    <van-search v-model="kw" :placeholder="$t('users.search')" @search="load" />

    <van-tabs v-model:active="activeTab" sticky @change="load">
      <van-tab :title="$t('users.pending')" name="2" />
      <van-tab :title="$t('users.active')" name="1" />
      <van-tab :title="$t('users.disabled')" name="0" />
      <van-tab :title="$t('users.rejected')" name="3" />
      <van-tab :title="$t('users.allStatus')" name="all" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-loading v-if="loading && !users.length" class="center" vertical>{{ $t('mobile.loading') }}</van-loading>
      <van-empty v-if="!loading && !users.length" :description="$t('users.empty')" />

      <div class="cards">
        <div v-for="u in users" :key="u.id" class="ucard">
          <div class="uhead">
            <span class="uname">{{ u.nickname || u.username }}</span>
            <van-tag v-if="u.status === 2" type="warning">{{ $t('users.pending') }}</van-tag>
            <van-tag v-else-if="u.status === 1" type="success">{{ $t('users.active') }}</van-tag>
            <van-tag v-else-if="u.status === 3" type="danger">{{ $t('users.rejected') }}</van-tag>
            <van-tag v-else type="danger">{{ $t('users.disabled') }}</van-tag>
          </div>
          <div class="umeta">
            <div><span class="k">{{ $t('users.username') }}</span>{{ u.username }}</div>
            <div v-if="u.phone"><span class="k">{{ $t('users.phone') }}</span>{{ u.phone }}</div>
            <div><span class="k">{{ $t('users.regAddr') }}</span><span v-if="u.registerIp">{{ u.registerIp }}<template v-if="u.registerPort">:{{ u.registerPort }}</template></span><template v-else>—</template></div>
            <div><span class="k">{{ $t('users.regTime') }}</span>{{ formatTime(u.createdAt) }}</div>
          </div>
          <div class="ubtns">
            <template v-if="u.status === 2">
              <van-button size="small" type="success" @click="setStatus(u, 1)">{{ $t('users.approve') }}</van-button>
              <van-button size="small" type="danger" plain @click="setStatus(u, 3)">{{ $t('users.reject') }}</van-button>
            </template>
            <van-button v-if="u.status === 1" size="small" type="danger" plain @click="setStatus(u, 0)">{{ $t('users.disable') }}</van-button>
            <van-button v-if="u.status === 0 || u.status === 3" size="small" type="success" @click="setStatus(u, 1)">{{ $t('users.enable') }}</van-button>
          </div>
        </div>
      </div>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { showConfirmDialog, showSuccessToast } from 'vant'
// 按需引入模式下函数式组件不经过模板解析，样式需手动注册，否则弹窗/Toast 裸渲染
import 'vant/es/dialog/style'
import 'vant/es/toast/style'
import { useI18n } from 'vue-i18n'
import { adminApi } from '../../api'
import { useAdminNotify } from '../../composables/useAdminNotify'

// 审核操作后立即刷新全局待审核提示（数量更新，为 0 停止闪动）
const { afterReview } = useAdminNotify()

const { t } = useI18n()
const users = ref([])
const loading = ref(false)
const refreshing = ref(false)
const kw = ref('')
const activeTab = ref('2') // 默认看待审核

function statusParam() {
  return activeTab.value === 'all' ? undefined : activeTab.value
}

function formatTime(v) {
  if (!v) return '—'
  const d = new Date(v)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    const { data } = await adminApi.listUsers(kw.value || undefined, statusParam())
    users.value = data || []
  } catch (e) { /* http 拦截器已提示 */ } finally { loading.value = false }
}

function onRefresh() {
  refreshing.value = false
  load()
}

async function setStatus(u, status) {
  const action = status === 1 ? (u.status === 2 ? t('users.approve') : t('users.enable'))
    : status === 3 ? t('users.reject') : t('users.disable')
  try {
    await showConfirmDialog({ message: t('users.confirmMsg', { name: u.username, action }) })
  } catch (e) { return }
  try {
    await adminApi.setUserStatus(u.id, status)
    showSuccessToast(t('users.ok'))
    load()
    afterReview()
  } catch (e) { /* http 拦截器已提示 */ }
}

onMounted(load)
</script>

<style scoped>
.m-admin-users { min-height: 100vh; }
.center { padding: 40px 0; }
.cards { padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.ucard {
  background: #fff; border-radius: 10px; padding: 12px 14px;
  box-shadow: 0 1px 4px rgba(0,0,0,.05);
}
.uhead { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.uname { font-size: 15px; font-weight: 600; }
.umeta { font-size: 12.5px; color: #666; line-height: 1.9; }
.umeta .k { display: inline-block; width: 86px; color: #999; }
.ubtns { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
</style>
