<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  Search, User, Refresh, InfoFilled, CollectionTag, Lock, ArrowRight,
} from '@element-plus/icons-vue'
import {
  pageEffectiveSnapshot,
  pageUserGrants,
  pageGrantItems,
  searchDataPermUsers,
  listUserGrants,
  revokeBundleGrant,
  revokeDirectGrant,
  type EffectiveSnapshotRow,
  type EffectiveSnapshotSource,
  type UserGrantView,
  type AggregatedUserGrants,
  type AggregatedGrant,
} from '@/api/data-perm'
import { useGrantView } from '@/composables/useGrantView'
import { usePagination } from '@/composables/usePagination'
import { usePermission } from '@/composables/usePermission'
import { useDataPermScopes } from '@/composables/useDataPermScopes'
import GrantItemList from './components/GrantItemList.vue'

const { t } = useI18n()
const { fmtTime, resourcePath, isActiveAt } = useGrantView()
const { isSuperAdmin } = usePermission()
const { services: scopes, ensureLoaded: ensureScopes } = useDataPermScopes()

const userOptions = ref<Array<{ id: number; username: string }>>([])
const selectedUserIds = ref<number[]>([])
const selectedScopeCodes = ref<number[]>([])
const keyword = ref('')
const asOf = ref<string>('')

// seq 丢弃过期响应,防快速打字时旧 keyword 响应覆盖新 keyword 结果误导授权
let userSearchSeq = 0
let userSearchTimer: number | null = null
function searchUsers(q: string) {
  if (userSearchTimer !== null) clearTimeout(userSearchTimer)
  userSearchTimer = window.setTimeout(async () => {
    const mySeq = ++userSearchSeq
    try {
      const res = (await searchDataPermUsers(q)) as any
      if (mySeq !== userSearchSeq) return
      userOptions.value = (res?.data as Array<{ id: number; username: string }>) ?? []
    } catch {
      if (mySeq !== userSearchSeq) return
      userOptions.value = []
    }
  }, 300)
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

// ============ 视图切换:用户授权(默认)/ 明细快照 ============
const viewMode = ref<'byUser' | 'snapshot'>('byUser')
const viewOptions = computed(() => [
  { label: t('dataPermGrant.viewByUser'), value: 'byUser' },
  { label: t('dataPermGrant.viewSnapshot'), value: 'snapshot' },
])

const {
  data: userRows,
  loading: userLoading,
  pageNum: userPageNum,
  pageSize: userPageSize,
  total: userTotal,
  fetch: loadByUser,
  handlePageChange: onUserPageChange,
  handleSizeChange: onUserPageSizeChange,
} = usePagination<AggregatedUserGrants>({
  fetchApi: (params) => pageUserGrants(params as never),
})

// el-tabs 切换时懒加载目标视图(快照视图未访问过不预拉)
function onTabChange(name: unknown) {
  if (name === 'snapshot' && !rows.value.length) load()
  else if (name === 'byUser' && !userRows.value.length) loadByUser()
}

// 展开状态:key = `${userId}-b-${bundleId}`(权限包)/ `${userId}-direct`(直接授权聚合块)。展开后由 GrantItemList 分页懒加载。
const expandedKeys = ref<Set<string>>(new Set())
function toggleExpand(key: string) {
  const next = new Set(expandedKeys.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedKeys.value = next
}

function bundlesOf(u: AggregatedUserGrants): AggregatedGrant[] {
  return u.grants.filter(g => g.kind === 'ROLE')
}
function directOf(u: AggregatedUserGrants): AggregatedGrant | undefined {
  return u.grants.find(g => g.kind === 'DIRECT')
}

// 筛选(用户/服务/关键字/时间点)仅作用于明细快照视图
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
  return row.scopeName ?? `scope-${row.scopeCode}`
}

function rowResourcePath(row: EffectiveSnapshotRow): string {
  return resourcePath({
    scopeCode: row.scopeCode,
    catalogName: row.catalogName,
    databaseName: row.databaseName,
    tableName: row.tableName,
    columnName: row.columnName,
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
      await revokeBundleGrant(g.grantId, note)
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
  await loadByUser()
})
</script>

<template>
  <div class="dp-overview">
    <section class="gm-hero">
      <div class="gm-hero__text">
        <h4>{{ t('dataPermGrant.title') }}</h4>
        <p>{{ t('dataPermGrant.lead') }}</p>
      </div>
      <el-segmented v-model="viewMode" :options="viewOptions" class="gm-hero__switch"
        @change="onTabChange" />
    </section>

    <template v-if="viewMode === 'byUser'">
      <section v-loading="userLoading" class="ledger">
          <div v-if="userTotal > 0" class="ledger__bar">
            <span class="ledger__total">{{ t('dataPermGrant.userTotalN', { n: userTotal }) }}</span>
          </div>

          <div v-if="!userRows.length && !userLoading" class="ledger__empty">
            <el-icon><User /></el-icon>
            <p>{{ t('dataPerm.empty') }}</p>
          </div>

          <article v-for="(u, ui) in userRows" :key="u.userId" class="urec"
            :style="{ animationDelay: `${Math.min(ui, 12) * 0.04}s` }">
            <header class="urec__head">
              <span class="urec__avatar">{{ (u.username || '?').charAt(0).toUpperCase() }}</span>
              <div class="urec__id">
                <strong class="urec__name">{{ u.username }}</strong>
                <span class="urec__uid">UID&nbsp;{{ u.userId }}</span>
              </div>
              <div class="urec__stats">
                <span class="ustat">
                  <span class="ustat__dot" data-tone="role" />
                  <b>{{ bundlesOf(u).length }}</b><em>{{ t('dataPermGrant.statBundles') }}</em>
                </span>
                <span class="ustat">
                  <span class="ustat__dot" data-tone="direct" />
                  <b>{{ directOf(u)?.permCount ?? 0 }}</b><em>{{ t('dataPermGrant.statDirect') }}</em>
                </span>
              </div>
            </header>

            <div class="urec__body">
              <div v-if="!bundlesOf(u).length && !directOf(u)" class="urec__none">
                {{ t('dataPermGrant.noGrants') }}
              </div>

              <div v-for="b in bundlesOf(u)" :key="`b-${b.bundleId}`" class="srow" data-kind="ROLE"
                :class="{ open: expandedKeys.has(`${u.userId}-b-${b.bundleId}`) }">
                <div class="srow__head" @click="toggleExpand(`${u.userId}-b-${b.bundleId}`)">
                  <el-icon class="srow__caret"><ArrowRight /></el-icon>
                  <span class="srow__tile"><el-icon><CollectionTag /></el-icon></span>
                  <strong class="srow__name">{{ b.bundleName }}</strong>
                  <span class="srow__tag">{{ t('dataPerm.kindRole') }}</span>
                  <span class="srow__count">{{ t('dataPerm.countN', { n: b.permCount }) }}</span>
                </div>
                <div v-if="expandedKeys.has(`${u.userId}-b-${b.bundleId}`)" class="srow__items">
                  <GrantItemList :load-fn="p => pageGrantItems({ userId: u.userId, bundleId: b.bundleId, ...p })" />
                </div>
              </div>

              <div v-if="directOf(u)" class="srow" data-kind="DIRECT"
                :class="{ open: expandedKeys.has(`${u.userId}-direct`) }">
                <div class="srow__head" @click="toggleExpand(`${u.userId}-direct`)">
                  <el-icon class="srow__caret"><ArrowRight /></el-icon>
                  <span class="srow__tile"><el-icon><Lock /></el-icon></span>
                  <strong class="srow__name">{{ t('dataPermGrant.statDirect') }}</strong>
                  <span class="srow__count">{{ t('dataPerm.countN', { n: directOf(u)?.permCount ?? 0 }) }}</span>
                </div>
                <div v-if="expandedKeys.has(`${u.userId}-direct`)" class="srow__items">
                  <GrantItemList :load-fn="p => pageGrantItems({ userId: u.userId, ...p })" />
                </div>
              </div>
            </div>
          </article>

          <el-pagination v-if="userTotal > 0" class="snap-pagination"
            background layout="total, prev, pager, next, sizes"
            :current-page="userPageNum" :page-size="userPageSize" :page-sizes="[20, 50, 100]"
            :total="userTotal"
            @current-change="onUserPageChange" @size-change="onUserPageSizeChange" />
        </section>
    </template>

    <template v-else>
        <div class="snap-hint">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ t('dataPermGrant.snapshotHint') }}</span>
        </div>

        <section class="snap-filters">
          <div class="snap-filters__field">
            <span class="snap-filters__label"><el-icon><User /></el-icon>{{ t('dataPermGrant.filterUser') }}</span>
            <el-select v-model="selectedUserIds" multiple filterable remote :remote-method="searchUsers"
              :placeholder="t('dataPermGrant.userSearchPlaceholder')"
              popper-class="r-stable-dropdown"
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
    </template>

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
                <strong>{{ g.bundleName }}</strong>
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

.dp-overview {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-3);
}

/* 与兄弟页 MyDataPerm 的 .hero 同款,视图切换收进右侧 */
.gm-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--r-space-5);
  padding: 16px 18px;
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
      max-width: 640px;
    }
  }
  // gm-hero__switch 这个 class 落在 el-segmented 根元素本身,故变量/边框直接写在它上面(不是 :deep 后代)
  &__switch {
    flex-shrink: 0;
    --el-segmented-bg-color: var(--r-bg-panel);
    --el-segmented-item-selected-bg-color: var(--r-bg-card);
    --el-segmented-item-selected-color: var(--r-accent);
    --el-segmented-item-hover-color: var(--r-text-primary);
    --el-segmented-item-hover-bg-color: transparent;
    --el-segmented-font-size: var(--r-font-sm);
    padding: 3px;
    border: 1px solid var(--r-border);
    border-radius: var(--r-radius-md);

    :deep(.el-segmented__item) {
      min-width: 82px;
      font-weight: var(--r-weight-medium);
      transition: color 0.18s;
    }
    :deep(.el-segmented__item.is-selected) { font-weight: var(--r-weight-semibold); }
    :deep(.el-segmented__item-selected) {
      border-radius: var(--r-radius-sm);
      box-shadow: var(--r-shadow-sm);
    }
  }
}

.snap-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  padding: 6px 12px;
  background: var(--r-accent-bg);
  border: 1px solid var(--r-accent-border);
  border-radius: var(--r-radius-md);
  color: var(--r-text-secondary);
  font-size: var(--r-font-xs);

  .el-icon { font-size: 14px; color: var(--r-accent); }
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

/* ============ 用户授权台账 ============ */
.ledger { display: flex; flex-direction: column; gap: var(--r-space-3); }

.ledger__bar { display: flex; align-items: center; padding: 0 2px; }
.ledger__total {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--r-text-muted);
}

.ledger__empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: var(--r-space-3);
  padding: var(--r-space-8) 0;
  color: var(--r-text-muted);

  .el-icon { font-size: var(--r-font-2xl); opacity: 0.45; }
  p { margin: 0; font-size: var(--r-font-sm); }
}

/* 每位用户一条台账记录,沿用 grant-card 的边框/圆角/悬停语言 */
.urec {
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-lg);
  background: var(--r-bg-card);
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
  animation: urec-in 0.3s cubic-bezier(0.22, 1, 0.36, 1) both;

  &:hover { border-color: var(--r-accent-border); box-shadow: var(--r-shadow-sm); }
}
@keyframes urec-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: none; }
}

.urec__head {
  display: flex; align-items: center; gap: var(--r-space-3);
  padding: var(--r-space-3) var(--r-space-4);
}
.urec__avatar {
  width: 30px; height: 30px; flex-shrink: 0;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: var(--r-radius-sm);
  background: var(--r-accent); color: #fff;
  font-family: var(--r-font-mono); font-size: var(--r-font-sm); font-weight: var(--r-weight-bold);
}
.urec__id { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.urec__name {
  font-size: var(--r-font-md); font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.urec__uid {
  font-family: var(--r-font-mono); font-size: var(--r-font-xs);
  letter-spacing: 0.04em; color: var(--r-text-muted);
}

.urec__stats { margin-left: auto; display: inline-flex; align-items: center; gap: var(--r-space-4); }
.ustat {
  display: inline-flex; align-items: center; gap: 6px;

  b {
    font-family: var(--r-font-mono); font-size: var(--r-font-md); font-weight: var(--r-weight-bold);
    color: var(--r-text-primary); font-variant-numeric: tabular-nums;
  }
  em {
    font-style: normal; font-size: var(--r-font-xs);
    text-transform: uppercase; letter-spacing: 0.1em; color: var(--r-text-muted);
  }
}
.ustat__dot {
  width: 7px; height: 7px; border-radius: 50%; align-self: center;
  background: var(--r-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-accent) 18%, transparent);

  &[data-tone='direct'] {
    background: var(--r-warning);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-warning) 18%, transparent);
  }
}

.urec__body {
  display: flex; flex-direction: column;
  border-top: 1px solid var(--r-border-light);
}
.urec__none {
  padding: var(--r-space-3) var(--r-space-4);
  font-size: var(--r-font-sm); color: var(--r-text-muted);
}

/* 来源行:权限包 / 直接授权,用 grant-card__icon 的软色调瓷砖区分类型 */
.srow {
  border-top: 1px solid var(--r-border-light);

  &:first-child { border-top: none; }
  &.open {
    background: var(--r-bg-panel);
    box-shadow: inset 2px 0 0 var(--r-accent);
  }
  &[data-kind='DIRECT'].open { box-shadow: inset 2px 0 0 var(--r-warning); }
}
.srow__head {
  display: flex; align-items: center; gap: var(--r-space-2);
  padding: var(--r-space-2) var(--r-space-4);
  cursor: pointer; user-select: none;
  transition: background 0.12s;

  &:hover {
    background: var(--r-bg-hover);
    .srow__caret { color: var(--r-text-secondary); }
  }
}
.srow__caret {
  font-size: var(--r-font-sm); color: var(--r-text-muted);
  transition: transform 0.18s ease, color 0.12s;
}
.srow.open .srow__caret { transform: rotate(90deg); color: var(--r-text-secondary); }
.srow__tile {
  width: 22px; height: 22px; flex-shrink: 0;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: var(--r-radius-sm); border: 1px solid;
  background: var(--r-accent-bg); color: var(--r-accent); border-color: var(--r-accent-border);

  .el-icon { font-size: var(--r-font-sm); }
}
.srow[data-kind='DIRECT'] .srow__tile {
  background: var(--r-warning-bg); color: var(--r-warning); border-color: var(--r-warning-border);
}
.srow__name {
  font-size: var(--r-font-sm); font-weight: var(--r-weight-medium); color: var(--r-text-primary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.srow__tag {
  flex-shrink: 0;
  font-size: var(--r-font-xs); font-weight: var(--r-weight-semibold);
  padding: 1px 8px; border-radius: var(--r-radius-sm); border: 1px solid;
  color: var(--r-accent); background: var(--r-accent-bg); border-color: var(--r-accent-border);
}
.srow[data-kind='DIRECT'] .srow__tag {
  color: var(--r-warning); background: var(--r-warning-bg); border-color: var(--r-warning-border);
}
.srow__count {
  margin-left: auto; flex-shrink: 0;
  font-family: var(--r-font-mono); font-size: var(--r-font-xs);
  color: var(--r-text-muted); font-variant-numeric: tabular-nums;
}
.srow__items {
  padding: var(--r-space-1) var(--r-space-4) var(--r-space-3) var(--r-space-8);
  display: flex; flex-direction: column; gap: var(--r-space-1);
  animation: srow-reveal 0.18s ease both;
}
@keyframes srow-reveal {
  from { opacity: 0; transform: translateY(-3px); }
  to { opacity: 1; transform: none; }
}
</style>
