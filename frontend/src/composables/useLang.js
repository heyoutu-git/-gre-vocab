import { ref, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { Locale } from 'vant'
import vantZhCN from 'vant/es/locale/lang/zh-CN'
import vantEnUS from 'vant/es/locale/lang/en-US'
import vantDeDE from 'vant/es/locale/lang/de-DE'
import vantFrFR from 'vant/es/locale/lang/fr-FR'

// vue-i18n code → Vant 组件库语言包（取消/确认等内置文案）
const VANT_LOCALES = {
  zh: vantZhCN,
  en: vantEnUS,
  de: vantDeDE,
  fr: vantFrFR
}

// 语言切换（桌面 header + 手机「我的」共用）
const SUPPORTED = [
  { code: 'zh', label: '简体中文' },
  { code: 'en', label: 'English' },
  { code: 'fr', label: 'Français' },
  { code: 'de', label: 'Deutsch' }
]

export function useLang() {
  const { locale } = useI18n()
  const current = ref(locale.value)
  const list = SUPPORTED
  function setLang(code) {
    if (!SUPPORTED.some((l) => l.code === code)) return
    locale.value = code
    current.value = code
    try { localStorage.setItem('gre-lang', code) } catch (e) { /* ignore */ }
    if (VANT_LOCALES[code]) Locale.use(code, VANT_LOCALES[code])
    document.documentElement.setAttribute('lang', code)
  }
  watchEffect(() => { document.documentElement.setAttribute('lang', locale.value) })
  return { current, list, setLang }
}
