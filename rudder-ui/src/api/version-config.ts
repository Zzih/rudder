export { type PluginParamDefinition, type PluginProviderDefinition } from './spi-config'
import { createSpiConfigApi } from './spi-config'

const api = createSpiConfigApi('version')

export const getVersionProviderDefinitions = api.getProviderDefinitions
export const getVersionConfig = api.getConfig
export const saveVersionConfig = api.saveConfig
export const listVersionConfigs = api.listConfigs
