export { type PluginParamDefinition, type PluginProviderDefinition } from './spi-config'
import { createSpiConfigApi } from './spi-config'

const api = createSpiConfigApi('ai-vector', { testEnabled: true })

export const getAiVectorProviderDefinitions = api.getProviderDefinitions
export const getAiVectorConfig = api.getConfig
export const saveAiVectorConfig = api.saveConfig
export const testAiVectorConfig = api.testConfig!
