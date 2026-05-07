export { type PluginParamDefinition, type PluginProviderDefinition } from './spi-config'
import { createSpiConfigApi } from './spi-config'

const api = createSpiConfigApi('result')

export const getResultProviderDefinitions = api.getProviderDefinitions
export const getResultConfig = api.getConfig
export const saveResultConfig = api.saveConfig
