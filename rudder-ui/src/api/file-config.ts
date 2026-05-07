export { type PluginParamDefinition, type PluginProviderDefinition } from './spi-config'
import { createSpiConfigApi } from './spi-config'

const api = createSpiConfigApi('file')

export const getFileProviderDefinitions = api.getProviderDefinitions
export const getFileConfig = api.getConfig
export const saveFileConfig = api.saveConfig
