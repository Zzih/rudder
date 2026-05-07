import type { InjectionKey } from 'vue'

/**
 * 脚本绑定的工作流/任务元信息（仅工作流任务脚本才会带）。
 */
export interface ScriptBinding {
  projectCode: string
  projectName: string
  workflowDefinitionCode: string
  workflowName: string
  taskDefinitionCode: string
  taskName: string
}

export type ResultTab = 'result' | 'log' | 'history' | 'jobs' | 'versions'

/**
 * 一个 tab 的完整 state,包括它正在执行的 execution。execution 是 per-tab 的,
 * 切换 / 关闭 tab 不再需要"清理全局 state",直接对 tab 本身操作即可。
 */
export interface Tab {
  id: string
  name: string
  scriptCode: string
  sql: string
  taskType: string
  datasourceId: number | null
  executionMode: string
  modified: boolean
  /** 持久化用——上次执行的 ID,刷新页面后供 ResultPanel 恢复显示。 */
  lastExecutionId: number | null
  params: Record<string, string>
  binding?: ScriptBinding | null
  /** 当前活跃的 execution ID,driver ResultPanel polling。null=没有 execution 在跑。 */
  executionId: number | null
  executionRunning: boolean
  /** 由外部(AI explain/optimize 等)注入到 ResultPanel 显示的临时日志。 */
  resultLog: string | null
  /** 命令式切到 ResultPanel 的某个 tab; ResultPanel 切完置 null。 */
  resultTab: ResultTab | null
}

/**
 * IDE 共享状态。仅持有跨 tab 的 state(布局、面板可见性、AI 上下文);
 * 跟 tab 强关联的执行 state 在 Tab 接口内。
 */
export interface IdeState {
  tabs: Tab[]
  activeTabId: string | null
  aiPanelVisible: boolean
  resultPanelVisible: boolean
  fileTreeRefreshKey: number
  editorRefreshKey: number
  /** "db.table" 引用,AI 对话会把这些表的 schema 摘要注入到 system prompt。 */
  pinnedTables: string[]
  /** 用户在 Monaco 编辑器中最近选中的文本,发给 AI 作为优先上下文。 */
  aiSelectionText: string
}

/**
 * 类型化的 provide/inject key。
 * 子组件用 `inject(IDE_STATE_KEY)!` 获取强类型 ideState。
 * <p>
 * 放在独立 .ts 文件的原因：Vue SFC 的 &lt;script setup&gt; 不允许 ES module exports，
 * 但常量需要被子组件跨文件 import，因此必须外置。
 */
export const IDE_STATE_KEY: InjectionKey<IdeState> = Symbol('ideState')

/** 构造发送 AI turn 的标准 ctx —— 用户对话框、IDE 按钮等所有入口共用同一份字段。 */
export function buildTurnContext(tab: Tab | undefined | null, ideState: IdeState) {
  return {
    datasourceId: tab?.datasourceId ?? null,
    scriptCode: tab ? Number(tab.scriptCode) : null,
    taskType: tab?.taskType ?? null,
    selection: ideState.aiSelectionText || null,
    pinnedTables: ideState.pinnedTables ?? [],
  }
}
