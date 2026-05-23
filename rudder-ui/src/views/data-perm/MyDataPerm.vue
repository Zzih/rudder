<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import {
  CollectionTag, Histogram, ArrowRight, Lock, Plus, ArrowDown,
} from '@element-plus/icons-vue'
import {
  getMyGrantsSummary,
  pageMyRolePermissions,
  pageMyDirectPermissions,
  listMyGrantsHistory,
  type MyGrantsSummary,
  type MyGrantsRoleCard,
  type MyGrantsDirectOverview,
  type DataPermRolePermissionItem,
  type UserGrantView,
} from '@/api/data-perm'
import { useGrantView } from '@/composables/useGrantView'
import ApplyDialog from './ApplyDialog.vue'

const { t } = useI18n()
const route = useRoute()
const { fmtTime, resourcePath } = useGrantView()
const workspaceId = computed(() => Number(route.params.workspaceId))
const applyOpen = ref(false)

const loading = ref(false)
const summary = ref<MyGrantsSummary | null>(null)

async function loadSummary() {
  loading.value = true
  try {
    const res = (await getMyGrantsSummary()) as any
    summary.value = (res?.data as MyGrantsSummary) ?? null
  } finally {
    loading.value = false
  }
}

const roleCards = computed<MyGrantsRoleCard[]>(() => summary.value?.roleCards ?? [])
const directOverview = computed<MyGrantsDirectOverview | null>(() => summary.value?.directOverview ?? null)
const stats = computed(() => summary.value?.stats ?? { roles: 0, direct: 0, expiringSoon: 0 })

const isExpired = (expiration?: string | null) =>
  !!expiration && new Date(expiration).getTime() <= Date.now()

interface RoleSlot {
  expanded: boolean
  loading: boolean
  loaded: boolean
  perms: DataPermRolePermissionItem[]
  pageNum: number
  pageSize: number
  total: number
}
const roleSlots = reactive<Record<number, RoleSlot>>({})
function slotOf(roleId: number): RoleSlot {
  return roleSlots[roleId] ?? (roleSlots[roleId] = {
    expanded: false, loading: false, loaded: false,
    perms: [], pageNum: 1, pageSize: 20, total: 0,
  })
}

async function loadRolePage(roleId: number) {
  const slot = slotOf(roleId)
  slot.loading = true
  try {
    const res: any = await pageMyRolePermissions(roleId, {
      pageNum: slot.pageNum, pageSize: slot.pageSize,
    })
    slot.perms = (res?.data as DataPermRolePermissionItem[]) ?? []
    slot.total = Number(res?.total ?? 0)
    slot.loaded = true
  } catch {
    slot.perms = []
  } finally {
    slot.loading = false
  }
}

async function toggleRoleExpand(card: MyGrantsRoleCard) {
  const slot = slotOf(card.roleId)
  slot.expanded = !slot.expanded
  if (slot.expanded && !slot.loaded) {
    await loadRolePage(card.roleId)
  }
}

function onRolePageChange(roleId: number, pn: number) {
  const slot = slotOf(roleId)
  slot.pageNum = pn
  loadRolePage(roleId)
}

const directExpanded = ref(false)
const directPage = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const directPerms = ref<DataPermRolePermissionItem[]>([])
const directLoading = ref(false)
const directLoaded = ref(false)

async function loadDirectPage() {
  directLoading.value = true
  try {
    const res: any = await pageMyDirectPermissions({
      pageNum: directPage.pageNum,
      pageSize: directPage.pageSize,
    })
    directPerms.value = (res?.data as DataPermRolePermissionItem[]) ?? []
    directPage.total = Number(res?.total ?? 0)
    directLoaded.value = true
  } catch {
    directPerms.value = []
  } finally {
    directLoading.value = false
  }
}

async function toggleDirectExpand() {
  directExpanded.value = !directExpanded.value
  if (directExpanded.value && !directLoaded.value) {
    await loadDirectPage()
  }
}

function onDirectPageChange(pn: number) {
  directPage.pageNum = pn
  loadDirectPage()
}

function serviceLabel(it: DataPermRolePermissionItem) {
  return it.scopeName ?? `svc-${it.scopeCode}`
}

const historyExpanded = ref(false)
const historyLoaded = ref(false)
const historyLoading = ref(false)
const historyGrants = ref<UserGrantView[]>([])

async function toggleHistory() {
  historyExpanded.value = !historyExpanded.value
  if (historyExpanded.value && !historyLoaded.value) {
    historyLoading.value = true
    try {
      const res = (await listMyGrantsHistory()) as any
      historyGrants.value = (res?.data as UserGrantView[]) ?? []
      historyLoaded.value = true
    } catch {
      historyGrants.value = []
    } finally {
      historyLoading.value = false
    }
  }
}

onMounted(loadSummary)
</script>

<template>
  <div v-loading="loading" class="dp-my">
    <section class="hero">
      <div class="hero__text">
        <h4>{{ t('dataPerm.myHeroTitle') }}</h4>
        <p>{{ t('dataPerm.myHeroLead') }}</p>
      </div>
      <div class="hero__right">
        <div class="hero__stats">
          <div class="stat">
            <span class="stat__value">{{ stats.roles }}</span>
            <span class="stat__label">roles</span>
          </div>
          <div class="stat-divider" />
          <div class="stat">
            <span class="stat__value">{{ stats.direct }}</span>
            <span class="stat__label" data-tone="direct">direct</span>
          </div>
          <div class="stat">
            <span class="stat__value">{{ stats.expiringSoon }}</span>
            <span class="stat__label" data-tone="soon">expiring</span>
          </div>
        </div>
        <el-button type="primary" :icon="Plus" class="hero__cta" @click="applyOpen = true">
          {{ t('dataPerm.applyEntry') }}
        </el-button>
      </div>
    </section>

    <ApplyDialog v-model="applyOpen" :workspace-id="workspaceId" @submitted="loadSummary" />

    <section v-if="roleCards.length || directOverview" class="domain-block">
      <header class="domain-header">
        <span class="domain-header__dot" />
        <h5 class="domain-header__name">{{ t('dataPerm.roleSection') }}</h5>
        <span class="domain-header__count">{{ t('dataPerm.countN', { n: roleCards.length }) }}</span>
      </header>

      <div v-if="!roleCards.length" class="placeholder-line">{{ t('dataPerm.empty') }}</div>
      <div v-else class="grant-list">
        <article v-for="card in roleCards" :key="card.roleId" class="grant-card" data-kind="ROLE">
          <button class="grant-card__head" type="button" @click="toggleRoleExpand(card)">
            <span class="grant-card__icon" data-kind="ROLE"><el-icon><CollectionTag /></el-icon></span>
            <strong class="grant-card__name">{{ card.roleName }}</strong>
            <div class="grant-card__meta-right">
              <span class="status-pill" :data-status="isExpired(card.expirationTime) ? 'EXPIRED' : 'ACTIVE'">
                <span class="dot" />
                {{ isExpired(card.expirationTime) ? t('dataPerm.stateExpired') : t('dataPerm.stateActive') }}
              </span>
              <span class="grant-card__subtitle">{{ t('dataPerm.kindRole') }} · {{ card.permCount }} {{ t('dataPerm.unitPerm') }}</span>
            </div>
            <span class="chevron" :class="{ 'is-open': slotOf(card.roleId).expanded }">
              <el-icon><ArrowDown /></el-icon>
            </span>
          </button>
          <div class="grant-card__meta">
            <span><em>{{ t('dataPerm.effectiveTime') }}</em>{{ fmtTime(card.effectiveTime) }}</span>
            <el-icon class="arrow"><ArrowRight /></el-icon>
            <span><em>{{ t('dataPerm.expirationTime') }}</em>{{ fmtTime(card.expirationTime) }}</span>
          </div>
          <div v-if="slotOf(card.roleId).expanded" v-loading="slotOf(card.roleId).loading" class="grant-card__items">
            <div v-if="slotOf(card.roleId).perms.length > 0" class="items-head">
              <span>{{ t('dataPerm.colService') }}</span>
              <span>{{ t('dataPerm.colResource') }}</span>
              <span>{{ t('dataPerm.colAccesses') }}</span>
            </div>
            <div v-for="(it, idx) in slotOf(card.roleId).perms" :key="idx" class="items-row">
              <span class="ds-chip" :title="serviceLabel(it)">{{ serviceLabel(it) }}</span>
              <code class="resource">{{ resourcePath(it) }}</code>
              <div class="access-chips">
                <span v-for="a in it.accesses" :key="a" class="access-chip">{{ a }}</span>
              </div>
            </div>
            <el-pagination v-if="slotOf(card.roleId).total > slotOf(card.roleId).pageSize"
              class="items-pagination" background layout="prev, pager, next"
              :current-page="slotOf(card.roleId).pageNum"
              :page-size="slotOf(card.roleId).pageSize"
              :total="slotOf(card.roleId).total"
              @current-change="(p: number) => onRolePageChange(card.roleId, p)" />
          </div>
        </article>
      </div>
    </section>

    <section class="domain-block">
      <header class="domain-header">
        <span class="domain-header__dot" data-tone="direct" />
        <h5 class="domain-header__name">{{ t('dataPerm.directSection') }}</h5>
        <span class="domain-header__count">
          {{ t('dataPerm.countN', { n: directOverview ? directOverview.permCount : 0 }) }}
        </span>
      </header>

      <div v-if="!directOverview" class="placeholder-line">{{ t('dataPerm.empty') }}</div>
      <article v-else class="grant-card" data-kind="DIRECT">
        <button class="grant-card__head" type="button" @click="toggleDirectExpand">
          <span class="grant-card__icon" data-kind="DIRECT"><el-icon><Lock /></el-icon></span>
          <strong class="grant-card__name">{{ t('dataPerm.directSection') }}</strong>
          <div class="grant-card__meta-right">
            <span class="grant-card__subtitle">{{ t('dataPerm.kindDirect') }} · {{ directOverview.permCount }} {{ t('dataPerm.unitPerm') }}</span>
          </div>
          <span class="chevron" :class="{ 'is-open': directExpanded }">
            <el-icon><ArrowDown /></el-icon>
          </span>
        </button>
        <div v-if="directExpanded" v-loading="directLoading" class="grant-card__items">
          <div v-if="directPerms.length > 0" class="items-head items-head--direct">
            <span>{{ t('dataPerm.colService') }}</span>
            <span>{{ t('dataPerm.colResource') }}</span>
            <span>{{ t('dataPerm.colAccesses') }}</span>
            <span>{{ t('dataPerm.effectiveTime') }}</span>
            <span>{{ t('dataPerm.expirationTime') }}</span>
          </div>
          <div v-for="(it, idx) in directPerms" :key="idx" class="items-row items-row--direct">
            <span class="ds-chip" :title="serviceLabel(it)">{{ serviceLabel(it) }}</span>
            <code class="resource">{{ resourcePath(it) }}</code>
            <div class="access-chips">
              <span v-for="a in it.accesses" :key="a" class="access-chip">{{ a }}</span>
            </div>
            <span class="time-cell">{{ fmtTime(it.effectiveTime) }}</span>
            <span class="time-cell" :class="{ 'is-expired': isExpired(it.expirationTime) }">
              {{ fmtTime(it.expirationTime) }}
            </span>
          </div>
          <el-pagination v-if="directPage.total > directPage.pageSize" class="items-pagination"
            background layout="prev, pager, next"
            :current-page="directPage.pageNum"
            :page-size="directPage.pageSize"
            :total="directPage.total"
            @current-change="onDirectPageChange" />
        </div>
      </article>
    </section>

    <section class="domain-block">
      <button class="history-toggle" type="button" @click="toggleHistory">
        <el-icon class="history-toggle__icon"><Histogram /></el-icon>
        <span class="history-toggle__label">{{ t('dataPerm.historySection') }}</span>
        <span v-if="historyLoaded" class="history-toggle__count">{{ historyGrants.length }}</span>
        <span class="history-toggle__chevron" :class="{ 'is-open': historyExpanded }">⌄</span>
      </button>

      <div v-if="historyExpanded" v-loading="historyLoading" class="history-body">
        <div v-if="historyLoaded && !historyGrants.length" class="placeholder-line">
          {{ t('dataPerm.empty') }}
        </div>
        <div v-else class="grant-list">
          <article v-for="g in historyGrants" :key="`h-${g.kind}-${g.grantId}`"
            class="grant-card grant-card--historical" :data-kind="g.kind">
            <header class="grant-card__head grant-card__head--static">
              <span class="grant-card__icon" :data-kind="g.kind">
                <el-icon><component :is="g.kind === 'ROLE' ? CollectionTag : Lock" /></el-icon>
              </span>
              <strong class="grant-card__name">
                {{ g.kind === 'ROLE' ? g.roleName : t('dataPerm.directSection') }}
              </strong>
              <div class="grant-card__meta-right">
                <span class="status-pill" data-status="EXPIRED">
                  <span class="dot" />{{ g.endReason || t('dataPerm.stateExpired') }}
                </span>
                <span class="grant-card__subtitle">
                  {{ g.kind === 'ROLE' ? t('dataPerm.kindRole') : t('dataPerm.kindDirect') }}
                </span>
              </div>
            </header>
            <div class="grant-card__meta">
              <span><em>{{ t('dataPerm.effectiveTime') }}</em>{{ fmtTime(g.effectiveTime) }}</span>
              <el-icon class="arrow"><ArrowRight /></el-icon>
              <span><em>{{ t('dataPerm.expirationTime') }}</em>{{ fmtTime(g.expirationTime) }}</span>
            </div>
            <div class="grant-card__items">
              <div v-for="(it, idx) in g.permissions" :key="idx" class="items-row">
                <span class="ds-chip" :title="serviceLabel(it)">{{ serviceLabel(it) }}</span>
                <code class="resource">{{ resourcePath(it) }}</code>
                <div class="access-chips">
                  <span v-for="a in it.accesses" :key="a" class="access-chip">{{ a }}</span>
                </div>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/data-perm-grant-card.scss' as *;

.dp-my {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-5);
}

.hero {
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

  &__stats { display: flex; align-items: center; gap: 18px; padding-top: 4px; }
  &__right { display: flex; align-items: center; gap: var(--r-space-4); flex-wrap: wrap; }
  &__cta { flex-shrink: 0; }
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;

  &__value {
    font-family: var(--r-font-mono);
    font-size: 22px;
    font-weight: var(--r-weight-bold);
    color: var(--r-text-primary);
    line-height: 1;
    font-variant-numeric: tabular-nums;
  }
  &__label {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.12em;
    color: var(--r-text-muted);

    &[data-tone="direct"] { color: var(--r-warning); }
    &[data-tone="soon"]   { color: var(--r-danger); }
  }
}
.stat-divider { width: 1px; height: 28px; background: var(--r-border); }

.domain-block { display: flex; flex-direction: column; gap: var(--r-space-3); }

.grant-card {
  transition: border-color 0.15s, box-shadow 0.15s;

  &:hover {
    border-color: var(--r-accent-border);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-accent) 6%, transparent);
  }

  &--historical {
    opacity: 0.7;
    filter: saturate(0.85);
    &:hover { border-color: var(--r-border-light); box-shadow: none; }
  }

  /* 标题作为 button 时需要 reset 浏览器默认;column layout 让 status+subtitle 收到右上 */
  &__head {
    all: unset;
    width: 100%;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 10px;
    cursor: pointer;
    &--static { cursor: default; }
  }

  &__name {
    flex: 1;
    min-width: 0;
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
    letter-spacing: -0.005em;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__meta-right {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    flex-shrink: 0;
  }
}

.chevron {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--r-text-muted);
  transition: transform 0.18s ease;
  font-size: 14px;

  &.is-open { transform: rotate(180deg); }
}

.items-head, .items-row {
  display: grid;
  grid-template-columns: minmax(180px, 240px) minmax(0, 1fr) minmax(0, 1.5fr);
  gap: 16px;
  align-items: start;
  padding: 6px 8px;
}
.items-head--direct, .items-row--direct {
  grid-template-columns:
    minmax(140px, 180px)
    minmax(0, 1fr)
    minmax(0, 1.4fr)
    minmax(120px, max-content)
    minmax(120px, max-content);
}
.time-cell {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  color: var(--r-text-secondary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;

  &.is-expired { color: var(--r-text-disabled); text-decoration: line-through; }
}
.items-head {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}
.items-row {
  border-top: 1px dashed var(--r-border-light);
  font-size: var(--r-font-sm);
}

.items-pagination {
  margin-top: var(--r-space-3);
  justify-content: flex-end;
  display: flex;
}

.history-toggle {
  all: unset;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: 999px;
  cursor: pointer;
  color: var(--r-text-secondary);
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-medium);
  width: fit-content;
  transition: background 0.15s, color 0.15s, border-color 0.15s;

  &:hover {
    background: var(--r-bg-hover);
    color: var(--r-text-primary);
    border-color: var(--r-border);
  }

  &__icon { color: var(--r-text-muted); font-size: 14px; }
  &__count {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 22px;
    height: 18px;
    padding: 0 6px;
    background: var(--r-bg-panel);
    border: 1px solid var(--r-border);
    border-radius: 9px;
    font-family: var(--r-font-mono);
    font-size: var(--r-font-xs);
    color: var(--r-text-tertiary);
  }
  &__chevron {
    font-size: 14px;
    transition: transform 0.15s ease;
    &.is-open { transform: rotate(180deg); }
  }
}

.history-body { margin-top: var(--r-space-3); }
</style>
