export { type PluginParamDefinition, type PluginProviderDefinition } from './spi-config'
import { createSpiConfigApi } from './spi-config'

const api = createSpiConfigApi('publish')

export const getPublishProviderDefinitions = api.getProviderDefinitions
export const getPublishConfig = api.getConfig
export const savePublishConfig = api.saveConfig
