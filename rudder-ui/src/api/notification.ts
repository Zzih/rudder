import request from '@/utils/request'

export interface ParamDefinition {
  name: string
  label: string
  type: string        // 'input' | 'password'
  required: boolean
  placeholder?: string
}

export interface NotificationConfigRow {
  id: number
  workspaceId: number | null
  enabled: boolean
  channel: string
  channelParams: string   // JSON string
  subscribedEvents: string
  createdAt: string
  updatedAt: string
}

/** 获取所有已注册渠道及其参数定义（SPI 自描述） */
export function getChannelDefinitions(): Promise<any> {
  return request.get('/config/notification/channels')
}

export function listNotificationConfigs() {
  return request.get('/config/notification/list')
}

export function savePlatformConfig(data: {
  enabled?: boolean
  channel: string
  channelParams?: string
  subscribedEvents?: string
}) {
  return request.post('/config/notification/platform', data)
}

export function saveWorkspaceConfig(workspaceId: number, data: {
  enabled?: boolean
  channel: string
  channelParams?: string
  subscribedEvents?: string
}) {
  return request.post(`/config/notification/workspace/${workspaceId}`, data)
}

export function deleteWorkspaceConfig(workspaceId: number) {
  return request.delete(`/config/notification/workspace/${workspaceId}`)
}

export function testNotification(id: number) {
  return request.post(`/config/notification/${id}/test`)
}
