export { type PluginParamDefinition, type PluginProviderDefinition } from './spi-config'
import { createSpiConfigApi } from './spi-config'

const api = createSpiConfigApi('ai-embedding', { testEnabled: true })

export const getAiEmbeddingProviderDefinitions = api.getProviderDefinitions
export const getAiEmbeddingConfig = api.getConfig
export const saveAiEmbeddingConfig = api.saveConfig
export const listAiEmbeddingConfigs = api.listConfigs
export const testAiEmbeddingConfig = api.testConfig!
