import { createI18n } from 'vue-i18n'
import en from './en'
import zh from './zh'

function detectLocale(): string {
  const saved = localStorage.getItem('rudder_locale')
  if (saved) return saved
  const browserLang = navigator.language || ''
  return browserLang.startsWith('zh') ? 'zh' : 'en'
}

const savedLocale = detectLocale()

const i18n = createI18n({
  legacy: false,
  locale: savedLocale,
  fallbackLocale: 'en',
  messages: { en, zh },
})

export function setLocale(locale: string) {
  ;(i18n.global.locale as any).value = locale
  localStorage.setItem('rudder_locale', locale)
}

export function getLocale(): string {
  return (i18n.global.locale as any).value
}

export default i18n
