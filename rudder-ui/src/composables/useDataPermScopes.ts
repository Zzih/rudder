import { ref } from 'vue'
import { listDataPermScopes, type DataPermScope } from '@/api/data-perm'

const services = ref<DataPermScope[]>([])
const ready = ref(false)
let inFlight: Promise<void> | null = null

async function load(force = false) {
  if (ready.value && !force) return
  if (inFlight) return inFlight
  inFlight = (async () => {
    try {
      const res = (await listDataPermScopes()) as any
      services.value = (res?.data as DataPermScope[]) ?? []
    } catch {
      services.value = []
    } finally {
      ready.value = true
      inFlight = null
    }
  })()
  return inFlight
}

/** 平台级数据权限域元数据。模块级单飞,多调用方共享同一份;走 listDataPermScopes 公开端点。 */
export function useDataPermScopes() {
  return {
    services,
    ready,
    refresh: () => load(true),
    ensureLoaded: () => load(false),
  }
}
