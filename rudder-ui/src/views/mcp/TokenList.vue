<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Key, Search, CircleClose } from '@element-plus/icons-vue'
import { listMyTokens, revokeToken, type TokenSummary } from '@/api/mcp'
import { relativeTime } from '@/utils/dateFormat'
import TokenCreateDialog from './TokenCreateDialog.vue'
import TokenDetailDialog from './TokenDetailDialog.vue'

const { t } = useI18n()

const tokens = ref<TokenSummary[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const detailTokenId = ref<number | null>(null)
const search = ref('')

async function load() {
  loading.value = true
  try {
    const { data } = await listMyTokens()
    tokens.value = data ?? []
  } catch {
    /* ignore */
  } finally {
    loading.value = false
  }
}

function openDetail(t: TokenSummary) {
  detailTokenId.value = t.id
  detailVisible.value = true
}

async function handleRevoke(row: TokenSummary) {
  await ElMessageBox.confirm(
    t('mcpPage.revokeConfirm', { name: row.name }),
    t('mcpPage.revokeTitle'),
    { type: 'warning' },
  )
  try {
    await revokeToken(row.id)
    ElMessage.success(t('mcpPage.revoked'))
    load()
  } catch { /* ignore */ }
}

function onCreated() {
  dialogVisible.value = false
  load()
}

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return tokens.value
  return tokens.value.filter(
    x => x.name.toLowerCase().includes(q) || x.tokenPrefix.toLowerCase().includes(q),
  )
})

function relativeFromNow(iso?: string): string {
  return relativeTime(iso)
}

function expiryFlag(iso?: string): { kind: 'soon' | 'normal'; days: number } | null {
  if (!iso) return null
  const days = Math.floor((new Date(iso).getTime() - Date.now()) / 86_400_000)
  if (days < 0) return null
  return { kind: days <= 7 ? 'soon' : 'normal', days }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="toolbar">
      <div class="toolbar__search">
        <el-icon class="toolbar__search-icon"><Search /></el-icon>
        <input
          v-model="search"
          type="text"
          class="toolbar__search-input"
          :placeholder="t('common.search')"
        />
        <button v-if="search" class="toolbar__search-clear" @click="search = ''" aria-label="clear">
          <el-icon><CircleClose /></el-icon>
        </button>
      </div>
      <el-button type="primary" :icon="Plus" class="toolbar__create" @click="dialogVisible = true">
        {{ t('mcpPage.create') }}
      </el-button>
    </div>

    <div class="admin-card">
      <el-table v-loading="loading" :data="filtered">
        <el-table-column prop="name" :label="t('mcpPage.name')" min-width="220">
          <template #default="{ row }">
            <div class="token-cell">
              <div class="token-cell__icon" :data-status="row.status">
                <el-icon><Key /></el-icon>
              </div>
              <div class="token-cell__body">
                <span class="token-name">{{ row.name }}</span>
                <span v-if="row.description" class="token-desc">{{ row.description }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column :label="t('mcpPage.prefix')" width="170">
          <template #default="{ row }">
            <span class="token-prefix">
              <span class="token-prefix__pre">{{ row.tokenPrefix }}</span>
              <span class="token-prefix__mask">••••</span>
            </span>
          </template>
        </el-table-column>

        <el-table-column :label="t('mcpPage.workspace')" min-width="150">
          <template #default="{ row }">
            <span class="ws-tag" :title="`#${row.workspaceId}`">{{ row.workspaceName || `#${row.workspaceId}` }}</span>
          </template>
        </el-table-column>

        <el-table-column :label="t('mcpPage.status')" width="140">
          <template #default="{ row }">
            <span class="status-pill" :data-status="row.status">
              <span class="dot" />{{ row.status }}
            </span>
          </template>
        </el-table-column>

        <el-table-column :label="t('mcpPage.expiresAt')" width="180">
          <template #default="{ row }">
            <template v-if="row.expiresAt">
              <div class="expires">
                <span class="expires__date">{{ row.expiresAt.slice(0, 10) }}</span>
                <span
                  v-if="expiryFlag(row.expiresAt)"
                  class="expires__flag"
                  :data-tone="expiryFlag(row.expiresAt)!.kind"
                >
                  {{ expiryFlag(row.expiresAt)!.days }}d
                </span>
              </div>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column :label="t('mcpPage.lastUsed')" width="160">
          <template #default="{ row }">
            <template v-if="row.lastUsedAt">
              <span class="last-used">
                <span class="last-used__pulse" />
                {{ relativeFromNow(row.lastUsedAt) }}
              </span>
            </template>
            <span v-else class="muted">{{ t('mcpPage.neverUsed') }}</span>
          </template>
        </el-table-column>

        <el-table-column :label="t('common.createdAt')" width="120">
          <template #default="{ row }">
            <span class="created">{{ row.createdAt?.slice(0, 10) }}</span>
          </template>
        </el-table-column>

        <el-table-column :label="t('common.actions')" width="160" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button text size="small" type="primary" @click="openDetail(row)">
                {{ t('mcpPage.detail') }}
              </el-button>
              <el-button v-if="row.status === 'ACTIVE'" text size="small" type="danger" @click="handleRevoke(row)">
                {{ t('mcpPage.revoke') }}
              </el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty-state">
            <div class="empty-state__icon">
              <el-icon><Key /></el-icon>
            </div>
            <p class="empty-state__title">{{ t('mcpPage.emptyHint') }}</p>
            <el-button type="primary" :icon="Plus" @click="dialogVisible = true">
              {{ t('mcpPage.create') }}
            </el-button>
          </div>
        </template>
      </el-table>
    </div>

    <TokenCreateDialog v-model="dialogVisible" @created="onCreated" />
    <TokenDetailDialog v-model="detailVisible" :token-id="detailTokenId" />
  </div>
</template>

<style scoped lang="scss">
/* ============ TOOLBAR ============ */
.toolbar {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);
  margin-bottom: var(--r-space-4);

  &__search {
    position: relative;
    flex: 1;
    max-width: 360px;
    display: flex;
    align-items: center;
    height: 34px;
    padding: 0 12px;
    background: var(--r-bg-card);
    border: 1px solid var(--r-border);
    border-radius: var(--r-radius-md);
    transition: border-color 0.15s, box-shadow 0.15s;

    &:focus-within {
      border-color: var(--r-accent);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-accent) 12%, transparent);
    }

    &-icon {
      color: var(--r-text-muted);
      font-size: 14px;
      flex-shrink: 0;
    }

    &-input {
      flex: 1;
      min-width: 0;
      margin: 0 8px;
      padding: 0;
      border: none;
      outline: none;
      background: transparent;
      font-family: inherit;
      font-size: var(--r-font-md);
      color: var(--r-text-primary);

      &::placeholder {
        color: var(--r-text-muted);
      }
    }

    &-clear {
      all: unset;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 18px;
      height: 18px;
      border-radius: 50%;
      cursor: pointer;
      color: var(--r-text-muted);
      transition: color 0.15s, background 0.15s;

      &:hover {
        color: var(--r-text-primary);
        background: var(--r-bg-hover);
      }

      .el-icon { font-size: 14px; }
    }
  }

  &__create {
    margin-left: auto;
    flex-shrink: 0;
  }
}

/* ============ ROW: NAME CELL ============ */
.token-cell {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);

  &__icon {
    width: 32px;
    height: 32px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-radius-md);
    background: var(--r-accent-bg);
    color: var(--r-accent);
    border: 1px solid var(--r-accent-border);

    &[data-status="REVOKED"],
    &[data-status="EXPIRED"],
    &[data-status="WITHDRAWN"] {
      background: var(--r-bg-panel);
      color: var(--r-text-muted);
      border-color: var(--r-border);
    }
    &[data-status="PENDING_APPROVAL"] {
      background: var(--r-warning-bg);
      color: var(--r-warning);
      border-color: var(--r-warning-border);
    }
    &[data-status="REJECTED"] {
      background: var(--r-danger-bg);
      color: var(--r-danger);
      border-color: var(--r-danger-border);
    }

    .el-icon { font-size: 15px; }
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }
}

.token-name {
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  font-size: var(--r-font-md);
  letter-spacing: -0.005em;
}

.token-desc {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 240px;
}

/* ============ TOKEN PREFIX ============ */
.token-prefix {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  background: var(--r-bg-code);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-sm);
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);

  &__pre {
    color: var(--r-text-primary);
    font-weight: var(--r-weight-medium);
  }
  &__mask {
    margin-left: 4px;
    color: var(--r-text-disabled);
    letter-spacing: 0.5px;
  }
}

/* ============ WORKSPACE TAG ============ */
.ws-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  background: var(--r-purple-bg);
  border: 1px solid var(--r-purple-border);
  color: var(--r-purple);
  border-radius: var(--r-radius-sm);
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
}

/* ============ STATUS PILL ============ */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.02em;

  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
  }

  &[data-status="ACTIVE"] {
    color: var(--r-success);
    background: var(--r-success-bg);
    border: 1px solid var(--r-success-border);
    .dot { box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-success) 25%, transparent); }
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
  &[data-status="EXPIRED"],
  &[data-status="WITHDRAWN"] {
    color: var(--r-text-tertiary);
    background: var(--r-bg-panel);
    border: 1px solid var(--r-border);
  }
}

/* ============ EXPIRES ============ */
.expires {
  display: inline-flex;
  align-items: center;
  gap: 6px;

  &__date {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-sm);
    color: var(--r-text-secondary);
    font-variant-numeric: tabular-nums;
  }

  &__flag {
    display: inline-flex;
    align-items: center;
    padding: 1px 6px;
    border-radius: 999px;
    font-family: var(--r-font-mono);
    font-size: 10px;
    font-weight: var(--r-weight-bold);

    &[data-tone="soon"] {
      color: var(--r-warning);
      background: var(--r-warning-bg);
      border: 1px solid var(--r-warning-border);
    }
    &[data-tone="normal"] {
      color: var(--r-text-tertiary);
      background: var(--r-bg-panel);
      border: 1px solid var(--r-border-light);
    }
  }
}

/* ============ LAST USED ============ */
.last-used {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
  font-variant-numeric: tabular-nums;

  &__pulse {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--r-accent);
    box-shadow: 0 0 0 3px var(--r-accent-bg);
  }
}

.created {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  color: var(--r-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.muted {
  color: var(--r-text-muted);
  font-size: var(--r-font-sm);
  font-style: italic;
}

/* ============ EMPTY STATE ============ */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--r-space-3);
  padding: 40px 24px 32px;

  &__icon {
    width: 52px;
    height: 52px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-radius-lg);
    background: var(--r-accent-bg);
    border: 1px solid var(--r-accent-border);
    color: var(--r-accent);
    margin-bottom: 4px;

    .el-icon { font-size: 22px; }
  }

  &__title {
    margin: 0 0 8px;
    font-size: var(--r-font-md);
    color: var(--r-text-tertiary);
    text-align: center;
    max-width: 360px;
    line-height: var(--r-leading-snug);
  }
}
</style>
