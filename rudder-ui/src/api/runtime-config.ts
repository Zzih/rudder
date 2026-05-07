export { type PluginParamDefinition, type PluginProviderDefinition } from './spi-config'
import { createSpiConfigApi } from './spi-config'

const api = createSpiConfigApi('runtime')

export const getRuntimeProviderDefinitions = api.getProviderDefinitions
export const getRuntimeConfig = api.getConfig
export const saveRuntimeConfig = api.saveConfig
