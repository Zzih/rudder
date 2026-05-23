<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Key, Reading, EditPen } from '@element-plus/icons-vue'
import { getToken, type TokenDetail } from '@/api/mcp'

const { t } = useI18n()

const props = defineProps<{ modelValue: boolean; tokenId: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const detail = ref<TokenDetail | null>(null)
const loading = ref(false)

watch(
  () => [props.modelValue, props.tokenId],
  async ([v, id]) => {
    if (!v || !id) {
      detail.value = null
      return
    }
    loading.value = true
    try {
      const { data } = await getToken(id as number)
      detail.value = data ?? null
    } finally {
      loading.value = false
    }
  },
)

const readGrants = computed(() => detail.value?.grants.filter((g) => g.rwClass === 'READ') ?? [])
const writeGrants = computed(() => detail.value?.grants.filter((g) => g.rwClass === 'WRITE') ?? [])
const pendingCount = computed(
  () => detail.value?.grants.filter((g) => g.status === 'PENDING_APPROVAL').length ?? 0,
)
const statusColor: Record<string, string> = {
  ACTIVE: 'var(--r-success)',
  REVOKED: 'var(--r-danger)',
  EXPIRED: 'var(--r-text-muted)',
}

const TOKEN_STATUS_I18N: Record<string, string> = {
  ACTIVE: 'mcpPage.tokenStatusActive',
  REVOKED: 'mcpPage.tokenStatusRevoked',
  EXPIRED: 'mcpPage.tokenStatusExpired',
}
const GRANT_STATUS_I18N: Record<string, string> = {
  ACTIVE: 'mcpPage.grantStatusActive',
  PENDING_APPROVAL: 'mcpPage.grantStatusPendingApproval',
  REJECTED: 'mcpPage.grantStatusRejected',
  REVOKED: 'mcpPage.grantStatusRevoked',
  WITHDRAWN: 'mcpPage.grantStatusWithdrawn',
  EXPIRED: 'mcpPage.grantStatusExpired',
}
function tokenStatusLabel(s: string): string {
  return TOKEN_STATUS_I18N[s] ? t(TOKEN_STATUS_I18N[s]) : s
}
function grantStatusLabel(s: string): string {
  return GRANT_STATUS_I18N[s] ? t(GRANT_STATUS_I18N[s]) : s
}
</script>

<template>
  <el-dialog v-model="visible" width="640px" :close-on-click-modal="false" :show-close="false"
    class="td-dialog">
    <template #header>
      <div class="td-head">
        <div class="td-head-id">
          <span class="td-head-kicker">{{ t('mcpPage.detailTitle') }}</span>
          <span v-if="detail" class="td-head-num">#{{ detail.token.id }}</span>
        </div>
        <div v-if="detail" class="td-head-main">
          <div class="td-head-row">
            <span class="td-status-dot" :style="{ background: statusColor[detail.token.status] || 'var(--r-text-muted)' }" />
            <span class="td-status-text" :style="{ color: statusColor[detail.token.status] || 'var(--r-text-muted)' }">
              {{ tokenStatusLabel(detail.token.status) }}
            </span>
            <span class="td-head-sep" aria-hidden="true">·</span>
            <span class="td-head-prefix">{{ detail.token.tokenPrefix }}…</span>
          </div>
          <h3 class="td-head-title">{{ detail.token.name }}</h3>
        </div>
      </div>
    </template>

    <div v-loading="loading" class="td-body">
      <template v-if="detail">
        <section class="td-section">
          <div class="td-section-head">
            <span class="td-section-mark" />
            <h4 class="td-section-title">{{ t('mcpPage.sectionBasic') }}</h4>
          </div>
          <dl class="td-kv">
            <dt>{{ t('mcpPage.workspaceId') }}</dt>
            <dd class="td-kv-mono">{{ detail.token.workspaceName || `#${detail.token.workspaceId}` }}</dd>

            <dt>{{ t('common.createdAt') }}</dt>
            <dd>{{ detail.token.createdAt }}</dd>

            <dt>{{ t('mcpPage.expiresAt') }}</dt>
            <dd>{{ detail.token.expiresAt ?? t('mcpPage.permanent') }}</dd>

            <dt>{{ t('mcpPage.lastUsed') }}</dt>
            <dd>{{ detail.token.lastUsedAt ?? t('mcpPage.neverUsed') }}</dd>
          </dl>
          <div v-if="detail.token.description" class="td-remark">
            <span class="td-remark-label">{{ t('mcpPage.description') }}</span>
            <p class="td-remark-text">{{ detail.token.description }}</p>
          </div>
        </section>

        <div v-if="pendingCount > 0" class="td-pending-banner">
          <el-icon class="td-pending-banner__icon"><EditPen /></el-icon>
          <span>{{ t('mcpPage.pendingApprovalAlert', { count: pendingCount }) }}</span>
        </div>

        <section class="td-section">
          <div class="td-section-head">
            <span class="td-section-mark" data-tone="read" />
            <h4 class="td-section-title">
              <el-icon class="td-section-icon"><Reading /></el-icon>
              {{ t('mcpPage.readCapabilities') }}
            </h4>
            <span class="td-section-count">{{ readGrants.length }}</span>
          </div>
          <div v-if="readGrants.length" class="td-grant-grid">
            <div v-for="g in readGrants" :key="g.capability" class="td-grant">
              <code class="td-grant-id">{{ g.capability }}</code>
              <span class="td-grant-pill" :data-status="g.status">{{ grantStatusLabel(g.status) }}</span>
            </div>
          </div>
          <div v-else class="td-empty-line">{{ t('mcpPage.readEmpty') }}</div>
        </section>

        <section class="td-section">
          <div class="td-section-head">
            <span class="td-section-mark" data-tone="write" />
            <h4 class="td-section-title">
              <el-icon class="td-section-icon"><Key /></el-icon>
              {{ t('mcpPage.writeCapabilities') }}
            </h4>
            <span class="td-section-count">{{ writeGrants.length }}</span>
          </div>
          <div v-if="writeGrants.length" class="td-grant-grid">
            <div v-for="g in writeGrants" :key="g.capability" class="td-grant">
              <code class="td-grant-id">{{ g.capability }}</code>
              <span class="td-grant-pill" :data-status="g.status">{{ grantStatusLabel(g.status) }}</span>
            </div>
          </div>
          <div v-else class="td-empty-line">{{ t('mcpPage.writeEmpty') }}</div>
        </section>
      </template>
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ t('mcpPage.close') }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.td-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: var(--r-space-5) var(--r-space-6) var(--r-space-4);
  border-bottom: 1px solid var(--r-border-light);
  background: var(--r-bg-card);
}
.td-dialog :deep(.el-dialog__body) { padding: 0; }
.td-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid var(--r-border-light);
  padding: var(--r-space-3) var(--r-space-6);
  background: var(--r-bg-card);
}

.td-head {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: center;
  gap: var(--r-space-4);
}
.td-head-id {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding-right: var(--r-space-4);
  border-right: 1px solid var(--r-border-light);
}
.td-head-kicker {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.td-head-num {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-lg);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  line-height: 1.1;
}
.td-head-main { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.td-head-row {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  flex-wrap: wrap;
  font-size: var(--r-font-xs);
}
.td-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.td-status-text {
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.td-head-sep { color: var(--r-text-disabled); }
.td-head-prefix {
  font-family: var(--r-font-mono);
  color: var(--r-text-muted);
  font-size: var(--r-font-xs);
}
.td-head-title {
  margin: 0;
  font-size: var(--r-font-lg);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.td-body {
  padding: var(--r-space-5) var(--r-space-6);
  background: var(--r-bg-page, var(--r-bg-panel));
  display: flex;
  flex-direction: column;
  gap: var(--r-space-4);
}

.td-section {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  padding: var(--r-space-4) var(--r-space-5);
}
.td-section-head {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  margin-bottom: var(--r-space-3);
}
.td-section-mark {
  width: 3px;
  height: 14px;
  background: var(--r-accent);
  border-radius: 2px;
  &[data-tone="read"]  { background: var(--r-success); }
  &[data-tone="write"] { background: var(--r-warning); }
}
.td-section-title {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  letter-spacing: 0.02em;
}
.td-section-icon { color: var(--r-text-muted); font-size: 13px; }
.td-section-count {
  margin-left: auto;
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-sm);
  padding: 1px 8px;
  font-variant-numeric: tabular-nums;
}

.td-kv {
  margin: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  column-gap: var(--r-space-5);
  row-gap: var(--r-space-3);
  font-size: var(--r-font-sm);

  dt {
    color: var(--r-text-muted);
    font-size: var(--r-font-xs);
    letter-spacing: 0.04em;
    text-transform: uppercase;
    margin-bottom: 2px;
  }
  dd {
    margin: 0 0 var(--r-space-2);
    color: var(--r-text-primary);
    line-height: 1.4;
  }
}
.td-kv-mono {
  font-family: var(--r-font-mono);
}

.td-remark {
  margin-top: var(--r-space-3);
  padding-top: var(--r-space-3);
  border-top: 1px dashed var(--r-border-light);
}
.td-remark-label {
  display: block;
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  margin-bottom: 4px;
}
.td-remark-text {
  margin: 0;
  font-size: var(--r-font-sm);
  color: var(--r-text-primary);
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.td-pending-banner {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  padding: var(--r-space-3) var(--r-space-4);
  background: var(--r-warning-bg);
  border: 1px solid var(--r-warning-border);
  border-left: 3px solid var(--r-warning);
  border-radius: var(--r-radius-md);
  color: var(--r-warning);
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-medium);

  &__icon { font-size: 14px; }
}

.td-grant-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--r-space-2);
}
.td-grant {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--r-space-3);
  padding: 8px var(--r-space-3);
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-sm);
}
.td-grant-id {
  flex: 1;
  min-width: 0;
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  color: var(--r-text-primary);
  background: none;
  padding: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.td-empty-line {
  padding: var(--r-space-3);
  text-align: center;
  color: var(--r-text-muted);
  font-size: var(--r-font-sm);
  font-style: italic;
}

.td-grant-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  flex-shrink: 0;

  &[data-status="ACTIVE"] {
    color: var(--r-success);
    background: var(--r-success-bg);
    border: 1px solid var(--r-success-border);
  }
  &[data-status="PENDING_APPROVAL"] {
    color: var(--r-warning);
    background: var(--r-warning-bg);
    border: 1px solid var(--r-warning-border);
  }
  &[data-status="REJECTED"] {
    color: var(--r-danger);
    background: var(--r-danger-bg);
    border: 1px solid var(--r-danger-border);
  }
  &[data-status="REVOKED"],
  &[data-status="WITHDRAWN"] {
    color: var(--r-danger);
    background: var(--r-danger-bg);
    border: 1px solid var(--r-danger-border);
  }
  &[data-status="EXPIRED"] {
    color: var(--r-text-muted);
    background: var(--r-bg-panel);
    border: 1px solid var(--r-border);
  }
}
</style>
