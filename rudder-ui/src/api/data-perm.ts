import request from '@/utils/request'

// ==================== 平台配置 (SuperAdmin) ====================

export type PluginType = 'HADOOP_SQL' | 'STARROCKS' | 'TRINO' | 'HBASE' | 'HDFS' | 'KAFKA'

export const PLUGIN_TYPES: readonly PluginType[] = ['HADOOP_SQL', 'STARROCKS', 'TRINO', 'HBASE', 'HDFS', 'KAFKA']

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
  /** 该域下的操作分组,随域一起暂存 + 保存(配置编辑时回填,含 accesses)。 */
  accessGroups?: DataPermScopeAccessGroup[]
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

// ==================== 操作分组 ====================

/** 操作分组:scope 下把 plugin 原生操作组合成业务语义(读/写)。accesses 仅管理员配置时可见。 */
export interface DataPermScopeAccessGroup {
  id?: number
  scopeCode?: number
  name: string
  accesses?: string[]
  description?: string
}

/**
 * scope 下分组精简列表(不含 accesses),申请 / 权限包编辑选分组用。
 * 分组的增删改随权限域一起走配置保存(见 saveDataPermConfig),不再单独提供 CRUD 接口。
 */
export const listAccessGroups = (scopeCode: number) =>
  request.get(`/config/data-perm/scopes/${scopeCode}/access-groups`)

export interface MarkdownGuide {
  description: string
  body: string
}

export const getDataPermGuide = () => request.get<MarkdownGuide>('/config/data-perm/guide')

// ==================== 资源包 (SuperAdmin) ====================

export interface DataPermPermissionItem {
  scopeCode: number
  scopeName?: string
  catalogName?: string
  databaseName?: string
  tableName?: string
  columnName?: string
  /** 操作分组展示名(当前配置出口回填)。 */
  groupNames?: string[]
  /** plugin 原生 access 列表;仅历史快照回放时有值,当前配置路径为空。 */
  accesses?: string[]
  /** 仅 direct grant 行视图填:该条 grant 的生效/到期。 */
  effectiveTime?: string
  expirationTime?: string
}

export interface DataPermBundle {
  id?: number
  name: string
  description?: string
  activeGrantCount?: number
}

// ---- 作用域块(作用域 + 操作分组 + 多条库表),录入即存储,后端 reconciler 物化时才展开 ----

export type ResourceLevelKey = 'catalog' | 'database' | 'table' | 'column'

/** 一条库表选择:每层是数组(多选)。只最深一层可多选,上层多选则下层强制 ['*']。 */
export interface ResourcePathDraft {
  catalogNames: string[]
  databaseNames: string[]
  tableNames: string[]
  columnNames: string[]
}

/** 一个作用域块:选一个 scope + 一组操作分组,挂多条库表行。id 在编辑已存块时回填。 */
export interface StatementDraft {
  id?: number
  scopeCode: number
  scopeName?: string
  groupIds: number[]
  /** 出口回填的分组展示名(只读展示用)。 */
  groupNames?: string[]
  resources: ResourcePathDraft[]
}

export const emptyResourcePath = (): ResourcePathDraft => ({
  catalogNames: [], databaseNames: [], tableNames: [], columnNames: [],
})

export const emptyStatement = (): StatementDraft => ({
  scopeCode: 0, groupIds: [], resources: [emptyResourcePath()],
})

/** 把后端块响应克隆成可编辑草稿(深拷贝数组,避免编辑污染原数据)。 */
export function cloneStatement(b: StatementDraft): StatementDraft {
  return {
    id: b.id,
    scopeCode: b.scopeCode,
    scopeName: b.scopeName,
    groupIds: [...(b.groupIds ?? [])],
    groupNames: b.groupNames ? [...b.groupNames] : undefined,
    resources: (b.resources ?? []).map(r => ({
      catalogNames: [...(r.catalogNames ?? [])],
      databaseNames: [...(r.databaseNames ?? [])],
      tableNames: [...(r.tableNames ?? [])],
      columnNames: [...(r.columnNames ?? [])],
    })),
  }
}

/** adapter.resourceLevels → 编辑器层级序列(schema 归并为 database)。 */
export function pathLevelsOf(resourceLevels: string[]): ResourceLevelKey[] {
  const out: ResourceLevelKey[] = []
  if (resourceLevels.includes('catalog')) out.push('catalog')
  if (resourceLevels.includes('database') || resourceLevels.includes('schema')) out.push('database')
  if (resourceLevels.includes('table')) out.push('table')
  if (resourceLevels.includes('column')) out.push('column')
  return out
}

/** 四层全空的库表行 = 会被后端 normalize 拒绝(等同放开整作用域),提交前同口径拦截。 */
export function isResourcePathEmpty(r: ResourcePathDraft): boolean {
  return !(r.catalogNames?.length || r.databaseNames?.length || r.tableNames?.length || r.columnNames?.length)
}

/** 库表行渲染成点分路径:单值取原值,多值 `[a,b]`,空层略过。 */
export function resourcePathLabel(r: ResourcePathDraft): string {
  return [r.catalogNames, r.databaseNames, r.tableNames, r.columnNames]
    .filter(v => v && v.length)
    .map(v => (v.length === 1 ? v[0] : `[${v.join(',')}]`))
    .join('.')
}

export const listDataPermBundles = () => request.get('/data-perm/bundles')
export const pageDataPermBundles = (params: { keyword?: string; pageNum: number; pageSize: number }) =>
  request.get('/data-perm/bundles/page', { params })
export const createDataPermBundle = (data: { name: string; description?: string }) =>
  request.post('/data-perm/bundles', data)
export const updateDataPermBundle = (id: number, data: { name: string; description?: string }) =>
  request.put(`/data-perm/bundles/${id}`, data)
export const deleteDataPermBundle = (id: number) => request.delete(`/data-perm/bundles/${id}`)

// ---- 权限包可见工作空间(申请侧据此过滤可见的权限包) ----

export interface BundleWorkspaceGrant { workspaceId: number; workspaceName: string }

export const listBundleWorkspaces = (bundleId: number) =>
  request.get<BundleWorkspaceGrant[]>(`/data-perm/bundles/${bundleId}/workspaces`)

export const setBundleWorkspaces = (bundleId: number, workspaceIds: number[]) =>
  request.put(`/data-perm/bundles/${bundleId}/workspaces`, workspaceIds)

// ---- 权限包内作用域块 CRUD ----

export const listBundleStatements = (
  bundleId: number,
  params?: { keyword?: string; pageNum?: number; pageSize?: number },
) => request.get(`/data-perm/bundles/${bundleId}/statements`, { params })

export const addBundleStatement = (bundleId: number, block: StatementDraft) =>
  request.post(`/data-perm/bundles/${bundleId}/statements`, block)

export const updateBundleStatement = (bundleId: number, statementId: number, block: StatementDraft) =>
  request.put(`/data-perm/bundles/${bundleId}/statements/${statementId}`, block)

export const deleteBundleStatement = (bundleId: number, statementId: number) =>
  request.delete(`/data-perm/bundles/${bundleId}/statements/${statementId}`)

// ==================== 申请 (LoggedIn) ====================

export interface DataPermApplyPayload {
  bundleIds?: number[]
  directGrants?: StatementDraft[]
  expireAt?: string
  reason: string
}

export const submitDataPermApplication = (data: DataPermApplyPayload) =>
  request.post('/data-perm/applications', data)

// ==================== 我的权限 (LoggedIn) ====================

export interface MyGrantsRoleCard {
  bundleId: number
  bundleName: string
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

type PageParams = { pageNum: number; pageSize: number }

export const listMyRolePermissions = (bundleId: number, params: PageParams) =>
  request.get(`/data-perm/my-grants/bundles/${bundleId}/permissions`, { params })

export const listMyDirectPermissions = (params: PageParams) =>
  request.get('/data-perm/my-grants/direct/permissions', { params })

export type GrantKind = 'ROLE' | 'DIRECT'

export interface UserGrantView {
  kind: GrantKind
  bundleId?: number
  bundleName: string
  sourceApprovalId?: number
  grantId: number
  effectiveTime: string
  expirationTime?: string
  endReason?: string
  permissions: DataPermPermissionItem[]
}

export const listMyGrantsHistory = () => request.get('/data-perm/my-grants/history')

// ==================== 全部用户权限 (SuperAdmin) ====================

export const listUserGrants = (userId: number, asOf?: string) =>
  request.get(`/data-perm/admin/grants/by-user/${userId}`,
    { params: asOf ? { asOf } : {} })

/** 「按用户」聚合视图里的一个授权来源摘要(仅计数,权限项明细经 pageGrantItems 懒加载)。 */
export interface AggregatedGrant {
  kind: GrantKind
  bundleId?: number
  bundleName: string
  grantId?: number
  permCount: number
  effectiveTime?: string
  expirationTime?: string
}

/** 「按用户」聚合视图行:某用户当前持有的全部活跃授权摘要(权限包 + 直接授权)。 */
export interface AggregatedUserGrants {
  userId: number
  username: string
  grants: AggregatedGrant[]
}

export const pageUserGrants = (params: { pageNum: number; pageSize: number }) =>
  request.get('/data-perm/admin/grants/aggregated', { params })

/** 总览展开某来源时分页拉取其库表行;bundleId 非空 = 权限包,为空 = 该用户全部直接授权。 */
export const pageGrantItems = (
  params: { userId: number; bundleId?: number; pageNum: number; pageSize: number },
) => request.get('/data-perm/admin/grants/items', { params })

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
export const revokeBundleGrant = (grantId: number, note?: string) =>
  request.post(`/data-perm/admin/grants/bundle/${grantId}/revoke`, { note })
export const revokeDirectGrant = (grantId: number, note?: string) =>
  request.post(`/data-perm/admin/grants/direct/${grantId}/revoke`, { note })
