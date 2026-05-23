import request from '@/utils/request'

// ==================== 平台配置 (SuperAdmin) ====================

export type PluginType = 'HADOOP_SQL' | 'STARROCKS' | 'TRINO' | 'HBASE' | 'HDFS' | 'KAFKA'

export const PLUGIN_TYPES: readonly PluginType[] = ['HADOOP_SQL', 'STARROCKS', 'TRINO', 'HBASE', 'HDFS', 'KAFKA']

/** access 名包含其一即视为写操作,审批触发 + UI 红色标识。 */
export const DANGEROUS_ACCESS_RE = /WRITE|DELETE|UPDATE|INSERT|ALTER|DROP|GRANT/i

export interface DataPermScope {
  code?: number
  name: string
  pluginType: PluginType
  /** 元数据 datasource:申请页拉表名 / 列名用。 */
  metadataDatasourceId: number
  /** 受 Local 鉴权管控的任务类型列表 (TaskType 枚举名);跨 Scope 全局唯一。 */
  managedTaskTypes?: string[]
  /** Ranger 端对应的 service.name;Ranger mode 开时必填。 */
  rangerServiceName?: string
  description?: string
  enabled: boolean
}

export interface DataPermConfig {
  enabled: boolean
  /** Ranger 鉴权 mode:开 → Reconciler 把 desired 推到 Ranger。 */
  rangerModeEnabled: boolean
  /** Local 鉴权 mode:开 → Worker 跑 SQL 前查 snapshot 表本地鉴权。 */
  localModeEnabled: boolean
  rangerAdminUrl: string
  rangerAdminUsername: string
  rangerAdminPassword?: string
  passwordConfigured?: boolean
  rangerAdminTimeoutMs: number
  rangerAdminPageSize: number
  rangerWriteConcurrency: number
  reconcileIntervalSeconds: number
  reconcileLockTtlSeconds: number
  reconcileBatchSize: number
  reconcileFailureAlertThreshold: number
  ensureRangerUser: boolean
  scopes: DataPermScope[]
}

export const DEFAULT_DATA_PERM_CONFIG: DataPermConfig = {
  enabled: false,
  rangerModeEnabled: false,
  localModeEnabled: false,
  rangerAdminUrl: '',
  rangerAdminUsername: '',
  rangerAdminPassword: '',
  passwordConfigured: false,
  rangerAdminTimeoutMs: 10_000,
  rangerAdminPageSize: 1_000,
  rangerWriteConcurrency: 4,
  reconcileIntervalSeconds: 300,
  reconcileLockTtlSeconds: 600,
  reconcileBatchSize: 100,
  reconcileFailureAlertThreshold: 3,
  ensureRangerUser: false,
  scopes: [],
}

export const getDataPermEnabled = () => request.get<{ enabled: boolean }>('/config/data-perm/enabled')
export const getDataPermConfig = () => request.get('/config/data-perm')

/** Ranger Service 元数据列表,所有登录用户可读(不含 Ranger Admin URL / 凭证等敏感字段)。 */
export const listDataPermScopes = () => request.get('/config/data-perm/scopes')
export const saveDataPermConfig = (data: DataPermConfig) => request.put('/config/data-perm', data)
export const testDataPermConnection = (data: DataPermConfig) => request.post('/config/data-perm/test', data)
export const triggerDataPermReconcile = () => request.post('/config/data-perm/reconcile-trigger')

/**
 * PluginType 维度的资源层级 + access 闭集。资源包 / 申请单 UI 据此渲染。
 */
export interface DataPermAdapter {
  pluginType: PluginType
  rangerServiceType: string
  resourceLevels: string[]
  accessTypes: string[]
}

export const listDataPermPluginTypes = () => request.get('/config/data-perm/plugin-types')

export interface MarkdownGuide {
  description: string
  body: string
}

export const getDataPermGuide = () => request.get<MarkdownGuide>('/config/data-perm/guide')

// ==================== 资源包 (SuperAdmin) ====================

export interface DataPermRolePermissionItem {
  scopeCode: number
  scopeName?: string
  catalogName?: string
  databaseName?: string
  tableName?: string
  columnName?: string
  accesses: string[]
  /** 仅 direct grant 行视图填:该条 grant 的生效/到期。 */
  effectiveTime?: string
  expirationTime?: string
}

export interface DataPermRole {
  id?: number
  name: string
  description?: string
  activeGrantCount?: number
}

export const listDataPermRoles = () => request.get('/data-perm/roles')
export const pageDataPermRoles = (params: { keyword?: string; pageNum: number; pageSize: number }) =>
  request.get('/data-perm/roles/page', { params })
export const getDataPermRole = (id: number) => request.get(`/data-perm/roles/${id}`)
export const createDataPermRole = (data: { name: string; description?: string }) =>
  request.post('/data-perm/roles', data)
export const updateDataPermRole = (id: number, data: { name: string; description?: string }) =>
  request.put(`/data-perm/roles/${id}`, data)
export const deleteDataPermRole = (id: number) => request.delete(`/data-perm/roles/${id}`)

// ---- 行级权限项 CRUD ----
export interface PermissionItemWithId extends DataPermRolePermissionItem {
  id: number
}

export const pageRolePermissions = (
    roleId: number,
    params: { keyword?: string; pluginType?: string; pageNum: number; pageSize: number }) =>
  request.get(`/data-perm/roles/${roleId}/permissions/page`, { params })

export const addRolePermission = (roleId: number, item: DataPermRolePermissionItem) =>
  request.post(`/data-perm/roles/${roleId}/permissions`, item)

export const updateRolePermission = (roleId: number, permId: number, item: DataPermRolePermissionItem) =>
  request.put(`/data-perm/roles/${roleId}/permissions/${permId}`, item)

export const deleteRolePermission = (roleId: number, permId: number) =>
  request.delete(`/data-perm/roles/${roleId}/permissions/${permId}`)

// ==================== 申请 (LoggedIn) ====================

export interface DataPermApplyPayload {
  roleIds?: number[]
  directItems?: DataPermRolePermissionItem[]
  expireAt?: string
  reason: string
}

export const submitDataPermApplication = (data: DataPermApplyPayload) =>
  request.post('/data-perm/applications', data)

// ==================== 我的权限 (LoggedIn) ====================

export interface MyGrantsRoleCard {
  roleId: number
  roleName: string
  grantId: number
  sourceApprovalId?: number
  effectiveTime: string
  expirationTime?: string
  permCount: number
}

export interface MyGrantsDirectOverview {
  effectiveTime: string
  expirationTime?: string
  permCount: number
}

export interface MyGrantsStats {
  roles: number
  direct: number
  expiringSoon: number
}

export interface MyGrantsSummary {
  roleCards: MyGrantsRoleCard[]
  directOverview: MyGrantsDirectOverview | null
  stats: MyGrantsStats
}

export const getMyGrantsSummary = () =>
  request.get<MyGrantsSummary>('/data-perm/my-grants/summary')

export const pageMyRolePermissions = (
    roleId: number,
    params: { pageNum: number; pageSize: number }) =>
  request.get(`/data-perm/my-grants/roles/${roleId}/permissions`, { params })

export const pageMyDirectPermissions = (params: { pageNum: number; pageSize: number }) =>
  request.get('/data-perm/my-grants/direct/permissions', { params })

export type GrantKind = 'ROLE' | 'DIRECT'

export interface UserGrantView {
  kind: GrantKind
  roleId?: number
  roleName: string
  sourceApprovalId?: number
  grantId: number
  effectiveTime: string
  expirationTime?: string
  endReason?: string
  permissions: DataPermRolePermissionItem[]
}

export const listMyGrantsHistory = () => request.get('/data-perm/my-grants/history')

// ==================== 全部用户权限 (SuperAdmin) ====================

export const listUserGrants = (userId: number, asOf?: string) =>
  request.get(`/data-perm/admin/grants/by-user/${userId}`,
    { params: asOf ? { asOf } : {} })

export interface EffectiveSnapshotSource {
  kind: 'ROLE' | 'DIRECT'
  id: number
  /** kind=ROLE 时为权限包名;kind=DIRECT / role 已删除时为 null。 */
  name?: string
}
export interface EffectiveSnapshotRow {
  userId: number
  username: string
  version: number
  snapshotTime: string
  scopeCode: number
  scopeName?: string
  catalogName?: string
  databaseName?: string
  tableName?: string
  columnName?: string
  accesses: string[]
  sources: EffectiveSnapshotSource[]
}
export const pageEffectiveSnapshot = (params: {
  userIds?: number[]
  scopeCodes?: number[]
  keyword?: string
  asOf?: string
  pageNum: number
  pageSize: number
}) => request.get('/data-perm/admin/effective-snapshot', { params })
export const searchDataPermUsers = (keyword: string) =>
  request.get<Array<{ id: number; username: string }>>(
    '/data-perm/admin/users/search', { params: { keyword } })
export const revokeRoleGrant = (grantId: number, note?: string) =>
  request.post(`/data-perm/admin/grants/role/${grantId}/revoke`, { note })
export const revokeDirectGrant = (grantId: number, note?: string) =>
  request.post(`/data-perm/admin/grants/direct/${grantId}/revoke`, { note })
