import { createParser } from 'eventsource-parser'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'

export interface PageVO<T> {
  records: T[]
  total: number
  size: number
  current: number
}

// ==================== AI Provider Config (legacy /api/ai/validate /test /health) ====================

export { type PluginParamDefinition as AiParamDefinition, type PluginProviderDefinition as AiProviderDefinition } from './spi-config'
import { createSpiConfigApi } from './spi-config'
const _aiConfigApi = createSpiConfigApi('ai')
export const getAiProviderDefinitions = _aiConfigApi.getProviderDefinitions
export const getAiConfig = _aiConfigApi.getConfig
export const saveAiConfig = _aiConfigApi.saveConfig

// ==================== Turn API ====================

export interface TurnMeta {
  turnId: string
  streamId: string
  sessionId: number
  userMessageId: number | null
  assistantMessageId: number | null
}

export interface TurnToolCall {
  toolCallId: string
  name: string
  input: unknown
  source: 'NATIVE' | 'SKILL' | 'MCP'
  messageId: number
  requiresConfirm: boolean
}

export interface TurnToolResult {
  toolCallId: string
  output: string
  success: boolean
  errorMessage: string | null
  messageId: number
}

export interface TurnUsage {
  promptTokens: number | null
  completionTokens: number | null
  costCents: number | null
  model: string | null
  latencyMs: number | null
}

/** Named SSE events emitted by POST /api/ai/sessions/{id}/turns. */
export type TurnEvent =
  | { type: 'meta'; data: TurnMeta }
  | { type: 'token'; data: string }
  | { type: 'thinking'; data: string }
  | { type: 'tool_call'; data: TurnToolCall }
  | { type: 'tool_result'; data: TurnToolResult }
  | { type: 'usage'; data: TurnUsage }
  | { type: 'done'; data: null }
  | { type: 'cancelled'; data: null }
  | { type: 'error'; data: { message: string } }

/**
 * Start a turn and stream SSE. Returns a function to abort the fetch.
 * Use POST /api/ai/streams/{streamId}/cancel for server-side cancel once streamId is known.
 */
export interface TurnBody {
  message: string
  datasourceId?: number | null
  scriptCode?: number | null
  selection?: string | null
  pinnedTables?: string[]
  taskType?: string | null
}

export function postTurn(
  sessionId: number,
  body: TurnBody,
  onEvent: (ev: TurnEvent) => void,
  onFinally: () => void,
): () => void {
  const controller = new AbortController()
  const userStore = useUserStore()
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined
  fetch(`/api/ai/sessions/${sessionId}/turns`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${userStore.token}`,
      'X-Workspace-Id': String(userStore.currentWorkspaceId ?? ''),
      'Accept': 'text/event-stream',
    },
    body: JSON.stringify(body),
    signal: controller.signal,
  })
    .then(async (resp) => {
      if (!resp.ok) { throw new Error(`HTTP ${resp.status}`) }
      reader = resp.body?.getReader()
      if (!reader) return
      const decoder = new TextDecoder()
      // SSE 解析交给 eventsource-parser:comment 行 / id 字段 / retry / 多行 data / UTF-8 BOM 等边界情况库内置处理
      const parser = createParser({
        onEvent(ev) {
          if (!ev.event) return  // 没声明 event type 的消息忽略(后端协议要求带 event:)
          let parsed: any = ev.data
          try { parsed = JSON.parse(ev.data) } catch { /* keep string */ }
          dispatch(ev.event, parsed, onEvent)
        },
      })
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        parser.feed(decoder.decode(value, { stream: true }))
      }
    })
    .catch((err) => {
      if (err.name === 'AbortError') return
      onEvent({ type: 'error', data: { message: err.message || String(err) } })
    })
    .finally(async () => {
      // 显式 cancel reader,避免 fetch 已结束但 ReadableStream 仍持有 buffer/底层 socket 不释放
      try { await reader?.cancel() } catch { /* already closed / aborted, ignore */ }
      onFinally()
    })
  return () => controller.abort()
}

function dispatch(name: string, data: any, onEvent: (ev: TurnEvent) => void) {
  switch (name) {
    case 'meta': onEvent({ type: 'meta', data }); break
    case 'token': onEvent({ type: 'token', data: typeof data === 'string' ? data : data?.text ?? '' }); break
    case 'thinking': onEvent({ type: 'thinking', data: typeof data === 'string' ? data : data?.text ?? '' }); break
    case 'tool_call': onEvent({ type: 'tool_call', data }); break
    case 'tool_result': onEvent({ type: 'tool_result', data }); break
    case 'usage': onEvent({ type: 'usage', data }); break
    case 'done': onEvent({ type: 'done', data: null }); break
    case 'cancelled': onEvent({ type: 'cancelled', data: null }); break
    case 'error': onEvent({ type: 'error', data }); break
    default: /* ignore unknown */
  }
}

export function cancelStream(streamId: string) {
  return request.post<boolean>(`/ai/streams/${streamId}/cancel`)
}

export function approveTool(streamId: string, toolCallId: string, approved: boolean) {
  return request.post<boolean>(`/ai/streams/${streamId}/tool-approve`, { toolCallId, approved })
}

// ==================== Session CRUD ====================

export interface AiSessionVO {
  id: number
  workspaceId: number
  title: string
  mode: 'CHAT' | 'AGENT'
  modelSnapshot: string | null
  totalPromptTokens: number
  totalCompletionTokens: number
  totalCostCents: number
  createdAt: string
  updatedAt: string
}

export interface AiMessageVO {
  id: number
  sessionId: number
  turnId: string
  role: 'user' | 'assistant' | 'tool_call' | 'tool_result' | 'system'
  status: 'PENDING' | 'STREAMING' | 'DONE' | 'CANCELLED' | 'FAILED' | null
  content: string | null
  errorMessage: string | null
  toolCallId: string | null
  toolName: string | null
  toolSource: 'NATIVE' | 'SKILL' | 'MCP' | null
  toolInput: string | null
  toolOutput: string | null
  toolSuccess: boolean | null
  model: string | null
  promptTokens: number | null
  completionTokens: number | null
  costCents: number | null
  latencyMs: number | null
  createdAt: string
}

export function listSessions(params: { pageNum?: number; pageSize?: number } = {}) {
  return request.get<PageVO<AiSessionVO>>('/ai/sessions', {
    params: { pageNum: params.pageNum ?? 1, pageSize: params.pageSize ?? 20 },
  })
}

export function createSession(data: { title?: string; mode: 'CHAT' | 'AGENT' }) {
  return request.post<AiSessionVO>('/ai/sessions', data)
}

export function updateSession(id: number, data: { title?: string; mode?: 'CHAT' | 'AGENT' }) {
  return request.put(`/ai/sessions/${id}`, data)
}

export function deleteSession(id: number) {
  return request.delete(`/ai/sessions/${id}`)
}

export function getSessionMessages(sessionId: number) {
  return request.get<AiMessageVO[]>(`/ai/sessions/${sessionId}/messages`)
}

// ==================== Skill / MCP (admin + list) ====================

export interface AiSkillVO {
  id: number | null
  name: string
  displayName: string
  description: string
  category: string
  requiredTools: string[]
}

export function listSkills() {
  return request.get<AiSkillVO[]>('/ai/skills')
}

// LLM / Embedding / Vector 各自走标准 SPI 配置页面,见 ai-llm-config.ts / ai-embedding-config.ts / ai-vector-config.ts

// ==================== Admin: Skill ====================

export interface AiSkillAdminVO {
  id?: number | null
  name: string
  displayName: string
  description?: string | null
  category?: string | null
  definition: string
  inputSchema?: string | null
  requiredTools?: string | null
  modelOverride?: string | null
  enabled?: boolean
}

export const adminSkill = {
  list: (params: { pageNum?: number; pageSize?: number } = {}) =>
    request.get<PageVO<AiSkillAdminVO>>('/ai/skills/admin', {
      params: { pageNum: params.pageNum ?? 1, pageSize: params.pageSize ?? 20 },
    }),
  create: (body: AiSkillAdminVO) => request.post<AiSkillAdminVO>('/ai/skills', body),
  update: (id: number, body: AiSkillAdminVO) => request.put(`/ai/skills/${id}`, body),
  remove: (id: number) => request.delete(`/ai/skills/${id}`),
  listCategories: () => request.get<string[]>('/ai/skills/categories'),
}

// ==================== Admin: Dialect (方言 prompt 覆盖) ====================

export interface DialectSlotVO {
  taskType: string
  label: string
  content: string
  overridden: boolean
}

export const adminDialect = {
  list: () => request.get<DialectSlotVO[]>('/ai/admin/dialects'),
  update: (taskType: string, content: string, enabled = true) =>
    request.put<void>(`/ai/admin/dialects/${taskType}`, { content, enabled }),
  reset: (taskType: string) => request.delete<void>(`/ai/admin/dialects/${taskType}`),
}

// ==================== Admin: MCP Server ====================

export interface AiMcpServerVO {
  id?: number | null
  name: string
  transport: 'STDIO' | 'HTTP_SSE'
  command?: string | null
  url?: string | null
  env?: string | null
  credentials?: string | null
  toolAllowlist?: string | null
  healthStatus?: 'UP' | 'DOWN' | 'UNKNOWN' | null
  lastHealthAt?: string | null
  enabled?: boolean
}

export const adminMcp = {
  list: (params: { pageNum?: number; pageSize?: number } = {}) =>
    request.get<PageVO<AiMcpServerVO>>('/ai/mcp/servers', {
      params: { pageNum: params.pageNum ?? 1, pageSize: params.pageSize ?? 20 },
    }),
  create: (body: AiMcpServerVO) => request.post<AiMcpServerVO>('/ai/mcp/servers', body),
  update: (id: number, body: AiMcpServerVO) => request.put(`/ai/mcp/servers/${id}`, body),
  remove: (id: number) => request.delete(`/ai/mcp/servers/${id}`),
  refreshHealth: () => request.post('/ai/mcp/refresh-health'),
}

// ==================== Admin: Redaction (rules + strategies) ====================

export type RedactionRuleType = 'TAG' | 'COLUMN' | 'TEXT'

export type RedactionExecutorType = 'REGEX_REPLACE' | 'PARTIAL' | 'REPLACE' | 'HASH' | 'REMOVE'

export interface RedactionRuleVO {
  id?: number | null
  name: string
  description?: string | null
  type: RedactionRuleType
  pattern: string
  strategyCode: string
  priority?: number | null
  enabled?: boolean
}

export interface RedactionStrategyVO {
  id?: number | null
  code: string
  name: string
  description?: string | null
  executorType: RedactionExecutorType
  // REGEX_REPLACE
  matchRegex?: string | null
  replacement?: string | null
  // PARTIAL
  keepPrefix?: number | null
  keepSuffix?: number | null
  maskChar?: string | null
  // REPLACE
  replaceValue?: string | null
  // HASH
  hashLength?: number | null
  builtin?: boolean
  enabled?: boolean
}

export interface RedactionTestRequest {
  strategy: RedactionStrategyVO
  rulePattern?: string | null
  sample: string
}

export const adminRedaction = {
  listRules: () => request.get<RedactionRuleVO[]>('/platform/redaction/rules'),
  createRule: (body: RedactionRuleVO) => request.post<RedactionRuleVO>('/platform/redaction/rules', body),
  updateRule: (id: number, body: RedactionRuleVO) => request.put(`/platform/redaction/rules/${id}`, body),
  deleteRule: (id: number) => request.delete(`/platform/redaction/rules/${id}`),

  listStrategies: () => request.get<RedactionStrategyVO[]>('/platform/redaction/strategies'),
  createStrategy: (body: RedactionStrategyVO) =>
    request.post<RedactionStrategyVO>('/platform/redaction/strategies', body),
  updateStrategy: (id: number, body: RedactionStrategyVO) =>
    request.put(`/platform/redaction/strategies/${id}`, body),
  deleteStrategy: (id: number) => request.delete(`/platform/redaction/strategies/${id}`),

  test: (body: RedactionTestRequest) =>
    request.post<{ output: string }>('/platform/redaction/test', body),
}

// ==================== RAG Documents ====================

export interface AiDocumentVO {
  id?: number
  /** JSON 数组字符串(如 "[1,3]");null=所有工作区可见(平台共享)。跟 AiToolConfigVO.workspaceIds 同模型。 */
  workspaceIds?: string | null
  docType: string
  engineType?: string | null
  sourceRef?: string | null
  title: string
  content: string
  description?: string | null
  contentHash?: string | null
  indexedAt?: string | null
  deletedAt?: string | null
}

export interface RetrievedChunkVO {
  documentId: number
  title: string
  chunkText: string
  score: number
  docType: string
}

export const adminDocuments = {
  list: (params: { docType?: string; pageNum?: number; pageSize?: number }) =>
    request.get<PageVO<AiDocumentVO>>('/ai/documents', { params }),
  get: (id: number) => request.get<AiDocumentVO>(`/ai/documents/${id}`),
  create: (body: AiDocumentVO) => request.post<AiDocumentVO>('/ai/documents', body),
  update: (id: number, body: AiDocumentVO) => request.put<AiDocumentVO>(`/ai/documents/${id}`, body),
  delete: (id: number) => request.delete(`/ai/documents/${id}`),
  upload: (file: File,
           params: { docType?: string; title?: string; engineType?: string; workspaceIds?: number[] } = {}) => {
    const fd = new FormData()
    fd.append('file', file)
    return request.post<AiDocumentVO>('/ai/documents/upload', fd, {
      params,
      headers: { 'Content-Type': 'multipart/form-data' },
      paramsSerializer: { indexes: null }, // workspaceIds 发成 workspaceIds=1&workspaceIds=3
    })
  },
  reindex: (docType?: string) => request.post<number>('/ai/documents/reindex', null, { params: { docType } }),
  search: (q: string, docType?: string, topK: number = 5) =>
    request.get<RetrievedChunkVO[]>('/ai/documents/search', { params: { q, docType, topK } }),
}

// ==================== Metadata Sync ====================

export interface AiMetadataSyncConfigVO {
  id?: number
  datasourceId: number | null
  enabled?: number
  scheduleCron?: string | null
  /** JSON array of catalog names (3-tier engines only). */
  includeCatalogs?: string | null
  /** JSON array of database names. 3-tier elements are prefixed "catalog.db". */
  includeDatabases?: string | null
  /** JSON array of table whitelist. Elements are "db.table" or "catalog.db.table". */
  includeTables?: string | null
  /** JSON array of keywords; table name containing any is skipped (case-insensitive substring). */
  excludeTables?: string | null
  maxColumnsPerTable?: number | null
  accessPaths?: string | null
  lastSyncAt?: string | null
  lastSyncStatus?: string | null
  lastSyncMessage?: string | null
}

export interface SyncResultVO {
  inserted: number
  updated: number
  skipped: number
  deleted: number
  message?: string | null
}

export const adminMetadataSync = {
  list: (params: { pageNum?: number; pageSize?: number } = {}) =>
    request.get<PageVO<AiMetadataSyncConfigVO>>('/ai/metadata-sync', {
      params: { pageNum: params.pageNum ?? 1, pageSize: params.pageSize ?? 20 },
    }),
  getByDatasource: (datasourceId: number) =>
    request.get<AiMetadataSyncConfigVO>(`/ai/metadata-sync/by-datasource/${datasourceId}`),
  save: (body: AiMetadataSyncConfigVO) => request.post<AiMetadataSyncConfigVO>('/ai/metadata-sync', body),
  update: (id: number, body: AiMetadataSyncConfigVO) =>
    request.put<AiMetadataSyncConfigVO>(`/ai/metadata-sync/${id}`, body),
  delete: (id: number) => request.delete(`/ai/metadata-sync/${id}`),
  sync: (datasourceId: number) => request.post<SyncResultVO>(`/ai/metadata-sync/sync/${datasourceId}`),
}

// ==================== Eval ====================

export type EvalMode = 'AGENT' | 'CHAT'

export interface AiEvalCaseVO {
  id?: number
  category: string
  taskType?: string | null
  difficulty?: string | null
  mode?: EvalMode | null
  datasourceId?: number | null
  engineType?: string | null
  workspaceId?: number | null
  prompt: string
  contextJson?: string | null
  expectedJson?: string | null
  active?: boolean
  createdBy?: number | null
}

export interface EvalToolInvocation {
  name: string
  input?: unknown
  output?: string
  success: boolean
  errorMessage?: string | null
  latencyMs?: number | null
}

export interface AiEvalRunVO {
  id: number
  batchId: string
  caseId: number
  provider?: string | null
  model?: string | null
  passed: boolean
  score?: number | null
  finalText?: string | null
  toolCallsJson?: string | null
  failReasonsJson?: string | null
  latencyMs?: number | null
  promptTokens?: number | null
  completionTokens?: number | null
  createdAt?: string | null
}

export interface EvalBatchResultVO {
  batchId: string
  total: number
  passed: number
  failed: number
  runs: AiEvalRunVO[]
}

export const adminEvals = {
  listCases: (params: { category?: string; activeOnly?: boolean; pageNum?: number; pageSize?: number }) =>
    request.get<PageVO<AiEvalCaseVO>>('/ai/eval/cases', { params }),
  getCase: (id: number) => request.get<AiEvalCaseVO>(`/ai/eval/cases/${id}`),
  createCase: (body: AiEvalCaseVO) => request.post<AiEvalCaseVO>('/ai/eval/cases', body),
  updateCase: (id: number, body: AiEvalCaseVO) => request.put(`/ai/eval/cases/${id}`, body),
  deleteCase: (id: number) => request.delete(`/ai/eval/cases/${id}`),
  runBatch: (category?: string) =>
    request.post<EvalBatchResultVO>('/ai/eval/batches', null, { params: { category } }),
  getBatch: (batchId: string) => request.get<AiEvalRunVO[]>(`/ai/eval/batches/${batchId}`),
  caseHistory: (caseId: number, pageNum: number = 1, pageSize: number = 20) =>
    request.get<PageVO<AiEvalRunVO>>(`/ai/eval/cases/${caseId}/runs`, { params: { pageNum, pageSize } }),
}

// ==================== Tool Configs(原 tool_permissions)====================

/**
 * 工具配置:对指定 workspace 列表应用一套规则。
 * workspaceIds=null 表示对所有 workspace 生效(平台级)。
 */
export interface AiToolConfigVO {
  id?: number
  toolName: string
  /** JSON 数组字符串;前端组装时传 number[] 再 JSON.stringify */
  workspaceIds?: string | null
  minRole?: string | null
  requireConfirm?: boolean | null
  readOnly?: boolean | null
  enabled?: boolean
}

/** 聚合 tool 总览 VO,对应后端 ToolOverviewService.ToolView */
export interface ToolViewVO {
  name: string
  source: 'NATIVE' | 'MCP' | 'SKILL'
  description: string | null
  inputSchema: any
  defaultPermission: {
    minRole: string
    requireConfirm: boolean
    readOnly: boolean
  }
  config: {
    id: number
    /** null = 所有 workspace 生效 */
    workspaceIds: number[] | null
    minRole: string | null
    requireConfirm: boolean | null
    readOnly: boolean | null
    enabled: boolean
  } | null
}

export function listTools(params: { source?: string; excludeSkill?: boolean } = {}) {
  return request.get<ToolViewVO[]>('/ai/tools', { params })
}

export const adminToolConfigs = {
  list: (params: { pageNum?: number; pageSize?: number } = {}) =>
    request.get<PageVO<AiToolConfigVO>>('/ai/admin/tool-configs', {
      params: { pageNum: params.pageNum ?? 1, pageSize: params.pageSize ?? 20 },
    }),
  create: (body: AiToolConfigVO) => request.post<AiToolConfigVO>('/ai/admin/tool-configs', body),
  update: (id: number, body: AiToolConfigVO) => request.put(`/ai/admin/tool-configs/${id}`, body),
  delete: (id: number) => request.delete(`/ai/admin/tool-configs/${id}`),
}

// ==================== Pinned Tables ====================

export interface AiPinnedTableVO {
  id?: number
  scope: 'USER' | 'WORKSPACE'
  scopeId?: number
  datasourceId: number
  databaseName?: string | null
  tableName: string
  note?: string | null
  createdAt?: string | null
}

export const pinnedTables = {
  list: (scope: 'USER' | 'WORKSPACE' = 'USER', pageNum: number = 1, pageSize: number = 20) =>
    request.get<PageVO<AiPinnedTableVO>>('/ai/pinned-tables', { params: { scope, pageNum, pageSize } }),
  pin: (body: Omit<AiPinnedTableVO, 'id' | 'scopeId' | 'createdAt'> & { database?: string; table?: string }) =>
    request.post<AiPinnedTableVO>('/ai/pinned-tables', body),
  unpinById: (id: number) => request.delete(`/ai/pinned-tables/${id}`),
}

// ==================== Context Profile ====================

export interface AiContextProfileVO {
  id?: number
  scope: 'WORKSPACE' | 'SESSION'
  scopeId: number
  injectSchemaLevel: 'NONE' | 'TABLES' | 'FULL'
  maxSchemaTables: number
  injectOpenScript: boolean
  injectSelection: boolean
  injectWikiRag: boolean
  injectHistoryLast: number
}

export const contextProfiles = {
  get: (scope: 'WORKSPACE' | 'SESSION', scopeId: number) =>
    request.get<AiContextProfileVO>(`/ai/context-profiles/${scope}/${scopeId}`),
  upsert: (body: AiContextProfileVO) => request.put<AiContextProfileVO>('/ai/context-profiles', body),
  clear: (scope: 'WORKSPACE' | 'SESSION', scopeId: number) =>
    request.delete(`/ai/context-profiles/${scope}/${scopeId}`),
}

// ==================== Feedback ====================

export type FeedbackSignal = 'THUMBS_UP' | 'THUMBS_DOWN' | 'EXECUTED_OK' | 'EXECUTED_FAIL' | 'EDITED' | 'CANCELLED'

export interface AiFeedbackVO {
  id?: number
  messageId: number
  userId?: number
  signal: FeedbackSignal
  comment?: string | null
  autoGenerated?: boolean
  createdAt?: string | null
}

export const feedback = {
  submit: (body: { messageId: number; signal: FeedbackSignal; comment?: string | null }) =>
    request.post<AiFeedbackVO>('/ai/feedback', body),
  listByMessage: (messageId: number) => request.get<AiFeedbackVO[]>(`/ai/feedback/messages/${messageId}`),
}
