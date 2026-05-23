<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  Search, User, Refresh, InfoFilled, CollectionTag, Lock, ArrowRight,
} from '@element-plus/icons-vue'
import {
  pageEffectiveSnapshot,
  searchDataPermUsers,
  listUserGrants,
  revokeRoleGrant,
  revokeDirectGrant,
  type EffectiveSnapshotRow,
  type EffectiveSnapshotSource,
  type UserGrantView,
} from '@/api/data-perm'
import { useGrantView } from '@/composables/useGrantView'
import { usePagination } from '@/composables/usePagination'
import { usePermission } from '@/composables/usePermission'
import { useDataPermScopes } from '@/composables/useDataPermScopes'

const { t } = useI18n()
const { fmtTime, resourcePath, isActiveAt } = useGrantView()
const { isSuperAdmin } = usePermission()
const { services: scopes, ensureLoaded: ensureScopes } = useDataPermScopes()

const userOptions = ref<Array<{ id: number; username: string }>>([])
const userSearchLoading = ref(false)
const selectedUserIds = ref<number[]>([])
const selectedScopeCodes = ref<number[]>([])
const keyword = ref('')
const asOf = ref<string>('')

async function searchUsers(q: string) {
  userSearchLoading.value = true
  try {
    const res = (await searchDataPermUsers(q)) as any
    userOptions.value = (res?.data as Array<{ id: number; username: string }>) ?? []
  } catch {
    userOptions.value = []
  } finally {
    userSearchLoading.value = false
  }
}

const {
  data: rows,
  loading,
  pageNum,
  pageSize,
  total,
  fetch: load,
  handlePageChange: onPageChange,
  handleSizeChange: onPageSizeChange,
  resetAndFetch,
} = usePagination<EffectiveSnapshotRow>({
  fetchApi: (params) => pageEffectiveSnapshot({
    ...params,
    userIds: selectedUserIds.value.length ? selectedUserIds.value : undefined,
    scopeCodes: selectedScopeCodes.value.length ? selectedScopeCodes.value : undefined,
    keyword: keyword.value.trim() || undefined,
    asOf: asOf.value || undefined,
  } as never),
})

function applyFilters() { resetAndFetch() }

function resetFilters() {
  selectedUserIds.value = []
  selectedScopeCodes.value = []
  keyword.value = ''
  asOf.value = ''
  resetAndFetch()
}

const isHistorical = computed(() => !!asOf.value
  && new Date(asOf.value).getTime() < Date.now() - 5_000)

function serviceLabel(row: EffectiveSnapshotRow) {
  return row.scopeName ?? `svc-${row.scopeCode}`
}

function rowResourcePath(row: EffectiveSnapshotRow): string {
  return resourcePath({
    scopeCode: row.scopeCode,
    catalogName: row.catalogName,
    databaseName: row.databaseName,
    tableName: row.tableName,
    columnName: row.columnName,
    accesses: row.accesses,
  })
}

function sourceLabel(s: EffectiveSnapshotSource): string {
  if (s.kind !== 'ROLE') return t('dataPerm.kindDirect')
  return s.name ?? `${t('dataPerm.kindRole')} #${s.id}`
}

const drawerOpen = ref(false)
const drawerLoading = ref(false)
const drawerUserId = ref<number | null>(null)
const drawerUsername = ref<string>('')
const drawerGrants = ref<UserGrantView[]>([])

async function openDrawer(row: EffectiveSnapshotRow) {
  drawerUserId.value = row.userId
  drawerUsername.value = row.username
  drawerOpen.value = true
  drawerLoading.value = true
  try {
    const res: any = await listUserGrants(row.userId, asOf.value || undefined)
    drawerGrants.value = (res?.data as UserGrantView[]) ?? []
  } catch {
    drawerGrants.value = []
  } finally {
    drawerLoading.value = false
  }
}

const drawerRoles = computed(() => drawerGrants.value.filter(g => g.kind === 'ROLE'))
const drawerDirect = computed(() => drawerGrants.value.filter(g => g.kind === 'DIRECT'))
function canRevoke(g: UserGrantView): boolean {
  return isActiveAt(g) && !isHistorical.value && isSuperAdmin.value
}

async function revoke(kind: 'role' | 'direct', g: UserGrantView) {
  try {
    const note = t('dataPermGrant.revokeNote', { at: new Date().toISOString() })
    if (kind === 'role') {
      await revokeRoleGrant(g.grantId, note)
    } else {
      await revokeDirectGrant(g.grantId, note)
    }
    ElMessage.success(t('dataPermGrant.revoked'))
    if (drawerUserId.value) {
      const res: any = await listUserGrants(drawerUserId.value, asOf.value || undefined)
      drawerGrants.value = (res?.data as UserGrantView[]) ?? []
    }
    load()
  } catch {
    ElMessage.error(t('common.failed'))
  }
}

onMounted(async () => {
  await ensureScopes()
  await load()
})
</script>

<template>
  <div class="dp-snap">
    <header class="snap-hero">
      <div class="snap-hero__text">
        <h4>{{ t('dataPermGrant.title') }}</h4>
        <p>{{ t('dataPermGrant.lead') }}</p>
      </div>
      <div class="snap-hero__hint">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ t('dataPermGrant.snapshotHint') }}</span>
      </div>
    </header>

    <section class="snap-filters">
      <div class="snap-filters__field">
        <span class="snap-filters__label"><el-icon><User /></el-icon>{{ t('dataPermGrant.filterUser') }}</span>
        <el-select v-model="selectedUserIds" multiple filterable remote :remote-method="searchUsers"
          :loading="userSearchLoading" :placeholder="t('dataPermGrant.userSearchPlaceholder')"
          collapse-tags collapse-tags-tooltip clearable class="snap-filters__select">
          <el-option v-for="u in userOptions" :key="u.id" :value="u.id" :label="u.username" />
        </el-select>
      </div>

      <div class="snap-filters__field">
        <span class="snap-filters__label">{{ t('dataPermGrant.filterService') }}</span>
        <el-select v-model="selectedScopeCodes" multiple filterable
          :placeholder="t('dataPermGrant.servicePlaceholder')"
          collapse-tags collapse-tags-tooltip clearable class="snap-filters__select">
          <el-option v-for="s in scopes" :key="s.code" :value="s.code as number" :label="s.name" />
        </el-select>
      </div>

      <div class="snap-filters__field">
        <span class="snap-filters__label">{{ t('dataPermGrant.filterKeyword') }}</span>
        <el-input v-model="keyword" :placeholder="t('dataPermGrant.keywordPlaceholder')"
          :prefix-icon="Search" clearable class="snap-filters__input"
          @keyup.enter="applyFilters" />
      </div>

      <div class="snap-filters__field">
        <span class="snap-filters__label">{{ t('dataPermGrant.asOf') }}</span>
        <el-date-picker v-model="asOf" type="datetime"
          :placeholder="t('dataPermGrant.asOfPlaceholder')"
          value-format="YYYY-MM-DDTHH:mm:ss" class="snap-filters__picker" />
      </div>

      <div class="snap-filters__actions">
        <el-button type="primary" :icon="Search" @click="applyFilters">{{ t('common.search') }}</el-button>
        <el-button :icon="Refresh" @click="resetFilters">{{ t('common.reset') }}</el-button>
      </div>
    </section>

    <div v-if="isHistorical" class="historical-banner">
      <el-icon><InfoFilled /></el-icon>
      {{ t('dataPermGrant.historicalView') }}
    </div>

    <section v-loading="loading" class="snap-table-wrap">
      <el-table :data="rows" stripe class="snap-table" :empty-text="t('dataPerm.empty')">
        <el-table-column prop="username" :label="t('dataPermGrant.colUser')" min-width="160">
          <template #default="{ row }">
            <button class="snap-userlink" type="button" @click="openDrawer(row)">
              <span class="snap-userlink__avatar">{{ row.username?.charAt(0)?.toUpperCase() }}</span>
              <span class="snap-userlink__name">{{ row.username }}</span>
            </button>
          </template>
        </el-table-column>

        <el-table-column :label="t('dataPerm.colService')" min-width="140">
          <template #default="{ row }">
            <span class="ds-chip" :title="serviceLabel(row)">{{ serviceLabel(row) }}</span>
          </template>
        </el-table-column>

        <el-table-column :label="t('dataPerm.colResource')" min-width="260">
          <template #default="{ row }">
            <code class="resource">{{ rowResourcePath(row) }}</code>
          </template>
        </el-table-column>

        <el-table-column :label="t('dataPerm.colAccesses')" min-width="360">
          <template #default="{ row }">
            <div class="access-chips">
              <span v-for="a in row.accesses" :key="a" class="access-chip">{{ a }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column :label="t('dataPermGrant.colSource')" min-width="140">
          <template #default="{ row }">
            <div class="source-chips">
              <span v-for="(s, i) in row.sources" :key="i"
                :class="['source-chip', s.kind === 'ROLE' ? 'is-role' : 'is-direct']">
                {{ sourceLabel(s) }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column :label="t('dataPermGrant.colSnapshot')" min-width="140">
          <template #default="{ row }">
            <span class="snap-meta">
              <span class="snap-meta__v">v{{ row.version }}</span>
              <span class="snap-meta__t">{{ fmtTime(row.snapshotTime) }}</span>
            </span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-if="total > 0" class="snap-pagination"
        background layout="total, prev, pager, next, sizes"
        :current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="[20, 50, 100]"
        :total="total"
        @current-change="onPageChange"
        @size-change="onPageSizeChange" />
    </section>

    <el-drawer v-model="drawerOpen" size="60%" :show-close="true" destroy-on-close
      :title="t('dataPermGrant.userDrawerTitle', { name: drawerUsername })">
      <div v-loading="drawerLoading" class="drawer-body">
        <section class="drawer-section">
          <header class="domain-header">
            <span class="domain-header__dot" />
            <h5 class="domain-header__name">{{ t('dataPerm.roleSection') }}</h5>
            <span class="domain-header__count">{{ t('dataPerm.countN', { n: drawerRoles.length }) }}</span>
          </header>
          <div v-if="!drawerRoles.length" class="placeholder-line">{{ t('dataPerm.empty') }}</div>
          <article v-for="g in drawerRoles" :key="`r-${g.grantId}`" class="grant-card" data-kind="ROLE">
            <header class="grant-card__head">
              <span class="grant-card__icon" data-kind="ROLE"><el-icon><CollectionTag /></el-icon></span>
              <div class="grant-card__title">
                <strong>{{ g.roleName }}</strong>
                <span class="grant-card__subtitle">{{ t('dataPerm.kindRole') }}</span>
              </div>
              <span class="status-pill" :data-status="isActiveAt(g) ? 'ACTIVE' : 'EXPIRED'">
                <span class="dot" />
                {{ isActiveAt(g) ? t('dataPerm.stateActive') : (g.endReason || t('dataPerm.stateExpired')) }}
              </span>
              <el-button v-if="canRevoke(g)" text size="small" type="danger"
                @click="revoke('role', g)">
                {{ t('dataPermGrant.revoke') }}
              </el-button>
            </header>
            <div class="grant-card__meta">
              <span><em>{{ t('dataPerm.effectiveTime') }}</em>{{ fmtTime(g.effectiveTime) }}</span>
              <el-icon class="arrow"><ArrowRight /></el-icon>
              <span><em>{{ t('dataPerm.expirationTime') }}</em>{{ fmtTime(g.expirationTime) }}</span>
            </div>
          </article>
        </section>

        <section class="drawer-section">
          <header class="domain-header">
            <span class="domain-header__dot" data-tone="direct" />
            <h5 class="domain-header__name">{{ t('dataPerm.directSection') }}</h5>
            <span class="domain-header__count">{{ t('dataPerm.countN', { n: drawerDirect.length }) }}</span>
          </header>
          <div v-if="!drawerDirect.length" class="placeholder-line">{{ t('dataPerm.empty') }}</div>
          <article v-for="g in drawerDirect" :key="`d-${g.grantId}`" class="grant-card" data-kind="DIRECT">
            <header class="grant-card__head">
              <span class="grant-card__icon" data-kind="DIRECT"><el-icon><Lock /></el-icon></span>
              <div class="grant-card__title">
                <strong>{{ t('dataPerm.directSection') }}</strong>
                <span class="grant-card__subtitle">{{ t('dataPerm.kindDirect') }} · #{{ g.grantId }}</span>
              </div>
              <span class="status-pill" :data-status="isActiveAt(g) ? 'ACTIVE' : 'EXPIRED'">
                <span class="dot" />
                {{ isActiveAt(g) ? t('dataPerm.stateActive') : t('dataPerm.stateExpired') }}
              </span>
              <el-button v-if="canRevoke(g)" text size="small" type="danger"
                @click="revoke('direct', g)">
                {{ t('dataPermGrant.revoke') }}
              </el-button>
            </header>
            <div class="grant-card__meta">
              <span><em>{{ t('dataPerm.effectiveTime') }}</em>{{ fmtTime(g.effectiveTime) }}</span>
              <el-icon class="arrow"><ArrowRight /></el-icon>
              <span><em>{{ t('dataPerm.expirationTime') }}</em>{{ fmtTime(g.expirationTime) }}</span>
            </div>
          </article>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/data-perm-grant-card.scss' as *;

.dp-snap {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-4);
}

.snap-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--r-space-4);
  padding: 14px 18px;
  background: linear-gradient(180deg, var(--r-bg-card) 0%, var(--r-bg-panel) 100%);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-lg);
  flex-wrap: wrap;

  &__text {
    flex: 1;
    min-width: 280px;

    h4 {
      margin: 0 0 4px;
      font-size: var(--r-font-md);
      font-weight: var(--r-weight-bold);
      color: var(--r-text-primary);
      letter-spacing: -0.01em;
    }
    p {
      margin: 0;
      font-size: var(--r-font-sm);
      color: var(--r-text-tertiary);
      line-height: var(--r-leading-snug);
      max-width: 720px;
    }
  }
  &__hint {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 10px;
    background: var(--r-bg-card);
    border: 1px solid var(--r-border-light);
    border-radius: var(--r-radius-md);
    color: var(--r-text-muted);
    font-size: var(--r-font-xs);

    .el-icon { font-size: 14px; color: var(--r-accent); }
  }
}

.snap-filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--r-space-3);
  align-items: flex-end;
  padding: 14px 16px;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-lg);

  &__field {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 180px;
    flex: 1 1 200px;
  }
  &__label {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-family: var(--r-font-mono);
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    color: var(--r-text-muted);

    .el-icon { font-size: 12px; }
  }
  &__select, &__input, &__picker { width: 100% !important; }
  &__actions {
    display: flex;
    gap: var(--r-space-2);
    align-self: flex-end;
    flex-shrink: 0;
  }
}

.historical-banner {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: var(--r-warning-bg);
  border: 1px solid var(--r-warning-border);
  color: var(--r-warning);
  border-radius: 999px;
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.02em;
  align-self: flex-start;

  .el-icon { font-size: 12px; }
}

.snap-table-wrap {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-3);
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-lg);
  padding: var(--r-space-3) var(--r-space-3) var(--r-space-2);
}

.snap-table {
  width: 100%;

  // access 列折行后行高被撑高,默认 middle 对齐会让短内容(v3 / 用户名)悬空到中部,看起来错位
  :deep(.el-table__cell) {
    vertical-align: top;
  }
}

.snap-userlink {
  all: unset;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--r-text-primary);
  font-weight: var(--r-weight-medium);

  &:hover { color: var(--r-accent); }
  &__avatar {
    width: 24px;
    height: 24px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-radius-sm);
    background: var(--r-accent);
    color: #fff;
    font-family: var(--r-font-mono);
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-bold);
  }
  &__name { font-size: var(--r-font-sm); }
}

.source-chips { display: flex; flex-wrap: wrap; gap: 4px; }
.source-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--r-radius-sm);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  border: 1px solid;

  &.is-role {
    color: var(--r-accent);
    background: var(--r-accent-bg);
    border-color: var(--r-accent-border);
  }
  &.is-direct {
    color: var(--r-warning);
    background: var(--r-warning-bg);
    border-color: var(--r-warning-border);
  }
}

.snap-meta {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  font-variant-numeric: tabular-nums;

  &__v {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-xs);
    color: var(--r-text-primary);
    font-weight: var(--r-weight-semibold);
  }
  &__t {
    font-size: var(--r-font-xs);
    color: var(--r-text-muted);
  }
}

.snap-pagination {
  align-self: flex-end;
  padding: var(--r-space-2) 0;
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-4);
  padding: 0 var(--r-space-4);
}
.drawer-section { display: flex; flex-direction: column; gap: var(--r-space-3); }

/* drawer 内 grant 卡片用更紧凑 padding 覆盖 partial */
.grant-card {
  padding: 12px 14px;

  &__icon {
    width: 30px;
    height: 30px;
  }
}
</style>
