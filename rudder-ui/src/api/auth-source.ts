import request from '@/utils/request'

export type AuthSourceType = 'PASSWORD' | 'OIDC' | 'LDAP'

export interface OidcConfig {
  clientId: string
  clientSecret: string
  redirectUri: string
  issuer: string
  authorizationUri: string
  tokenUri: string
  userInfoUri: string
  scopes: string
  frontendRedirectUrl: string
}

export interface LdapConfig {
  url: string
  trustAllCerts: boolean
  baseDn: string
  bindDn: string
  bindPassword: string
  userSearchFilter: string
  usernameAttribute: string
  emailAttribute: string
  displayNameAttribute: string
}

/** 与后端 SourceConfig sealed interface 对齐;Jackson 按兄弟字段 type 决定具体子类。 */
export type SourceConfig = OidcConfig | LdapConfig

export interface AuthSourceSummary {
  id: number
  name: string
  type: AuthSourceType
  enabled: boolean
  isSystem: boolean
  priority: number
  createdAt: string
  updatedAt: string
}

export interface AuthSourceDetail extends AuthSourceSummary {
  /** 已脱敏 (clientSecret / bindPassword 替换为 ••••••);PASSWORD 行为 null。 */
  config: SourceConfig | null
}

export interface AuthSourceCreateBody {
  name: string
  type: 'OIDC' | 'LDAP'
  config: SourceConfig
  enabled?: boolean
  priority?: number
}

export interface AuthSourceUpdateBody {
  /** 必传:用于 Jackson 多态反序列化 config(后端忽略 type 不允许改)。 */
  type: AuthSourceType
  name?: string
  /** 不传表示本次不改 config(保留旧值)。 */
  config?: SourceConfig
  enabled?: boolean
  priority?: number
}

export interface HealthStatus {
  state: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'UNKNOWN'
  message: string
}

// admin
export function listAuthSources() {
  return request.get<AuthSourceSummary[]>('/admin/auth-sources')
}

export function getAuthSource(id: number) {
  return request.get<AuthSourceDetail>(`/admin/auth-sources/${id}`)
}

export function createAuthSource(body: AuthSourceCreateBody) {
  return request.post<AuthSourceDetail>('/admin/auth-sources', body)
}

export function updateAuthSource(id: number, body: AuthSourceUpdateBody) {
  return request.put<AuthSourceDetail>(`/admin/auth-sources/${id}`, body)
}

export function deleteAuthSource(id: number) {
  return request.delete(`/admin/auth-sources/${id}`)
}

export function toggleAuthSource(id: number, enabled: boolean) {
  return request.post(`/admin/auth-sources/${id}/toggle`, null, { params: { enabled } })
}

export function testAuthSource(id: number) {
  return request.post<HealthStatus>(`/admin/auth-sources/${id}/test`)
}
