export { type PluginParamDefinition, type PluginProviderDefinition } from './spi-config'
import { createSpiConfigApi } from './spi-config'

const api = createSpiConfigApi('metadata')

export const getMetadataProviderDefinitions = api.getProviderDefinitions
export const getMetadataConfig = api.getConfig
export const saveMetadataConfig = api.saveConfig
