export { type PluginParamDefinition, type PluginProviderDefinition, type TestResult } from './spi-config'
import { createSpiConfigApi } from './spi-config'

const api = createSpiConfigApi('ai-rerank', { testEnabled: true })

export const getAiRerankProviderDefinitions = api.getProviderDefinitions
export const getAiRerankConfig = api.getConfig
export const saveAiRerankConfig = api.saveConfig
export const listAiRerankConfigs = api.listConfigs
export const testAiRerankConfig = api.testConfig!
