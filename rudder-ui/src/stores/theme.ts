import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'rudder_theme'
const validModes: ThemeMode[] = ['light', 'dark', 'system']

export const useThemeStore = defineStore('theme', () => {
  const stored = localStorage.getItem(STORAGE_KEY)
  const mode = ref<ThemeMode>(validModes.includes(stored as ThemeMode) ? stored as ThemeMode : 'system')

  const mq = window.matchMedia('(prefers-color-scheme: dark)')
  const systemDark = ref(mq.matches)
  mq.addEventListener('change', (e) => { systemDark.value = e.matches })

  const isDark = computed(() => mode.value === 'dark' || (mode.value === 'system' && systemDark.value))

  watch(isDark, (dark) => {
    document.documentElement.classList.toggle('dark', dark)
  }, { immediate: true })

  function setMode(m: ThemeMode) {
    mode.value = m
    localStorage.setItem(STORAGE_KEY, m)
  }

  return { mode, isDark, setMode }
})
