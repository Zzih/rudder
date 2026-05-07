import { defineStore } from 'pinia'
import { getTaskTypes, type TaskTypeDef } from '@/api/config'

/**
 * 平台支持的全部 TaskType 元数据。
 *
 * <p>所有 IDE / 工作流编辑器都需要这份元数据(决定脚本内容怎么解析、编辑器 dialect、表单字段等)。
 * 由 router 守卫在进入相关路由前 ensureLoaded(),组件代码无需自己 await,直接读 reactive 字段。
 *
 * <p>跟之前 module-level `categoryMap + ensureCategoryMap()` 比,这是 reactive 单例 store,
 * 加载状态可被任何组件 watch,失败可重试,且不需要每个 component 自己记得 await。
 */
export const useTaskTypesStore = defineStore('taskTypes', {
  state: () => ({
    list: [] as TaskTypeDef[],
    loaded: false,
    loading: null as Promise<void> | null,
  }),
  getters: {
    /** taskType.value → category。未知 type 返 'OTHER'。 */
    categoryOf: (state) => (taskType: string): string => {
      const found = state.list.find(t => t.value === taskType)
      return found ? found.category : 'OTHER'
    },
    /**
     * 有 dialect 概念的 TaskType (SQL / SCRIPT / DATA_INTEGRATION)。
     * Workflow / control-flow 类型用不到 dialect,过滤掉避免出现在 RAG/方言 UI 选项里。
     */
    dialectAware: (state) => state.list.filter(
      t => t.category === 'SQL' || t.category === 'SCRIPT' || t.category === 'DATA_INTEGRATION',
    ),
  },
  actions: {
    /** 幂等加载。多处并发 await 共用同一 promise;失败时清掉允许重试。 */
    async ensureLoaded(): Promise<void> {
      if (this.loaded) return
      if (!this.loading) {
        this.loading = getTaskTypes()
          .then(({ data }) => {
            this.list = data ?? []
            this.loaded = true
          })
          .catch((err) => {
            this.loading = null
            throw err
          })
      }
      return this.loading
    },
  },
})
