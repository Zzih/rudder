import request from '@/utils/request'
import type { AuthSourceType } from './auth-source'

export interface PublicAuthSource {
  id: number
  name: string
  type: AuthSourceType
  priority: number
}

export function login(data: { username: string; password: string }) {
  return request.post('/auth/login', data)
}

/** 通过指定 source(LDAP / 其它 credential 类)登录。 */
export function loginBySource(sourceId: number, data: { username: string; password: string }) {
  return request.post(`/auth/sources/${sourceId}/login`, data)
}

/** 拉登录页可见的 source 列表(仅启用 + 仅非敏感字段)。 */
export function listPublicSources() {
  return request.get<PublicAuthSource[]>('/auth/sources')
}

export function getMe() {
  return request.get('/auth/me')
}

/** OIDC 跳转 URL —— 浏览器直接 window.location 即可,后端会 302 到 IdP。 */
export function ssoStartUrl(sourceId: number): string {
  return `/api/auth/sources/${sourceId}/sso/start`
}
