<template>
  <div class="jm">
    <!-- Stats cards -->
    <div class="jm-stats">
      <div class="jm-stat" v-for="s in stats" :key="s.key">
        <div class="jm-stat__value" :style="{ color: s.color }">{{ s.count }}</div>
        <div class="jm-stat__label">{{ s.label }}</div>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="jm-toolbar">
      <div class="jm-toolbar__left">
        <el-input v-model="searchName" :placeholder="t('common.search')" clearable style="width: 200px" @keyup.enter="handleSearch" @clear="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterTaskType" :placeholder="t('jobs.taskType')" clearable style="width: 150px" @change="handleSearch">
          <el-option v-for="tt in taskTypeOptions" :key="tt.value" :label="tt.label" :value="tt.value" />
        </el-select>
        <el-select v-model="filterRuntime" :placeholder="t('jobs.runtimeType')" clearable style="width: 160px" @change="handleSearch">
          <el-option v-for="rt in runtimeTypes" :key="rt.value" :label="rt.label" :value="rt.value" />
        </el-select>
      </div>
      <div class="jm-toolbar__right">
        <el-switch v-model="autoRefresh" :active-text="t('jobs.autoRefresh')" size="small" />
        <el-button :icon="Refresh" circle size="small" @click="() => loadJobs()" :loading="loading" />
      </div>
    </div>

    <!-- Table -->
    <div class="jm-card">
      <el-table v-loading="loading" :data="jobs" stripe style="width: 100%" :empty-text="t('common.noData')">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="name" :label="t('jobs.name')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="jm-name" @click="openDetail(row)">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskType" :label="t('jobs.taskType')" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" round>{{ row.taskType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="runtimeType" :label="t('jobs.runtimeType')" width="150" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.runtimeType" size="small" :type="runtimeTagType(row.runtimeType)" round>{{ runtimeLabel(row.runtimeType) }}</el-tag>
            <span v-else class="jm-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('jobs.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" round>
              <span v-if="row.status === 'RUNNING'" class="jm-pulse" />
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="appId" :label="t('jobs.appId')" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <code v-if="row.appId" class="jm-code">{{ row.appId }}</code>
            <span v-else class="jm-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="executionHost" :label="t('jobs.executionHost')" width="150" show-overflow-tooltip />
        <el-table-column :label="t('jobs.startedAt')" width="170">
          <template #default="{ row }">
            <span class="jm-date">{{ row.startedAt ? new Date(row.startedAt).toLocaleString() : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('jobs.duration')" width="100" align="center">
          <template #default="{ row }">
            <span class="jm-dur">{{ row.startedAt ? formatDuration(row.startedAt) : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('jobs.operations')" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-tooltip :content="t('jobs.kill')" placement="top">
              <el-button circle size="small" type="danger" @click="handleKill(row)">
                <el-icon><Close /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip v-if="isFlinkStreaming(row)" :content="t('jobs.savepoint')" placement="top">
              <el-button circle size="small" type="warning" @click="handleSavepoint(row)">
                <el-icon><Camera /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip v-if="row.trackingUrl" content="Tracking UI" placement="top">
              <el-button circle size="small" @click="openTrackingUrl(row.trackingUrl)">
                <el-icon><Link /></el-icon>
              </el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > pageSize" class="pagination-bar">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="pageNum"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- Detail drawer -->
    <el-drawer v-model="drawerVisible" :title="t('jobs.detail')" size="420px" direction="rtl">
      <template v-if="detailRow">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('jobs.name')">{{ detailRow.name }}</el-descriptions-item>
          <el-descriptions-item :label="t('jobs.taskType')">
            <el-tag size="small" effect="plain">{{ detailRow.taskType }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('jobs.runtimeType')">
            <el-tag v-if="detailRow.runtimeType" size="small" :type="runtimeTagType(detailRow.runtimeType)">{{ runtimeLabel(detailRow.runtimeType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('jobs.status')">
            <el-tag :type="statusTagType(detailRow.status)" size="small">{{ detailRow.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('jobs.appId')">
            <code v-if="detailRow.appId">{{ detailRow.appId }}</code>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('jobs.trackingUrl')">
            <el-link v-if="detailRow.trackingUrl" :href="detailRow.trackingUrl" target="_blank" rel="noopener noreferrer" type="primary">{{ detailRow.trackingUrl }}</el-link>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('jobs.sourceType')">{{ detailRow.sourceType || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('jobs.executionHost')">{{ detailRow.executionHost || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('jobs.startedAt')">{{ detailRow.startedAt ? new Date(detailRow.startedAt).toLocaleString() : '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('jobs.duration')">{{ detailRow.startedAt ? formatDuration(detailRow.startedAt) : '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detailRow.errorMessage" :label="t('jobs.errorMessage')">
            <span style="color: var(--el-color-danger)">{{ detailRow.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { Search, Refresh, Close, Camera, Link } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listRunningJobs, killJob, triggerSavepoint } from '@/api/jobs'
import { getRuntimeTypes, getTaskTypes, type RuntimeTypeDef, type TaskTypeDef } from '@/api/config'
import { usePagination } from '@/composables/usePagination'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

interface JobRow {
  id: number
  name: string
  scriptName: string
  taskType: string
  runtimeType: string
  status: string
  executionHost: string
  startedAt: string
  duration: number
  workspaceId: number
  workspaceName: string
}

const { t } = useI18n()
const route = useRoute()
const workspaceId = computed(() => Number(route.params.workspaceId) || undefined)

const {
  data: jobs,
  total,
  pageNum,
  pageSize,
  loading,
  fetch: loadJobs,
  handlePageChange,
  handleSizeChange,
  resetAndFetch,
} = usePagination<JobRow>({
  fetchApi: (params) => listRunningJobs({
    workspaceId: workspaceId.value,
    name: searchName.value || undefined,
    taskType: filterTaskType.value || undefined,
    runtimeType: filterRuntime.value || undefined,
    ...params,
  }),
})

const autoRefresh = ref(true)
const runtimeTypes = ref<RuntimeTypeDef[]>([])
const taskTypeOptions = ref<{ value: string; label: string }[]>([])
let timer: ReturnType<typeof setInterval> | null = null

// Filters
const searchName = ref('')
const filterTaskType = ref('')
const filterRuntime = ref('')

// Detail drawer
const drawerVisible = ref(false)
const detailRow = ref<any>(null)

const stats = computed(() => {
  const all = jobs.value
  return [
    { key: 'total', label: t('jobs.running'), count: total.value, color: 'var(--r-accent)' },
    { key: 'running', label: 'RUNNING', count: all.filter(j => j.status === 'RUNNING').length, color: 'var(--r-success)' },
    { key: 'pending', label: 'PENDING', count: all.filter(j => j.status === 'PENDING').length, color: 'var(--r-warning)' },
    { key: 'flink', label: 'Flink', count: all.filter(j => j.taskType?.startsWith('FLINK')).length, color: 'var(--r-purple)' },
    { key: 'spark', label: 'Spark', count: all.filter(j => j.taskType?.startsWith('SPARK')).length, color: 'var(--r-pink)' },
  ]
})

function handleSearch() {
  resetAndFetch()
}

onMounted(async () => {
  getRuntimeTypes().then(res => { runtimeTypes.value = res.data ?? [] }).catch(() => {})
  getTaskTypes().then(res => {
    taskTypeOptions.value = (res.data ?? []).map((tt: TaskTypeDef) => ({ value: tt.value, label: tt.label }))
  }).catch(() => {})
  await loadJobs()
  timer = setInterval(() => { if (autoRefresh.value) loadJobs() }, 5000)
})

onUnmounted(() => { if (timer) clearInterval(timer) })

function runtimeLabel(value: string) {
  return runtimeTypes.value.find(rt => rt.value === value)?.label ?? value ?? '-'
}

const statusTagMap: Record<string, TagType> = {
  RUNNING: 'primary', PENDING: 'warning', SUCCESS: 'success', FAILED: 'danger', CANCELLED: 'info',
}
function statusTagType(status: string): TagType { return statusTagMap[status] ?? 'info' }

function runtimeTagType(type: string): TagType {
  switch (type) {
    case 'CLUSTER': return 'info'
    case 'ALIYUN': return 'warning'
    case 'AWS': return 'success'
    default: return 'info'
  }
}

function isFlinkStreaming(row: any) {
  return row.taskType?.startsWith('FLINK') && row.status === 'RUNNING'
}

function formatDuration(startedAt: string) {
  const ms = Date.now() - new Date(startedAt).getTime()
  const s = Math.floor(ms / 1000)
  if (s < 60) return s + 's'
  const m = Math.floor(s / 60)
  if (m < 60) return m + 'm ' + (s % 60) + 's'
  const h = Math.floor(m / 60)
  return h + 'h ' + (m % 60) + 'm'
}

function openDetail(row: any) {
  detailRow.value = row
  drawerVisible.value = true
}

const { confirmDelete } = useDeleteConfirm()

async function handleKill(row: any) {
  confirmDelete(t('jobs.killConfirm'), () => killJob(row.id), () => loadJobs())
}

async function handleSavepoint(row: any) {
  try {
    await triggerSavepoint(row.id)
    ElMessage.success(t('jobs.savepointSuccess'))
  } catch (e: any) {
    // 不直出后端 message,避免泄漏内部错误细节;详细信息进 console
    console.error('savepoint failed', e)
    ElMessage.error(t('common.failed'))
  }
}

function openTrackingUrl(url: string) {
  window.open(url, '_blank', 'noopener,noreferrer')
}
</script>

<style scoped lang="scss">
.jm { padding: 24px; height: 100%; overflow: auto; background: var(--r-bg-panel); }

.jm-stats {
  display: flex; gap: 16px; margin-bottom: 20px;
}
.jm-stat {
  flex: 1; background: var(--r-bg-card); border-radius: 8px; padding: 16px 20px;
  box-shadow: var(--r-shadow-sm);
  &__value { font-size: 24px; font-weight: 700; line-height: 1.2; }
  &__label { font-size: 12px; color: var(--r-text-muted); margin-top: 4px; }
}

.jm-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; gap: 12px;
  &__left { display: flex; gap: 8px; flex-wrap: wrap; }
  &__right { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
}

.jm-card {
  background: var(--r-bg-card); border-radius: 8px; padding: 16px;
  box-shadow: var(--r-shadow-sm);
}

.jm-name {
  color: var(--r-accent); cursor: pointer; font-weight: 500;
  &:hover { text-decoration: underline; }
}
.jm-code { font-family: var(--r-font-mono); font-size: 11px; color: var(--r-text-secondary); background: var(--r-bg-hover); padding: 1px 6px; border-radius: 3px; }
.jm-date { font-size: 12px; color: var(--r-text-secondary); }
.jm-dur { font-size: 12px; color: var(--r-text-muted); font-variant-numeric: tabular-nums; }
.jm-muted { color: var(--r-text-disabled); }

.jm-pulse {
  display: inline-block; width: 6px; height: 6px; border-radius: 50%;
  background: currentColor; margin-right: 4px;
  animation: pulse 1.5s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.jm-empty { padding: 40px 0; }

.pagination-bar {
  display: flex; justify-content: flex-end; padding-top: 16px;
}
</style>
