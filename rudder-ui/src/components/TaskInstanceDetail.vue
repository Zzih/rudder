<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import TaskIcon from '@/components/TaskIcon.vue'
import MonacoInput from '@/components/MonacoInput.vue'
import { getExecutionLog, getExecutionResult, getScript } from '@/api/script'
import { useDatasourceStore } from '@/stores/datasource'
import { listWorkflowDefinitions, listTaskDefinitions } from '@/api/workflow'
import { formatDate } from '@/utils/dateFormat'
import { listProjects } from '@/api/workspace'
import { useRoute } from 'vue-router'

const props = defineProps<{
  node: Record<string, any>
  dagJson?: string
}>()

const { t } = useI18n()

const statusTagMap: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  RUNNING: 'warning', SUCCESS: 'success', FAILED: 'danger', CANCELLED: 'info', PENDING: 'primary', SKIPPED: 'info',
}

// Parse content JSON (with script fallback for control flow tasks)
const content = computed(() => {
  if (props.node?.content) {
    try { return JSON.parse(props.node.content) } catch { return {} }
  }
  return scriptContent.value || {}
})

// Parse params JSON
const params = computed(() => {
  if (!props.node?.params) return null
  try { return JSON.parse(props.node.params) } catch { return null }
})

// Result: paginated fetch via API
const resultColumns = ref<string[]>([])
const resultRows = ref<Record<string, any>[]>([])
const resultTotal = ref(0)
const resultOffset = ref(0)
const resultLimit = ref(500)
const resultLoaded = ref(false)
const resultLoading = ref(false)

const hasResult = computed(() => props.node?.rowCount > 0 || props.node?.resultPath)

async function fetchResult(offset = 0) {
  if (!props.node?.id) return
  resultLoading.value = true
  try {
    const { data } = await getExecutionResult(props.node.id, offset, resultLimit.value)
    resultColumns.value = data?.columns ?? []
    resultRows.value = data?.rows ?? []
    resultTotal.value = data?.totalRows ?? 0
    resultOffset.value = offset
    resultLoaded.value = true
  } catch {
    resultColumns.value = []
    resultRows.value = []
  } finally {
    resultLoading.value = false
  }
}

// 切换 node 时统一重置 result / log / 缓存状态(原拆成 4 个 watch,合并避免回调冗余)
watch(() => props.node?.id, () => {
  resultLoaded.value = false
  resultColumns.value = []
  resultRows.value = []
  logLoaded.value = false
  logContent.value = ''
})

// Parse varPool
const varPool = computed(() => {
  if (!props.node?.varPool) return null
  try { return JSON.parse(props.node.varPool) } catch { return null }
})

const route = useRoute()
const workspaceId = computed(() => Number(route.params.workspaceId))

// Fallback: load script content when task instance content is empty
const scriptContent = ref<Record<string, any> | null>(null)
const scriptLoading = ref(false)

watch(() => [props.node?.id, props.node?.content], async () => {
  scriptContent.value = null
  if (props.node?.content || !props.node?.scriptCode) return
  scriptLoading.value = true
  try {
    const res: any = await getScript(workspaceId.value, props.node.scriptCode)
    if (res?.data?.content) {
      try { scriptContent.value = JSON.parse(res.data.content) } catch { scriptContent.value = { content: res.data.content } }
    }
  } catch { /* ignore */ }
  finally { scriptLoading.value = false }
}, { immediate: true })

// Task type helpers
const datasourceStore = useDatasourceStore()

const taskType = computed(() => props.node?.taskType || '')
const isSqlTask = computed(() => taskType.value.endsWith('_SQL'))
const isJarTask = computed(() => taskType.value.includes('JAR'))
const isScriptTask = computed(() => ['PYTHON', 'SHELL'].includes(taskType.value))
const isFlinkSql = computed(() => taskType.value === 'FLINK_SQL')

const datasourceName = computed(() => {
  const dsId = content.value?.dataSourceId
  if (dsId == null) return '-'
  const ds = datasourceStore.datasources.find(d => d.id === dsId)
  return ds ? ds.name : `#${dsId}`
})

// Build node label lookup from dagJson for CONDITION/SWITCH display
const nodeLabels = computed<Record<string, string>>(() => {
  if (!props.dagJson) return {}
  try {
    const dag = JSON.parse(props.dagJson)
    const map: Record<string, string> = {}
    for (const n of (dag.nodes || [])) map[String(n.taskCode)] = n.label
    return map
  } catch { return {} }
})

function nodeName(code: string | number | null): string {
  if (code == null) return '-'
  return nodeLabels.value[String(code)] || String(code)
}

// Log
const logContent = ref('')
const logLoading = ref(false)
const logLoaded = ref(false)

async function fetchLog() {
  if (logLoaded.value || !props.node?.id) return
  logLoading.value = true
  try {
    if (props.node.status === 'SKIPPED') {
      logContent.value = t('instance.nodeSkipped')
    } else {
      const { data } = await getExecutionLog(props.node.id, 0)
      logContent.value = data?.lines || t('instance.noLogs')
    }
  } catch {
    logContent.value = t('instance.loadLogFailed')
  } finally {
    logLoading.value = false
    logLoaded.value = true
  }
}

function onTabChange(name: string | number) {
  if (name === 'log' && !logLoaded.value) fetchLog()
  if (name === 'result' && !resultLoaded.value) fetchResult()
}

// DEPENDENT: resolve project/workflow/task codes to names
const depNames = ref<Record<string, string>>({})

watch(() => [props.node?.id, content.value], async () => {
  depNames.value = {}
  if (taskType.value !== 'DEPENDENT') return
  const deps = content.value?.dependence?.dependTaskList
  if (!deps?.length) return

  for (const group of deps) {
    for (const item of (group.dependItemList || [])) {
      const pc = item.projectCode
      const dc = item.definitionCode
      const tc = item.depTaskCode

      // resolve project name
      if (pc && !depNames.value[`p_${pc}`]) {
        try {
          const { data } = await listProjects(workspaceId.value, { pageSize: 9999 })
          for (const p of (data?.records ?? data ?? []))
            depNames.value[`p_${p.code}`] = p.name
        } catch { /* ignore */ }
      }

      // resolve workflow name
      if (pc && dc && !depNames.value[`w_${dc}`]) {
        try {
          const { data } = await listWorkflowDefinitions(workspaceId.value, pc, { pageSize: 9999 })
          for (const w of (data?.records ?? data ?? []))
            depNames.value[`w_${w.code}`] = w.name
        } catch { /* ignore */ }
      }

      // resolve task name
      if (pc && dc && tc && tc !== 0 && !depNames.value[`t_${tc}`]) {
        try {
          const { data } = await listTaskDefinitions(workspaceId.value, pc, dc)
          for (const td of (data ?? []))
            depNames.value[`t_${td.code}`] = td.name
        } catch { /* ignore */ }
      }
    }
  }
}, { immediate: true })

function depName(prefix: string, code: any, fallback?: string): string {
  if (code == null) return '-'
  return depNames.value[`${prefix}_${code}`] || fallback || String(code)
}
</script>

<template>
  <div v-if="node" class="tid">
    <!-- Header info -->
    <el-descriptions :column="2" border size="small" class="tid-desc">
      <el-descriptions-item :label="t('instance.nodeType')">
        <div style="display:flex;align-items:center;gap:6px">
          <TaskIcon :type="taskType" :size="18" />
          <el-tag size="small" effect="light" round>{{ taskType }}</el-tag>
        </div>
      </el-descriptions-item>
      <el-descriptions-item :label="t('common.status')">
        <el-tag :type="statusTagMap[node.status]" size="small" round>{{ node.status }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item :label="t('instance.started')">{{ formatDate(node.startedAt) }}</el-descriptions-item>
      <el-descriptions-item :label="t('instance.finished')">{{ formatDate(node.finishedAt) }}</el-descriptions-item>
      <el-descriptions-item v-if="node.duration != null" :label="t('instance.duration')">{{ node.duration }}ms</el-descriptions-item>
      <el-descriptions-item v-if="node.executionHost" :label="t('instance.host')">{{ node.executionHost }}</el-descriptions-item>
    </el-descriptions>

    <el-tabs type="border-card" class="tid-tabs" @tab-change="onTabChange">
      <!-- Tab: Task Content -->
      <el-tab-pane :label="t('workflow.sectionTask')">

        <!-- SQL Tasks -->
        <template v-if="isSqlTask">
          <el-form label-position="top" class="tid-form">
            <el-row :gutter="16">
              <el-col :span="isFlinkSql ? 12 : 24">
                <el-form-item :label="t('workflow.datasource')">
                  <el-input :model-value="datasourceName" disabled />
                </el-form-item>
              </el-col>
              <el-col v-if="isFlinkSql" :span="12">
                <el-form-item :label="t('instance.executionMode')">
                  <el-input :model-value="content.executionMode || 'BATCH'" disabled />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
          <div class="tid-section-label">SQL</div>
          <MonacoInput :model-value="content.sql || ''" :task-type="taskType" height="240px" read-only />
        </template>

        <!-- Script Tasks (Python / Shell) -->
        <template v-else-if="isScriptTask">
          <div class="tid-section-label">{{ taskType === 'PYTHON' ? 'Python' : 'Shell' }}</div>
          <MonacoInput :model-value="content.content || ''" :task-type="taskType" height="300px" read-only />
        </template>

        <!-- CONDITION -->
        <template v-else-if="taskType === 'CONDITION'">
          <el-form label-position="top" class="tid-form">
            <div class="tid-section">{{ t('workflow.conditionDeps') }}</div>
            <template v-for="(group, gIdx) in (content.dependence?.dependTaskList || [])" :key="gIdx">
              <div v-if="gIdx > 0" class="tid-relation">
                <span class="tid-relation__line" />
                <el-tag size="small" effect="plain">{{ content.dependence?.relation || 'AND' }}</el-tag>
                <span class="tid-relation__line" />
              </div>
              <div class="tid-dep-group">
                <div class="tid-dep-group__title">{{ t('workflow.conditionGroup') }} {{ gIdx + 1 }}</div>
                <template v-for="(item, iIdx) in group.dependItemList" :key="iIdx">
                  <div v-if="iIdx > 0" class="tid-relation tid-relation--inner">
                    <span class="tid-relation__line" />
                    <el-tag size="small" effect="plain">{{ group.relation || 'AND' }}</el-tag>
                    <span class="tid-relation__line" />
                  </div>
                  <div class="tid-dep-entry">
                    <span class="tid-dep-entry__label">{{ nodeName(item.depTaskCode) }}</span>
                    <el-tag :type="item.status === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ item.status }}</el-tag>
                  </div>
                </template>
              </div>
            </template>

            <div class="tid-section" style="margin-top:16px">{{ t('workflow.conditionBranch') }}</div>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item :label="t('workflow.successBranch')">
                  <el-input :model-value="nodeName(content.conditionResult?.successNode?.[0])" disabled />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="t('workflow.failedBranch')">
                  <el-input :model-value="nodeName(content.conditionResult?.failedNode?.[0])" disabled />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </template>

        <!-- SWITCH -->
        <template v-else-if="taskType === 'SWITCH'">
          <el-form label-position="top" class="tid-form">
            <div class="tid-section">{{ t('workflow.switchCases') }}</div>
            <div v-for="(sc, idx) in (content.switchResult?.dependTaskList || [])" :key="idx" class="tid-switch-row">
              <el-input :model-value="sc.condition" disabled class="tid-switch-cond" />
              <span style="color:var(--r-text-muted);font-size:12px;flex-shrink:0">→</span>
              <el-input :model-value="nodeName(sc.nextNode)" disabled style="width:160px" />
            </div>
            <el-form-item :label="t('workflow.switchDefault')" style="margin-top:12px">
              <el-input :model-value="nodeName(content.switchResult?.nextNode)" disabled />
            </el-form-item>
          </el-form>
        </template>

        <!-- SUB_WORKFLOW -->
        <template v-else-if="taskType === 'SUB_WORKFLOW'">
          <el-form label-position="top" class="tid-form">
            <el-form-item :label="t('workflow.subWorkflowId')">
              <el-input :model-value="content.workflowDefinitionCode || '-'" disabled />
            </el-form-item>
          </el-form>
        </template>

        <!-- DEPENDENT -->
        <template v-else-if="taskType === 'DEPENDENT'">
          <el-form label-position="top" class="tid-form">
            <div class="tid-section">{{ t('workflow.dependItems') }}</div>
            <template v-for="(group, gIdx) in (content.dependence?.dependTaskList || [])" :key="gIdx">
              <div v-if="gIdx > 0" class="tid-relation">
                <span class="tid-relation__line" />
                <el-tag size="small" effect="plain">{{ content.dependence?.relation || 'AND' }}</el-tag>
                <span class="tid-relation__line" />
              </div>
              <div class="tid-dep-group">
                <div class="tid-dep-group__title">{{ t('workflow.dependGroup') }} {{ gIdx + 1 }}</div>
                <template v-for="(item, iIdx) in group.dependItemList" :key="iIdx">
                  <div v-if="iIdx > 0" class="tid-relation tid-relation--inner">
                    <span class="tid-relation__line" />
                    <el-tag size="small" effect="plain">{{ group.relation || 'AND' }}</el-tag>
                    <span class="tid-relation__line" />
                  </div>
                  <div class="tid-dep-entry">
                    <el-descriptions :column="2" size="small" border>
                      <el-descriptions-item :label="t('workflow.selectProject')">{{ depName('p', item.projectCode) }}</el-descriptions-item>
                      <el-descriptions-item :label="t('workflow.dependOnWorkflow')">{{ depName('w', item.definitionCode) }}</el-descriptions-item>
                      <el-descriptions-item :label="t('workflow.dependOnTask')">{{ item.depTaskCode === 0 ? t('workflow.allTasks') : depName('t', item.depTaskCode) }}</el-descriptions-item>
                      <el-descriptions-item :label="t('workflow.timeCycle')">{{ item.cycle || '-' }} / {{ item.dateValue || '-' }}</el-descriptions-item>
                    </el-descriptions>
                  </div>
                </template>
              </div>
            </template>

            <div class="tid-section" style="margin-top:16px">{{ t('workflow.dependSettings') }}</div>
            <el-descriptions :column="3" size="small" border>
              <el-descriptions-item :label="t('workflow.checkInterval')">{{ content.dependence?.checkInterval || '-' }}s</el-descriptions-item>
              <el-descriptions-item :label="t('workflow.failurePolicy')">{{ content.dependence?.failurePolicy || '-' }}</el-descriptions-item>
              <el-descriptions-item v-if="content.dependence?.failurePolicy === 'DEPENDENT_FAILURE_WAITING'" :label="t('workflow.failureWaitingTime')">{{ content.dependence?.failureWaitingTime || '-' }}min</el-descriptions-item>
            </el-descriptions>
          </el-form>
        </template>

        <!-- SEATUNNEL -->
        <template v-else-if="taskType === 'SEATUNNEL'">
          <div class="tid-section-label">SeaTunnel Config</div>
          <MonacoInput :model-value="content.content || content.config || content.seatunnelConfig || node.content || ''" task-type="SEATUNNEL" height="300px" read-only />
        </template>

        <!-- JAR Tasks -->
        <template v-else-if="isJarTask">
          <el-form label-position="top" class="tid-form">
            <div class="tid-section">{{ t('jar.program') }}</div>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item :label="t('jar.mainClass')">
                  <el-input :model-value="content.mainClass || '-'" disabled />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="t('jar.jarPath')">
                  <el-input :model-value="content.jarPath || '-'" disabled />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item :label="t('jar.args')">
              <el-input :model-value="content.args || '-'" disabled type="textarea" :rows="2" />
            </el-form-item>
            <div class="tid-section">{{ t('jar.deploy') }}</div>
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item v-if="content.master" label="Master">{{ content.master }}</el-descriptions-item>

              <el-descriptions-item :label="t('jar.deployMode')">{{ content.deployMode || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('jar.appName')">{{ content.appName || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="t('jar.queue')">{{ content.queue || '-' }}</el-descriptions-item>
            </el-descriptions>
            <div class="tid-section">{{ t('jar.resources') }}</div>
            <el-descriptions :column="3" size="small" border>
              <el-descriptions-item v-if="content.driverCores" :label="t('jar.driverCores')">{{ content.driverCores }}</el-descriptions-item>
              <el-descriptions-item v-if="content.driverMemory" :label="t('jar.driverMemory')">{{ content.driverMemory }}</el-descriptions-item>
              <el-descriptions-item v-if="content.executorInstances" :label="t('jar.executorInstances')">{{ content.executorInstances }}</el-descriptions-item>
              <el-descriptions-item v-if="content.executorCores" :label="t('jar.executorCores')">{{ content.executorCores }}</el-descriptions-item>
              <el-descriptions-item v-if="content.executorMemory" :label="t('jar.executorMemory')">{{ content.executorMemory }}</el-descriptions-item>
              <el-descriptions-item v-if="content.parallelism" :label="t('jar.parallelism')">{{ content.parallelism }}</el-descriptions-item>
              <el-descriptions-item v-if="content.jobManagerMemory" :label="t('jar.jobManagerMemory')">{{ content.jobManagerMemory }}</el-descriptions-item>
              <el-descriptions-item v-if="content.taskManagerMemory" :label="t('jar.taskManagerMemory')">{{ content.taskManagerMemory }}</el-descriptions-item>
            </el-descriptions>
          </el-form>
        </template>

        <!-- Fallback: raw JSON -->
        <template v-else>
          <div class="tid-code"><pre>{{ JSON.stringify(content, null, 2) }}</pre></div>
        </template>
      </el-tab-pane>

      <!-- Tab: Params -->
      <el-tab-pane v-if="params || varPool" :label="t('instance.params')">
        <template v-if="params">
          <div class="tid-section">{{ t('instance.runtimeParams') }}</div>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item v-for="(v, k) in params" :key="k" :label="String(k)">{{ v }}</el-descriptions-item>
          </el-descriptions>
        </template>
        <template v-if="varPool">
          <div class="tid-section" :style="params ? 'margin-top:16px' : ''">{{ t('instance.varPool') }}</div>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item v-for="(v, k) in varPool" :key="k" :label="String(k)">{{ v }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </el-tab-pane>

      <!-- Tab: Result -->
      <el-tab-pane v-if="hasResult" :label="t('instance.result')" name="result" lazy>
        <div v-if="resultLoading" v-loading="true" style="height:200px" />
        <template v-else-if="resultColumns.length">
          <el-table :data="resultRows" size="small" border stripe max-height="320" :scrollbar-always-on="true">
            <el-table-column type="index" :label="'#'" width="50" :index="(i: number) => resultOffset + i + 1" />
            <el-table-column v-for="col in resultColumns" :key="col" :prop="col" :label="col" min-width="120" show-overflow-tooltip />
          </el-table>
          <div v-if="resultTotal > resultLimit" style="display:flex;justify-content:space-between;align-items:center;margin-top:8px;font-size:12px;color:var(--r-text-muted)">
            <span>{{ resultOffset + 1 }}-{{ Math.min(resultOffset + resultLimit, resultTotal) }} / {{ resultTotal }} rows</span>
            <el-pagination
              small layout="prev, pager, next" :total="resultTotal" :page-size="resultLimit"
              :current-page="Math.floor(resultOffset / resultLimit) + 1"
              @current-change="(p: number) => fetchResult((p - 1) * resultLimit)" />
          </div>
        </template>
        <div v-else style="color:var(--r-text-muted);text-align:center;padding:20px">{{ t('instance.noResult') }}</div>
      </el-tab-pane>

      <!-- Tab: Log -->
      <el-tab-pane :label="t('instance.log')" name="log" lazy>
        <div v-if="logLoading" v-loading="true" style="height:200px" />
        <div v-else class="tid-log"><pre>{{ logContent || t('instance.noLogs') }}</pre></div>
      </el-tab-pane>

      <!-- Tab: Error -->
      <el-tab-pane v-if="node.errorMessage" :label="t('instance.error')">
        <div class="tid-code tid-code--error"><pre>{{ node.errorMessage }}</pre></div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.tid-desc { margin-bottom: 16px; }

.tid-tabs {
  :deep(.el-tabs__header) { margin: 0; }
  :deep(.el-tabs__content) { padding: 0; }
  :deep(.el-tab-pane) { padding: 16px 20px; max-height: 460px; overflow: auto; }
}

.tid-form {
  :deep(.el-form-item) { margin-bottom: 14px; }
  :deep(.el-form-item__label) { font-size: 13px; color: var(--r-text-secondary); padding-bottom: 4px; }
}

.tid-section {
  font-size: 12px; font-weight: 600; color: var(--r-accent); text-transform: uppercase;
  letter-spacing: 0.5px; margin: 0 0 12px; padding-bottom: 6px;
  border-bottom: 1px solid var(--r-accent-bg);
}

.tid-section-label {
  font-size: 13px; font-weight: 500; color: var(--r-text-secondary); margin-bottom: 6px;
}

.tid-relation {
  display: flex; align-items: center; gap: 8px; margin: 8px 0;
}
.tid-relation__line { flex: 1; height: 1px; background: var(--r-border); }
.tid-relation--inner { margin: 4px 0; }

.tid-dep-group {
  padding: 10px 12px; margin-bottom: 8px;
  background: var(--r-bg-panel); border: 1px solid var(--r-border); border-radius: 6px;
}
.tid-dep-group__title {
  font-size: 13px; font-weight: 500; color: var(--r-text-secondary); margin-bottom: 8px;
}

.tid-dep-entry {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; margin-bottom: 6px;
  background: var(--r-bg-card); border: 1px solid var(--r-border); border-radius: 4px;
}
.tid-dep-entry__label { flex: 1; font-size: 13px; color: var(--r-text-primary); }

.tid-switch-row {
  display: flex; align-items: center; gap: 8px; margin-bottom: 8px;
}
.tid-switch-cond { flex: 1; }

.tid-code {
  background: var(--r-bg-panel); border: 1px solid var(--r-border); border-radius: 6px;
  padding: 12px; max-height: 260px; overflow: auto;
  pre { margin: 0; font-family: var(--r-font-mono); font-size: 12px; line-height: 1.6; color: var(--r-text-primary); white-space: pre-wrap; word-break: break-all; }
}
.tid-code--error {
  background: var(--r-danger-bg); border-color: var(--r-danger-border);
  pre { color: var(--r-danger); }
}

.tid-log {
  background: var(--r-bg-code); border-radius: 6px; padding: 14px; max-height: 400px; overflow: auto;
  pre { margin: 0; font-family: var(--r-font-mono); font-size: 12px; line-height: 1.7; color: var(--r-text-primary); white-space: pre-wrap; word-break: break-all; }
}
</style>
