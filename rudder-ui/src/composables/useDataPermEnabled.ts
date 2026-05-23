import { ref } from 'vue'
import { getDataPermEnabled } from '@/api/data-perm'

const enabled = ref(false)
const ready = ref(false)
let inFlight: Promise<void> | null = null

async function load(force = false) {
  if (ready.value && !force) return
  if (inFlight) return inFlight
  inFlight = (async () => {
    try {
      const res = (await getDataPermEnabled()) as any
      enabled.value = !!res?.data?.enabled
    } catch {
      enabled.value = false
    } finally {
      ready.value = true
      inFlight = null
    }
  })()
  return inFlight
}

/** 平台级 data-perm enable 状态。模块级单飞,多个调用方共享同一份结果。 */
export function useDataPermEnabled() {
  return {
    enabled,
    ready,
    refresh: () => load(true),
    ensureLoaded: () => load(false),
  }
}
