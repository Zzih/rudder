<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { listPublishRecords, executePublish } from '@/api/workflow'
import { formatDate, relativeTime as relativeTimeUtil } from '@/utils/dateFormat'
import { usePagination } from '@/composables/usePagination'

const { t } = useI18n()
const route = useRoute()
const workspaceId = computed(() => Number(route.params.workspaceId))
const projectCode = computed(() => route.params.projectCode as string)

const statusFilter = ref('')

interface BatchRow {
  batchCode: string
  publishType: string
  status: string
  remark: string
  createdAt: string
  publishedAt: string | null
  expanded: boolean
  workflows: { name: string; versionNo: number; status: string }[]
}

const {
  data: batchRows,
  total,
  pageNum,
  pageSize,
  loading,
  appeared,
  fetch: loadRecords,
  handlePageChange,
  handleSizeChange,
  resetAndFetch,
} = usePagination<BatchRow>({
  fetchApi: (params) => listPublishRecords(workspaceId.value, projectCode.value, {
    status: statusFilter.value || undefined,
    ...params,
  }),
  extractData: (res: any) => {
    const list: any[] = res.data ?? []
    return list.map(batch => ({
      batchCode: String(batch.batchCode),
      publishType: batch.publishType,
      status: batch.status,
      remark: batch.remark,
      createdAt: batch.createdAt,
      publishedAt: batch.publishedAt,
      expanded: false,
      workflows: (batch.workflows ?? []).map((wf: any) => ({
        name: wf.name,
        versionNo: wf.versionNo,
        status: wf.status,
      })),
    }))
  },
  animated: true,
})

function handleFilterChange() {
  resetAndFetch()
}

onMounted(loadRecords)

function toggleExpand(row: BatchRow) {
  row.expanded = !row.expanded
}

const statusMeta: Record<string, { color: string; bg: string; border: string; glow: string; label: string }> = {
  PUBLISHED:        { color: 'var(--r-success)', bg: 'var(--r-success-bg)', border: 'var(--r-success-border)', glow: 'rgba(5,150,105,0.08)', label: 'project.statusPublished' },
  APPROVED:         { color: 'var(--r-accent-hover)', bg: 'var(--r-accent-bg)', border: 'var(--r-accent)', glow: 'rgba(37,99,235,0.08)', label: 'project.statusApproved' },
  PENDING_APPROVAL: { color: 'var(--r-warning)', bg: 'var(--r-warning-bg)', border: 'var(--r-warning-border)', glow: 'rgba(217,119,6,0.08)', label: 'project.statusPending' },
  REJECTED:         { color: 'var(--r-danger)', bg: 'var(--r-danger-bg)', border: 'var(--r-danger-border)', glow: 'rgba(220,38,38,0.08)', label: 'project.statusRejected' },
  PUBLISH_FAILED:   { color: 'var(--r-danger)', bg: 'var(--r-danger-bg)', border: 'var(--r-danger-border)', glow: 'rgba(220,38,38,0.08)', label: 'project.statusFailed' },
}

const publishing = ref<Set<string>>(new Set())

async function handleExecutePublish(row: BatchRow) {
  try {
    await ElMessageBox.confirm(t('project.executePublishConfirm'), t('project.executePublish'), {
      confirmButtonText: t('project.executePublish'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
  } catch {
    return
  }

  publishing.value.add(row.batchCode)
  try {
    await executePublish(workspaceId.value, projectCode.value, row.batchCode)
    ElMessage.success(t('common.success'))
    loadRecords()
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    publishing.value.delete(row.batchCode)
  }
}

function getMeta(status: string) {
  return statusMeta[status] ?? { color: 'var(--r-text-tertiary)', bg: 'var(--r-bg-panel)', border: 'var(--r-border)', glow: 'transparent', label: status }
}

function relativeTime(d: string | null) {
  return relativeTimeUtil(d, t('project.justNow'))
}
</script>

<template>
  <div class="pr-page">
    <!-- Header bar -->
    <div class="pr-toolbar">
      <div class="pr-toolbar__left">
        <span class="pr-toolbar__count" v-if="total > 0">{{ total }}</span>
      </div>
      <div class="pr-toolbar__right">
        <div class="pr-filter-group">
          <button
            v-for="opt in [
              { value: '', label: t('common.all') },
              { value: 'PENDING_APPROVAL', label: t('project.statusPending') },
              { value: 'APPROVED', label: t('project.statusApproved') },
              { value: 'PUBLISHED', label: t('project.statusPublished') },
              { value: 'REJECTED', label: t('project.statusRejected') },
            ]"
            :key="opt.value"
            :class="['pr-filter-btn', { 'pr-filter-btn--active': statusFilter === opt.value }]"
            @click="statusFilter = opt.value; handleFilterChange()"
          >
            <span
              v-if="opt.value"
              class="pr-filter-dot"
              :style="{ background: getMeta(opt.value).color }"
            />
            {{ opt.label }}
          </button>
        </div>
        <button class="pr-refresh-btn" @click="() => loadRecords()" :disabled="loading">
          <el-icon :class="{ 'is-loading': loading }"><Refresh /></el-icon>
        </button>
      </div>
    </div>

    <!-- Loading skeleton -->
    <div v-if="loading && batchRows.length === 0" class="pr-skeleton">
      <div v-for="i in 4" :key="i" class="pr-skeleton__card">
        <div class="pr-skeleton__row">
          <div class="pr-skeleton__bar pr-skeleton__bar--tag" />
          <div class="pr-skeleton__bar pr-skeleton__bar--pill" />
        </div>
        <div class="pr-skeleton__bar pr-skeleton__bar--long" />
        <div class="pr-skeleton__bar pr-skeleton__bar--medium" />
      </div>
    </div>

    <!-- Timeline + Card list -->
    <div v-else-if="batchRows.length" class="pr-timeline">
      <div class="pr-timeline__line" />

      <div
        v-for="(row, idx) in batchRows"
        :key="row.batchCode"
        :class="['pr-timeline__item', { 'pr-timeline__item--appeared': appeared }]"
        :style="{ '--delay': idx * 60 + 'ms' }"
      >
        <!-- Timeline node -->
        <div class="pr-timeline__node">
          <div
            class="pr-timeline__dot"
            :style="{ background: getMeta(row.status).color, boxShadow: '0 0 0 4px ' + getMeta(row.status).glow }"
          />
        </div>

        <!-- Card -->
        <div class="pr-card" :style="{ '--accent': getMeta(row.status).color }">
          <div class="pr-card__body" @click="toggleExpand(row)">
            <div class="pr-card__main">
              <!-- Row 1: Type + Status + Actions -->
              <div class="pr-card__top">
                <span
                  :class="['pr-type-badge', row.publishType === 'PROJECT' ? 'pr-type-badge--project' : 'pr-type-badge--workflow']"
                >
                  <svg v-if="row.publishType === 'PROJECT'" width="12" height="12" viewBox="0 0 16 16" fill="none">
                    <path d="M2 4.5L8 1.5L14 4.5V11.5L8 14.5L2 11.5V4.5Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
                    <path d="M2 4.5L8 7.5M8 7.5L14 4.5M8 7.5V14.5" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
                  </svg>
                  <svg v-else width="12" height="12" viewBox="0 0 16 16" fill="none">
                    <path d="M3 8L7 12L13 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                  {{ row.publishType === 'PROJECT' ? t('project.publishTypeProject') : t('project.publishTypeWorkflow') }}
                </span>

                <span
                  class="pr-status-pill"
                  :style="{
                    color: getMeta(row.status).color,
                    background: getMeta(row.status).bg,
                    borderColor: getMeta(row.status).border,
                  }"
                >
                  <span
                    :class="['pr-status-dot', { 'pr-status-dot--pulse': row.status === 'PENDING_APPROVAL' }]"
                    :style="{ background: getMeta(row.status).color }"
                  />
                  {{ t(getMeta(row.status).label) }}
                </span>

                <span class="pr-time" :title="formatDate(row.createdAt)">
                  {{ relativeTime(row.createdAt) }}
                </span>

                <button
                  v-if="row.status === 'APPROVED'"
                  class="pr-publish-btn"
                  :disabled="publishing.has(row.batchCode)"
                  @click.stop="handleExecutePublish(row)"
                >
                  <svg v-if="!publishing.has(row.batchCode)" width="14" height="14" viewBox="0 0 16 16" fill="none">
                    <path d="M8 2v8M4 6l4-4 4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
                    <path d="M3 12h10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
                  </svg>
                  <el-icon v-else class="is-loading"><Refresh /></el-icon>
                  {{ t('project.executePublish') }}
                </button>
              </div>

              <!-- Row 2: Workflow chips -->
              <div class="pr-card__workflows">
                <span
                  v-for="(wf, i) in row.workflows.slice(0, 6)"
                  :key="i"
                  class="pr-wf-chip"
                >
                  <span class="pr-wf-chip__name">{{ wf.name }}</span>
                  <span class="pr-wf-chip__ver">v{{ wf.versionNo }}</span>
                </span>
                <span v-if="row.workflows.length > 6" class="pr-wf-more">
                  +{{ row.workflows.length - 6 }}
                </span>
              </div>

              <!-- Row 3: Remark -->
              <div v-if="row.remark" class="pr-card__remark">
                {{ row.remark }}
              </div>
            </div>

            <!-- Meta dates -->
            <div class="pr-card__meta">
              <div class="pr-meta-item">
                <span class="pr-meta-label">{{ t('common.createdAt') }}</span>
                <span class="pr-meta-value">{{ formatDate(row.createdAt) }}</span>
              </div>
              <div v-if="row.publishedAt" class="pr-meta-item pr-meta-item--highlight">
                <span class="pr-meta-label">{{ t('project.publishedAt') }}</span>
                <span class="pr-meta-value">{{ formatDate(row.publishedAt) }}</span>
              </div>
            </div>

            <!-- Expand indicator -->
            <div class="pr-card__chevron" :class="{ 'pr-card__chevron--open': row.expanded }">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <path d="M4 6L8 10L12 6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
          </div>

          <!-- Expand detail -->
          <transition name="pr-expand">
            <div v-if="row.expanded" class="pr-card__detail">
              <div class="pr-detail-grid">
                <div
                  v-for="(wf, i) in row.workflows"
                  :key="i"
                  class="pr-detail-item"
                >
                  <div class="pr-detail-item__left">
                    <span
                      class="pr-detail-indicator"
                      :style="{ background: getMeta(wf.status).color }"
                    />
                    <span class="pr-detail-name">{{ wf.name }}</span>
                  </div>
                  <div class="pr-detail-item__right">
                    <span class="pr-detail-ver">v{{ wf.versionNo }}</span>
                    <span
                      class="pr-detail-status"
                      :style="{
                        color: getMeta(wf.status).color,
                        background: getMeta(wf.status).bg,
                        borderColor: getMeta(wf.status).border,
                      }"
                    >
                      {{ t(getMeta(wf.status).label) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </transition>
        </div>
      </div>
    </div>

    <!-- Empty state -->
    <div v-else-if="!loading" class="pr-empty">
      <div class="pr-empty__visual">
        <svg width="80" height="80" viewBox="0 0 80 80" fill="none">
          <rect x="14" y="20" width="52" height="44" rx="6" stroke="#d1d5db" stroke-width="1.5" fill="#fafafa"/>
          <rect x="20" y="28" width="24" height="3" rx="1.5" fill="#e5e7eb"/>
          <rect x="20" y="35" width="36" height="3" rx="1.5" fill="#e5e7eb"/>
          <rect x="20" y="42" width="16" height="3" rx="1.5" fill="#e5e7eb"/>
          <circle cx="58" cy="22" r="10" fill="#f3f4f6" stroke="#d1d5db" stroke-width="1.5"/>
          <path d="M55 22h6M58 19v6" stroke="#9ca3af" stroke-width="1.5" stroke-linecap="round"/>
          <rect x="14" y="52" width="52" height="2" rx="1" fill="#f3f4f6"/>
          <rect x="14" y="57" width="52" height="2" rx="1" fill="#f3f4f6"/>
        </svg>
      </div>
      <p class="pr-empty__text">{{ t('common.noData') }}</p>
      <p class="pr-empty__hint">{{ t('project.emptyHint') }}</p>
    </div>

    <!-- Pagination -->
    <div v-if="total > pageSize" class="pr-pagination">
      <el-pagination
        background
        :current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
/* ── Page layout ── */
.pr-page {
  padding: 20px 24px;
  min-height: 100%;
}

/* ── Toolbar ── */
.pr-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  flex-wrap: wrap;
  gap: 12px;
}

.pr-toolbar__left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pr-toolbar__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 11px;
  background: var(--r-bg-hover);
  color: var(--r-text-tertiary);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.pr-toolbar__right {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ── Filter button group ── */
.pr-filter-group {
  display: flex;
  background: var(--r-bg-hover);
  border-radius: 10px;
  padding: 3px;
  gap: 2px;
}

.pr-filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border: none;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  color: var(--r-text-tertiary);
  background: transparent;
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;

  &:hover {
    color: var(--r-text-secondary);
    background: var(--r-bg-hover);
  }

  &--active {
    color: var(--r-text-primary);
    background: var(--r-bg-card);
    box-shadow: var(--r-shadow-sm);
    font-weight: 600;
  }
}

.pr-filter-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.pr-refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--r-border);
  border-radius: 10px;
  background: var(--r-bg-card);
  color: var(--r-text-tertiary);
  cursor: pointer;
  transition: all 0.18s ease;

  &:hover {
    border-color: var(--r-border-dark);
    color: var(--r-text-secondary);
    background: var(--r-bg-panel);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

/* ── Skeleton loading ── */
.pr-skeleton {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-left: 40px;
}

.pr-skeleton__card {
  background: var(--r-bg-card);
  border: 1px solid var(--r-bg-hover);
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pr-skeleton__row {
  display: flex;
  gap: 10px;
}

.pr-skeleton__bar {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--r-bg-hover) 25%, var(--r-border) 50%, var(--r-bg-hover) 75%);
  background-size: 200% 100%;
  animation: pr-shimmer 1.5s infinite;

  &--tag { width: 80px; height: 24px; border-radius: 6px; }
  &--pill { width: 72px; height: 24px; border-radius: 12px; }
  &--long { width: 55%; }
  &--medium { width: 30%; }
}

@keyframes pr-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ── Timeline layout ── */
.pr-timeline {
  position: relative;
  padding-left: 40px;
}

.pr-timeline__line {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 15px;
  width: 2px;
  background: linear-gradient(to bottom, var(--r-border) 0%, var(--r-border) 90%, transparent 100%);
  border-radius: 1px;
}

.pr-timeline__item {
  position: relative;
  display: flex;
  align-items: flex-start;
  margin-bottom: 14px;
  opacity: 0;
  transform: translateY(10px);

  &--appeared {
    animation: pr-slideIn 0.35s cubic-bezier(0.16, 1, 0.3, 1) forwards;
    animation-delay: var(--delay);
  }
}

@keyframes pr-slideIn {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.pr-timeline__node {
  position: absolute;
  left: -40px;
  top: 20px;
  width: 30px;
  display: flex;
  justify-content: center;
}

.pr-timeline__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

/* ── Card ── */
.pr-card {
  flex: 1;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 12px;
  overflow: hidden;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    border-color: var(--r-border-dark);
    box-shadow: var(--r-shadow-md);
  }
}

.pr-card__body {
  display: flex;
  align-items: flex-start;
  padding: 18px 22px;
  cursor: pointer;
  user-select: none;
  gap: 16px;
  border-left: 3px solid var(--accent, var(--r-border));
  transition: border-color 0.2s;
}

.pr-card__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pr-card__top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ── Type badge ── */
.pr-type-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;

  &--project {
    color: var(--r-accent-hover);
    background: var(--r-accent-bg);
  }

  &--workflow {
    color: var(--r-text-secondary);
    background: var(--r-bg-hover);
  }

  svg { flex-shrink: 0; }
}

/* ── Status pill ── */
.pr-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid;
}

.pr-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;

  &--pulse {
    animation: pr-statusPulse 2s ease-in-out infinite;
  }
}

@keyframes pr-statusPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.pr-time {
  font-size: 12px;
  color: var(--r-text-muted);
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

/* ── Publish button ── */
.pr-publish-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 16px;
  border: none;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;
  margin-left: 4px;
  box-shadow: 0 1px 3px rgba(37, 99, 235, 0.3);

  &:hover {
    background: linear-gradient(135deg, #1d4ed8 0%, #1e40af 100%);
    box-shadow: 0 2px 6px rgba(37, 99, 235, 0.35);
    transform: translateY(-0.5px);
  }

  &:active {
    transform: translateY(0);
    box-shadow: 0 1px 2px rgba(37, 99, 235, 0.3);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
    transform: none;
  }
}

/* ── Workflow chips ── */
.pr-card__workflows {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.pr-wf-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border);
  border-radius: 8px;
  font-size: 13px;
  transition: all 0.15s ease;

  .pr-card:hover & {
    background: var(--r-bg-hover);
    border-color: var(--r-border-dark);
  }
}

.pr-wf-chip__name {
  font-weight: 500;
  color: var(--r-text-primary);
}

.pr-wf-chip__ver {
  font-size: 11px;
  font-weight: 600;
  color: var(--r-text-muted);
  font-variant-numeric: tabular-nums;
  font-family: var(--r-font-mono);
}

.pr-wf-more {
  font-size: 12px;
  color: var(--r-text-muted);
  font-weight: 500;
  padding: 4px 8px;
}

/* ── Remark ── */
.pr-card__remark {
  font-size: 13px;
  color: var(--r-text-tertiary);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 640px;
}

/* ── Meta (dates) ── */
.pr-card__meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
  text-align: right;
  padding-top: 2px;
}

.pr-meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;

  &--highlight .pr-meta-value {
    color: var(--r-success);
    font-weight: 500;
  }
}

.pr-meta-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--r-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.pr-meta-value {
  font-size: 12px;
  color: var(--r-text-tertiary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* ── Chevron expand icon ── */
.pr-card__chevron {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: var(--r-text-muted);
  flex-shrink: 0;
  margin-top: 2px;
  transition: all 0.2s ease;

  .pr-card__body:hover & {
    background: var(--r-bg-hover);
    color: var(--r-text-tertiary);
  }

  &--open {
    transform: rotate(180deg);
  }
}

/* ── Expand detail panel ── */
.pr-card__detail {
  border-top: 1px solid var(--r-bg-hover);
  background: var(--r-bg-panel);
  padding: 16px 22px;
}

.pr-expand-enter-active,
.pr-expand-leave-active {
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  overflow: hidden;
}

.pr-expand-enter-from,
.pr-expand-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.pr-expand-enter-to,
.pr-expand-leave-from {
  opacity: 1;
  max-height: 500px;
}

.pr-detail-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pr-detail-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 10px;
  transition: all 0.15s ease;

  &:hover {
    border-color: var(--r-border-dark);
    box-shadow: var(--r-shadow-sm);
  }
}

.pr-detail-item__left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.pr-detail-indicator {
  width: 3px;
  height: 20px;
  border-radius: 2px;
  flex-shrink: 0;
}

.pr-detail-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--r-text-primary);
}

.pr-detail-item__right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.pr-detail-ver {
  font-size: 12px;
  font-weight: 600;
  color: var(--r-text-muted);
  font-variant-numeric: tabular-nums;
  font-family: var(--r-font-mono);
}

.pr-detail-status {
  display: inline-flex;
  padding: 2px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
  border: 1px solid;
}

/* ── Empty state ── */
.pr-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100px 20px 80px;
}

.pr-empty__visual {
  margin-bottom: 20px;
  opacity: 0.7;
}

.pr-empty__text {
  font-size: 14px;
  font-weight: 600;
  color: var(--r-text-tertiary);
  margin: 0 0 6px 0;
}

.pr-empty__hint {
  font-size: 13px;
  color: var(--r-text-muted);
  margin: 0;
}

/* ── Pagination ── */
.pr-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 20px 0 8px;
  margin-left: 40px;
}
</style>
