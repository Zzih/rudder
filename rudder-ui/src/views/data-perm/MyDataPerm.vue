<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import {
  CollectionTag, Histogram, ArrowRight, Lock, Plus, ArrowDown,
} from '@element-plus/icons-vue'
import {
  getMyGrantsSummary,
  listMyRolePermissions,
  listMyDirectPermissions,
  listMyGrantsHistory,
  type MyGrantsSummary,
  type MyGrantsRoleCard,
  type MyGrantsDirectOverview,
  type UserGrantView,
} from '@/api/data-perm'
import { useGrantView } from '@/composables/useGrantView'
import ApplyDialog from './ApplyDialog.vue'
import GrantItemList from './components/GrantItemList.vue'

const { t } = useI18n()
const route = useRoute()
const { fmtTime, resourcePath, serviceLabel, permLabels } = useGrantView()
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

// 展开仅切显隐;权限项由 GrantItemList 分页懒加载(与数据权限总览共用同一接口与渲染)。
const expandedRoles = reactive<Set<number>>(new Set())
function toggleRoleExpand(card: MyGrantsRoleCard) {
  if (expandedRoles.has(card.bundleId)) expandedRoles.delete(card.bundleId)
  else expandedRoles.add(card.bundleId)
}

const directExpanded = ref(false)
function toggleDirectExpand() {
  directExpanded.value = !directExpanded.value
}

const historyExpanded = ref(false)
const historyLoaded = ref(false)
const historyLoading = ref(false)
const historyGrants = ref<UserGrantView[]>([])
const historyPage = ref(1)
const historyPageSize = ref(10)
const historyTotal = ref(0)

async function loadHistoryPage(page: number) {
  historyLoading.value = true
  try {
    const res = (await listMyGrantsHistory(page, historyPageSize.value)) as any
    historyGrants.value = (res?.data as UserGrantView[]) ?? []
    historyTotal.value = res?.total ?? 0
    historyPage.value = page
    historyLoaded.value = true
  } catch {
    historyGrants.value = []
    historyTotal.value = 0
  } finally {
    historyLoading.value = false
  }
}

async function toggleHistory() {
  historyExpanded.value = !historyExpanded.value
  if (historyExpanded.value && !historyLoaded.value) {
    await loadHistoryPage(1)
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
        <article v-for="card in roleCards" :key="card.bundleId" class="grant-card" data-kind="ROLE">
          <button class="grant-card__head" type="button" @click="toggleRoleExpand(card)">
            <span class="grant-card__icon" data-kind="ROLE"><el-icon><CollectionTag /></el-icon></span>
            <strong class="grant-card__name">{{ card.bundleName }}</strong>
            <div class="grant-card__meta-right">
              <span class="status-pill" :data-status="isExpired(card.expirationTime) ? 'EXPIRED' : 'ACTIVE'">
                <span class="dot" />
                {{ isExpired(card.expirationTime) ? t('dataPerm.stateExpired') : t('dataPerm.stateActive') }}
              </span>
              <span class="grant-card__subtitle">{{ t('dataPerm.kindRole') }} · {{ card.permCount }} {{ t('dataPerm.unitPerm') }}</span>
            </div>
            <span class="chevron" :class="{ 'is-open': expandedRoles.has(card.bundleId) }">
              <el-icon><ArrowDown /></el-icon>
            </span>
          </button>
          <div class="grant-card__meta">
            <span><em>{{ t('dataPerm.effectiveTime') }}</em>{{ fmtTime(card.effectiveTime) }}</span>
            <el-icon class="arrow"><ArrowRight /></el-icon>
            <span><em>{{ t('dataPerm.expirationTime') }}</em>{{ fmtTime(card.expirationTime) }}</span>
          </div>
          <div v-if="expandedRoles.has(card.bundleId)" class="grant-card__items">
            <GrantItemList :load-fn="p => listMyRolePermissions(card.bundleId, p)" />
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
        <div v-if="directExpanded" class="grant-card__items">
          <GrantItemList :load-fn="p => listMyDirectPermissions(p)" />
        </div>
      </article>
    </section>

    <section class="domain-block">
      <button class="history-toggle" type="button" @click="toggleHistory">
        <el-icon class="history-toggle__icon"><Histogram /></el-icon>
        <span class="history-toggle__label">{{ t('dataPerm.historySection') }}</span>
        <span v-if="historyLoaded" class="history-toggle__count">{{ historyTotal }}</span>
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
                {{ g.kind === 'ROLE' ? g.bundleName : t('dataPerm.directSection') }}
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
                  <span v-for="a in permLabels(it)" :key="a" class="access-chip">{{ a }}</span>
                </div>
              </div>
            </div>
          </article>
        </div>
        <el-pagination v-if="historyTotal > historyPageSize" small layout="prev, pager, next"
                       :total="historyTotal" :page-size="historyPageSize" :current-page="historyPage"
                       class="history-pagination" @current-change="loadHistoryPage" />
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

// 历史(已失效)折叠区的扁平权限行;当前态列表已抽到 GrantItemList。
.items-row {
  display: grid;
  grid-template-columns: minmax(180px, 240px) minmax(0, 1fr) minmax(0, 1.5fr);
  gap: 16px;
  align-items: start;
  padding: 6px 8px;
  border-top: 1px dashed var(--r-border-light);
  font-size: var(--r-font-sm);
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
.history-pagination { justify-content: center; margin-top: var(--r-space-3); }
</style>
