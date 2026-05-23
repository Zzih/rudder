<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Key, Search } from '@element-plus/icons-vue'
import { listMyTokens, revokeToken, type TokenSummary } from '@/api/mcp'
import { relativeTime } from '@/utils/dateFormat'
import { usePagination } from '@/composables/usePagination'
import TokenCreateDialog from './TokenCreateDialog.vue'
import TokenDetailDialog from './TokenDetailDialog.vue'

const { t } = useI18n()

const dialogVisible = ref(false)
const detailVisible = ref(false)
const detailTokenId = ref<number | null>(null)
const search = ref('')

const {
  data: tokens,
  loading,
  pageNum,
  pageSize,
  total,
  fetch: fetchTokens,
  handlePageChange,
  resetAndFetch,
} = usePagination<TokenSummary>({
  fetchApi: (params) => listMyTokens({ ...params, search: search.value.trim() || undefined }),
})

function handleSearch() { resetAndFetch() }

function openDetail(row: TokenSummary) {
  detailTokenId.value = row.id
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
    fetchTokens()
  } catch { /* ignore */ }
}

function onCreated() {
  dialogVisible.value = false
  resetAndFetch()
}

function relativeFromNow(iso?: string): string {
  return relativeTime(iso)
}

function expiryFlag(iso?: string): { kind: 'soon' | 'normal'; days: number } | null {
  if (!iso) return null
  const days = Math.floor((new Date(iso).getTime() - Date.now()) / 86_400_000)
  if (days < 0) return null
  return { kind: days <= 7 ? 'soon' : 'normal', days }
}

const TOKEN_STATUS_I18N: Record<string, string> = {
  ACTIVE: 'mcpPage.tokenStatusActive',
  REVOKED: 'mcpPage.tokenStatusRevoked',
  EXPIRED: 'mcpPage.tokenStatusExpired',
}

const TOKEN_STATUS_TAG_TYPE: Record<string, 'success' | 'danger' | 'info'> = {
  ACTIVE: 'success',
  REVOKED: 'danger',
  EXPIRED: 'info',
}

function statusLabel(s: string): string {
  const key = TOKEN_STATUS_I18N[s]
  return key ? t(key) : s
}

function statusTagType(s: string): 'success' | 'danger' | 'info' {
  return TOKEN_STATUS_TAG_TYPE[s] ?? 'info'
}

onMounted(fetchTokens)
</script>

<template>
  <div>
    <div class="filter-bar">
      <el-input
        v-model="search"
        :placeholder="t('common.search')"
        :prefix-icon="Search"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button type="primary" :icon="Plus" style="margin-left: auto" @click="dialogVisible = true">
        {{ t('mcpPage.create') }}
      </el-button>
    </div>

    <div class="admin-card">
      <el-table v-loading="loading" :data="tokens">
        <el-table-column prop="name" :label="t('mcpPage.name')" min-width="220">
          <template #default="{ row }">
            <div class="token-cell">
              <div class="token-cell__icon">
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
            <span class="token-prefix">{{ row.tokenPrefix }}</span>
          </template>
        </el-table-column>

        <el-table-column :label="t('mcpPage.workspace')" min-width="150">
          <template #default="{ row }">
            <el-tag type="info" size="small" :title="`#${row.workspaceId}`">
              {{ row.workspaceName || `#${row.workspaceId}` }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column :label="t('mcpPage.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column :label="t('mcpPage.expiresAt')" width="180">
          <template #default="{ row }">
            <template v-if="row.expiresAt">
              <span class="cell-date">{{ row.expiresAt.slice(0, 10) }}</span>
              <el-tag
                v-if="expiryFlag(row.expiresAt)"
                :type="expiryFlag(row.expiresAt)!.kind === 'soon' ? 'warning' : 'info'"
                size="small"
                effect="plain"
                style="margin-left: 6px"
              >
                {{ expiryFlag(row.expiresAt)!.days }}d
              </el-tag>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column :label="t('mcpPage.lastUsed')" width="160">
          <template #default="{ row }">
            <span v-if="row.lastUsedAt" class="cell-date">{{ relativeFromNow(row.lastUsedAt) }}</span>
            <span v-else class="muted">{{ t('mcpPage.neverUsed') }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" :label="t('common.createdAt')" width="120">
          <template #default="{ row }">
            <span class="cell-date">{{ row.createdAt?.slice(0, 10) }}</span>
          </template>
        </el-table-column>

        <el-table-column :label="t('common.actions')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="openDetail(row)">
              {{ t('mcpPage.detail') }}
            </el-button>
            <el-button v-if="row.status === 'ACTIVE'" text size="small" type="danger" @click="handleRevoke(row)">
              {{ t('mcpPage.revoke') }}
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty-state">
            <div class="empty-state__icon">
              <el-icon><Key /></el-icon>
            </div>
            <p class="empty-state__title">{{ t('mcpPage.emptyHint') }}</p>
            <p class="empty-state__lead">{{ t('mcpPage.emptyLead') }}</p>
            <el-button type="primary" :icon="Plus" @click="dialogVisible = true">
              {{ t('mcpPage.create') }}
            </el-button>
          </div>
        </template>
      </el-table>
    </div>

    <el-pagination v-if="total > pageSize" layout="total, prev, pager, next"
                   :total="total" :page-size="pageSize" :current-page="pageNum"
                   @current-change="handlePageChange" class="admin-pagination" />

    <TokenCreateDialog v-model="dialogVisible" @created="onCreated" />
    <TokenDetailDialog v-model="detailVisible" :token-id="detailTokenId" />
  </div>
</template>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  margin-bottom: var(--r-space-5);
}

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
}

.token-desc {
  font-size: var(--r-font-sm);
  color: var(--r-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 320px;
}

.token-prefix {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  color: var(--r-text-primary);
}

.cell-date {
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
  font-variant-numeric: tabular-nums;
}

.muted {
  color: var(--r-text-muted);
  font-size: var(--r-font-sm);
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
    margin: 0;
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-secondary);
    text-align: center;
  }

  &__lead {
    margin: 0 0 var(--r-space-2);
    font-size: var(--r-font-sm);
    color: var(--r-text-tertiary);
    text-align: center;
    max-width: 420px;
    line-height: var(--r-leading-snug);
  }
}
</style>
