<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const baseUrl = `${window.location.origin}/mcp`

const activeTab = computed(() => {
  if (route.path.endsWith('/connect')) return 'connect'
  if (route.path.endsWith('/capabilities')) return 'capabilities'
  return 'tokens'
})

function handleTabChange(tab: string | number) {
  const name = tab === 'connect'
    ? 'McpConnect'
    : tab === 'capabilities'
      ? 'McpCapabilities'
      : 'McpTokens'
  router.push({ name })
}

async function copyBaseUrl() {
  try {
    await navigator.clipboard.writeText(baseUrl)
    ElMessage.success(t('mcpPage.copied'))
  } catch {
    /* ignore */
  }
}
</script>

<template>
  <div class="page-container mcp-page">
    <!-- Tab bar with right-side actions -->
    <div class="mcp-tab-bar">
      <el-tabs v-model="activeTab" class="mcp-tabs" @tab-change="handleTabChange">
        <el-tab-pane :label="t('mcpPage.tabTokens')" name="tokens" />
        <el-tab-pane :label="t('mcpPage.tabCapabilities')" name="capabilities" />
        <el-tab-pane :label="t('mcpPage.tabConnect')" name="connect" />
      </el-tabs>
      <button class="endpoint-chip" :title="t('mcpPage.copy')" @click="copyBaseUrl">
        <span class="endpoint-chip__label">endpoint</span>
        <span class="endpoint-chip__url">{{ baseUrl }}</span>
        <el-icon class="endpoint-chip__copy"><CopyDocument /></el-icon>
      </button>
    </div>

    <router-view />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/admin.scss';

.mcp-page {
  padding-top: var(--r-space-5);
}

/* ============ TAB BAR ============ */
.mcp-tab-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--r-space-4);
  margin-bottom: var(--r-space-5);
  border-bottom: 1px solid var(--r-border-light);
  flex-wrap: wrap;
}

.mcp-tabs {
  flex: 1;
  min-width: 0;

  :deep(.el-tabs__nav-wrap) {
    margin-bottom: 0;
    &::after { display: none; }
  }
  :deep(.el-tabs__header) {
    margin-bottom: 0;
  }
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

/* ============ ENDPOINT CHIP ============ */
.endpoint-chip {
  all: unset;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px 6px 12px;
  margin-bottom: 8px;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-md);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;

  &:hover {
    border-color: var(--r-accent-border);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-accent) 8%, transparent);

    .endpoint-chip__copy { color: var(--r-accent); }
  }

  &__label {
    font-family: var(--r-font-mono);
    font-size: 10px;
    font-weight: var(--r-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.14em;
    color: var(--r-text-muted);
  }

  &__url {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-sm);
    color: var(--r-text-primary);
    font-weight: var(--r-weight-medium);
  }

  &__copy {
    color: var(--r-text-muted);
    font-size: 14px;
    transition: color 0.15s;
  }
}

@media (max-width: 720px) {
  .endpoint-chip__url {
    max-width: 180px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
