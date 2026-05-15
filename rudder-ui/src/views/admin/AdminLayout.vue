<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { usePermission } from '@/composables/usePermission'
import type { Role } from '@/stores/user'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { hasRole } = usePermission()

// basePath 跟随当前 URL 形态: workspace-scoped 走 /workspaces/:id/admin, 顶级走 /admin
const basePath = computed(() => {
  const wsId = route.params.workspaceId
  return wsId ? `/workspaces/${wsId}/admin` : '/admin'
})

// requireRole 必须与 router meta 一对一对齐 —— 双源任何一边漏改都会让 UI 与守卫不一致
// 排序: 平台运维 → 用户与权限 → 业务资源 → 首页定制 → AI/业务 SPI → 运行时 SPI → 审计日志(末位)
const allMenuItems = computed<{ key: string; icon: string; label: string; requireRole: Role }[]>(() => [
  // 平台运维
  { key: 'services', icon: 'Monitor', label: t('admin.services'), requireRole: 'VIEWER' },

  // 用户与权限
  { key: 'workspaces', icon: 'OfficeBuilding', label: t('admin.workspaces'), requireRole: 'WORKSPACE_OWNER' },
  { key: 'users', icon: 'User', label: t('admin.users'), requireRole: 'SUPER_ADMIN' },

  // 业务资源
  { key: 'datasources', icon: 'Connection', label: t('admin.datasources'), requireRole: 'SUPER_ADMIN' },

  // 身份与首页定制
  { key: 'auth-sources', icon: 'Key', label: t('admin.authSources'), requireRole: 'SUPER_ADMIN' },
  { key: 'quick-links', icon: 'Link', label: t('admin.quickLinks'), requireRole: 'SUPER_ADMIN' },

  // AI / 业务 SPI
  { key: 'ai-config', icon: 'MagicStick', label: t('admin.aiConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'metadata-config', icon: 'Coin', label: t('admin.metadataConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'approval-config', icon: 'Stamp', label: t('admin.approvalConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'publish-config', icon: 'Promotion', label: t('admin.publishConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'notification-config', icon: 'Bell', label: t('admin.notificationConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'redaction-config', icon: 'Lock', label: t('admin.redactionConfig'), requireRole: 'SUPER_ADMIN' },

  // 运行时 SPI
  { key: 'runtime-config', icon: 'Cpu', label: t('admin.runtimeConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'file-config', icon: 'FolderOpened', label: t('admin.fileConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'result-config', icon: 'Files', label: t('admin.resultConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'version-config', icon: 'Document', label: t('admin.versionConfig'), requireRole: 'SUPER_ADMIN' },

  // 审计日志
  { key: 'audit-logs', icon: 'Document', label: t('admin.auditLogs'), requireRole: 'SUPER_ADMIN' },
])

const menuItems = computed(() => allMenuItems.value.filter(item => hasRole(item.requireRole)))

const activeKey = computed(() => {
  const path = route.path
  for (const item of menuItems.value) {
    if (path.endsWith(`/admin/${item.key}`) || path.includes(`/admin/${item.key}/`)) return item.key
  }
  return menuItems.value[0]?.key ?? 'services'
})

function navigate(key: string) {
  router.push(`${basePath.value}/${key}`)
}
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="sidebar-title">{{ t('admin.title') }}</div>
      <nav class="sidebar-nav">
        <div
          v-for="item in menuItems"
          :key="item.key"
          class="sidebar-item"
          :class="{ active: activeKey === item.key }"
          @click="navigate(item.key)"
        >
          <el-icon :size="16"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </div>
      </nav>
    </aside>
    <main class="admin-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped lang="scss">
.admin-layout {
  display: flex;
  height: 100%;
}

.admin-sidebar {
  width: 232px;
  background: var(--r-bg-card);
  border-right: 1px solid var(--r-border);
  flex-shrink: 0;
  overflow-y: auto;
  padding: var(--r-space-6) var(--r-space-3);
}

.admin-content {
  flex: 1;
  overflow: auto;
  background: var(--r-bg-panel);
}
</style>
