import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore, type Role } from '@/stores/user'
import { useTaskTypesStore } from '@/stores/taskTypes'
import { ROLE_LEVEL } from '@/composables/usePermission'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/sso/callback',
    name: 'SsoCallback',
    component: () => import('@/views/login/SsoCallbackView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    redirect: '/workspaces',
  },
  {
    path: '/workspaces',
    name: 'WorkspaceList',
    component: () => import('@/views/workspace/WorkspaceList.vue'),
  },
  {
    path: '/workspaces/:workspaceId',
    component: () => import('@/views/workspace/WorkspaceLayout.vue'),
    children: [
      {
        path: 'ide',
        name: 'IDE',
        component: () => import('@/views/ide/IdeLayout.vue'),
      },
      {
        path: 'projects',
        name: 'ProjectList',
        component: () => import('@/views/project/ProjectList.vue'),
      },
      {
        path: 'projects/:projectCode',
        component: () => import('@/views/project/ProjectLayout.vue'),
        children: [
          {
            path: '',
            redirect: (to) => `${to.path}/workflow-definitions`,
          },
          {
            path: 'workflow-definitions',
            name: 'WorkflowDefinitions',
            component: () => import('@/views/workflow/WorkflowDefinitions.vue'),
          },
          {
            path: 'instances',
            name: 'WorkflowInstances',
            component: () => import('@/views/workflow/WorkflowInstances.vue'),
          },
          {
            path: 'parameters',
            name: 'ProjectParameters',
            component: () => import('@/views/project/ProjectParams.vue'),
          },
          {
            path: 'publish-records',
            name: 'PublishRecords',
            component: () => import('@/views/project/PublishRecords.vue'),
          },
        ],
      },
      {
        path: 'projects/:projectCode/workflow-definitions/:workflowDefinitionCode',
        name: 'WorkflowDetail',
        component: () => import('@/views/workflow/WorkflowDetail.vue'),
      },
      {
        path: 'jobs',
        name: 'JobManage',
        component: () => import('@/views/jobs/JobManage.vue'),
        meta: { requireRole: 'DEVELOPER' },
      },
      {
        path: 'files',
        name: 'FileManage',
        component: () => import('@/views/file/FileManage.vue'),
      },
      {
        path: 'approvals',
        name: 'ApprovalList',
        component: () => import('@/views/approval/ApprovalList.vue'),
      },
      {
        path: 'mcp',
        component: () => import('@/views/mcp/McpLayout.vue'),
        children: [
          { path: '', redirect: { name: 'McpTokens' } },
          { path: 'tokens', name: 'McpTokens', component: () => import('@/views/mcp/TokenList.vue') },
          { path: 'capabilities', name: 'McpCapabilities', component: () => import('@/views/mcp/CapabilityCatalog.vue') },
          { path: 'connect', name: 'McpConnect', component: () => import('@/views/mcp/ConnectGuide.vue') },
        ],
      },
      {
        path: 'admin',
        component: () => import('@/views/admin/AdminLayout.vue'),
        // admin 父路由 meta 不再设 SUPER_ADMIN —— 服务监控对全员开放,工作空间管理对 OWNER 开放,
        // 真正的 role 控制下沉到每个子路由 meta;父路由不再做兜底,避免误拦。
        children: [
          { path: '', redirect: { name: 'ServiceMonitor' } },
          { path: 'services', name: 'ServiceMonitor', component: () => import('@/views/admin/ServiceMonitor.vue'), meta: { requireRole: 'VIEWER' } },
          { path: 'workspaces', name: 'WorkspaceManage', component: () => import('@/views/admin/WorkspaceManage.vue'), meta: { requireRole: 'WORKSPACE_OWNER' } },
          { path: 'users', name: 'UserManage', component: () => import('@/views/admin/UserManage.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'auth-sources', name: 'AuthSourceManage', component: () => import('@/views/admin/auth-sources/AuthSourcesView.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'datasources', name: 'DatasourceManage', component: () => import('@/views/datasource/DatasourceManage.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'audit-logs', name: 'AuditLogList', component: () => import('@/views/admin/AuditLogList.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'notifications', name: 'NotificationManage', component: () => import('@/views/notification/NotificationConfig.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'approval-config', name: 'ApprovalConfig', component: () => import('@/views/approval/ApprovalConfig.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'ai-config', name: 'AiConfig', component: () => import('@/views/ai/AiConfig.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'redaction-config', name: 'RedactionConfig', component: () => import('@/views/redaction/RedactionConfig.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'metadata-config', name: 'MetadataConfig', component: () => import('@/views/metadata/MetadataConfig.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'file-config', name: 'FileConfig', component: () => import('@/views/file/FileConfig.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'result-config', name: 'ResultConfig', component: () => import('@/views/result/ResultConfig.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'version-config', name: 'VersionConfig', component: () => import('@/views/version/VersionConfig.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'runtime-config', name: 'RuntimeConfig', component: () => import('@/views/runtime/RuntimeConfig.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
          { path: 'quick-links', name: 'QuickLinkManage', component: () => import('@/views/admin/QuickLinkManage.vue'), meta: { requireRole: 'SUPER_ADMIN' } },
        ],
      },
    ],
  },
  {
    path: '/admin/:pathMatch(.*)*',
    redirect: '/workspaces',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  const workspaceId = Number(to.params.workspaceId)
  if (Number.isFinite(workspaceId) && workspaceId > 0) {
    userStore.setWorkspace(workspaceId)
  }
  const projectCode = Number(to.params.projectCode)
  if (Number.isFinite(projectCode) && projectCode > 0) {
    userStore.setProject(projectCode)
  }
  if (to.meta.public) {
    next()
    return
  }
  if (!userStore.token) {
    next({ path: '/login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} })
    return
  }
  // 沿 matched 链取最高 role 要求,父子路由 meta 都参与判断
  const requiredLevel = to.matched.reduce((max, r) => {
    const role = r.meta.requireRole as Role | undefined
    if (!role) return max
    const lvl = ROLE_LEVEL[role]
    return lvl > max ? lvl : max
  }, -1)
  if (requiredLevel >= 0) {
    const userRole = userStore.userInfo?.role
    const userLevel = userRole ? ROLE_LEVEL[userRole] : -1
    if (userLevel < requiredLevel) {
      next('/workspaces')
      return
    }
  }
  // 进入工作空间任何子页面都可能渲染脚本/任务编辑器,需要 task types 元数据。
  // 守卫这一处 ensure,组件代码就不必每个 mount 自己 await。
  if (Number.isFinite(workspaceId) && workspaceId > 0) {
    try {
      await useTaskTypesStore().ensureLoaded()
    } catch {
      /* 加载失败时继续放行,组件层面会因 categoryOf 返 'OTHER' 走兜底分支 */
    }
  }
  next()
})

export default router
