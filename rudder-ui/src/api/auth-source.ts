import request from '@/utils/request'

export type AuthSourceType = 'OIDC' | 'LDAP'

/**
 * OIDC 配置:Spring Security oauth2Login 通过 issuer 自动 discovery
 * authorization / token / userinfo / jwks endpoint,前端无需配置 4 个 URI。
 * redirect_uri 由后端按 {callbackBaseUrl|baseUrl}/login/oauth2/code/{sourceId} 构造,IdP 注册时填这个模板。
 */
export interface OidcConfig {
  clientId: string
  clientSecret: string
  issuer: string
  scopes: string
  callbackBaseUrl?: string
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

/** 协议特定 config。后端落库前 AES 加密敏感字段(clientSecret / bindPassword)。 */
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
