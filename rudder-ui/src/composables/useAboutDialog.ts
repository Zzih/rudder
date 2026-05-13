import { ref } from 'vue'

// 模块级 ref 让 AppHeader 渲染 AboutDialog,其他组件(如 WorkspaceList 的 footer)能共享开关
const visible = ref(false)

export function useAboutDialog() {
  return {
    visible,
    open: () => { visible.value = true },
  }
}
