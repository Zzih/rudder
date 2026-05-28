<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import {
  getApproval,
  approveApproval,
  rejectApproval,
  type ApprovalDetail,
  type ApprovalDecision,
} from '@/api/approval'
import { formatDate } from '@/utils/dateFormat'
import {
  STAGE_LABEL_KEY,
  TYPE_LABEL_KEY,
  getStatusMeta,
  getStageState as resolveStageState,
  type ApprovalStageState,
} from './labels'

const props = defineProps<{
  modelValue: boolean
  approvalId: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  /** 通过 / 拒绝后通知父组件刷新列表 */
  'resolved': []
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v),
})

const loading = ref(false)
const detail = ref<ApprovalDetail | null>(null)

const actionVisible = ref(false)
const actionType = ref<'approve' | 'reject'>('approve')
const actionComment = ref('')
const actionLoading = ref(false)


watch(() => [visible.value, props.approvalId], async ([v, id]) => {
  if (!v || id == null) {
    detail.value = null
    return
  }
  loading.value = true
  try {
    const res: any = await getApproval(id as number)
    detail.value = res.data as ApprovalDetail
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}, { immediate: true })

function getStageState(idx: number): ApprovalStageState {
  return detail.value ? resolveStageState(detail.value, idx) : 'waiting'
}

function decisionsOf(stage: string): ApprovalDecision[] {
  return (detail.value?.decisions ?? []).filter(d => d.stage === stage)
}

function candidatesOf(stage: string): string[] {
  const list = detail.value?.stageCandidates?.[stage] ?? []
  return list.map(c => c.username || `#${c.userId}`)
}

function openAction(type: 'approve' | 'reject') {
  actionType.value = type
  actionComment.value = ''
  actionVisible.value = true
}

async function submitAction() {
  if (!detail.value) return
  const id = detail.value.id
  actionLoading.value = true
  try {
    if (actionType.value === 'approve') {
      await approveApproval(id, actionComment.value)
    } else {
      await rejectApproval(id, actionComment.value)
    }
    ElMessage.success(t('common.success'))
    actionVisible.value = false
    emit('resolved')
    const res: any = await getApproval(id)
    detail.value = res.data as ApprovalDetail
  } catch { /* interceptor 已提示 */ } finally {
    actionLoading.value = false
  }
}

const canDecide = computed(() =>
  !!detail.value?.currentUserCanDecide
  && detail.value?.status === 'PENDING'
  && detail.value?.channel === 'LOCAL')
</script>

<template>
  <el-drawer v-model="visible" size="60%" :show-close="false"
    :close-on-click-modal="true" destroy-on-close class="apd"
    :data-status="detail?.status ?? 'PENDING'">
    <template #header>
      <div class="apd__head">
        <div class="apd__head-id">
          <span class="apd__head-kicker">{{ t('approval.detailTitle') }}</span>
          <span v-if="detail" class="apd__head-num">#{{ detail.id }}</span>
        </div>
        <div v-if="detail" class="apd__head-main">
          <div class="apd__head-row">
            <span class="apd__status-dot" :style="{ background: getStatusMeta(detail.status).color }" />
            <span class="apd__status-text" :style="{ color: getStatusMeta(detail.status).color }">
              {{ t(getStatusMeta(detail.status).label) }}
            </span>
            <span class="apd__head-sep" aria-hidden="true">·</span>
            <span class="apd__head-type">
              {{ t(TYPE_LABEL_KEY[detail.resourceType] ?? 'approval.unknownType') }}
            </span>
            <span class="apd__head-sep" aria-hidden="true">·</span>
            <span class="apd__head-channel">{{ detail.channel }}</span>
          </div>
          <h3 class="apd__title">{{ detail.title }}</h3>
        </div>
        <button class="apd__close" @click="visible = false" :aria-label="t('common.close')">
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </template>

    <div v-loading="loading" class="apd__body">
      <div v-if="detail" class="apd__sections">
        <section class="apd__section">
          <div class="apd__section-head">
            <span class="apd__section-mark" />
            <h4 class="apd__section-title">{{ t('approval.sectionBasic') }}</h4>
          </div>
          <dl class="apd__kv">
            <dt>{{ t('approval.applicant') }}</dt>
            <dd>{{ detail.createdByUsername || '-' }}</dd>

            <dt>{{ t('approval.workspace') }}</dt>
            <dd>{{ detail.workspaceName || '-' }}</dd>

            <template v-if="detail.projectCode">
              <dt>{{ t('approval.projectCode') }}</dt>
              <dd class="apd__kv-mono">{{ detail.projectCode }}</dd>
            </template>

            <template v-if="detail.resourceCode">
              <dt>{{ t('approval.resourceCode') }}</dt>
              <dd class="apd__kv-mono">{{ detail.resourceCode }}</dd>
            </template>

            <dt>{{ t('common.createdAt') }}</dt>
            <dd>{{ formatDate(detail.createdAt) }}</dd>

            <template v-if="detail.expiresAt">
              <dt>{{ t('approval.expiresAt') }}</dt>
              <dd>{{ formatDate(detail.expiresAt) }}</dd>
            </template>

            <template v-if="detail.resolvedAt">
              <dt>{{ t('approval.resolvedAt') }}</dt>
              <dd>{{ formatDate(detail.resolvedAt) }}</dd>
            </template>

            <template v-if="detail.withdrawnAt">
              <dt>{{ t('approval.withdrawnAt') }}</dt>
              <dd>{{ formatDate(detail.withdrawnAt) }}</dd>
            </template>

            <template v-if="detail.withdrawnReason">
              <dt>{{ t('approval.withdrawnReason') }}</dt>
              <dd>{{ detail.withdrawnReason }}</dd>
            </template>
          </dl>

          <div v-if="detail.submitRemark" class="apd__remark">
            <span class="apd__remark-label">{{ t('approval.publishReason') }}</span>
            <p class="apd__remark-text">{{ detail.submitRemark }}</p>
          </div>
        </section>

        <section v-if="detail.description" class="apd__section">
          <div class="apd__section-head">
            <span class="apd__section-mark" />
            <h4 class="apd__section-title">{{ t('approval.sectionDescription') }}</h4>
          </div>
          <pre class="apd__desc">{{ detail.description }}</pre>
        </section>

        <section class="apd__section">
          <div class="apd__section-head">
            <span class="apd__section-mark" />
            <h4 class="apd__section-title">{{ t('approval.sectionProgress') }}</h4>
          </div>
          <ol class="apd__timeline">
            <li v-for="(stage, i) in detail.stageChain ?? []" :key="stage"
              :class="['apd__node', 'apd__node--' + getStageState(i)]">
              <div class="apd__node-marker">
                <div class="apd__node-ring">
                  <span class="apd__node-num">{{ i + 1 }}</span>
                </div>
                <div v-if="i < (detail.stageChain ?? []).length - 1" class="apd__node-line" />
              </div>
              <div class="apd__node-body">
                <div class="apd__node-head">
                  <span class="apd__node-label">{{ t(STAGE_LABEL_KEY[stage] ?? stage) }}</span>
                  <span v-if="getStageState(i) === 'active'" class="apd__node-pill">
                    {{ t('approval.pending') }}
                  </span>
                </div>
                <ul v-if="decisionsOf(stage).length" class="apd__decisions">
                  <li v-for="d in decisionsOf(stage)" :key="d.id" class="apd__decision">
                    <span :class="['apd__decision-icon', d.decision === 'APPROVE' ? 'is-approve' : 'is-reject']">
                      <svg v-if="d.decision === 'APPROVE'" width="10" height="10" viewBox="0 0 16 16" fill="none">
                        <path d="M3 8.5L6.5 12L13 4" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                      <svg v-else width="10" height="10" viewBox="0 0 16 16" fill="none">
                        <path d="M4 4L12 12M12 4L4 12" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"/>
                      </svg>
                    </span>
                    <div class="apd__decision-body">
                      <div class="apd__decision-meta">
                        <span class="apd__decision-who">{{ d.deciderUsername ?? `#${d.deciderUserId}` }}</span>
                        <span class="apd__decision-act">
                          {{ d.decision === 'APPROVE' ? t('approval.decisionApprove') : t('approval.decisionReject') }}
                        </span>
                        <span class="apd__decision-time">{{ formatDate(d.decidedAt) }}</span>
                      </div>
                      <p v-if="d.remark" class="apd__decision-remark">{{ d.remark }}</p>
                    </div>
                  </li>
                </ul>
                <p v-else-if="candidatesOf(stage).length" class="apd__node-candidates">
                  <span class="apd__node-candidates-label">{{ t('approval.candidates') }}</span>
                  <span class="apd__node-candidates-list">{{ candidatesOf(stage).join('、') }}</span>
                </p>
                <p v-else class="apd__node-empty">{{ t('approval.noDecisions') }}</p>
              </div>
            </li>
          </ol>
        </section>
      </div>
    </div>

    <template #footer>
      <div v-if="canDecide" class="apd__footer">
        <el-button @click="openAction('reject')" type="danger" plain>
          {{ t('approval.reject') }}
        </el-button>
        <el-button @click="openAction('approve')" type="primary">
          {{ t('approval.approve') }}
        </el-button>
      </div>
    </template>

    <!-- Approve/Reject 输入框 -->
    <el-dialog v-model="actionVisible"
      :title="actionType === 'approve' ? t('approval.approve') : t('approval.reject')"
      width="440" destroy-on-close append-to-body>
      <el-input v-model="actionComment" type="textarea" :rows="3"
        :placeholder="t('approval.commentPlaceholder')" />
      <template #footer>
        <el-button @click="actionVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button :type="actionType === 'approve' ? 'primary' : 'danger'"
          :loading="actionLoading" @click="submitAction">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped lang="scss">
/* ── Drawer shell ── */
.apd :deep(.el-drawer__header) {
  margin: 0;
  padding: var(--r-space-5) var(--r-space-6) var(--r-space-4);
  border-bottom: 1px solid var(--r-border-light);
  background: var(--r-bg-card);
}
.apd :deep(.el-drawer__body) { padding: 0; display: flex; flex-direction: column; min-height: 0; }
.apd :deep(.el-drawer__footer) {
  border-top: 1px solid var(--r-border-light);
  padding: var(--r-space-3) var(--r-space-6);
  background: var(--r-bg-card);
}

/* ── Header ── */
.apd__head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--r-space-4);
}
.apd__head-id {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding-right: var(--r-space-4);
  border-right: 1px solid var(--r-border-light);
}
.apd__head-kicker {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.apd__head-num {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-lg);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  line-height: 1.1;
}
.apd__head-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.apd__head-row {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  flex-wrap: wrap;
  font-size: var(--r-font-xs);
}
.apd__status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 0 3px var(--r-bg-card);
  .apd[data-status="PENDING"] & {
    animation: apd-pulse 1.8s ease-in-out infinite;
  }
}
@keyframes apd-pulse {
  0%, 100% { box-shadow: 0 0 0 3px var(--r-bg-card); }
  50%      { box-shadow: 0 0 0 3px var(--r-bg-card), 0 0 0 6px var(--r-warning-bg, var(--r-bg-panel)); }
}
.apd__status-text {
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.apd__head-sep { color: var(--r-text-disabled); }
.apd__head-type {
  color: var(--r-text-secondary);
  font-weight: var(--r-weight-medium);
}
.apd__head-channel {
  font-family: var(--r-font-mono);
  color: var(--r-text-muted);
  font-size: var(--r-font-xs);
}
.apd__title {
  margin: 0;
  font-size: var(--r-font-lg);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.apd__close {
  all: unset;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-radius-sm);
  color: var(--r-text-muted);
  cursor: pointer;
  align-self: start;
  &:hover { color: var(--r-text-primary); background: var(--r-bg-hover); }
}

/* ── Body ── */
.apd__body {
  flex: 1;
  overflow-y: auto;
  padding: var(--r-space-5) var(--r-space-6);
  background: var(--r-bg-page, var(--r-bg-panel));
}
.apd__sections {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-4);
}

/* Section card */
.apd__section {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  padding: var(--r-space-4) var(--r-space-5);
}
.apd__section-head {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  margin-bottom: var(--r-space-3);
}
.apd__section-mark {
  width: 3px;
  height: 14px;
  background: var(--r-accent);
  border-radius: 2px;
}
.apd__section-title {
  margin: 0;
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  letter-spacing: 0.02em;
}

/* KV grid */
.apd__kv {
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
.apd__kv-mono {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
}

.apd__remark {
  margin-top: var(--r-space-3);
  padding-top: var(--r-space-3);
  border-top: 1px dashed var(--r-border-light);
}
.apd__remark-label {
  display: block;
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  margin-bottom: 4px;
}
.apd__remark-text {
  margin: 0;
  font-size: var(--r-font-sm);
  color: var(--r-text-primary);
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

/* Description block */
.apd__desc {
  margin: 0;
  padding: var(--r-space-3) var(--r-space-4);
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-left: 3px solid var(--r-accent-border);
  border-radius: var(--r-radius-sm);
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  color: var(--r-text-secondary);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow-y: auto;
}

/* ── Timeline ── */
.apd__timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}
.apd__node {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: var(--r-space-3);
  position: relative;
}
.apd__node-marker {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 2px;
}
.apd__node-ring {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--r-bg-card);
  border: 1.5px solid var(--r-border);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-muted);
  transition: all 0.18s ease;
  flex-shrink: 0;
  z-index: 1;
}
.apd__node-line {
  flex: 1;
  width: 2px;
  background: var(--r-border-light);
  margin: 4px 0;
  min-height: 16px;
}

/* Node state */
.apd__node--done .apd__node-ring {
  background: var(--r-success);
  border-color: var(--r-success);
  color: var(--r-bg-card);
}
.apd__node--done .apd__node-line { background: var(--r-success); }
.apd__node--active .apd__node-ring {
  background: var(--r-accent-bg);
  border-color: var(--r-accent);
  color: var(--r-accent);
  box-shadow: 0 0 0 4px var(--r-accent-bg);
}
.apd__node--rejected .apd__node-ring {
  background: var(--r-danger);
  border-color: var(--r-danger);
  color: var(--r-bg-card);
}
.apd__node--rejected .apd__node-line { background: var(--r-danger); }

.apd__node-body {
  padding-bottom: var(--r-space-4);
  min-width: 0;
}
.apd__node:last-child .apd__node-body { padding-bottom: 0; }

.apd__node-head {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  min-height: 24px;
  margin-bottom: var(--r-space-2);
}
.apd__node-label {
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
}
.apd__node-pill {
  font-size: var(--r-font-xs);
  color: var(--r-accent);
  background: var(--r-accent-bg);
  border: 1px solid var(--r-accent-border);
  border-radius: var(--r-radius-pill, 999px);
  padding: 0 var(--r-space-2);
  line-height: 1.6;
  letter-spacing: 0.04em;
}
.apd__node-empty {
  margin: 0;
  font-size: var(--r-font-xs);
  color: var(--r-text-disabled);
  font-style: italic;
}
.apd__node-candidates {
  margin: 0 0 var(--r-space-2) 0;
  font-size: var(--r-font-xs);
  color: var(--r-text-secondary);
  display: flex;
  gap: var(--r-space-2);
}
.apd__node-candidates-label {
  color: var(--r-text-muted);
  flex-shrink: 0;
  &::after { content: ':'; }
}
.apd__node-candidates-list {
  color: var(--r-text-primary);
  font-weight: var(--r-weight-medium);
  word-break: break-all;
}

/* Decisions */
.apd__decisions {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--r-space-2);
}
.apd__decision {
  display: grid;
  grid-template-columns: 20px 1fr;
  gap: var(--r-space-2);
  padding: var(--r-space-2) var(--r-space-3);
  background: var(--r-bg-panel);
  border-radius: var(--r-radius-sm);
  border: 1px solid var(--r-border-light);
}
.apd__decision-icon {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 1px;

  &.is-approve {
    background: var(--r-success);
    color: var(--r-bg-card);
  }
  &.is-reject {
    background: var(--r-danger);
    color: var(--r-bg-card);
  }
}
.apd__decision-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.apd__decision-meta {
  display: flex;
  align-items: baseline;
  gap: var(--r-space-2);
  flex-wrap: wrap;
  font-size: var(--r-font-xs);
}
.apd__decision-who {
  color: var(--r-text-primary);
  font-weight: var(--r-weight-semibold);
}
.apd__decision-act {
  color: var(--r-text-muted);
}
.apd__decision-time {
  color: var(--r-text-disabled);
  font-family: var(--r-font-mono);
  margin-left: auto;
}
.apd__decision-remark {
  margin: 0;
  font-size: var(--r-font-xs);
  color: var(--r-text-secondary);
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  padding-left: var(--r-space-2);
  border-left: 2px solid var(--r-border-light);
}

/* Footer */
.apd__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--r-space-2);
}
</style>
