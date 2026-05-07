import request from '@/utils/request'

/**
 * RAG 链路配置 (Spring AI 2.0 Modular RAG advisor 链路开关).
 * 与 LLM/Embedding/Vector/Rerank 这种 provider 配置不同 —— 单例,无 provider 选择.
 */
export interface RagPipelineSettings {
  rewriteEnabled: boolean
  multiQueryEnabled: boolean
  multiQueryCount: number
  multiQueryIncludeOriginal: boolean
  compressionEnabled: boolean
  translationEnabled: boolean
  translationTargetLanguage: string
  rerankStageEnabled: boolean
  rerankTopN: number
  keywordEnricherEnabled: boolean
  summaryEnricherEnabled: boolean
  augmenterAllowEmptyContext: boolean
}

export const DEFAULT_RAG_PIPELINE: RagPipelineSettings = {
  rewriteEnabled: false,
  multiQueryEnabled: false,
  multiQueryCount: 3,
  multiQueryIncludeOriginal: true,
  compressionEnabled: false,
  translationEnabled: false,
  translationTargetLanguage: 'english',
  rerankStageEnabled: false,
  rerankTopN: 5,
  keywordEnricherEnabled: false,
  summaryEnricherEnabled: false,
  augmenterAllowEmptyContext: true,
}

// 注意: request 拦截器返回的是 Result wrapper {code, message, data},
// 调用方需要从 .data 取实际 payload。返回类型保持原样,不做自动解包,
// 与项目中其它 SPI config API 一致(见 SpiConfigPage.vue 的 (cfgRes as any).data 用法)。
export const getAiRagPipelineConfig = () => request.get('/config/ai-rag-pipeline')

export const saveAiRagPipelineConfig = (settings: RagPipelineSettings) =>
  request.post('/config/ai-rag-pipeline', settings)
