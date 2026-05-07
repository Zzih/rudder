<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { pageApprovals, approveApproval, rejectApproval } from '@/api/approval'
import { formatDate, relativeTime as relativeTimeUtil } from '@/utils/dateFormat'
import { colorMeta } from '@/utils/colorMeta'
import { usePagination } from '@/composables/usePagination'
import { usePermission } from '@/composables/usePermission'

const { t } = useI18n()
const { canEdit } = usePermission()

interface ApprovalRecord {
  id: number
  channel: string
  title: string
  description: string
  submitRemark: string
  status: string
  approvalLevel: string
  approver: string
  remark: string
  projectApprover: string
  projectApprovedAt: string
  resolvedAt: string
  createdAt: string
  workflows: string[]
}

const statusMeta: Record<string, { color: string; bg: string; border: string; label: string }> = {
  PENDING:  { ...colorMeta('#f59e0b'), label: 'approval.pending' },
  APPROVED: { ...colorMeta('#10b981'), label: 'approval.approved' },
  REJECTED: { ...colorMeta('#ef4444'), label: 'approval.rejected' },
}

const statusOptions = [
  { value: '', label: 'common.all' },
  { value: 'PENDING', label: 'approval.pending' },
  { value: 'APPROVED', label: 'approval.approved' },
  { value: 'REJECTED', label: 'approval.rejected' },
]

const statusFilter = ref('')

const {
  data: approvals,
  total,
  pageNum,
  pageSize,
  loading,
  appeared,
  fetch: fetchApprovals,
  handlePageChange,
  handleSizeChange,
  resetAndFetch,
} = usePagination<ApprovalRecord>({
  fetchApi: (params) => {
    const p: Record<string, unknown> = { ...params }
    if (statusFilter.value) {
      p.status = statusFilter.value
    }
    return pageApprovals(p as never)
  },
  extractData: (res: any) => {
    const records = (res.data?.records as ApprovalRecord[]) || []
    records.forEach(r => { r.workflows = parseWorkflows(r.description) })
    return records
  },
  extractTotal: (res: any) => (res.data?.total as number) || 0,
  defaultPageSize: 10,
  animated: true,
})

const actionDialogVisible = ref(false)
const actionType = ref<'approve' | 'reject'>('approve')
const actionId = ref(0)
const actionComment = ref('')
const actionLoading = ref(false)

watch(statusFilter, () => {
  resetAndFetch()
})

function openAction(id: number, type: 'approve' | 'reject') {
  actionId.value = id
  actionType.value = type
  actionComment.value = ''
  actionDialogVisible.value = true
}

async function doAction() {
  actionLoading.value = true
  try {
    if (actionType.value === 'approve') {
      await approveApproval(actionId.value, actionComment.value || undefined)
    } else {
      await rejectApproval(actionId.value, actionComment.value || undefined)
    }
    ElMessage.success(t('common.success'))
    actionDialogVisible.value = false
    fetchApprovals()
  } catch { /* interceptor */ }
  finally { actionLoading.value = false }
}

function relativeTime(d: string) {
  return relativeTimeUtil(d, t('project.justNow'))
}

function getMeta(status: string) {
  return statusMeta[status] ?? { ...colorMeta('#64748b'), label: status }
}

function isProjectPublish(title: string): boolean {
  return title?.startsWith('Project Publish') ?? false
}

function getPublishLabel(title: string): string {
  const name = title?.replace(/^(Project|Workflow) Publish:\s*/, '') || title
  const key = title?.startsWith('Project Publish') ? 'approval.projectName' : 'approval.workflowName'
  return t(key) + name
}

function parseWorkflows(desc: string): string[] {
  if (!desc) return []
  const items = desc.split('\n').filter(l => l.trimStart().startsWith('- ')).map(l => l.trimStart().substring(2).trim())
  if (items.length) return items
  const m = desc.match(/\[(.+?)]\s*(v\d+)/)
  if (m) return [`${m[1]} (${m[2]})`]
  return []
}

function getStep1State(row: ApprovalRecord): 'done' | 'active' | 'rejected' | 'waiting' {
  if (row.status === 'REJECTED' && row.approvalLevel === 'PROJECT_OWNER') return 'rejected'
  if (row.approvalLevel === 'PROJECT_OWNER' && row.status === 'PENDING') return 'active'
  return 'done'
}
function getStep2State(row: ApprovalRecord): 'done' | 'active' | 'rejected' | 'waiting' {
  if (row.status === 'APPROVED') return 'done'
  if (row.status === 'REJECTED' && row.approvalLevel === 'WORKSPACE_OWNER') return 'rejected'
  if (row.approvalLevel === 'WORKSPACE_OWNER' && row.status === 'PENDING') return 'active'
  return 'waiting'
}

onMounted(fetchApprovals)
</script>

<template>
  <div class="ap-page">
    <!-- Toolbar -->
    <div class="ap-toolbar">
      <div class="ap-toolbar__left">
        <h2 class="ap-toolbar__title">{{ t('approval.title') }}</h2>
        <span v-if="total > 0" class="ap-toolbar__count">{{ total }}</span>
      </div>
      <div class="ap-toolbar__right">
        <div class="ap-filter-group">
          <button
            v-for="opt in statusOptions"
            :key="opt.value"
            :class="['ap-filter-btn', { 'ap-filter-btn--active': statusFilter === opt.value }]"
            @click="statusFilter = opt.value"
          >
            <span v-if="opt.value" class="ap-filter-dot" :style="{ background: getMeta(opt.value).color }" />
            {{ t(opt.label) }}
          </button>
        </div>
        <button class="ap-refresh-btn" @click="() => fetchApprovals()" :disabled="loading">
          <el-icon :class="{ 'is-loading': loading }"><Refresh /></el-icon>
        </button>
      </div>
    </div>

    <!-- Loading skeleton -->
    <div v-if="loading && approvals.length === 0" class="ap-skeleton">
      <div v-for="i in 5" :key="i" class="ap-skel-card">
        <div class="ap-skel-row"><div class="ap-skel ap-skel--sm" /><div class="ap-skel ap-skel--pill" /><div class="ap-skel ap-skel--tag" /></div>
        <div class="ap-skel ap-skel--title" />
        <div class="ap-skel-row"><div class="ap-skel ap-skel--steps" /><div style="flex:1" /><div class="ap-skel ap-skel--btn" /></div>
      </div>
    </div>

    <!-- Card list -->
    <div v-else-if="approvals.length" class="ap-list">
      <div
        v-for="(row, idx) in approvals"
        :key="row.id"
        :class="['ap-card', { 'ap-card--appeared': appeared }]"
        :style="{ '--delay': idx * 35 + 'ms', '--accent': getMeta(row.status).color }"
      >
        <!-- Main content area (left) + Actions area (right) -->
        <div class="ap-card__body">
          <div class="ap-card__content">
            <!-- Line 1: badges -->
            <div class="ap-card__badges">
              <span class="ap-card__id">#{{ row.id }}</span>
              <span :class="['ap-type-tag', isProjectPublish(row.title) ? 'ap-type-tag--project' : 'ap-type-tag--workflow']">
                {{ isProjectPublish(row.title) ? t('approval.projectPublish') : t('approval.workflowPublish') }}
              </span>
              <span
                class="ap-pill"
                :style="{ color: getMeta(row.status).color, background: getMeta(row.status).bg, borderColor: getMeta(row.status).border }"
              >
                <span :class="['ap-pill__dot', { 'ap-pill__dot--pulse': row.status === 'PENDING' }]" :style="{ background: getMeta(row.status).color }" />
                {{ t(getMeta(row.status).label) }}
              </span>
              <span class="ap-tag">{{ row.channel }}</span>
              <span class="ap-card__time" :title="formatDate(row.createdAt)">{{ relativeTime(row.createdAt) }}</span>
            </div>

            <!-- Line 2: title (with label prefix) -->
            <div class="ap-card__title" :title="row.title">{{ getPublishLabel(row.title) }}</div>

            <!-- Line 3: workflow chips (parsed from description) -->
            <div v-if="row.workflows.length" class="ap-card__workflows">
              <span v-for="wf in row.workflows" :key="wf" class="ap-wf-chip">{{ wf }}</span>
            </div>

            <!-- Line 4: publish reason (submitter's remark) -->
            <div v-if="row.submitRemark" class="ap-card__remark">
              <span class="ap-card__remark-label">{{ t('approval.publishReason') }}</span>
              <span class="ap-card__remark-text">{{ row.submitRemark }}</span>
            </div>

            <!-- Line 5: approval comment (approver's remark, shown when resolved) -->
            <div v-if="row.remark && row.status !== 'PENDING'" class="ap-card__remark">
              <span class="ap-card__remark-label">{{ t('approval.remark') }}</span>
              <span class="ap-card__remark-text">{{ row.remark }}</span>
            </div>
          </div>

          <!-- Right: steps + date + actions -->
          <div class="ap-card__aside">
            <!-- Step flow -->
            <div class="ap-flow">
              <div :class="['ap-node', 'ap-node--' + getStep1State(row)]">
                <div class="ap-node__circle">
                  <svg v-if="getStep1State(row) === 'done'" width="10" height="10" viewBox="0 0 16 16" fill="none"><path d="M3 8.5L6.5 12L13 4" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                  <svg v-else-if="getStep1State(row) === 'rejected'" width="10" height="10" viewBox="0 0 16 16" fill="none"><path d="M4 4L12 12M12 4L4 12" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/></svg>
                  <span v-else>1</span>
                </div>
                <span class="ap-node__text">{{ t('approval.stepProject') }}</span>
                <span v-if="row.projectApprover" class="ap-node__who">{{ row.projectApprover }}</span>
              </div>
              <div class="ap-flow__line" :class="{ 'ap-flow__line--done': getStep1State(row) === 'done' }" />
              <div :class="['ap-node', 'ap-node--' + getStep2State(row)]">
                <div class="ap-node__circle">
                  <svg v-if="getStep2State(row) === 'done'" width="10" height="10" viewBox="0 0 16 16" fill="none"><path d="M3 8.5L6.5 12L13 4" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                  <svg v-else-if="getStep2State(row) === 'rejected'" width="10" height="10" viewBox="0 0 16 16" fill="none"><path d="M4 4L12 12M12 4L4 12" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/></svg>
                  <span v-else>2</span>
                </div>
                <span class="ap-node__text">{{ t('approval.stepWorkspace') }}</span>
                <span v-if="row.approver && row.status !== 'PENDING'" class="ap-node__who">{{ row.approver }}</span>
              </div>
            </div>

            <!-- Date + Actions -->
            <div class="ap-card__footer">
              <span class="ap-card__date" :title="formatDate(row.createdAt)">{{ formatDate(row.createdAt) }}</span>
              <div v-if="canEdit && row.status === 'PENDING' && row.channel === 'LOCAL'" class="ap-card__actions">
                <button class="ap-btn ap-btn--approve" @click="openAction(row.id, 'approve')">
                  <svg width="12" height="12" viewBox="0 0 16 16" fill="none"><path d="M3 8.5L6.5 12L13 4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                  {{ t('approval.approve') }}
                </button>
                <button class="ap-btn ap-btn--reject" @click="openAction(row.id, 'reject')">
                  <svg width="12" height="12" viewBox="0 0 16 16" fill="none"><path d="M4 4L12 12M12 4L4 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                  {{ t('approval.reject') }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty state -->
    <div v-else-if="!loading" class="ap-empty">
      <svg width="56" height="56" viewBox="0 0 72 72" fill="none">
        <rect x="12" y="16" width="48" height="40" rx="6" stroke="#cbd5e1" stroke-width="1.5" fill="#f8fafc"/>
        <circle cx="36" cy="32" r="8" stroke="#cbd5e1" stroke-width="1.5" fill="#f1f5f9"/>
        <path d="M33 32l2 2 4-4" stroke="#94a3b8" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        <rect x="22" y="44" width="28" height="2.5" rx="1.25" fill="#e2e8f0"/>
      </svg>
      <p class="ap-empty__text">{{ t('approval.noApprovals') }}</p>
      <p class="ap-empty__hint">{{ t('approval.emptyHint') }}</p>
    </div>

    <!-- Pagination -->
    <div v-if="total > 0" class="ap-pagination">
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

    <!-- Action Dialog -->
    <el-dialog
      v-model="actionDialogVisible"
      :title="actionType === 'approve' ? t('approval.confirmApprove') : t('approval.confirmReject')"
      width="420px" destroy-on-close
    >
      <el-input
        v-model="actionComment"
        type="textarea"
        :rows="3"
        :placeholder="actionType === 'reject' ? t('approval.rejectReasonPlaceholder') : t('approval.commentPlaceholder')"
      />
      <template #footer>
        <el-button @click="actionDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          :type="actionType === 'approve' ? 'primary' : 'danger'"
          :loading="actionLoading"
          :disabled="actionType === 'reject' && !actionComment.trim()"
          @click="doAction"
        >
          {{ actionType === 'approve' ? t('approval.approve') : t('approval.reject') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.ap-page {
  padding: 20px 24px;
  min-height: 100%;
}

/* ── Toolbar ── */
.ap-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}

.ap-toolbar__left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ap-toolbar__title {
  font-size: 16px;
  font-weight: 700;
  color: var(--r-text-primary);
  margin: 0;
  letter-spacing: -0.02em;
}

.ap-toolbar__count {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--r-bg-hover);
  color: var(--r-text-muted);
  font-size: 11px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-variant-numeric: tabular-nums;
}

.ap-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ap-filter-group {
  display: flex;
  background: var(--r-bg-hover);
  border-radius: 8px;
  padding: 2px;
  gap: 1px;
}

.ap-filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--r-text-muted);
  background: transparent;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;

  &:hover { color: var(--r-text-secondary); background: var(--r-bg-hover); }
  &--active {
    color: var(--r-text-primary);
    background: var(--r-bg-card);
    box-shadow: var(--r-shadow-sm);
    font-weight: 600;
  }
}

.ap-filter-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ap-refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--r-border);
  border-radius: 8px;
  background: var(--r-bg-card);
  color: var(--r-text-muted);
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover { border-color: var(--r-border-dark); color: var(--r-text-secondary); background: var(--r-bg-panel); }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

/* ── Skeleton ── */
.ap-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ap-skel-card {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: 10px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ap-skel-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ap-skel {
  border-radius: 6px;
  background: linear-gradient(90deg, var(--r-bg-hover) 25%, var(--r-border) 50%, var(--r-bg-hover) 75%);
  background-size: 200% 100%;
  animation: ap-shimmer 1.5s infinite;

  &--sm { width: 24px; height: 14px; }
  &--pill { width: 60px; height: 20px; border-radius: 10px; }
  &--tag { width: 48px; height: 18px; }
  &--title { width: 50%; height: 14px; }
  &--steps { width: 180px; height: 22px; border-radius: 11px; }
  &--btn { width: 100px; height: 26px; border-radius: 6px; }
}

@keyframes ap-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ── Card list ── */
.ap-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* ── Card ── */
.ap-card {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-left: 3px solid var(--accent, var(--r-border));
  border-radius: 10px;
  padding: 14px 18px;
  transition: border-color 0.15s, box-shadow 0.15s;
  opacity: 0;
  transform: translateY(4px);

  &--appeared {
    animation: ap-in 0.22s ease forwards;
    animation-delay: var(--delay);
  }

  &:hover {
    border-color: var(--r-border-dark);
    border-left-color: var(--accent);
    box-shadow: var(--r-shadow-sm);
  }
}

@keyframes ap-in {
  to { opacity: 1; transform: translateY(0); }
}

/* ── Card body: left content + right aside ── */
.ap-card__body {
  display: flex;
  gap: 24px;
  align-items: stretch;
}

/* Left: content area (flexible) */
.ap-card__content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* Right: steps + footer (fixed width) */
.ap-card__aside {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  gap: 8px;
  min-width: 260px;
}

/* ── Badges line ── */
.ap-card__badges {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ap-card__id {
  font-size: 11px;
  font-weight: 600;
  color: var(--r-text-disabled);
  font-variant-numeric: tabular-nums;
  font-family: var(--r-font-mono);
}

.ap-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
  border: 1px solid;
  line-height: 18px;
}

.ap-pill__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;

  &--pulse { animation: ap-pulse 2s ease-in-out infinite; }
}

@keyframes ap-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.ap-type-tag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  border: 1px solid;
  line-height: 18px;

  &--project {
    color: var(--r-purple);
    background: var(--r-purple-bg);
    border-color: var(--r-purple-border);
  }

  &--workflow {
    color: var(--r-cyan);
    background: var(--r-cyan-bg);
    border-color: var(--r-cyan-border);
  }
}

.ap-tag {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--r-text-muted);
  background: var(--r-bg-hover);
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.ap-card__time {
  font-size: 11px;
  color: var(--r-text-disabled);
  margin-left: 2px;
}

/* ── Title ── */
.ap-card__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--r-text-primary);
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Workflow chips ── */
.ap-card__workflows {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.ap-wf-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 11px;
  color: var(--r-text-secondary);
  background: var(--r-bg-hover);
  border: 1px solid var(--r-border);
  font-family: var(--r-font-mono);
  line-height: 18px;
}

/* ── Remark ── */
.ap-card__remark {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.ap-card__remark-label {
  font-size: 11px;
  color: var(--r-text-muted);
  white-space: nowrap;
  flex-shrink: 0;
}

.ap-card__remark-text {
  font-size: 12px;
  color: var(--r-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Step flow (horizontal) ── */
.ap-flow {
  display: flex;
  align-items: center;
  gap: 0;
}

.ap-node {
  display: flex;
  align-items: center;
  gap: 5px;
}

.ap-node__circle {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
  transition: all 0.15s;
}

.ap-node__text {
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.ap-node__who {
  font-size: 11px;
  color: var(--r-text-muted);
  white-space: nowrap;
}

/* Node states */
.ap-node--done {
  .ap-node__circle { background: var(--r-success); color: #fff; }
  .ap-node__text { color: var(--r-success); }
}
.ap-node--active {
  .ap-node__circle { background: var(--r-accent-hover); color: #fff; box-shadow: 0 0 0 3px rgba(37,99,235,0.1); }
  .ap-node__text { color: var(--r-accent-hover); }
}
.ap-node--rejected {
  .ap-node__circle { background: var(--r-danger); color: #fff; }
  .ap-node__text { color: var(--r-danger); }
}
.ap-node--waiting {
  .ap-node__circle { background: var(--r-bg-hover); color: var(--r-text-muted); border: 1.5px dashed var(--r-border-dark); }
  .ap-node__text { color: var(--r-text-disabled); }
}

.ap-flow__line {
  width: 20px;
  height: 2px;
  background: var(--r-border);
  border-radius: 1px;
  margin: 0 6px;
  flex-shrink: 0;

  &--done { background: var(--r-success); }
}

/* ── Card footer (date + actions) ── */
.ap-card__footer {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ap-card__date {
  font-size: 11px;
  color: var(--r-text-disabled);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.ap-card__actions {
  display: flex;
  gap: 6px;
}

.ap-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 14px;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;

  &--approve {
    color: #fff;
    background: var(--r-success);
    &:hover { background: var(--r-success); filter: brightness(0.9); transform: translateY(-0.5px); }
  }

  &--reject {
    color: var(--r-danger);
    background: var(--r-danger-bg);
    border: 1px solid var(--r-danger-border);
    &:hover { background: var(--r-danger-bg); border-color: var(--r-danger-border); transform: translateY(-0.5px); }
  }

  &:active { transform: translateY(0); }
}

/* ── Empty ── */
.ap-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px 40px;
  gap: 8px;
}

.ap-empty__text {
  font-size: 14px;
  font-weight: 600;
  color: var(--r-text-muted);
  margin: 0;
}

.ap-empty__hint {
  font-size: 12px;
  color: var(--r-text-muted);
  margin: 0;
}

/* ── Pagination ── */
.ap-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 14px 0 4px;
}
</style>
