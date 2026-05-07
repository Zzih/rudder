import request from '@/utils/request'

/** 与后端 RagDebugTrace.StageTrace 对应。 */
export interface RagStageTrace {
  name: string
  skipped?: boolean
  skipReason?: string | null
  durationMs?: number | null
  error?: string | null
  input?: unknown
  output?: unknown
}

export interface RagDebugTrace {
  originalQuery: string
  ragEnabled: boolean
  totalLatencyMs?: number | null
  pipelineSnapshot?: Record<string, unknown> | null
  stages: RagStageTrace[]
  finalPrompt?: string | null
}

export interface RagDebugRequest {
  query: string
  workspaceId?: number | null
  /** TaskType enum name, e.g. "STARROCKS_SQL". 可选,影响 engineType 过滤. */
  taskType?: string | null
}

// request 拦截器返回 {code, message, data} wrapper, 调用方取 .data
export const debugRagRetrieval = (req: RagDebugRequest) => request.post('/ai/rag/debug', req)
