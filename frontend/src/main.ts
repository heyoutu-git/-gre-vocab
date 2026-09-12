import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import { createI18n } from 'vue-i18n'
import { Locale } from 'vant'
import vantZhCN from 'vant/es/locale/lang/zh-CN'
import vantEnUS from 'vant/es/locale/lang/en-US'
import vantDeDE from 'vant/es/locale/lang/de-DE'
import vantFrFR from 'vant/es/locale/lang/fr-FR'
import App from './App.vue'
import router from './router'
import { useUiStore } from './store/ui'
import './styles/global.css'
import zh from './locales/zh'
import en from './locales/en'
import fr from './locales/fr'
import de from './locales/de'

// 语言持久化：localStorage.gre-lang（zh/en/fr/de），默认中文
const savedLang = (() => {
  try { return localStorage.getItem('gre-lang') || 'zh' } catch (e) { return 'zh' }
})()
const i18n = createI18n({
  legacy: false,
  locale: ['zh', 'en', 'fr', 'de'].includes(savedLang) ? savedLang : 'zh',
  fallbackLocale: 'zh',
  messages: { zh, en, fr, de }
})

// Vant 组件库（取消/确认等）跟随当前语言
const VANT_LOCALES: Record<string, any> = { zh: vantZhCN, en: vantEnUS, de: vantDeDE, fr: vantFrFR }
if (VANT_LOCALES[savedLang]) Locale.use(savedLang, VANT_LOCALES[savedLang])

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(ElementPlus)
useUiStore().init()
app.mount('#app')
