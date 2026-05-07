import request from '@/utils/request'

export interface PluginParamDefinition {
  name: string
  label: string
  type: string
  required: boolean
  placeholder: string
  defaultValue?: string
}

export interface ProviderMetadata {
  version?: string | null
  description?: string | null
  author?: string | null
  since?: string | null
  docsUrl?: string | null
}

export interface PluginProviderDefinition {
  params: PluginParamDefinition[]
  guide: string
  metadata?: ProviderMetadata | null
  priority?: number
  testConnectionSupported?: boolean
}

/** 与后端 io.github.zzih.rudder.spi.api.model.TestResult 对应。 */
export interface TestResult {
  success: boolean
  message?: string | null
  latencyMs?: number | null
}

export interface SpiConfigApi {
  getProviderDefinitions: () => Promise<any>
  getConfig: () => Promise<any>
  saveConfig: (data: { provider: string; providerParams?: string; enabled?: boolean }) => Promise<any>
  /** 可选: 部分 SPI 暴露 /test 端点,用于 admin UI 实时验证配置(连通性 / model 名 / apiKey)。 */
  testConfig?: (data: { provider: string; config: Record<string, string> }) => Promise<any>
}

export function createSpiConfigApi(basePath: string, opts?: { testEnabled?: boolean }): SpiConfigApi {
  const api: SpiConfigApi = {
    getProviderDefinitions: () => request.get(`/config/${basePath}/providers`),
    getConfig: () => request.get(`/config/${basePath}`),
    saveConfig: (data) => request.post(`/config/${basePath}`, data),
  }
  if (opts?.testEnabled) {
    api.testConfig = (data) => request.post(`/config/${basePath}/test`, data)
  }
  return api
}
