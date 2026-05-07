<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getExecutionLog } from '@/api/script'
import DagViewer from '@/components/DagViewer.vue'
import TaskInstanceDetail from '@/components/TaskInstanceDetail.vue'
import TaskIcon from '@/components/TaskIcon.vue'
import { formatDate } from '@/utils/dateFormat'

interface Instance {
  id: number
  workflowId?: number
  workflowDefinitionCode?: string
  name?: string
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

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const statusTagMap: Record<string, TagType> = {
  RUNNING: 'warning', SUCCESS: 'success', FAILED: 'danger', CANCELLED: 'info', PENDING: 'primary',
}

const props = defineProps<{
  instance: Instance
  nodes: NodeInstance[]
  loading: boolean
  backLabel: string
}>()

const emit = defineEmits<{
  close: []
  cancel: [instance: Instance]
  refresh: []
}>()

const { t } = useI18n()

// Log dialog
const logDialogVisible = ref(false)
const logDialogTitle = ref('')
const logDialogContent = ref('')

// Node detail dialog
const nodeDialogVisible = ref(false)
const nodeDialogNode = ref<any>(null)

async function handleViewLog(nodeId: string) {
  const node = props.nodes.find(n => String(n.taskDefinitionCode) === nodeId)
  if (!node) return
  logDialogTitle.value = `${node.name} (${node.taskType})`
  if (node.status === 'SKIPPED') {
    logDialogContent.value = 'Node skipped (isEnabled=false)'
  } else {
    try {
      const { data } = await getExecutionLog(node.id, 0)
      logDialogContent.value = data?.lines || t('common.noData')
    } catch { logDialogContent.value = t('common.noData') }
  }
  logDialogVisible.value = true
}

function handleViewNode(nodeId: string) {
  let node: any = props.nodes.find(n => String(n.taskDefinitionCode) === nodeId)
  if (!node && props.instance.dagSnapshot) {
    try {
      const dag = JSON.parse(props.instance.dagSnapshot)
      const dagNode = dag.nodes?.find((n: any) => String(n.taskCode) === nodeId)
      if (dagNode) {
        node = { name: dagNode.label, taskType: '', status: 'WAITING', nodeId: nodeId }
      }
    } catch { /* ignore */ }
  }
  if (!node) return
  nodeDialogNode.value = node
  nodeDialogVisible.value = true
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
</script>

<template>
  <div v-loading="loading">
    <!-- Info card -->
    <div class="wi-card" style="margin-bottom: 16px">
      <div class="wi-detail-header">
        <div style="display:flex;align-items:center;gap:8px">
          <el-button text size="small" @click="emit('close')">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <span class="wi-detail-title">{{ instance.name || `#${instance.id}` }}</span>
          <el-tag :type="statusTagMap[instance.status]" size="small" round>{{ instance.status }}</el-tag>
        </div>
        <div style="display:flex;gap:8px">
          <el-button size="small" circle @click="emit('refresh')"><el-icon><Refresh /></el-icon></el-button>
          <el-button v-if="instance.status === 'RUNNING'" type="danger" size="small" @click="emit('cancel', instance)">{{ t('instance.cancel') }}</el-button>
        </div>
      </div>
      <div class="wi-detail-info">
        <div class="wi-info-item">
          <span class="wi-info-label">{{ t('instance.started') }}</span>
          <span class="wi-info-value">{{ formatDate(instance.startedAt) }}</span>
        </div>
        <div class="wi-info-item">
          <span class="wi-info-label">{{ t('instance.finished') }}</span>
          <span class="wi-info-value">{{ formatDate(instance.finishedAt) }}</span>
        </div>
        <div class="wi-info-item">
          <span class="wi-info-label">{{ t('instance.duration') }}</span>
          <span class="wi-info-value">{{ formatDuration(instance.startedAt, instance.finishedAt) }}</span>
        </div>
        <div class="wi-info-item">
          <span class="wi-info-label">{{ t('instance.trigger') }}</span>
          <span class="wi-info-value"><el-tag size="small" effect="plain" round>{{ instance.triggerType }}</el-tag></span>
        </div>
      </div>
    </div>

    <!-- DAG -->
    <div v-if="instance.dagSnapshot" class="wi-card" style="margin-bottom: 16px">
      <div class="wi-detail-header">
        <span class="wi-detail-title">DAG</span>
      </div>
      <div style="height: 320px">
        <DagViewer
          :dag-json="instance.dagSnapshot"
          :node-statuses="nodes.map(n => ({ nodeId: String(n.taskDefinitionCode), nodeType: n.taskType, status: n.status, startedAt: n.startedAt, finishedAt: n.finishedAt }))"
          @view-log="handleViewLog"
          @view-node="handleViewNode"
        />
      </div>
    </div>
  </div>

  <!-- Log Dialog -->
  <el-dialog v-model="logDialogVisible" :title="logDialogTitle" width="700px" destroy-on-close>
    <div class="wi-log">
      <pre>{{ logDialogContent }}</pre>
    </div>
  </el-dialog>

  <!-- Node Detail Dialog (read-only) -->
  <el-dialog v-model="nodeDialogVisible" width="700px" destroy-on-close :close-on-click-modal="false">
    <template #header>
      <div style="display:flex;align-items:center;gap:10px">
        <TaskIcon v-if="nodeDialogNode" :type="nodeDialogNode.taskType" :size="22" />
        <span style="font-size:16px;font-weight:600;color:var(--r-text-primary)">{{ nodeDialogNode?.name }}</span>
        <el-tag v-if="nodeDialogNode" size="small" effect="light" round>{{ nodeDialogNode.taskType }}</el-tag>
      </div>
    </template>
    <TaskInstanceDetail v-if="nodeDialogNode" :node="nodeDialogNode" :dag-json="instance.dagSnapshot" />
  </el-dialog>
</template>

<style scoped lang="scss">
.wi-card {
  background: var(--r-bg-card); border: 1px solid var(--r-border); border-radius: 8px; overflow: hidden;
}

.wi-detail-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; border-bottom: 1px solid var(--r-bg-hover);
}
.wi-detail-title { font-size: 14px; font-weight: 600; color: var(--r-text-primary); }
.wi-detail-info { display: flex; gap: 32px; padding: 16px 20px; }
.wi-info-item { display: flex; flex-direction: column; gap: 4px; }
.wi-info-label { font-size: 11px; color: var(--r-text-muted); text-transform: uppercase; letter-spacing: 0.3px; }
.wi-info-value { font-size: 14px; color: var(--r-text-primary); }

.wi-log {
  background: var(--r-bg-code); border-radius: 6px; padding: 16px; max-height: 400px; overflow: auto;
  pre { margin: 0; font-family: var(--r-font-mono); font-size: 12px; line-height: 1.7; color: var(--r-text-primary); white-space: pre-wrap; }
}
</style>