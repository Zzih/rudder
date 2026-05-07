import { watch, effectScope } from 'vue'
import { editor } from 'monaco-editor'
import { useThemeStore } from '@/stores/theme'

let scope: ReturnType<typeof effectScope> | null = null

/** Returns the current Monaco theme name and registers a global watcher (once) to keep it in sync. */
export function useMonacoTheme(): string {
  const themeStore = useThemeStore()
  if (!scope) {
    scope = effectScope(true)
    scope.run(() => {
      watch(() => themeStore.isDark, (dark) => {
        editor.setTheme(dark ? 'vs-dark' : 'vs')
      })
    })
  }
  return themeStore.isDark ? 'vs-dark' : 'vs'
}
