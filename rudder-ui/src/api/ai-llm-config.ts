export { type PluginParamDefinition, type PluginProviderDefinition } from './spi-config'
import { createSpiConfigApi } from './spi-config'

const api = createSpiConfigApi('ai-llm', { testEnabled: true })

export const getAiLlmProviderDefinitions = api.getProviderDefinitions
export const getAiLlmConfig = api.getConfig
export const saveAiLlmConfig = api.saveConfig
export const listAiLlmConfigs = api.listConfigs
export const testAiLlmConfig = api.testConfig!
