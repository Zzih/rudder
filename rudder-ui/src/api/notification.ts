export { type PluginParamDefinition, type PluginProviderDefinition } from './spi-config'
import { createSpiConfigApi } from './spi-config'

const api = createSpiConfigApi('notification')

export const getNotificationProviderDefinitions = api.getProviderDefinitions
export const getNotificationConfig = api.getConfig
export const saveNotificationConfig = api.saveConfig
export const listNotificationConfigs = api.listConfigs
