<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { listWorkflowInstances, getWorkflowInstance, cancelWorkflowInstance, listNodeInstances } from '@/api/workflow'
import { formatDate } from '@/utils/dateFormat'
import WorkflowInstance from '@/components/WorkflowInstance.vue'
import { usePermission } from '@/composables/usePermission'

const { canEdit } = usePermission()

interface Instance {
  id: number
  workflowId: number
  workflowDefinitionCode: string
  name: string
  triggerType: string
  status: string
  dagSnapshot?: string
  startedAt: string
  finishedAt: string
}

interface NodeInstance {
  id: number
  taskDefinitionCode: number
  taskType: string
  status: string
  name: string
  content?: string
  params?: string
  startedAt: string
  finishedAt: string
  duration?: number
  executionHost?: string
  errorMessage?: string
  varPool?: string
}

const statusColor: Record<string, string> = {
  RUNNING: 'var(--r-warning)',
  SUCCESS: 'var(--r-success)',
  FAILED: 'var(--r-danger)',
  CANCELLED: 'var(--r-text-muted)',
  PENDING: 'var(--r-accent)',
}

const statusOptions = ['RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED'] as const

const props = withDefaults(defineProps<{
  workflowDefinitionCode?: string | number
  showToolbar?: boolean
}>(), {
  showToolbar: true,
})

const { t } = useI18n()
const route = useRoute()
const workspaceId = Number(route.params.workspaceId)
const projectCode = route.params.projectCode as string

const loading = ref(false)
const instances = ref<Instance[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const searchName = ref('')
const searchStatus = ref('')

// Instance detail (inline)
const viewingInstance = ref<Instance | null>(null)
const viewingNodes = ref<NodeInstance[]>([])
const viewingLoading = ref(false)
let viewDetailGen = 0

async function fetchInstances() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    if (props.workflowDefinitionCode) {
      params.workflowDefinitionCode = props.workflowDefinitionCode
    } else {
      params.projectCode = projectCode
    }
    if (searchName.value.trim()) params.searchVal = searchName.value.trim()
    if (searchStatus.value) params.status = searchStatus.value

    const res: any = await listWorkflowInstances(workspaceId, params)
    instances.value = res.data ?? []
    total.value = res.total ?? 0
  } catch { ElMessage.error(t('common.failed')) }
  finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; fetchInstances() }
function handlePageChange(page: number) { pageNum.value = page; fetchInstances() }
function handleSizeChange(size: number) { pageSize.value = size; pageNum.value = 1; fetchInstances() }

function toggleStatus(status: string) {
  searchStatus.value = searchStatus.value === status ? '' : status
  pageNum.value = 1
  fetchInstances()
}

async function viewDetail(row: Instance) {
  const gen = ++viewDetailGen
  viewingLoading.value = true
  try {
    const [instRes, nodesRes] = await Promise.all([
      getWorkflowInstance(workspaceId, row.workflowDefinitionCode, row.id),
      listNodeInstances(workspaceId, row.workflowDefinitionCode, row.id),
    ])
    if (gen !== viewDetailGen) return
    viewingInstance.value = instRes.data
    viewingNodes.value = nodesRes.data || []
  } catch { if (gen === viewDetailGen) ElMessage.error(t('common.failed')) }
  finally { if (gen === viewDetailGen) viewingLoading.value = false }
}

function closeDetail() {
  viewingInstance.value = null
  viewingNodes.value = []
}

async function handleCancel(inst: any) {
  try {
    await cancelWorkflowInstance(workspaceId, inst.workflowDefinitionCode, inst.id)
    ElMessage.success(t('common.success'))
    await fetchInstances()
    if (viewingInstance.value?.id === inst.id) closeDetail()
  } catch { ElMessage.error(t('common.failed')) }
}

function formatDuration(startedAt?: string, finishedAt?: string): string {
  if (!startedAt) return '-'
  const start = new Date(startedAt).getTime()
  const end = finishedAt ? new Date(finishedAt).getTime() : Date.now()
  const s = Math.floor((end - start) / 1000)
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}m ${s % 60}s`
  return `${Math.floor(m / 60)}h ${m % 60}m`
}

defineExpose({ fetchInstances })

onMounted(fetchInstances)
</script>

<template>
  <div class="wi">
    <!-- List mode -->
    <template v-if="!viewingInstance">
      <!-- Toolbar -->
      <div v-if="showToolbar" class="wi-bar">
        <div class="wi-bar__left">
          <button
            :class="['wi-chip', { 'wi-chip--on': !searchStatus }]"
            @click="searchStatus = ''; handleSearch()"
          >{{ t('common.all') }}</button>
          <button
            v-for="s in statusOptions"
            :key="s"
            :class="['wi-chip', { 'wi-chip--on': searchStatus === s }]"
            @click="toggleStatus(s)"
          >
            <span class="wi-chip__dot" :style="{ background: statusColor[s] }" />
            {{ s }}
          </button>
        </div>
        <el-input
          v-model="searchName"
          :placeholder="t('instance.searchName')"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <!-- Table -->
      <div class="wi-table" v-loading="loading">
        <table v-if="instances.length || loading">
          <thead>
            <tr>
              <th class="wi-th" style="width: 48px; text-align: center">#</th>
              <th class="wi-th" style="min-width: 200px">{{ t('common.name') }}</th>
              <th class="wi-th" style="width: 110px; text-align: center">{{ t('common.status') }}</th>
              <th class="wi-th" style="width: 90px; text-align: center">{{ t('instance.trigger') }}</th>
              <th class="wi-th" style="width: 155px">{{ t('instance.started') }}</th>
              <th class="wi-th" style="width: 80px; text-align: right">{{ t('instance.duration') }}</th>
              <th class="wi-th" style="width: 52px"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, idx) in instances" :key="row.id" class="wi-row" @click="viewDetail(row)">
              <td class="wi-td" style="text-align: center; color: var(--r-text-disabled); font-size: 12px">{{ idx + 1 + (pageNum - 1) * pageSize }}</td>
              <td class="wi-td">
                <span class="wi-inst-name">{{ row.name || `#${row.id}` }}</span>
              </td>
              <td class="wi-td" style="text-align: center">
                <span class="wi-status">
                  <span class="wi-status__dot" :style="{ background: statusColor[row.status] }" />
                  {{ row.status }}
                </span>
              </td>
              <td class="wi-td" style="text-align: center">
                <el-tag size="small" effect="plain" round>{{ row.triggerType }}</el-tag>
              </td>
              <td class="wi-td">
                <span class="wi-date">{{ formatDate(row.startedAt) }}</span>
              </td>
              <td class="wi-td" style="text-align: right">
                <span class="wi-dur">{{ formatDuration(row.startedAt, row.finishedAt) }}</span>
              </td>
              <td class="wi-td" style="text-align: center" @click.stop>
                <el-tooltip v-if="canEdit && row.status === 'RUNNING'" :content="t('instance.cancel')" placement="top">
                  <button class="wi-cancel" @click="handleCancel(row)">
                    <el-icon size="14"><Close /></el-icon>
                  </button>
                </el-tooltip>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- Empty -->
        <div v-if="!loading && instances.length === 0" class="wi-empty">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
            <rect x="8" y="12" width="32" height="26" rx="4" stroke="var(--r-border-dark)" stroke-width="1.4" fill="none"/>
            <circle cx="20" cy="25" r="4" stroke="var(--r-text-disabled)" stroke-width="1.4" fill="none"/>
            <path d="M28 23h10M28 27h6" stroke="var(--r-text-disabled)" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
          <p>{{ t('instance.empty') }}</p>
        </div>

        <!-- Pagination -->
        <div v-if="total > pageSize" class="wi-pager">
          <el-pagination
            background small
            layout="total, prev, pager, next"
            :total="total"
            :current-page="pageNum"
            :page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </template>

    <!-- Instance detail (inline) -->
    <template v-else>
      <WorkflowInstance
        :instance="viewingInstance"
        :nodes="viewingNodes"
        :loading="viewingLoading"
        :back-label="t('instance.title')"
        @close="closeDetail"
        @cancel="handleCancel"
        @refresh="viewDetail(viewingInstance!)"
      />
    </template>
  </div>
</template>

<style scoped lang="scss">
.wi { padding: 20px 24px; }

/* ── Toolbar ── */
.wi-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 12px;
}

.wi-bar__left {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ── Status chips ── */
.wi-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border: 1px solid var(--r-border);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--r-text-muted);
  background: var(--r-bg-card);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;

  &:hover {
    border-color: var(--r-border-dark);
    color: var(--r-text-secondary);
  }

  &--on {
    border-color: var(--r-accent-border);
    background: var(--r-accent-bg);
    color: var(--r-accent);
    font-weight: 600;
  }
}

.wi-chip__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* ── Table ── */
.wi-table {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 10px;
  overflow: hidden;

  table {
    width: 100%;
    border-collapse: collapse;
  }
}

.wi-th {
  padding: 9px 16px;
  font-size: 11px;
  font-weight: 600;
  color: var(--r-text-muted);
  text-align: left;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--r-border);
  background: var(--r-bg-panel);
  white-space: nowrap;
  user-select: none;
}

.wi-row {
  cursor: pointer;
  transition: background 0.12s ease;

  &:nth-child(even) { background: var(--r-bg-panel); }

  &:hover { background: var(--r-bg-hover) !important; }

  .wi-td {
    border-bottom: 1px solid var(--r-border-light);
  }
}

.wi-td {
  padding: 10px 16px;
  font-size: 13px;
  vertical-align: middle;
}

/* ── Name ── */
.wi-inst-name {
  font-family: var(--r-font-mono);
  font-size: 12px;
  color: var(--r-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
  max-width: 300px;

  .wi-row:hover & { color: var(--r-accent); }
}

/* ── Status ── */
.wi-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 500;
  color: var(--r-text-secondary);
}

.wi-status__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.wi-date {
  font-size: 12px;
  color: var(--r-text-muted);
  font-variant-numeric: tabular-nums;
}

.wi-dur {
  font-size: 12px;
  color: var(--r-text-secondary);
  font-family: var(--r-font-mono);
  font-variant-numeric: tabular-nums;
}

/* ── Cancel button ── */
.wi-cancel {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--r-text-muted);
  cursor: pointer;
  transition: background 0.12s, color 0.12s;

  &:hover {
    background: var(--r-danger-bg);
    color: var(--r-danger);
  }
}

/* ── Empty ── */
.wi-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 20px;
  gap: 10px;

  p {
    margin: 0;
    font-size: 13px;
    color: var(--r-text-muted);
  }
}

/* ── Pagination ── */
.wi-pager {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px;
  border-top: 1px solid var(--r-border-light);
}
</style>
