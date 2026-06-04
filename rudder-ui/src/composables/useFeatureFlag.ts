import { ref } from 'vue'

/**
 * 平台级布尔功能开关:模块级单飞,多个调用方共享同一份结果。
 * 在模块作用域调用一次本工厂得到单例,再由具名 composable 透出。
 *
 * @param fetcher 拉取函数,返回形如 {@code { data: { enabled: boolean } }} 的响应
 */
export function createFeatureFlag(fetcher: () => Promise<any>) {
  const enabled = ref(false)
  const ready = ref(false)
  let inFlight: Promise<void> | null = null

  async function load(force = false) {
    if (ready.value && !force) return
    if (inFlight) return inFlight
    inFlight = (async () => {
      try {
        const res = (await fetcher()) as any
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

  return {
    enabled,
    ready,
    refresh: () => load(true),
    ensureLoaded: () => load(false),
  }
}
