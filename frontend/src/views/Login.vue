<template>
  <div class="login-page">
    <el-card class="box">
      <div class="lang-row">
        <el-dropdown trigger="click" @command="setLang">
          <span class="lang-btn">🌐 {{ currentLangLabel }} ▾</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="l in langList" :key="l.code" :command="l.code" :class="{ active: l.code === current }">{{ l.label }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <h2>📚 {{ $t('app.title') }}</h2>
      <el-tabs v-model="tab">
        <el-tab-pane :label="$t('login.title')" name="login">
          <el-form :model="loginForm" label-width="0">
            <el-input v-model="loginForm.username" :placeholder="$t('login.username')" :prefix-icon="User" />
            <el-input v-model="loginForm.password" type="password" :placeholder="$t('login.password')" :prefix-icon="Lock" show-password style="margin-top:12px" />
            <div class="captcha-row">
              <el-input v-model="loginForm.captcha" :placeholder="$t('login.captcha')" :prefix-icon="Picture" maxlength="4" style="flex:1" @keyup.enter="doLogin" />
              <img class="captcha-img" :src="captchaImage" :alt="$t('login.captcha')" :title="$t('login.captchaRefresh')" @click="refreshCaptcha" />
            </div>
            <el-checkbox v-model="loginForm.agreedTerms" class="agree">
              {{ $t('login.agree') }}<a href="#" @click.prevent="showTerms">{{ $t('login.terms') }}</a>
            </el-checkbox>
            <el-button type="primary" class="btn" :loading="loading" @click="doLogin">{{ $t('login.btnLogin') }}</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane :label="$t('login.register')" name="register">
          <el-form :model="regForm" label-width="0">
            <el-input v-model="regForm.username" :placeholder="$t('login.username')" :prefix-icon="User" />
            <el-input v-model="regForm.password" type="password" :placeholder="$t('login.password')" :prefix-icon="Lock" show-password style="margin-top:12px" />
            <el-input v-model="regForm.phone" :placeholder="$t('login.phone')" :prefix-icon="Iphone" style="margin-top:12px" maxlength="11" />
            <el-input v-model="regForm.nickname" :placeholder="$t('login.nickname')" :prefix-icon="User" style="margin-top:12px" />
            <el-checkbox v-model="regForm.agreedTerms" class="agree">
              {{ $t('login.agree') }}<a href="#" @click.prevent="showTerms">{{ $t('login.terms') }}</a>
            </el-checkbox>
            <el-button type="primary" class="btn" :loading="loading" @click="doRegister">{{ $t('login.btnRegister') }}</el-button>
            <p class="review-tip"> ⓘ {{ $t('login.reviewTip') }}</p>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <p class="tip">{{ $t('login.devTip') }}</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { User, Lock, Picture } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { useI18n } from 'vue-i18n'
import { useLang } from '../composables/useLang'
const { t } = useI18n()
const { current, list: langList, setLang } = useLang()
const currentLangLabel = computed(() => langList.find((l) => l.code === current.value)?.label || '简体中文')

const tab = ref('login')
const loading = ref(false)
const loginForm = reactive({ username: '', password: '', agreedTerms: false, captcha: '' })
const regForm = reactive({ username: '', password: '', phone: '', nickname: '', agreedTerms: false })
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

// 图形验证码
const captchaToken = ref('')
const captchaImage = ref('')

async function refreshCaptcha() {
  try {
    const { data } = await authApi.captcha()
    captchaToken.value = data.token
    captchaImage.value = 'data:image/png;base64,' + data.imageBase64
    loginForm.captcha = ''
  } catch (e) { /* 取验证码失败时忽略，提交时后端会报验证码错误 */ }
}

function showTerms() {
  ElMessage.info(t('login.privacyText'))
}

async function doLogin() {
  if (!loginForm.username || !loginForm.password) return ElMessage.warning(t('login.needUsername'))
  if (!loginForm.agreedTerms) return ElMessage.warning(t('login.needTerms'))
  if (!loginForm.captcha) return ElMessage.warning(t('login.needCaptcha'))
  loading.value = true
  try {
    await userStore.login(
      loginForm.username,
      loginForm.password,
      loginForm.agreedTerms,
      loginForm.captcha,
      captchaToken.value
    )
    ElMessage.success(t('login.okLogin'))
    router.replace(route.query.redirect || '/home')
  } catch (e) {
    refreshCaptcha() // 登录失败（含验证码错误）刷新验证码，防止重放
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
async function doRegister() {
  if (!regForm.username || !regForm.password) return ElMessage.warning(t('login.needUsername'))
  if (!/^1[3-9]\d{9}$/.test(regForm.phone || '')) return ElMessage.warning(t('login.badPhone'))
  if (!regForm.agreedTerms) return ElMessage.warning(t('login.needTerms'))
  loading.value = true
  try {
    await userStore.register({ ...regForm })
    ElMessage.success(t('login.okRegister'))
    regForm.username = ''; regForm.password = ''; regForm.phone = ''; regForm.nickname = ''
    tab.value = 'login'
    refreshCaptcha()
  } catch (e) { } finally { loading.value = false }
}
</script>

<style scoped>
.login-page { height: 100%; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg,#2f7ed8,#56c596); }
.box { width: 360px; padding: 10px 8px; }
.lang-row { display: flex; justify-content: flex-end; margin-bottom: -6px; }
.lang-btn { cursor: pointer; font-size: 13px; color: #606266; user-select: none; }
.lang-btn:hover { color: #409eff; }
:deep(.el-dropdown-menu__item.active) { color: #409eff; font-weight: 600; }
.box h2 { text-align: center; margin: 6px 0 18px; }
.btn { width: 100%; margin-top: 18px; }
.review-tip { text-align: center; color: #e6a23c; font-size: 12px; margin: 10px 0 0; }
.tip { text-align: center; color: #999; font-size: 12px; margin: 10px 0 0; }
.captcha-row { display: flex; gap: 10px; margin-top: 12px; align-items: center; }
.captcha-img {
  height: 40px; width: 110px; border: 1px solid #dcdfe6; border-radius: 4px;
  cursor: pointer; background: #fff; flex: 0 0 auto; object-fit: contain;
}
</style>
