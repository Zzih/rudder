import request from '@/utils/request'

// 统一返回结构
export interface CapabilityItem {
  id: string
  domain: string
  rwClass: 'READ' | 'WRITE'
  sensitivity: 'NORMAL' | 'HIGH'
  description: string
  requiredRoles: string[]
}

export interface ScopesAvailable {
  role: string
  capabilities: CapabilityItem[]
}

export interface TokenSummary {
  id: number
  name: string
  description?: string
  workspaceId: number
  workspaceName?: string
  tokenPrefix: string
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED'
  expiresAt?: string
  lastUsedAt?: string
  createdAt: string
}

export interface GrantInfo {
  capability: string
  rwClass: 'READ' | 'WRITE'
  status: 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED' | 'WITHDRAWN' | 'REVOKED'
}

export interface TokenDetail {
  token: TokenSummary
  grants: GrantInfo[]
}

export interface CreateTokenRequest {
  name: string
  description?: string
  workspaceId: number
  expiresInDays: number
  capabilities: string[]
}

export interface CreateTokenResponse {
  tokenId: number
  plainToken: string // 仅本次返回
  token: TokenSummary
  grants: GrantInfo[]
}

// ===== API =====

export function listAvailableScopes(workspaceId: number) {
  return request.get<ScopesAvailable>('/mcp/scopes/available', { params: { workspaceId } })
}

export function listAllCapabilities() {
  return request.get<CapabilityItem[]>('/mcp/capabilities')
}

export function listMyTokens() {
  return request.get<TokenSummary[]>('/mcp/tokens')
}

export function getToken(id: number) {
  return request.get<TokenDetail>(`/mcp/tokens/${id}`)
}

export function createToken(body: CreateTokenRequest) {
  return request.post<CreateTokenResponse>('/mcp/tokens', body)
}

export function revokeToken(id: number) {
  return request.delete(`/mcp/tokens/${id}`)
}

export interface McpClientGuide {
  id: string
  label: string
  color: string
  description: string
  guide: string
}

export function listMcpClients(lang?: string) {
  return request.get<McpClientGuide[]>('/mcp/clients', { params: lang ? { lang } : undefined })
}
