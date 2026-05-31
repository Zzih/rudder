<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Lock } from '@element-plus/icons-vue'
import { usePermission } from '@/composables/usePermission'
import { useDataPermEnabled } from '@/composables/useDataPermEnabled'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { hasRole } = usePermission()
const { enabled, ready, ensureLoaded } = useDataPermEnabled()
onMounted(ensureLoaded)

const workspaceId = computed(() => route.params.workspaceId)

interface TabDef { key: string; label: string; routeName: string; superAdminOnly: boolean }

const tabs = computed<TabDef[]>(() => {
  const all: TabDef[] = [
    { key: 'my', label: t('dataPerm.tabMy'), routeName: 'MyDataPerm', superAdminOnly: false },
    { key: 'grants', label: t('dataPerm.tabGrants'), routeName: 'DataPermGrantManage', superAdminOnly: false },
    { key: 'roles', label: t('dataPerm.tabRoles'), routeName: 'DataPermBundleManage', superAdminOnly: true },
  ]
  return all.filter(tab => !tab.superAdminOnly || hasRole('SUPER_ADMIN'))
})

const activeTab = computed(() => {
  const path = route.path
  if (path.includes('/data-perm/roles')) return 'roles'
  if (path.includes('/data-perm/grants')) return 'grants'
  return 'my'
})

function handleTabChange(key: string | number) {
  const tab = tabs.value.find(t => t.key === key)
  if (tab) {
    router.push({ name: tab.routeName, params: { workspaceId: workspaceId.value } })
  }
}
</script>

<template>
  <div class="page-container dp-layout">
    <div class="dp-tab-bar">
      <el-tabs v-model="activeTab" class="dp-tabs" @tab-change="handleTabChange">
        <el-tab-pane v-for="tab in tabs" :key="tab.key" :label="tab.label" :name="tab.key" />
      </el-tabs>
      <span class="dp-kicker">
        <el-icon><Lock /></el-icon>
        <span class="dp-kicker__label">data permissions</span>
      </span>
    </div>

    <div v-if="ready && !enabled" class="dp-disabled">
      <div class="dp-disabled__icon"><el-icon><Lock /></el-icon></div>
      <h4 class="dp-disabled__title">{{ t('dataPerm.disabledTitle') }}</h4>
      <p class="dp-disabled__lead">{{ t('dataPerm.disabledLead') }}</p>
    </div>
    <router-view v-else-if="ready" />
  </div>
</template>

<style scoped lang="scss">
.dp-layout { padding-top: var(--r-space-5); }

.dp-tab-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--r-space-4);
  margin-bottom: var(--r-space-5);
  border-bottom: 1px solid var(--r-border-light);
  flex-wrap: wrap;
}

.dp-tabs {
  flex: 1;
  min-width: 0;

  :deep(.el-tabs__nav-wrap) {
    margin-bottom: 0;
    &::after { display: none; }
  }
  :deep(.el-tabs__header) { margin-bottom: 0; }
  :deep(.el-tabs__item) {
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-medium);
    color: var(--r-text-secondary);
    height: 44px;
    line-height: 44px;

    &.is-active {
      color: var(--r-accent);
      font-weight: var(--r-weight-semibold);
    }
  }
  :deep(.el-tabs__active-bar) {
    background-color: var(--r-accent);
    height: 2px;
  }
}

.dp-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding: 4px 10px 4px 8px;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  color: var(--r-text-muted);

  .el-icon {
    color: var(--r-accent);
    font-size: 12px;
  }

  &__label {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.14em;
  }
}

.dp-disabled {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 64px 24px 48px;
  background: linear-gradient(180deg, var(--r-bg-card) 0%, var(--r-bg-panel) 100%);
  border: 1px dashed var(--r-border);
  border-radius: var(--r-radius-lg);

  &__icon {
    width: 56px;
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-radius-lg);
    background: var(--r-bg-card);
    border: 1px solid var(--r-border-light);
    color: var(--r-text-muted);
    margin-bottom: 4px;
    .el-icon { font-size: 22px; }
  }
  &__title {
    margin: 0;
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-secondary);
    letter-spacing: -0.005em;
  }
  &__lead {
    margin: 0;
    max-width: 480px;
    text-align: center;
    font-size: var(--r-font-sm);
    color: var(--r-text-tertiary);
    line-height: var(--r-leading-snug);
  }
}
</style>
