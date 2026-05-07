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

const basePath = computed(() => {
  const wsId = route.params.workspaceId
  return `/workspaces/${wsId}/admin`
})

// requireRole 必须与 router meta 一对一对齐 —— 双源任何一边漏改都会让 UI 与守卫不一致
const allMenuItems = computed<{ key: string; icon: string; label: string; requireRole: Role }[]>(() => [
  { key: 'services', icon: 'Monitor', label: t('admin.services'), requireRole: 'VIEWER' },
  { key: 'workspaces', icon: 'OfficeBuilding', label: t('admin.workspaces'), requireRole: 'WORKSPACE_OWNER' },
  { key: 'quick-links', icon: 'Link', label: t('admin.quickLinks'), requireRole: 'SUPER_ADMIN' },
  { key: 'users', icon: 'User', label: t('admin.users'), requireRole: 'SUPER_ADMIN' },
  { key: 'auth-sources', icon: 'Key', label: t('admin.authSources'), requireRole: 'SUPER_ADMIN' },
  { key: 'datasources', icon: 'Connection', label: t('admin.datasources'), requireRole: 'SUPER_ADMIN' },
  { key: 'audit-logs', icon: 'Document', label: t('admin.auditLogs'), requireRole: 'SUPER_ADMIN' },
  { key: 'notifications', icon: 'Bell', label: t('admin.notifications'), requireRole: 'SUPER_ADMIN' },
  { key: 'approval-config', icon: 'Stamp', label: t('admin.approvalConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'ai-config', icon: 'MagicStick', label: t('admin.aiConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'redaction-config', icon: 'Lock', label: t('admin.redactionConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'metadata-config', icon: 'Coin', label: t('admin.metadataConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'file-config', icon: 'FolderOpened', label: t('admin.fileConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'result-config', icon: 'Files', label: t('admin.resultConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'version-config', icon: 'Document', label: t('admin.versionConfig'), requireRole: 'SUPER_ADMIN' },
  { key: 'runtime-config', icon: 'Cpu', label: t('admin.runtimeConfig'), requireRole: 'SUPER_ADMIN' },
])

const menuItems = computed(() => allMenuItems.value.filter(item => hasRole(item.requireRole)))

const activeKey = computed(() => {
  const path = route.path
  for (const item of menuItems.value) {
    if (path.includes(`/admin/${item.key}`)) return item.key
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
          class="nav-item"
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

.sidebar-title {
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-bold);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--r-text-muted);
  padding: 0 var(--r-space-3) var(--r-space-4);
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);
  padding: 9px var(--r-space-3);
  border-radius: var(--r-radius-md);
  font-size: var(--r-font-base);
  color: var(--r-text-secondary);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;

  &:hover {
    color: var(--r-text-primary);
    background: var(--r-bg-hover);
  }

  &.active {
    color: var(--r-accent);
    background: var(--r-accent-bg);
    font-weight: var(--r-weight-semibold);
  }
}

.admin-content {
  flex: 1;
  overflow: auto;
  background: var(--r-bg-panel);
}
</style>
