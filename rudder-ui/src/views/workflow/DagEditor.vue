<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Graph, Shape, Node } from '@antv/x6'
import { getWorkflowDefinition, updateWorkflowDefinition, runWorkflowDefinition, listTaskDefinitions, listWorkflowDefinitions } from '@/api/workflow'
import { listProjects } from '@/api/workspace'
import { getTaskTypes, generateCodes, type TaskTypeDef } from '@/api/config'
import { useDatasourceStore } from '@/stores/datasource'
import { useWorkspaceStore } from '@/stores/workspace'
import TaskIcon from '@/components/TaskIcon.vue'
import MonacoInput from '@/components/MonacoInput.vue'
import HttpTaskEditor from '@/views/ide/HttpTaskEditor.vue'
import { defaultHttpParams } from '@/views/ide/httpParams'
import { getTaskIconUrl } from '@/utils/taskIconUrl'
import { cv } from '@/utils/cssVar'
import { useThemeStore } from '@/stores/theme'

const props = defineProps<{ workflowDefinitionCode?: string | number; readOnly?: boolean }>()

const { t } = useI18n()
const route = useRoute()
const workflowDefinitionCode = computed(() => props.workflowDefinitionCode ?? route.params.workflowDefinitionCode as string)
const datasourceStore = useDatasourceStore()
const workspaceStore = useWorkspaceStore()
const themeStore = useThemeStore()

const workspaceId = computed(() => workspaceStore.currentWorkspace?.id ?? Number(route.params.workspaceId))
const projectCode = computed(() => route.params.projectCode as string)

const workflowName = ref('')
const loading = ref(false)
const paletteTarget = ref(false)
const saving = ref(false)
const taskTypes = ref<TaskTypeDef[]>([])
let graph: Graph | null = null
let debugPollTimer: ReturnType<typeof setInterval> | null = null
const ideEditorRef = ref<InstanceType<typeof MonacoInput> | null>(null)

// ===== Node categories =====
const nodeCategories = ref<{ name: string; types: TaskTypeDef[] }[]>([])
function buildCategories(types: TaskTypeDef[]) {
  const batchTypes = types.filter(tt => !tt.executionModes || tt.executionModes.includes('BATCH'))
  const map = new Map<string, TaskTypeDef[]>()
  for (const tt of batchTypes) {
    const cat = tt.category || 'OTHER'
    if (!map.has(cat)) map.set(cat, [])
    map.get(cat)!.push(tt)
  }
  nodeCategories.value = Array.from(map.entries()).map(([name, types]) => ({ name, types }))
}
const categoryLabels = computed<Record<string, string>>(() => ({
  SQL: t('workflow.categorySql'),
  JAR: t('workflow.categoryJar'),
  SCRIPT: t('workflow.categoryScript'),
  DATA_INTEGRATION: t('workflow.categoryDataIntegration'),
  CONTROL: t('workflow.categoryControl'),
  OTHER: t('workflow.categoryOther'),
}))
const dataTypes = ['VARCHAR', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DATE', 'TIME', 'TIMESTAMP', 'BOOLEAN', 'LIST', 'FILE']
const categoryColors: Record<string, string> = { SQL: 'var(--r-accent)', JAR: 'var(--r-purple)', SCRIPT: 'var(--r-success)', DATA_INTEGRATION: 'var(--r-cyan)', CONTROL: 'var(--r-orange)' }

// ===== Control flow data =====
const allProjects = ref<{ id: number; code: string; name: string }[]>([])
const allWorkflows = ref<{ id: number; code: string; name: string }[]>([])
const dagNodeOptions = computed(() => {
  if (!graph) return []
  const nodes: { id: string; label: string }[] = []
  for (const cell of graph.getCells()) {
    if (cell.isNode() && cell.id !== editingNodeId.value) {
      nodes.push({ id: cell.id, label: (cell.getAttrs()?.label?.text as string) || cell.id })
    }
  }
  return nodes
})
/** 当前编辑节点的上游节点（通过连线确定） */
const upstreamNodeOptions = computed(() => {
  if (!graph || !editingNodeId.value) return []
  const upIds = new Set<string>()
  for (const edge of graph.getEdges()) {
    if (edge.getTargetCellId() === editingNodeId.value) {
      upIds.add(edge.getSourceCellId())
    }
  }
  return dagNodeOptions.value.filter(n => upIds.has(n.id))
})
/** 当前编辑节点的下游节点（通过连线确定） */
const downstreamNodeOptions = computed(() => {
  if (!graph || !editingNodeId.value) return []
  const downIds = new Set<string>()
  for (const edge of graph.getEdges()) {
    if (edge.getSourceCellId() === editingNodeId.value) {
      downIds.add(edge.getTargetCellId())
    }
  }
  return dagNodeOptions.value.filter(n => downIds.has(n.id))
})
// DEPENDENT: 按项目加载工作流，按工作流加载任务（存到每个 item 上）
const depWorkflows = ref<Record<number, { code: string; name: string }[]>>({})
const depTasks = ref<Record<number, { code: string; name: string }[]>>({})
async function onDependProjectChange(item: any) {
  item.definitionCode = null
  item.depTaskCode = 0
  if (!item.projectCode) return
  if (!depWorkflows.value[item.projectCode]) {
    try {
      const { data } = await listWorkflowDefinitions(workspaceId.value!, item.projectCode, { pageSize: 9999 })
      depWorkflows.value[item.projectCode] = (data?.records ?? data ?? []).map((w: any) => ({ code: String(w.code), name: w.name }))
    } catch { depWorkflows.value[item.projectCode] = [] }
  }
}
async function onDependWorkflowChange(item: any) {
  item.depTaskCode = 0
  if (!item.definitionCode) return
  if (!depTasks.value[item.definitionCode]) {
    try {
      const { data } = await listTaskDefinitions(workspaceId.value, item.projectCode, item.definitionCode)
      depTasks.value[item.definitionCode] = (data ?? []).map((td: any) => ({ code: String(td.code), name: td.name }))
    } catch { depTasks.value[item.definitionCode] = [] }
  }
}

// ===== Task Modal =====
const modalVisible = ref(false)
const isNewNode = ref(false)
const editingNodeId = ref<string | null>(null)

interface Property {
  prop: string
  direct: 'IN' | 'OUT'
  type: string
  value: string
}

const formData = reactive({
  label: '',
  taskType: '',
  description: '',
  isEnabled: true,
  priority: 'MEDIUM',
  delayTime: 0,
  retryTimes: 0,
  retryInterval: 30,
  timeoutEnabled: false,
  timeoutNotifyStrategy: ['WARN'] as string[],
  timeout: 30,
  // SQL tasks
  datasourceId: null as number | null,
  preStatements: [] as string[],
  postStatements: [] as string[],
  // JAR tasks
  mainClass: '',
  jarPath: '',
  args: '',
  master: 'yarn',
  deployMode: 'cluster',
  appName: '',
  queue: 'default',
  driverCores: 1,
  driverMemory: '1g',
  executorCores: 2,
  executorMemory: '2g',
  executorInstances: 2,
  parallelism: 2,
  jobManagerMemory: '1g',
  taskManagerMemory: '2g',
  engineParams: [] as { key: string; value: string }[],
  // Script tasks (Python/Shell)
  scriptContent: '',
  // Data Integration: SEATUNNEL (Zeta Engine)
  seatunnelConfig: '',
  seatunnelDeployMode: 'cluster',
  httpConfig: '',
  // Control: CONDITION (DS: ConditionsParameters)
  conditionDependence: {
    dependTaskList: [] as { dependItemList: { depTaskCode: string | null; status: string }[]; relation: string }[],
    relation: 'AND',
  },
  conditionResult: {
    successNode: [] as string[],
    failedNode: [] as string[],
  },
  // Control: SUB_WORKFLOW (DS: SubWorkflowParameters)
  workflowDefinitionCode: null as string | null,
  // Control: SWITCH (DS: SwitchParameters)
  switchResult: {
    dependTaskList: [] as { condition: string; nextNode: string | null }[],
    nextNode: null as string | null,
  },
  // Control: DEPENDENT (DS: DependentParameters)
  dependence: {
    dependTaskList: [] as { dependItemList: { dependentType: string; projectCode: number | null; definitionCode: number | null; depTaskCode: number; cycle: string; dateValue: string; parameterPassing: boolean }[]; relation: string }[],
    relation: 'AND',
    checkInterval: 10,
    failurePolicy: 'DEPENDENT_FAILURE_FAILURE',
    failureWaitingTime: 1,
  },
  // Params
  inputParams: [] as Property[],
  outputParams: [] as Property[],
})

const editingTaskTypeDef = computed(() => taskTypes.value.find(tt => tt.value === formData.taskType))
const isStreamCapable = computed(() => editingTaskTypeDef.value?.executionModes?.includes('STREAMING') ?? false)

const filteredDatasources = computed(() => {
  const dsType = editingTaskTypeDef.value?.datasourceType
  if (!dsType) return []
  return datasourceStore.byType(dsType)
})

// Single merged param list — stored flat, split back on confirmModal
const customParams = ref<Property[]>([])

function syncCustomParamsFromForm() {
  customParams.value = [
    ...formData.inputParams.map(p => ({ ...p })),
    ...formData.outputParams.map(p => ({ ...p })),
  ]
}

function syncCustomParamsToForm() {
  formData.inputParams = customParams.value.filter(p => p.direct === 'IN')
  formData.outputParams = customParams.value.filter(p => p.direct === 'OUT')
}

// Alias for template
const allParams = customParams

function addCustomParam() {
  customParams.value.push({ prop: '', direct: 'IN', type: 'VARCHAR', value: '' })
}

function removeCustomParam(idx: number) {
  customParams.value.splice(idx, 1)
}

function resetForm() {
  Object.assign(formData, {
    label: '', taskType: '', description: '', isEnabled: true, priority: 'MEDIUM',
    delayTime: 0, retryTimes: 0, retryInterval: 30,
    timeoutEnabled: false, timeoutNotifyStrategy: ['WARN'], timeout: 30,
    datasourceId: null, preStatements: [], postStatements: [],
    mainClass: '', jarPath: '', args: '',
    master: 'yarn', deployMode: 'cluster', appName: '', queue: 'default',
    driverCores: 1, driverMemory: '1g', executorCores: 2, executorMemory: '2g', executorInstances: 2,
    parallelism: 2, jobManagerMemory: '1g', taskManagerMemory: '2g', engineParams: [],
    scriptContent: '',
    seatunnelConfig: '', seatunnelDeployMode: 'cluster',
    httpConfig: '',
    conditionDependence: { dependTaskList: [], relation: 'AND' },
    conditionResult: { successNode: [], failedNode: [] },
    workflowDefinitionCode: null,
    switchResult: { dependTaskList: [], nextNode: null },
    dependence: { dependTaskList: [], relation: 'AND', checkInterval: 10, failurePolicy: 'DEPENDENT_FAILURE_FAILURE', failureWaitingTime: 1 },
    inputParams: [], outputParams: [],
  })
  debugRunning.value = false
  debugStatus.value = ''
  debugLog.value = ''
  debugTab.value = 'log'
  debugResultCols.value = []
  debugResultRows.value = []
}

function openModalForNew(nodeId: string, taskType: TaskTypeDef) {
  resetForm()
  formData.label = taskType.label
  formData.taskType = taskType.value
  editingNodeId.value = nodeId
  isNewNode.value = true
  syncCustomParamsFromForm()
  modalVisible.value = true
}

function openModalForEdit(nodeId: string) {
  if (!graph) return
  const node = graph.getCellById(nodeId) as Node
  if (!node) return
  const data = node.getData() || {}
  Object.assign(formData, {
    label: node.getAttrs()?.label?.text || '',
    taskType: data.taskType || '',
    description: data.description || '',
    isEnabled: data.isEnabled ?? true,
    priority: data.priority || 'MEDIUM',
    delayTime: data.delayTime ?? 0,
    retryTimes: data.retryTimes ?? 0,
    retryInterval: data.retryInterval ?? 30,
    timeoutEnabled: data.timeoutEnabled ?? false,
    timeoutNotifyStrategy: data.timeoutNotifyStrategy ?? ['WARN'],
    timeout: data.timeout ?? 30,
    datasourceId: data.datasourceId || null,
    preStatements: Array.isArray(data.preStatements) ? data.preStatements : [],
    postStatements: Array.isArray(data.postStatements) ? data.postStatements : [],
    mainClass: data.mainClass || '',
    jarPath: data.jarPath || '',
    args: data.args || '',
    master: data.master || 'yarn',
    deployMode: data.deployMode || 'cluster',
    appName: data.appName || '',
    queue: data.queue || 'default',
    driverCores: data.driverCores ?? 1,
    driverMemory: data.driverMemory || '1g',
    executorCores: data.executorCores ?? 2,
    executorMemory: data.executorMemory || '2g',
    executorInstances: data.executorInstances ?? 2,
    parallelism: data.parallelism ?? 2,
    jobManagerMemory: data.jobManagerMemory || '1g',
    taskManagerMemory: data.taskManagerMemory || '2g',
    engineParams: Array.isArray(data.engineParams) ? data.engineParams : [],
    scriptContent: data.scriptContent || '',
    seatunnelConfig: data.seatunnelConfig || '',
    seatunnelDeployMode: data.seatunnelDeployMode || 'cluster',
    conditionDependence: data.conditionDependence ?? { dependTaskList: [], relation: 'AND' },
    conditionResult: data.conditionResult ?? { successNode: [], failedNode: [] },
    workflowDefinitionCode: data.workflowDefinitionCode != null ? String(data.workflowDefinitionCode) : null,
    switchResult: data.switchResult ?? { dependTaskList: [], nextNode: null },
    dependence: data.dependence ?? { dependTaskList: [], relation: 'AND', checkInterval: 10, failurePolicy: 'DEPENDENT_FAILURE_FAILURE', failureWaitingTime: 1 },
    inputParams: Array.isArray(data.inputParams) ? data.inputParams : [],
    outputParams: Array.isArray(data.outputParams) ? data.outputParams : [],
    httpConfig: data.httpConfig || '',
  })
  editingNodeId.value = nodeId
  isNewNode.value = false
  syncCustomParamsFromForm()
  modalVisible.value = true

  // DEPENDENT: 预加载已有依赖项的工作流和任务列表
  if (data.taskType === 'DEPENDENT' && formData.dependence?.dependTaskList) {
    for (const group of formData.dependence.dependTaskList) {
      for (const item of (group.dependItemList || [])) {
        const pc = item.projectCode
        const dc = item.definitionCode
        if (pc && !depWorkflows.value[pc]) {
          listWorkflowDefinitions(workspaceId.value!, pc, { pageSize: 9999 }).then(({ data: wfData }) => {
            depWorkflows.value[pc] = (wfData?.records ?? wfData ?? []).map((w: any) => ({ code: String(w.code), name: w.name }))
          }).catch(() => {})
        }
        if (dc && !depTasks.value[dc]) {
          listTaskDefinitions(workspaceId.value, pc!, dc).then(({ data: tdData }) => {
            depTasks.value[dc] = (tdData ?? []).map((td: any) => ({ code: String(td.code), name: td.name }))
          }).catch(() => {})
        }
      }
    }
  }
}

function confirmModal() {
  if (!graph || !editingNodeId.value) return

  // SUB_WORKFLOW 必填校验
  if (formData.taskType === 'SUB_WORKFLOW' && !formData.workflowDefinitionCode) {
    ElMessage.warning(t('workflow.subWorkflowRequired'))
    return
  }

  // SWITCH 必填校验：有下游连线时，至少配置一个条件分支或默认分支
  if (formData.taskType === 'SWITCH' && downstreamNodeOptions.value.length > 0) {
    const hasConditions = formData.switchResult.dependTaskList.some(c => c.condition?.trim() && c.nextNode)
    const hasDefault = !!formData.switchResult.nextNode
    if (!hasConditions && !hasDefault) {
      ElMessage.warning(t('workflow.switchRequired'))
      return
    }
  }

  // CONDITION 必填校验：有连线时才校验对应部分
  if (formData.taskType === 'CONDITION') {
    if (upstreamNodeOptions.value.length > 0) {
      const hasDeps = formData.conditionDependence.dependTaskList.some(g => g.dependItemList?.some(i => i.depTaskCode))
      if (!hasDeps) {
        ElMessage.warning(t('workflow.conditionDepsRequired'))
        return
      }
    }
    if (downstreamNodeOptions.value.length > 0) {
      if (!formData.conditionResult.successNode?.length && !formData.conditionResult.failedNode?.length) {
        ElMessage.warning(t('workflow.conditionBranchRequired'))
        return
      }
    }
  }

  // DEPENDENT 必填校验：至少有一个依赖项
  if (formData.taskType === 'DEPENDENT') {
    const hasDeps = formData.dependence.dependTaskList.some(g => g.dependItemList?.some(i => i.definitionCode))
    if (!hasDeps) {
      ElMessage.warning(t('workflow.dependentRequired'))
      return
    }
  }

  // SEATUNNEL 必填校验
  if (formData.taskType === 'SEATUNNEL' && !formData.seatunnelConfig?.trim()) {
    ElMessage.warning(t('workflow.seatunnelConfigRequired'))
    return
  }

  syncCustomParamsToForm()
  const node = graph.getCellById(editingNodeId.value) as Node
  if (!node) return

  // Update node visual
  node.attr('label/text', formData.label)

  // Apply skip style if isEnabled is false
  if (formData.isEnabled) {
    node.attr('body/fill', cv('--r-bg-card'))
    node.attr('body/opacity', 1)
    node.attr('label/fill', cv('--r-text-primary'))
    node.attr('typeLabel/fill', cv('--r-text-muted'))
    node.attr('icon/opacity', 1)
  } else {
    node.attr('body/fill', cv('--r-bg-hover'))
    node.attr('body/opacity', 0.6)
    node.attr('label/fill', cv('--r-text-disabled'))
    node.attr('typeLabel/fill', cv('--r-text-disabled'))
    node.attr('icon/opacity', 0.3)
  }

  // Update node data (preserve _taskCode and scriptCode)
  const prevData = node.getData() || {}
  node.setData({
    _taskCode: prevData._taskCode,
    scriptCode: prevData.scriptCode,
    taskType: formData.taskType, description: formData.description,
    isEnabled: formData.isEnabled, priority: formData.priority,
    delayTime: formData.delayTime, retryTimes: formData.retryTimes, retryInterval: formData.retryInterval,
    timeoutEnabled: formData.timeoutEnabled, timeoutNotifyStrategy: formData.timeoutNotifyStrategy, timeout: formData.timeout,
    datasourceId: formData.datasourceId,
    preStatements: formData.preStatements, postStatements: formData.postStatements,
    mainClass: formData.mainClass, jarPath: formData.jarPath, args: formData.args,
    master: formData.master, deployMode: formData.deployMode, appName: formData.appName, queue: formData.queue,
    driverCores: formData.driverCores, driverMemory: formData.driverMemory,
    executorCores: formData.executorCores, executorMemory: formData.executorMemory, executorInstances: formData.executorInstances,
    parallelism: formData.parallelism, jobManagerMemory: formData.jobManagerMemory, taskManagerMemory: formData.taskManagerMemory,
    engineParams: formData.engineParams,
    scriptContent: formData.scriptContent,
    seatunnelConfig: formData.seatunnelConfig, seatunnelDeployMode: formData.seatunnelDeployMode,
    httpConfig: formData.httpConfig,
    conditionDependence: formData.conditionDependence, conditionResult: formData.conditionResult,
    workflowDefinitionCode: formData.workflowDefinitionCode,
    switchResult: formData.switchResult,
    dependence: formData.dependence,
    inputParams: formData.inputParams, outputParams: formData.outputParams,
  })

  modalVisible.value = false
  editingNodeId.value = null
}

function cancelModal() {
  if (isNewNode.value && editingNodeId.value && graph) {
    // Remove newly created node if user cancels
    const node = graph.getCellById(editingNodeId.value)
    if (node) graph.removeCell(node)
  }
  modalVisible.value = false
  editingNodeId.value = null
}

// ===== Debug execution =====
const debugRunning = ref(false)
const debugStatus = ref('')
const debugLog = ref('')
const debugTab = ref<'log' | 'result'>('log')
const debugResultCols = ref<string[]>([])
const debugResultRows = ref<Record<string, any>[]>([])

// ===== Context Menu =====
const ctxMenu = reactive({ visible: false, x: 0, y: 0, type: '' as 'node' | 'edge' | '', targetId: '' })

function showContextMenu(e: MouseEvent, type: 'node' | 'edge', targetId: string) {
  e.preventDefault()
  e.stopPropagation()
  ctxMenu.visible = true
  ctxMenu.x = e.clientX
  ctxMenu.y = e.clientY
  ctxMenu.type = type
  ctxMenu.targetId = targetId
}

function hideContextMenu() {
  ctxMenu.visible = false
}

function ctxDelete() {
  if (!graph) return
  if (ctxMenu.type === 'node') {
    graph.removeNode(ctxMenu.targetId)
  } else if (ctxMenu.type === 'edge') {
    graph.removeEdge(ctxMenu.targetId)
  }
  hideContextMenu()
}

async function handleDebugRun() {
  if (editingTaskTypeDef.value?.needsDatasource && !formData.datasourceId) {
    ElMessage.warning(t('ide.selectDatasourceFirst'))
    return
  }

  debugRunning.value = true
  debugLog.value = t('workflow.debugExecuting') + '\n'
  debugStatus.value = 'RUNNING'
  debugTab.value = 'log'
  debugResultCols.value = []
  debugResultRows.value = []

  try {
    const { executeDirect } = await import('@/api/script')
    const selectedSql = ideEditorRef.value?.getSelection?.() || ''
    const sqlToRun = selectedSql.trim() || formData.scriptContent
    // 传入 workflowDefinitionCode 和 taskDefinitionCode，让后端同时创建 WorkflowInstance
    const nodeData = editingNodeId.value && graph ? graph.getCellById(editingNodeId.value)?.getData() : null
    const { data } = await executeDirect({
      taskType: formData.taskType, datasourceId: formData.datasourceId, sql: sqlToRun,
      workflowDefinitionCode: workflowDefinitionCode.value ? Number(workflowDefinitionCode.value) : undefined,
      taskDefinitionCode: nodeData?._taskCode ? Number(nodeData._taskCode) : undefined,
    })
    if (!data?.id) { debugStatus.value = 'FAILED'; debugLog.value += t('workflow.debugNoExecutionId') + '\n'; return }

    const { getExecution } = await import('@/api/script')
    if (debugPollTimer) clearInterval(debugPollTimer)
    debugPollTimer = setInterval(async () => {
      try {
        const res = await getExecution(data.id)
        const exec = res.data
        debugStatus.value = exec.status
        if (exec.status === 'SUCCESS') {
          clearInterval(debugPollTimer!); debugPollTimer = null
          debugLog.value += t('workflow.debugCompleted') + '\n'
          try {
            const { getExecutionResult } = await import('@/api/script')
            const { data: r } = await getExecutionResult(data.id)
            debugResultCols.value = r?.columns ?? []
            debugResultRows.value = r?.rows ?? []
            if (debugResultCols.value.length > 0) debugTab.value = 'result'
          } catch {}
        } else if (exec.status === 'FAILED') {
          clearInterval(debugPollTimer!); debugPollTimer = null
          debugLog.value += t('workflow.debugFailed', { reason: exec.errorMessage || t('workflow.debugUnknownError') }) + '\n'
        }
      } catch { if (debugPollTimer) { clearInterval(debugPollTimer); debugPollTimer = null } }
    }, 1500)
  } catch (e: any) {
    debugStatus.value = 'FAILED'
    debugLog.value += t('workflow.debugError', { msg: e.message }) + '\n'
  } finally {
    debugRunning.value = false
  }
}

// ===== Graph =====
function initGraph() {
  const container = document.getElementById('dag-canvas')
  if (!container) return

  graph = new Graph({
    container,
    autoResize: true,
    background: { color: cv('--r-bg-panel') },
    grid: { visible: true, type: 'dot', size: 16, args: { color: cv('--r-border'), thickness: 1 } },
    panning: { enabled: true, eventTypes: ['leftMouseDown'] },
    mousewheel: { enabled: true, factor: 1.05, modifiers: ['ctrl', 'meta'] },
    // fn 形式让 X6 每次交互重评,响应 prop 变化(返回 false = 全禁)
    interacting: () => !props.readOnly,
    connecting: {
      router: 'manhattan',
      connector: { name: 'rounded', args: { radius: 8 } },
      snap: { radius: 30 },
      allowBlank: false, allowLoop: false, allowMulti: false, highlight: true,
      createEdge() {
        return new Shape.Edge({
          attrs: { line: { stroke: cv('--r-border-dark'), strokeWidth: 1.5, targetMarker: { name: 'block', width: 8, height: 6 } } },
          zIndex: 0,
        })
      },
      validateConnection({ sourceCell, targetCell, targetMagnet }) {
        if (!targetMagnet || !sourceCell || !targetCell) return false
        if (sourceCell.id === targetCell.id) return false
        // DAG 环检测：如果从 target 能沿已有边到达 source，则此边会成环
        const adj = new Map<string, string[]>()
        for (const edge of graph!.getEdges()) {
          const s = edge.getSourceCellId()
          if (!adj.has(s)) adj.set(s, [])
          adj.get(s)!.push(edge.getTargetCellId())
        }
        const visited = new Set<string>()
        const stack = [targetCell.id]
        while (stack.length) {
          const cur = stack.pop()!
          if (cur === sourceCell.id) return false
          if (visited.has(cur)) continue
          visited.add(cur)
          for (const next of adj.get(cur) || []) stack.push(next)
        }
        return true
      },
    },
  })

  // Double-click node → edit modal (readOnly 下 modal 内 input 已 disabled,只是查看)
  graph.on('node:dblclick', ({ node }) => {
    openModalForEdit(node.id)
  })

  // 右键菜单含删除/复制等修改操作,只读模式不展示
  graph.on('node:contextmenu', ({ e, node }) => {
    if (props.readOnly) return
    showContextMenu(e.originalEvent as MouseEvent, 'node', node.id)
  })

  graph.on('edge:contextmenu', ({ e, edge }) => {
    if (props.readOnly) return
    showContextMenu(e.originalEvent as MouseEvent, 'edge', edge.id)
  })

  // Click canvas → hide context menu
  graph.on('blank:click', () => hideContextMenu())
  graph.on('node:click', () => hideContextMenu())
  graph.on('edge:click', () => hideContextMenu())

  // Delete key
  document.addEventListener('keydown', handleKeyDown)
}

function handleKeyDown(e: KeyboardEvent) {
  if ((e.key === 'Delete' || e.key === 'Backspace') && !modalVisible.value) {
    if ((e.target as HTMLElement)?.tagName === 'INPUT' || (e.target as HTMLElement)?.tagName === 'TEXTAREA') return
    // No built-in selection in X6 v2 base — user should use double-click to edit/delete via modal
  }
}

// ===== Theme reactivity =====
watch(() => themeStore.isDark, () => {
  nextTick(() => {
    if (!graph) return
    const bgPanel = cv('--r-bg-panel')
    const bgCard = cv('--r-bg-card')
    const bgHover = cv('--r-bg-hover')
    const border = cv('--r-border')
    const borderDark = cv('--r-border-dark')
    const textPrimary = cv('--r-text-primary')
    const textMuted = cv('--r-text-muted')
    const textDisabled = cv('--r-text-disabled')

    graph.drawBackground({ color: bgPanel })
    for (const node of graph.getNodes()) {
      const data = node.getData() || {}
      const isEnabled = data.isEnabled !== false
      node.attr('body/fill', isEnabled ? bgCard : bgHover)
      node.attr('body/stroke', border)
      node.attr('label/fill', isEnabled ? textPrimary : textDisabled)
      node.attr('typeLabel/fill', isEnabled ? textMuted : textDisabled)
      for (const p of node.getPorts()) {
        if (!p.id) continue
        node.setPortProp(p.id, 'attrs/circle/fill', bgCard)
        node.setPortProp(p.id, 'attrs/circle/stroke', borderDark)
      }
    }
    for (const edge of graph.getEdges()) {
      edge.attr('line/stroke', borderDark)
    }
  })
})

// ===== Drag & Drop =====
function handleDragStart(event: DragEvent, nodeType: TaskTypeDef) {
  if (props.readOnly) { event.preventDefault(); return }
  event.dataTransfer?.setData('application/json', JSON.stringify(nodeType))
  event.dataTransfer!.effectAllowed = 'move'
}

async function handleDrop(event: DragEvent) {
  if (!graph || props.readOnly) return
  const raw = event.dataTransfer?.getData('application/json')
  if (!raw) return

  const nodeType: TaskTypeDef = JSON.parse(raw)
  const point = graph.clientToLocal({ x: event.clientX, y: event.clientY })
  const color = categoryColors[nodeType.category] || cv('--r-accent')

  let nodeCode: string
  try {
    const { data } = await generateCodes(1)
    nodeCode = String(data[0])
  } catch { ElMessage.error(t('common.failed')); return }

  const bgCard = cv('--r-bg-card')
  const border = cv('--r-border')
  const textPrimary = cv('--r-text-primary')
  const textMuted = cv('--r-text-muted')

  const node = graph.addNode({
    id: nodeCode,
    x: point.x - 110, y: point.y - 24, width: 220, height: 48, shape: 'rect',
    markup: [
      { tagName: 'rect', selector: 'body' },
      { tagName: 'rect', selector: 'accent' },
      { tagName: 'image', selector: 'icon' },
      { tagName: 'text', selector: 'label' },
      { tagName: 'text', selector: 'typeLabel' },
    ],
    attrs: {
      body: { width: 220, height: 48, rx: 6, ry: 6, fill: bgCard, stroke: border, strokeWidth: 1, filter: { name: 'dropShadow', args: { dx: 0, dy: 2, blur: 4, opacity: 0.06 } } },
      accent: { width: 4, height: 48, rx: 6, ry: 0, fill: color },
      icon: { 'xlink:href': getTaskIconUrl(nodeType.value), width: 24, height: 24, x: 14, y: 12 },
      label: { text: nodeType.label, fontSize: 13, fontWeight: 500, fill: textPrimary, fontFamily: 'sans-serif', refX: 48, refY: 16, textAnchor: 'start' },
      typeLabel: { text: nodeType.category, fontSize: 10, fill: textMuted, fontFamily: 'sans-serif', refX: 48, refY: 34, textAnchor: 'start' },
    },
    ports: {
      groups: {
        top: { position: 'top', attrs: { circle: { r: 4, magnet: true, stroke: color, fill: bgCard, strokeWidth: 1.5 } } },
        bottom: { position: 'bottom', attrs: { circle: { r: 4, magnet: true, stroke: color, fill: bgCard, strokeWidth: 1.5 } } },
        left: { position: 'left', attrs: { circle: { r: 4, magnet: true, stroke: color, fill: bgCard, strokeWidth: 1.5 } } },
        right: { position: 'right', attrs: { circle: { r: 4, magnet: true, stroke: color, fill: bgCard, strokeWidth: 1.5 } } },
      },
      items: [{ group: 'top', id: 'in-top' }, { group: 'bottom', id: 'out-bottom' }, { group: 'left', id: 'in-left' }, { group: 'right', id: 'out-right' }],
    },
    data: { _taskCode: nodeCode, taskType: nodeType.value },
  })

  // Immediately open config modal for new node
  openModalForNew(node.id, nodeType)
}

// 内容指纹:fetch 时存,save 时回传作为乐观锁;save 成功后 refetch 拿新 hash
const contentHash = ref<string | null>(null)

// ===== Workflow actions =====
async function fetchWorkflow() {
  loading.value = true
  try {
    const [wfRes, tdRes] = await Promise.all([
      getWorkflowDefinition(workspaceId.value, projectCode.value, workflowDefinitionCode.value),
      listTaskDefinitions(workspaceId.value, projectCode.value, workflowDefinitionCode.value),
    ])
    workflowName.value = wfRes.data.name
    contentHash.value = wfRes.data.contentHash ?? null
    // Restore saved DAG + task definitions onto canvas
    if (wfRes.data.dagJson && graph) {
      try {
        const dag = JSON.parse(wfRes.data.dagJson)
        deserializeDag(dag, tdRes.data || [])
      } catch (e) { console.warn('Failed to load DAG:', e) }
    }
  } catch { ElMessage.error(t('common.failed')) }
  finally { loading.value = false }
}

/**
 * Convert X6 graph to backend format:
 * - dagJson: {nodes: [{taskCode, label, position}], edges: [{source, target}]}
 * - taskDefinitions: task config array
 *
 * X6 node id === taskCode (snowflake string)
 */
function serializeDag(): { dagJson: { nodes: any[]; edges: any[] }; taskDefinitions: any[] } {
  if (!graph) return { dagJson: { nodes: [], edges: [] }, taskDefinitions: [] }
  const cells = graph.getCells()
  const nodes: any[] = []
  const edges: any[] = []
  const taskDefinitions: any[] = []

  for (const cell of cells) {
    if (cell.isNode()) {
      const data = cell.getData() || {}
      const pos = cell.getPosition()
      const taskCode = data._taskCode || cell.id

      nodes.push({
        taskCode,
        label: cell.getAttrs()?.label?.text || '',
        position: { x: pos.x, y: pos.y },
      })

      const taskType = data.taskType || ''
      const label = String(cell.getAttrs()?.label?.text || '')

      taskDefinitions.push({
        code: taskCode,
        name: label,
        taskType,
        script: buildScript(data, taskType, label),
        description: data.description || null,
        isEnabled: data.isEnabled ?? true,
        priority: data.priority || 'MEDIUM',
        delayTime: data.delayTime || 0,
        retryTimes: data.retryTimes || 0,
        retryInterval: data.retryInterval || 30,
        timeout: data.timeout || null,
        timeoutEnabled: data.timeoutEnabled ?? false,
        timeoutNotifyStrategy: data.timeoutNotifyStrategy?.length ? data.timeoutNotifyStrategy : [],
        inputParams: data.inputParams?.length ? data.inputParams : [],
        outputParams: data.outputParams?.length ? data.outputParams : [],
      })
    } else if (cell.isEdge()) {
      const src = cell.getSourceCellId()
      const tgt = cell.getTargetCellId()
      if (src && tgt) {
        edges.push({ source: src, target: tgt })
      }
    }
  }
  return { dagJson: { nodes, edges }, taskDefinitions }
}

/**
 * 构建 Script 对象。所有任务类型都通过 Script 存储配置：
 * - SQL/SCRIPT: content = 脚本内容
 * - JAR: content = JAR 配置 JSON
 * - SEATUNNEL: content = SeaTunnel 配置
 * - 控制流: content = 控制流参数 JSON
 */
function buildScript(data: Record<string, any>, taskType: string, name: string): Record<string, any> {
  const category = taskTypes.value.find(t => t.value === taskType)?.category || ''
  const script: Record<string, any> = {
    code: data.scriptCode || null,
    name,
    taskType,
  }

  if (category === 'CONTROL') {
    // 控制流任务：将配置序列化为 content JSON
    const config: Record<string, any> = {}
    if (taskType === 'CONDITION') {
      if (data.conditionDependence?.dependTaskList?.length) config.dependence = data.conditionDependence
      if (data.conditionResult) config.conditionResult = data.conditionResult
    } else if (taskType === 'SUB_WORKFLOW') {
      if (data.workflowDefinitionCode) config.workflowDefinitionCode = Number(data.workflowDefinitionCode)
    } else if (taskType === 'SWITCH') {
      if (data.switchResult?.dependTaskList?.length || data.switchResult?.nextNode) config.switchResult = data.switchResult
    } else if (taskType === 'DEPENDENT') {
      if (data.dependence) config.dependence = data.dependence
    }
    script.content = Object.keys(config).length > 0 ? JSON.stringify(config) : ''
  } else if (category === 'JAR') {
    // JAR 任务：将各配置字段序列化为 content JSON
    const jarConfig: Record<string, any> = {}
    if (data.mainClass) jarConfig.mainClass = data.mainClass
    if (data.jarPath) jarConfig.jarPath = data.jarPath
    if (data.args) jarConfig.args = data.args
    if (data.master) jarConfig.master = data.master
    if (data.deployMode) jarConfig.deployMode = data.deployMode
    if (data.appName) jarConfig.appName = data.appName
    if (data.queue) jarConfig.queue = data.queue
    if (data.driverCores) jarConfig.driverCores = data.driverCores
    if (data.driverMemory) jarConfig.driverMemory = data.driverMemory
    if (data.executorCores) jarConfig.executorCores = data.executorCores
    if (data.executorMemory) jarConfig.executorMemory = data.executorMemory
    if (data.executorInstances) jarConfig.executorInstances = data.executorInstances
    if (data.parallelism) jarConfig.parallelism = data.parallelism
    if (data.jobManagerMemory) jarConfig.jobManagerMemory = data.jobManagerMemory
    if (data.taskManagerMemory) jarConfig.taskManagerMemory = data.taskManagerMemory
    if (data.engineParams?.length) jarConfig.engineParams = data.engineParams
    jarConfig.executionMode = data.executionMode || 'BATCH'
    script.content = JSON.stringify(jarConfig)
  } else if (taskType === 'SEATUNNEL') {
    // SeaTunnelTaskParams: { content, deployMode }
    script.content = JSON.stringify({
      content: data.seatunnelConfig || '',
      deployMode: data.seatunnelDeployMode || 'cluster',
    })
  } else if (category === 'API') {
    script.content = data.httpConfig || JSON.stringify(defaultHttpParams())
  } else if (category === 'SQL') {
    // SqlTaskParams: { sql, dataSourceId, executionMode, preStatements, postStatements, engineParams }
    const sqlConfig: Record<string, any> = { sql: data.scriptContent || '' }
    if (data.datasourceId) sqlConfig.dataSourceId = data.datasourceId
    sqlConfig.executionMode = data.executionMode || 'BATCH'
    if (data.preStatements?.length) sqlConfig.preStatements = data.preStatements
    if (data.postStatements?.length) sqlConfig.postStatements = data.postStatements
    if (data.engineParams?.length) sqlConfig.engineParams = data.engineParams
    script.content = JSON.stringify(sqlConfig)
  } else {
    // ScriptTaskParams: { content }
    script.content = JSON.stringify({ content: data.scriptContent || '' })
  }

  return script
}

/**
 * Restore DAG onto X6 canvas from thin dagJson + taskDefinitions.
 * dag node 的 taskCode 就是 t_r_task_definition.code（雪花 ID），也用作 X6 node id。
 */
function deserializeDag(dag: { nodes: any[]; edges: any[] }, tdList: any[]) {
  if (!graph || !dag) return
  graph.clearCells()

  const bgCard = cv('--r-bg-card')
  const bgHover = cv('--r-bg-hover')
  const border = cv('--r-border')
  const borderDark = cv('--r-border-dark')
  const textPrimary = cv('--r-text-primary')
  const textMuted = cv('--r-text-muted')
  const textDisabled = cv('--r-text-disabled')
  const accent = cv('--r-accent')

  // Build taskCode → taskDefinition map
  const tdMap = new Map<string, any>()
  for (const td of tdList) {
    tdMap.set(String(td.code), td)
  }

  for (const n of (dag.nodes || [])) {
    const taskCode = String(n.taskCode)
    const td = tdMap.get(taskCode) || {}
    const taskType = td.taskType || ''
    const script = td.script || {}
    const isEnabled = td.isEnabled ?? true
    const category = taskTypes.value.find(t => t.value === taskType)?.category || ''

    const nodeData: Record<string, any> = {
      _taskCode: taskCode,
      taskType,
      scriptCode: script.code || null,
      description: td.description || '',
      isEnabled,
      priority: td.priority || 'MEDIUM',
      delayTime: td.delayTime ?? 0,
      retryTimes: td.retryTimes ?? 0,
      retryInterval: td.retryInterval ?? 30,
      timeout: td.timeout || 0,
      timeoutEnabled: td.timeoutEnabled ?? false,
      timeoutNotifyStrategy: Array.isArray(td.timeoutNotifyStrategy) ? td.timeoutNotifyStrategy : ['WARN'],
      inputParams: Array.isArray(td.inputParams) ? td.inputParams : [],
      outputParams: Array.isArray(td.outputParams) ? td.outputParams : [],
    }

    // 统一从 script.content 读取配置，按 category 解析
    if (category === 'CONTROL' && script.content) {
      // 控制流任务：content 是配置 JSON，展开到各字段
      try {
        const config = typeof script.content === 'string' ? JSON.parse(script.content) : script.content
        Object.assign(nodeData, config)
        // CONDITION 的 dependence 字段重命名避免冲突，并将 depTaskCode 转字符串
        if (taskType === 'CONDITION' && config.dependence) {
          nodeData.conditionDependence = config.dependence
          delete nodeData.dependence
          for (const group of (nodeData.conditionDependence.dependTaskList || [])) {
            for (const item of (group.dependItemList || [])) {
              if (item.depTaskCode != null) item.depTaskCode = String(item.depTaskCode)
            }
          }
        }
        // DEPENDENT / SUB_WORKFLOW: code 数字转字符串，和 el-select option value 对齐
        if (taskType === 'DEPENDENT' && nodeData.dependence?.dependTaskList) {
          for (const group of nodeData.dependence.dependTaskList) {
            for (const item of (group.dependItemList || [])) {
              if (item.projectCode != null) item.projectCode = String(item.projectCode)
              if (item.definitionCode != null) item.definitionCode = String(item.definitionCode)
              if (item.depTaskCode != null) item.depTaskCode = String(item.depTaskCode)
            }
          }
        }
        if (taskType === 'SUB_WORKFLOW' && nodeData.workflowDefinitionCode != null) {
          nodeData.workflowDefinitionCode = String(nodeData.workflowDefinitionCode)
        }
        // CONDITION: successNode/failedNode 也转字符串
        if (taskType === 'CONDITION' && nodeData.conditionResult) {
          if (Array.isArray(nodeData.conditionResult.successNode)) {
            nodeData.conditionResult.successNode = nodeData.conditionResult.successNode.map(String)
          }
          if (Array.isArray(nodeData.conditionResult.failedNode)) {
            nodeData.conditionResult.failedNode = nodeData.conditionResult.failedNode.map(String)
          }
        }
        // SWITCH: nextNode 转字符串
        if (taskType === 'SWITCH' && nodeData.switchResult) {
          if (nodeData.switchResult.nextNode != null) nodeData.switchResult.nextNode = String(nodeData.switchResult.nextNode)
          for (const b of (nodeData.switchResult.dependTaskList || [])) {
            if (b.nextNode != null) b.nextNode = String(b.nextNode)
          }
        }
      } catch { /* ignore */ }
    } else if (category === 'JAR' && script.content) {
      // JAR 任务：content 是 JSON 配置，展开到各字段
      try {
        const jarConfig = typeof script.content === 'string' ? JSON.parse(script.content) : script.content
        Object.assign(nodeData, jarConfig)
      } catch { /* ignore */ }
    } else if (taskType === 'SEATUNNEL' && script.content) {
      // SeaTunnelTaskParams: { content, deployMode }
      try {
        const stConfig = typeof script.content === 'string' ? JSON.parse(script.content) : script.content
        nodeData.seatunnelConfig = stConfig.content || ''
        nodeData.seatunnelDeployMode = stConfig.deployMode || 'cluster'
      } catch {
        nodeData.seatunnelConfig = script.content || ''
        nodeData.seatunnelDeployMode = 'cluster'
      }
    } else if (category === 'API' && script.content) {
      nodeData.httpConfig = typeof script.content === 'string'
        ? script.content
        : JSON.stringify(script.content)
    } else if (category === 'SQL' && script.content) {
      // SqlTaskParams: { sql, dataSourceId, executionMode, preStatements, postStatements, engineParams }
      try {
        const sqlConfig = typeof script.content === 'string' ? JSON.parse(script.content) : script.content
        nodeData.scriptContent = sqlConfig.sql || ''
        nodeData.datasourceId = sqlConfig.dataSourceId || null
        nodeData.executionMode = sqlConfig.executionMode || 'BATCH'
        nodeData.preStatements = Array.isArray(sqlConfig.preStatements) ? sqlConfig.preStatements : []
        nodeData.postStatements = Array.isArray(sqlConfig.postStatements) ? sqlConfig.postStatements : []
        nodeData.engineParams = Array.isArray(sqlConfig.engineParams) ? sqlConfig.engineParams : []
      } catch {
        nodeData.scriptContent = script.content || ''
      }
    } else {
      // ScriptTaskParams: { content }
      try {
        const parsed = typeof script.content === 'string' ? JSON.parse(script.content) : script.content
        nodeData.scriptContent = parsed.content || ''
      } catch {
        nodeData.scriptContent = script.content || ''
      }
    }

    const color = categoryColors[taskTypes.value.find(t => t.value === taskType)?.category || ''] || accent
    const iconUrl = getTaskIconUrl(taskType)

    graph.addNode({
      id: taskCode,
      x: n.position?.x ?? 100, y: n.position?.y ?? 100,
      width: 220, height: 48, shape: 'rect',
      markup: [
        { tagName: 'rect', selector: 'body' },
        { tagName: 'rect', selector: 'accent' },
        { tagName: 'image', selector: 'icon' },
        { tagName: 'text', selector: 'label' },
        { tagName: 'text', selector: 'typeLabel' },
      ],
      attrs: {
        body: { fill: isEnabled ? bgCard : bgHover, stroke: border, strokeWidth: 1, rx: 6, ry: 6, width: 220, height: 48 },
        accent: { fill: isEnabled ? color : textDisabled, width: 4, height: 48, rx: 6, ry: 0, x: 0, y: 0 },
        icon: { 'xlink:href': iconUrl, width: 30, height: 30, x: 12, y: 9, opacity: isEnabled ? 1 : 0.3 },
        label: { text: n.label || '', fontSize: 13, fontWeight: 500, fill: isEnabled ? textPrimary : textDisabled, refX: 48, refY: 14, textAnchor: 'start' },
        typeLabel: { text: taskType, fontSize: 10, fill: isEnabled ? textMuted : textDisabled, refX: 48, refY: 32, textAnchor: 'start' },
      },
      ports: {
        groups: {
          top: { position: 'top', attrs: { circle: { r: 4, magnet: true, stroke: borderDark, fill: bgCard, strokeWidth: 1 }}},
          bottom: { position: 'bottom', attrs: { circle: { r: 4, magnet: true, stroke: borderDark, fill: bgCard, strokeWidth: 1 }}},
          left: { position: 'left', attrs: { circle: { r: 4, magnet: true, stroke: borderDark, fill: bgCard, strokeWidth: 1 }}},
          right: { position: 'right', attrs: { circle: { r: 4, magnet: true, stroke: borderDark, fill: bgCard, strokeWidth: 1 }}},
        },
        items: [{ group: 'top' }, { group: 'bottom' }, { group: 'left' }, { group: 'right' }],
      },
      data: nodeData,
    })
  }

  for (const e of (dag.edges || [])) {
    graph.addEdge({
      source: String(e.source),
      target: String(e.target),
      router: { name: 'manhattan' },
      connector: { name: 'rounded', args: { radius: 6 } },
      attrs: { line: { stroke: borderDark, strokeWidth: 1.5, targetMarker: { name: 'block', width: 8, height: 6 } } },
    })
  }
}

/**
 * Save DAG only (standalone save from DagEditor toolbar).
 * Can also be called with extra fields to merge in (from WorkflowDetail.confirmSave).
 */
function hasCycle(): boolean {
  if (!graph) return false
  const edges = graph.getEdges()
  const adj = new Map<string, string[]>()
  const nodes = new Set<string>()
  for (const edge of edges) {
    const s = edge.getSourceCellId(), t = edge.getTargetCellId()
    nodes.add(s); nodes.add(t)
    if (!adj.has(s)) adj.set(s, [])
    adj.get(s)!.push(t)
  }
  // Kahn's algorithm (topological sort)
  const inDeg = new Map<string, number>()
  for (const n of nodes) inDeg.set(n, 0)
  for (const [, targets] of adj) for (const t of targets) inDeg.set(t, (inDeg.get(t) || 0) + 1)
  const queue = [...nodes].filter(n => inDeg.get(n) === 0)
  let qi = 0
  while (qi < queue.length) {
    const cur = queue[qi++]
    for (const next of adj.get(cur) || []) {
      inDeg.set(next, inDeg.get(next)! - 1)
      if (inDeg.get(next) === 0) queue.push(next)
    }
  }
  return qi < nodes.size
}

async function handleSave(extraFields?: Record<string, any>) {
  if (!graph) return
  if (hasCycle()) {
    ElMessage.error(t('workflow.dagCycleError'))
    return
  }
  const code = workflowDefinitionCode.value
  const name = workflowName.value
  saving.value = true
  try {
    const { dagJson, taskDefinitions } = serializeDag()
    const res: any = await updateWorkflowDefinition(workspaceId.value, projectCode.value, code, {
      name,
      dagJson: JSON.stringify(dagJson),
      taskDefinitions,
      expectedHash: contentHash.value,
      ...extraFields,
    })
    contentHash.value = res?.data?.contentHash ?? null
    ElMessage.success(t('ide.saved'))
  } finally { saving.value = false }
}


async function handleRun() {
  if (!graph) return
  const code = workflowDefinitionCode.value
  const name = workflowName.value
  // Auto-save before running
  try {
    const { dagJson, taskDefinitions } = serializeDag()
    if (!dagJson.nodes.length) {
      ElMessage.warning(t('workflow.noNodes'))
      return
    }
    await updateWorkflowDefinition(workspaceId.value, projectCode.value, code, {
      name,
      dagJson: JSON.stringify(dagJson),
      taskDefinitions,
    })
  } catch { ElMessage.error(t('common.failed')); return }

  try {
    await runWorkflowDefinition(workspaceId.value, projectCode.value, code)
    ElMessage.success(t('workflow.runSuccess'))
  } catch { ElMessage.error(t('common.failed')) }
}

function handleZoomIn() { graph?.zoom(0.1) }
function handleZoomOut() { graph?.zoom(-0.1) }
function handleFit() { graph?.zoomToFit({ padding: 40, maxScale: 1 }) }


onMounted(async () => {
  paletteTarget.value = !!document.getElementById('wfd-palette-target')
  try { const { data } = await getTaskTypes(); taskTypes.value = data ?? []; buildCategories(data ?? []) } catch {}
  try { const wsId = workspaceStore.currentWorkspace?.id; if (wsId) { const { data } = await listWorkflowDefinitions(wsId, projectCode.value, { pageSize: 9999 }); allWorkflows.value = (data?.records ?? data ?? []).map((w: any) => ({ id: w.id, code: String(w.code), name: w.name })) } } catch {}
  try { const wsId = workspaceStore.currentWorkspace?.id; if (wsId) { const { data } = await listProjects(wsId, { pageSize: 9999 }); allProjects.value = (data?.records ?? data ?? []).map((p: any) => ({ id: p.id, code: String(p.code), name: p.name })) } } catch {}
  await nextTick()
  initGraph()
  await fetchWorkflow()
})

onUnmounted(() => {
  if (debugPollTimer) { clearInterval(debugPollTimer); debugPollTimer = null }
  document.removeEventListener('keydown', handleKeyDown)
  graph?.dispose()
})

defineExpose({ handleSave, handleRun, reload: fetchWorkflow })
</script>

<template>
  <div v-loading="loading" class="dag-editor">
    <!-- Body -->
    <div class="dag-body">
      <!-- Palette: teleport to sidebar slot if available, otherwise inline -->
      <Teleport to="#wfd-palette-target" :disabled="!paletteTarget">
        <div class="dag-palette">
          <div class="palette-search">
            <el-input size="small" :placeholder="t('common.search')" clearable />
          </div>
          <div v-for="cat in nodeCategories" :key="cat.name" class="palette-group">
            <div class="palette-group__title">{{ categoryLabels[cat.name] ?? cat.name }}</div>
            <div v-for="nt in cat.types" :key="nt.value"
                 class="palette-item" :class="{ 'is-disabled': props.readOnly }"
                 :draggable="!props.readOnly"
                 @dragstart="handleDragStart($event, nt)">
              <TaskIcon :type="nt.value" :size="20" />
              <span>{{ nt.label }}</span>
            </div>
          </div>
        </div>
      </Teleport>

      <!-- Canvas -->
      <div class="dag-canvas-wrap" @dragover.prevent @drop.prevent="handleDrop">
        <div id="dag-canvas" class="dag-canvas" />
        <div class="dag-zoom-controls">
          <el-button-group>
            <el-button size="small" @click="handleZoomIn"><el-icon><ZoomIn /></el-icon></el-button>
            <el-button size="small" @click="handleZoomOut"><el-icon><ZoomOut /></el-icon></el-button>
            <el-button size="small" @click="handleFit"><el-icon><FullScreen /></el-icon></el-button>
          </el-button-group>
        </div>
      </div>
    </div>

    <!-- Task Config Modal -->
    <el-dialog
      v-model="modalVisible"
      :title="formData.label || 'Node Settings'"
      width="640px"
      :close-on-click-modal="false"
      :before-close="cancelModal"
      destroy-on-close
    >
      <template #header>
        <div class="modal-header">
          <TaskIcon :type="formData.taskType" :size="22" />
          <span class="modal-header__title">{{ formData.label || 'Node Settings' }}</span>
          <el-tag size="small" effect="light" round>{{ editingTaskTypeDef?.label }}</el-tag>
        </div>
      </template>

      <!-- 只读时整段 fieldset disabled,native HTML 自动透传到所有 button / input -->
      <fieldset :disabled="props.readOnly" class="modal-fieldset">
      <el-tabs type="border-card" class="node-tabs">
        <!-- Tab 1: Basic Config -->
        <el-tab-pane :label="t('workflow.sectionBasic')">
          <el-form label-position="top" class="node-form" @submit.prevent>
            <el-row :gutter="16">
              <el-col :span="16">
                <el-form-item :label="t('workflow.nodeName')" required>
                  <el-input v-model="formData.label" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item :label="t('workflow.isEnabled')">
                  <el-radio-group v-model="formData.isEnabled" size="small">
                    <el-radio-button :value="true">{{ t('workflow.enable') }}</el-radio-button>
                    <el-radio-button :value="false">{{ t('workflow.skip') }}</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item :label="t('common.description')">
              <el-input v-model="formData.description" type="textarea" :rows="2" />
            </el-form-item>

            <!-- Priority & Delay -->
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item :label="t('workflow.priority')">
                  <el-select v-model="formData.priority" style="width:100%">
                    <el-option v-for="p in ['HIGHEST','HIGH','MEDIUM','LOW','LOWEST']" :key="p" :label="p" :value="p" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="t('workflow.delayTime')">
                  <el-input-number v-model="formData.delayTime" :min="0" :max="86400" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <!-- Retry -->
            <div class="form-section">{{ t('workflow.sectionFailure') }}</div>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item :label="t('workflow.retryTimes')">
                  <el-input-number v-model="formData.retryTimes" :min="0" :max="10" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="t('workflow.retryInterval')">
                  <el-input-number v-model="formData.retryInterval" :min="1" :max="3600" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <!-- Timeout -->
            <el-form-item :label="t('workflow.timeout')">
              <el-switch v-model="formData.timeoutEnabled" />
            </el-form-item>
            <template v-if="formData.timeoutEnabled">
              <el-form-item :label="t('workflow.timeoutStrategy')">
                <el-checkbox-group v-model="formData.timeoutNotifyStrategy">
                  <el-checkbox value="WARN">{{ t('workflow.strategyWarn') }}</el-checkbox>
                  <el-checkbox value="FAILED">{{ t('workflow.strategyFail') }}</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              <el-form-item :label="t('workflow.timeoutMinutes')">
                <el-input-number v-model="formData.timeout" :min="1" :max="1440" style="width:180px" />
              </el-form-item>
            </template>

            <!-- Custom Parameters (collapsible) -->
            <el-collapse class="custom-param-collapse">
              <el-collapse-item :title="t('workflow.customParams') + (allParams.length ? ` (${allParams.length})` : '')">
                <div class="param-hint">{{ t('workflow.customParamsHint') }}</div>
                <div v-if="allParams.length" class="custom-param-header">
                  <span class="cp-col cp-col--name">{{ t('workflow.paramProp') }}</span>
                  <span class="cp-col cp-col--direct">{{ t('workflow.paramDirect') }}</span>
                  <span class="cp-col cp-col--type">{{ t('workflow.paramType') }}</span>
                  <span class="cp-col cp-col--value">{{ t('workflow.paramValue') }}</span>
                  <span class="cp-col cp-col--action"></span>
                </div>
                <div v-for="(param, idx) in allParams" :key="idx" class="custom-param-row">
                  <el-input v-model="param.prop" size="small" :placeholder="t('workflow.paramPropPh')" class="cp-col cp-col--name" />
                  <el-select v-model="param.direct" size="small" class="cp-col cp-col--direct">
                    <el-option label="IN" value="IN" />
                    <el-option label="OUT" value="OUT" />
                  </el-select>
                  <el-select v-model="param.type" size="small" class="cp-col cp-col--type">
                    <el-option v-for="dt in dataTypes" :key="dt" :label="dt" :value="dt" />
                  </el-select>
                  <el-input v-model="param.value" size="small" placeholder="${varName}" class="cp-col cp-col--value" />
                  <el-button class="cp-col cp-col--action" type="danger" circle size="small" @click="removeCustomParam(idx)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <el-button size="small" type="primary" plain @click="addCustomParam" style="margin-top:6px">
                  <el-icon><Plus /></el-icon> {{ t('workflow.addParam') }}
                </el-button>
              </el-collapse-item>
            </el-collapse>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: SQL Task Config -->
        <el-tab-pane v-if="editingTaskTypeDef?.needsDatasource && !formData.taskType.includes('JAR')" :label="t('workflow.sectionTask')">
          <el-form class="ide-tab" @submit.prevent>
            <div class="ide-tab__toolbar">
              <el-button type="primary" size="small" :loading="debugRunning" @click="handleDebugRun">
                <el-icon><VideoPlay /></el-icon> {{ t('ide.run') }}
              </el-button>
              <span class="ide-tab__toolbar-label">{{ t('workflow.selectDatasource') }}</span>
              <el-select
                v-model="formData.datasourceId"
                :placeholder="t('workflow.selectDatasource')"
                size="small"
                style="width: 200px"
                clearable
              >
                <el-option v-for="ds in filteredDatasources" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-select>
              <el-tag v-if="isStreamCapable" size="small" effect="plain" round style="margin-left: 8px">Batch</el-tag>
              <div style="flex:1" />
              <el-tag v-if="debugStatus" :type="debugStatus === 'SUCCESS' ? 'success' : debugStatus === 'FAILED' ? 'danger' : 'warning'" size="small" effect="light" round>{{ debugStatus }}</el-tag>
            </div>
            <div class="stmt-block">
              <div class="stmt-block__header">
                <span class="stmt-block__title">{{ t('workflow.preStatements') }}</span>
                <span v-if="formData.preStatements.length" class="stmt-block__count">{{ formData.preStatements.length }}</span>
                <el-button type="primary" link size="small" @click="formData.preStatements.push('')">
                  <el-icon size="14"><Plus /></el-icon> {{ t('common.add') }}
                </el-button>
              </div>
              <transition-group name="stmt-fade" tag="div">
                <div v-for="(_, idx) in formData.preStatements" :key="'pre-' + idx" class="stmt-block__row">
                  <span class="stmt-block__idx">{{ idx + 1 }}</span>
                  <el-input v-model="formData.preStatements[idx]" size="small" placeholder="SET hive.exec.parallel=true;" />
                  <el-button type="danger" link size="small" class="stmt-block__del" @click="formData.preStatements.splice(idx, 1)">
                    <el-icon size="14"><Delete /></el-icon>
                  </el-button>
                </div>
              </transition-group>
            </div>
            <div class="ide-tab__section-label">SQL</div>
            <div class="ide-tab__editor">
              <MonacoInput ref="ideEditorRef" v-model="formData.scriptContent" :task-type="formData.taskType" :read-only="props.readOnly" height="320px" />
            </div>
            <div class="stmt-block">
              <div class="stmt-block__header">
                <span class="stmt-block__title">{{ t('workflow.postStatements') }}</span>
                <span v-if="formData.postStatements.length" class="stmt-block__count">{{ formData.postStatements.length }}</span>
                <el-button type="primary" link size="small" @click="formData.postStatements.push('')">
                  <el-icon size="14"><Plus /></el-icon> {{ t('common.add') }}
                </el-button>
              </div>
              <transition-group name="stmt-fade" tag="div">
                <div v-for="(_, idx) in formData.postStatements" :key="'post-' + idx" class="stmt-block__row">
                  <span class="stmt-block__idx">{{ idx + 1 }}</span>
                  <el-input v-model="formData.postStatements[idx]" size="small" placeholder="DROP TABLE IF EXISTS tmp_xxx;" />
                  <el-button type="danger" link size="small" class="stmt-block__del" @click="formData.postStatements.splice(idx, 1)">
                    <el-icon size="14"><Delete /></el-icon>
                  </el-button>
                </div>
              </transition-group>
            </div>
            <div class="ide-tab__result">
              <div class="ide-tab__result-header">
                <span :class="{ active: debugTab === 'log' }" @click="debugTab = 'log'">{{ t('ide.log') }}</span>
                <span :class="{ active: debugTab === 'result' }" @click="debugTab = 'result'">{{ t('ide.result') }}</span>
              </div>
              <div class="ide-tab__result-body">
                <div v-show="debugTab === 'log'" class="ide-tab__log"><pre>{{ debugLog || t('ide.noLogs') }}</pre></div>
                <div v-show="debugTab === 'result'" class="ide-tab__table">
                  <el-table v-if="debugResultCols.length" :data="debugResultRows" size="small" border stripe height="100%" :scrollbar-always-on="true">
                    <el-table-column type="index" label="#" width="50" />
                    <el-table-column v-for="col in debugResultCols" :key="col" :prop="col" :label="col" min-width="120" show-overflow-tooltip />
                  </el-table>
                  <div v-else class="ide-tab__empty">{{ t('ide.noResults') }}</div>
                </div>
              </div>
            </div>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: JAR Task Config -->
        <el-tab-pane v-if="formData.taskType.includes('JAR')" :label="t('workflow.sectionTask')">
          <el-form label-position="top" class="node-form" @submit.prevent>
            <!-- Program -->
            <div class="form-section">{{ t('jar.program') }}</div>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item :label="t('jar.mainClass')" required>
                  <el-input v-model="formData.mainClass" placeholder="com.example.MainApp" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="t('jar.jarPath')" required>
                  <el-input v-model="formData.jarPath" placeholder="/jars/my-app.jar" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item :label="t('jar.args')">
              <el-input v-model="formData.args" type="textarea" :rows="2" placeholder="--input /data/input --output /data/output" />
            </el-form-item>

            <!-- Deploy -->
            <div class="form-section">{{ t('jar.deploy') }}</div>
            <template v-if="formData.taskType === 'SPARK_JAR'">
              <el-row :gutter="16">
                <el-col :span="6">
                  <el-form-item label="Master">
                    <el-select v-model="formData.master">
                      <el-option label="YARN" value="yarn" />
                      <el-option label="Local" value="local[*]" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item :label="t('jar.deployMode')">
                    <el-select v-model="formData.deployMode">
                      <el-option label="Cluster" value="cluster" />
                      <el-option label="Client" value="client" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item :label="t('jar.appName')">
                    <el-input v-model="formData.appName" placeholder="my-spark-app" />
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item :label="t('jar.queue')">
                    <el-input v-model="formData.queue" placeholder="default" />
                  </el-form-item>
                </el-col>
              </el-row>
            </template>
            <template v-else>
              <el-row :gutter="16">
                <el-col :span="6">
                  <el-form-item :label="t('jar.deployMode')">
                    <el-select v-model="formData.deployMode">
                      <el-option label="YARN Application" value="yarn-application" />
                      <el-option label="YARN Session" value="yarn-session" />
                      <el-option label="Local" value="local" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col v-if="isStreamCapable" :span="4">
                  <el-form-item :label="t('instance.executionMode')">
                    <el-input model-value="Batch" disabled />
                  </el-form-item>
                </el-col>
                <el-col :span="isStreamCapable ? 6 : 8">
                  <el-form-item :label="t('jar.appName')">
                    <el-input v-model="formData.appName" placeholder="my-flink-app" />
                  </el-form-item>
                </el-col>
                <el-col :span="isStreamCapable ? 6 : 8">
                  <el-form-item :label="t('jar.queue')">
                    <el-input v-model="formData.queue" placeholder="default" />
                  </el-form-item>
                </el-col>
              </el-row>
            </template>

            <!-- Resources -->
            <div class="form-section">{{ t('jar.resources') }}</div>
            <template v-if="formData.taskType === 'SPARK_JAR'">
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item :label="t('jar.driverCores')">
                    <el-input-number v-model="formData.driverCores" :min="1" :max="32" style="width:100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item :label="t('jar.driverMemory')">
                    <el-input v-model="formData.driverMemory" placeholder="1g" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item :label="t('jar.executorInstances')">
                    <el-input-number v-model="formData.executorInstances" :min="1" :max="500" style="width:100%" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item :label="t('jar.executorCores')">
                    <el-input-number v-model="formData.executorCores" :min="1" :max="32" style="width:100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item :label="t('jar.executorMemory')">
                    <el-input v-model="formData.executorMemory" placeholder="2g" />
                  </el-form-item>
                </el-col>
              </el-row>
            </template>
            <template v-else>
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item :label="t('jar.jobManagerMemory')">
                    <el-input v-model="formData.jobManagerMemory" placeholder="1g" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item :label="t('jar.taskManagerMemory')">
                    <el-input v-model="formData.taskManagerMemory" placeholder="2g" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item :label="t('jar.parallelism')">
                    <el-input-number v-model="formData.parallelism" :min="1" :max="1000" style="width:100%" />
                  </el-form-item>
                </el-col>
              </el-row>
            </template>

            <!-- Engine Params -->
            <div class="form-section">
              {{ t('jar.engineParams') }}
              <el-button type="primary" link size="small" style="margin-left: 8px" @click="formData.engineParams.push({ key: '', value: '' })">
                <el-icon size="14"><Plus /></el-icon> {{ t('common.add') }}
              </el-button>
            </div>
            <div v-for="(row, idx) in formData.engineParams" :key="idx" class="engine-param-row">
              <el-input v-model="row.key" size="small" placeholder="spark.sql.shuffle.partitions" />
              <el-input v-model="row.value" size="small" placeholder="200" />
              <el-button type="danger" link size="small" @click="formData.engineParams.splice(idx, 1)">
                <el-icon size="14"><Delete /></el-icon>
              </el-button>
            </div>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: Script Task Config (Python/Shell) -->
        <el-tab-pane v-if="formData.taskType === 'PYTHON' || formData.taskType === 'SHELL'" :label="t('workflow.sectionTask')">
          <div class="ide-tab">
            <div class="ide-tab__toolbar">
              <el-button type="primary" size="small" :loading="debugRunning" @click="handleDebugRun">
                <el-icon><VideoPlay /></el-icon> {{ t('ide.run') }}
              </el-button>
              <div style="flex:1" />
              <el-tag v-if="debugStatus" :type="debugStatus === 'SUCCESS' ? 'success' : debugStatus === 'FAILED' ? 'danger' : 'warning'" size="small" effect="light" round>{{ debugStatus }}</el-tag>
            </div>
            <div class="ide-tab__section-label">{{ formData.taskType === 'PYTHON' ? 'Python' : 'Shell' }}</div>
            <div class="ide-tab__editor">
              <MonacoInput ref="ideEditorRef" v-model="formData.scriptContent" :task-type="formData.taskType" height="380px" />
            </div>
            <div class="ide-tab__result">
              <div class="ide-tab__result-header">
                <span :class="{ active: debugTab === 'log' }" @click="debugTab = 'log'">{{ t('ide.log') }}</span>
              </div>
              <div class="ide-tab__result-body">
                <div class="ide-tab__log"><pre>{{ debugLog || t('ide.noLogs') }}</pre></div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab 2: CONDITION Task Config -->
        <el-tab-pane v-if="formData.taskType === 'CONDITION'" :label="t('workflow.sectionTask')">
          <el-form label-position="top" class="node-form" @submit.prevent>
            <div class="form-section">{{ t('workflow.conditionDeps') }}</div>
            <div class="param-hint">{{ t('workflow.conditionHint') }}</div>
            <el-alert v-if="!upstreamNodeOptions.length" type="info" :closable="false" show-icon style="margin-bottom:12px">
              {{ t('workflow.noUpstreamHint') }}
            </el-alert>

            <template v-for="(taskGroup, gIdx) in formData.conditionDependence.dependTaskList" :key="gIdx">
              <!-- 组间关系分隔符 -->
              <div v-if="gIdx > 0" class="relation-divider">
                <span class="relation-divider__line" />
                <el-select v-model="formData.conditionDependence.relation" size="small" class="relation-divider__select">
                  <el-option label="AND" value="AND" />
                  <el-option label="OR" value="OR" />
                </el-select>
                <span class="relation-divider__line" />
              </div>

              <div class="depend-item">
                <div class="depend-item__header">
                  <span>{{ t('workflow.conditionGroup') }} {{ gIdx + 1 }}</span>
                  <el-button type="danger" link size="small" @click="formData.conditionDependence.dependTaskList.splice(gIdx, 1)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <template v-for="(item, iIdx) in taskGroup.dependItemList" :key="iIdx">
                  <!-- 组内关系分隔符 -->
                  <div v-if="iIdx > 0" class="relation-divider relation-divider--inner">
                    <span class="relation-divider__line" />
                    <el-select v-model="taskGroup.relation" size="small" class="relation-divider__select">
                      <el-option label="AND" value="AND" />
                      <el-option label="OR" value="OR" />
                    </el-select>
                    <span class="relation-divider__line" />
                  </div>
                  <div class="depend-item__entry">
                    <div class="depend-item__row">
                      <el-select v-model="item.depTaskCode" size="small" :placeholder="t('workflow.selectUpstream')" style="flex:1" clearable filterable>
                        <el-option v-for="n in upstreamNodeOptions" :key="n.id" :label="n.label" :value="n.id" />
                      </el-select>
                      <el-select v-model="item.status" size="small" style="width:120px">
                        <el-option label="SUCCESS" value="SUCCESS" />
                        <el-option label="FAILED" value="FAILED" />
                      </el-select>
                      <el-button type="danger" link size="small" @click="taskGroup.dependItemList.splice(iIdx, 1)">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </div>
                  </div>
                </template>
                <el-button size="small" type="primary" link @click="taskGroup.dependItemList.push({ depTaskCode: null, status: 'SUCCESS' })">
                  <el-icon size="14"><Plus /></el-icon> {{ t('workflow.addConditionItem') }}
                </el-button>
              </div>
            </template>

            <el-button size="small" type="primary" plain @click="formData.conditionDependence.dependTaskList.push({ dependItemList: [{ depTaskCode: null, status: 'SUCCESS' }], relation: 'AND' })" style="margin-bottom:12px">
              <el-icon><Plus /></el-icon> {{ t('workflow.addConditionGroup') }}
            </el-button>
            <div class="form-section">{{ t('workflow.conditionBranch') }}</div>
            <el-alert v-if="!downstreamNodeOptions.length" type="info" :closable="false" show-icon style="margin-bottom:12px">
              {{ t('workflow.noDownstreamHint') }}
            </el-alert>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item :label="t('workflow.successBranch')">
                  <el-select v-model="formData.conditionResult.successNode[0]" :placeholder="t('workflow.selectDownstream')" style="width:100%" clearable filterable
                    @change="(v) => { formData.conditionResult.successNode = v != null ? [v] : [] }">
                    <el-option v-for="n in downstreamNodeOptions" :key="n.id" :label="n.label" :value="n.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="t('workflow.failedBranch')">
                  <el-select v-model="formData.conditionResult.failedNode[0]" :placeholder="t('workflow.selectDownstream')" style="width:100%" clearable filterable
                    @change="(v) => { formData.conditionResult.failedNode = v != null ? [v] : [] }">
                    <el-option v-for="n in downstreamNodeOptions" :key="n.id" :label="n.label" :value="n.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: SeaTunnel Task Config (Zeta Engine) -->
        <el-tab-pane v-if="formData.taskType === 'SEATUNNEL'" :label="t('workflow.sectionTask')">
          <el-form label-position="top" class="node-form" @submit.prevent>
            <el-form-item :label="t('workflow.seatunnelDeployMode')">
              <el-radio-group v-model="formData.seatunnelDeployMode">
                <el-radio-button value="local">Local</el-radio-button>
                <el-radio-button value="cluster">Cluster</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item :label="t('workflow.seatunnelConfig')">
              <MonacoInput ref="ideEditorRef" v-model="formData.seatunnelConfig" task-type="SEATUNNEL" height="380px" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane v-if="editingTaskTypeDef?.category === 'API'" :label="t('workflow.sectionTask')">
          <HttpTaskEditor v-model="formData.httpConfig" embedded />
        </el-tab-pane>

        <!-- Tab 2: SUB_WORKFLOW Task Config -->
        <el-tab-pane v-if="formData.taskType === 'SUB_WORKFLOW'" :label="t('workflow.sectionTask')">
          <el-form label-position="top" class="node-form" @submit.prevent>
            <el-form-item :label="t('workflow.subWorkflowId')">
              <el-select v-model="formData.workflowDefinitionCode" :placeholder="t('workflow.selectWorkflow')" style="width:100%" clearable filterable>
                <el-option
                  v-for="wf in allWorkflows.filter(w => w.code !== workflowDefinitionCode)"
                  :key="wf.code" :label="wf.name" :value="wf.code"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: SWITCH Task Config -->
        <el-tab-pane v-if="formData.taskType === 'SWITCH'" :label="t('workflow.sectionTask')">
          <el-form label-position="top" class="node-form" @submit.prevent>
            <div class="form-section">{{ t('workflow.switchCases') }}</div>
            <div class="param-hint">{{ t('workflow.switchHint') }}</div>
            <el-alert v-if="!downstreamNodeOptions.length" type="info" :closable="false" show-icon style="margin-bottom:12px">
              {{ t('workflow.noDownstreamHint') }}
            </el-alert>
            <div v-for="(sc, idx) in formData.switchResult.dependTaskList" :key="idx" class="switch-case-row">
              <el-input v-model="sc.condition" size="small" :placeholder="t('workflow.switchConditionPh')" class="switch-case-input" />
              <el-select v-model="sc.nextNode" size="small" :placeholder="t('workflow.selectDownstream')" class="switch-case-node" clearable filterable>
                <el-option v-for="n in downstreamNodeOptions" :key="n.id" :label="n.label" :value="n.id" />
              </el-select>
              <el-button type="danger" circle size="small" @click="formData.switchResult.dependTaskList.splice(idx, 1)"><el-icon><Delete /></el-icon></el-button>
            </div>
            <el-button size="small" type="primary" plain @click="formData.switchResult.dependTaskList.push({ condition: '', nextNode: null })" style="margin-bottom:12px">
              <el-icon><Plus /></el-icon> {{ t('workflow.addCase') }}
            </el-button>
            <el-form-item :label="t('workflow.switchDefault')">
              <el-select v-model="formData.switchResult.nextNode" :placeholder="t('workflow.selectDownstream')" style="width:100%" clearable filterable>
                <el-option v-for="n in downstreamNodeOptions" :key="n.id" :label="n.label" :value="n.id" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: DEPENDENT Task Config -->
        <el-tab-pane v-if="formData.taskType === 'DEPENDENT'" :label="t('workflow.sectionTask')">
          <el-form label-position="top" class="node-form" @submit.prevent>
            <div class="form-section">{{ t('workflow.dependItems') }}</div>
            <div class="param-hint">{{ t('workflow.dependHint') }}</div>

            <template v-for="(taskGroup, gIdx) in formData.dependence.dependTaskList" :key="gIdx">
              <div v-if="gIdx > 0" class="relation-divider">
                <span class="relation-divider__line" />
                <el-select v-model="formData.dependence.relation" size="small" class="relation-divider__select">
                  <el-option label="AND" value="AND" />
                  <el-option label="OR" value="OR" />
                </el-select>
                <span class="relation-divider__line" />
              </div>

              <div class="depend-item">
                <div class="depend-item__header">
                  <span>{{ t('workflow.dependGroup') }} {{ gIdx + 1 }}</span>
                  <el-button type="danger" link size="small" @click="formData.dependence.dependTaskList.splice(gIdx, 1)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <template v-for="(item, iIdx) in taskGroup.dependItemList" :key="iIdx">
                  <div v-if="iIdx > 0" class="relation-divider relation-divider--inner">
                    <span class="relation-divider__line" />
                    <el-select v-model="taskGroup.relation" size="small" class="relation-divider__select">
                      <el-option label="AND" value="AND" />
                      <el-option label="OR" value="OR" />
                    </el-select>
                    <span class="relation-divider__line" />
                  </div>
                  <div class="depend-item__entry">
                  <div class="depend-item__row">
                    <el-select v-model="item.projectCode" size="small" :placeholder="t('workflow.selectProject')" style="flex:1" clearable filterable @change="onDependProjectChange(item)">
                      <el-option v-for="p in allProjects" :key="p.code" :label="p.name" :value="p.code" />
                    </el-select>
                    <el-select v-model="item.definitionCode" size="small" :placeholder="t('workflow.selectWorkflow')" style="flex:1" clearable filterable :disabled="!item.projectCode" @change="onDependWorkflowChange(item)">
                      <el-option v-for="wf in (depWorkflows[item.projectCode!] || [])" :key="wf.code" :label="wf.name" :value="wf.code" />
                    </el-select>
                    <el-select v-model="item.depTaskCode" size="small" :placeholder="t('workflow.selectTask')" style="flex:1" :disabled="!item.definitionCode">
                      <el-option :label="t('workflow.allTasks')" :value="0" />
                      <el-option v-for="td in (depTasks[item.definitionCode!] || [])" :key="td.code" :label="td.name" :value="td.code" />
                    </el-select>
                    <el-button type="danger" link size="small" @click="taskGroup.dependItemList.splice(iIdx, 1)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                  <div class="depend-item__row" style="margin-top:4px">
                    <span class="depend-item__label">{{ t('workflow.timeCycle') }}</span>
                    <el-select v-model="item.cycle" size="small" style="width:100px">
                      <el-option label="Day" value="day" />
                      <el-option label="Hour" value="hour" />
                      <el-option label="Week" value="week" />
                      <el-option label="Month" value="month" />
                    </el-select>
                    <span class="depend-item__label">{{ t('workflow.dateValue') }}</span>
                    <el-select v-model="item.dateValue" size="small" style="width:150px">
                      <template v-if="item.cycle === 'day'">
                        <el-option label="Today" value="today" />
                        <el-option label="Last 1 Day" value="last1Days" />
                        <el-option label="Last 2 Days" value="last2Days" />
                        <el-option label="Last 3 Days" value="last3Days" />
                        <el-option label="Last 7 Days" value="last7Days" />
                      </template>
                      <template v-else-if="item.cycle === 'hour'">
                        <el-option label="Current Hour" value="currentHour" />
                        <el-option label="Last 1 Hour" value="last1Hour" />
                        <el-option label="Last 2 Hours" value="last2Hours" />
                        <el-option label="Last 3 Hours" value="last3Hours" />
                        <el-option label="Last 24 Hours" value="last24Hours" />
                      </template>
                      <template v-else-if="item.cycle === 'week'">
                        <el-option label="This Week" value="thisWeek" />
                        <el-option label="Last Week" value="lastWeek" />
                        <el-option label="Last Monday" value="lastMonday" />
                        <el-option label="Last Tuesday" value="lastTuesday" />
                        <el-option label="Last Wednesday" value="lastWednesday" />
                        <el-option label="Last Thursday" value="lastThursday" />
                        <el-option label="Last Friday" value="lastFriday" />
                        <el-option label="Last Saturday" value="lastSaturday" />
                        <el-option label="Last Sunday" value="lastSunday" />
                      </template>
                      <template v-else-if="item.cycle === 'month'">
                        <el-option label="This Month" value="thisMonth" />
                        <el-option label="This Month Begin" value="thisMonthBegin" />
                        <el-option label="Last Month" value="lastMonth" />
                        <el-option label="Last Month Begin" value="lastMonthBegin" />
                        <el-option label="Last Month End" value="lastMonthEnd" />
                      </template>
                    </el-select>
                  </div>
                  </div>
                </template>
                <el-button size="small" type="primary" link @click="taskGroup.dependItemList.push({ dependentType: 'DEPENDENT', projectCode: null, definitionCode: null, depTaskCode: 0, cycle: 'day', dateValue: 'today', parameterPassing: false })">
                  <el-icon size="14"><Plus /></el-icon> {{ t('workflow.addDependItem') }}
                </el-button>
              </div>
            </template>

            <el-button size="small" type="primary" plain @click="formData.dependence.dependTaskList.push({ dependItemList: [{ dependentType: 'DEPENDENT', projectCode: null, definitionCode: null, depTaskCode: 0, cycle: 'day', dateValue: 'today', parameterPassing: false }], relation: 'AND' })" style="margin-bottom:12px">
              <el-icon><Plus /></el-icon> {{ t('workflow.addDependGroup') }}
            </el-button>

            <div class="form-section">{{ t('workflow.dependSettings') }}</div>
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item :label="t('workflow.checkInterval')">
                  <el-input-number v-model="formData.dependence.checkInterval" :min="1" :max="3600" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item :label="t('workflow.failurePolicy')">
                  <el-select v-model="formData.dependence.failurePolicy" style="width:100%">
                    <el-option :label="t('workflow.policyFailure')" value="DEPENDENT_FAILURE_FAILURE" />
                    <el-option :label="t('workflow.policyWaiting')" value="DEPENDENT_FAILURE_WAITING" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col v-if="formData.dependence.failurePolicy === 'DEPENDENT_FAILURE_WAITING'" :span="8">
                <el-form-item :label="t('workflow.failureWaitingTime')">
                  <el-input-number v-model="formData.dependence.failureWaitingTime" :min="1" :max="1440" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-tab-pane>

      </el-tabs>
      </fieldset>

      <template #footer>
        <el-button @click="cancelModal">{{ t(props.readOnly ? 'common.back' : 'common.cancel') }}</el-button>
        <el-button v-if="!props.readOnly" type="primary" @click="confirmModal">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Context Menu -->
    <Teleport to="body">
      <div
        v-if="ctxMenu.visible"
        class="dag-ctx-menu"
        :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
        @click.stop
      >
        <div class="dag-ctx-menu__item" @click="ctxDelete">
          <el-icon><Delete /></el-icon>
          {{ t('common.delete') }}
        </div>
      </div>
      <div v-if="ctxMenu.visible" class="dag-ctx-overlay" @click="hideContextMenu" @contextmenu.prevent="hideContextMenu" />
    </Teleport>
  </div>
</template>

<style scoped lang="scss">
.dag-editor { display: flex; flex-direction: column; height: 100%; background: var(--r-bg-page); }

/* Body */
.dag-body { display: flex; flex: 1; overflow: hidden; position: relative; }

/* Palette — 208px matches ProjectLayout sidebar & WorkflowDetail nav */
.dag-palette {
  width: 208px;
  flex-shrink: 0;
  background: var(--r-bg-card);
  border-right: 1px solid var(--r-border);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.palette-search {
  padding: 10px 12px;
  border-bottom: 1px solid var(--r-border-light);
}

.palette-group {
  padding: 10px 10px 2px;
}

.palette-group__title {
  font-size: 11px;
  font-weight: 600;
  color: var(--r-text-disabled);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 6px;
  padding-left: 6px;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  margin-bottom: 2px;
  font-size: 13px;
  color: var(--r-text-tertiary);
  border-radius: 6px;
  cursor: grab;
  user-select: none;
  border: 1px solid transparent;
  transition: all 0.12s;

  &:hover {
    background: var(--r-bg-hover);
    border-color: var(--r-border);
    color: var(--r-text-primary);
  }

  &.is-disabled {
    opacity: 0.4;
    cursor: not-allowed;
    &:hover { background: transparent; border-color: transparent; color: var(--r-text-tertiary); }
  }

  &:active {
    cursor: grabbing;
    background: var(--r-accent-bg);
    border-color: var(--r-accent-border);
    transform: scale(0.97);
  }
}

/* Canvas */
.dag-canvas-wrap { flex: 1; overflow: hidden; position: relative; background: var(--r-bg-panel); }
.dag-canvas { width: 100%; height: 100%; }
.dag-zoom-controls {
  position: absolute; bottom: 16px; right: 16px; z-index: 10;
  :deep(.el-button-group .el-button) {
    background: var(--r-bg-card); border-color: var(--r-border);
    &:hover { background: var(--r-bg-hover); }
  }
}

/* Modal */
.modal-header {
  display: flex; align-items: center; gap: 10px;
}
.modal-header__title { font-size: 16px; font-weight: 600; color: var(--r-text-primary); }

/* fieldset 默认有 padding/border/min-width:min-content,reset 为透明容器 */
.modal-fieldset { all: unset; display: contents; }

.node-tabs {
  :deep(.el-tabs__header) { margin: 0; }
  :deep(.el-tabs__content) { padding: 0; }
  :deep(.el-tab-pane) { padding: 16px 20px; }
}

.node-form {
  :deep(.el-form-item) { margin-bottom: 14px; }
  :deep(.el-form-item__label) { font-size: 13px; color: var(--r-text-secondary); padding-bottom: 4px; }
}

.form-section {
  font-size: 12px; font-weight: 600; color: var(--r-accent); text-transform: uppercase;
  letter-spacing: 0.5px; margin: 20px 0 12px; padding-bottom: 6px;
  border-bottom: 1px solid var(--r-accent-bg);
  &:first-child { margin-top: 0; }
}

.param-hint { font-size: 12px; color: var(--r-text-muted); margin-bottom: 10px; }

.engine-param-row {
  display: flex; align-items: center; gap: 8px; margin-bottom: 6px;
  .el-input { flex: 1; }
}

.depend-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: 6px;
}
.depend-item__header {
  display: flex; align-items: center; gap: 8px;
  font-size: 13px; font-weight: 500; color: var(--r-text-secondary);
  margin-bottom: 8px;
}
.depend-item__header > span:first-child { flex-shrink: 0; }
.depend-item__header-hint {
  flex: 1; font-size: 12px; font-weight: 400; color: var(--r-text-muted); text-align: right;
}

.relation-divider {
  display: flex; align-items: center; gap: 8px;
  margin: 8px 0;
}
.relation-divider__line {
  flex: 1; height: 1px; background: var(--r-border);
}
.relation-divider__select {
  width: 80px; flex-shrink: 0;
}
.relation-divider--inner {
  margin: 4px 0;
}
.depend-item__entry {
  padding: 8px;
  margin-bottom: 6px;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 4px;
}
.depend-item__row {
  display: flex; align-items: center; gap: 8px;
}
.depend-item__label {
  font-size: 12px; color: var(--r-text-muted); flex-shrink: 0;
}

/* Custom Parameters collapse */
.custom-param-collapse {
  margin-top: 16px; border: none;
  :deep(.el-collapse-item__header) {
    font-size: 12px; font-weight: 600; color: var(--r-accent); height: 34px;
    background: none; border: none; border-bottom: 1px solid var(--r-accent-bg);
  }
  :deep(.el-collapse-item__wrap) { border: none; background: none; }
  :deep(.el-collapse-item__content) { padding: 12px 0 0; }
}

.custom-param-header {
  display: flex; align-items: center; gap: 8px; margin-bottom: 6px;
  .cp-col { font-size: 11px; font-weight: 600; color: var(--r-text-muted); text-transform: uppercase; letter-spacing: 0.3px; }
}
.custom-param-row {
  display: flex; align-items: center; gap: 8px; margin-bottom: 8px;
}
.cp-col {
  &--name   { flex: 6; min-width: 0; }
  &--direct { flex: 3; min-width: 0; }
  &--type   { flex: 5; min-width: 0; }
  &--value  { flex: 6; min-width: 0; }
  &--action { flex: 0 0 32px; }
}

.switch-case-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.switch-case-input { flex: 1; }
.switch-case-node { width: 140px; }

/* Pre/Post Statements */
.stmt-block {
  margin-bottom: 10px;
}
.stmt-block__header {
  display: flex; align-items: center; gap: 6px;
  margin-bottom: 6px;
}
.stmt-block__title {
  font-size: 13px; font-weight: 500; color: var(--r-text-secondary);
}
.stmt-block__count {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 18px; height: 18px; padding: 0 5px;
  font-size: 11px; font-weight: 500; color: var(--r-accent); background: var(--r-accent-bg);
  border-radius: 9px; line-height: 1;
}
.stmt-block__row {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 6px;
  .el-input { flex: 1; }
}
.stmt-block__idx {
  flex-shrink: 0; width: 20px; text-align: center;
  font-size: 11px; color: var(--r-text-disabled); font-weight: 500;
}
.stmt-block__del {
  flex-shrink: 0; opacity: 0.5; transition: opacity 0.2s;
}
.stmt-block__row:hover .stmt-block__del { opacity: 1; }

.stmt-fade-enter-active, .stmt-fade-leave-active { transition: all 0.2s ease; }
.stmt-fade-enter-from, .stmt-fade-leave-to { opacity: 0; transform: translateY(-4px); }

/* IDE Tab */
.ide-tab { display: flex; flex-direction: column; }

.ide-tab__section-label {
  font-size: 13px; font-weight: 500; color: var(--r-text-secondary); margin-bottom: 6px;
}

.ide-tab__toolbar {
  display: flex; align-items: center; gap: 10px;
  padding-bottom: 10px; margin-bottom: 10px; border-bottom: 1px solid var(--r-border-light);
}
.ide-tab__toolbar-label {
  font-size: 13px; color: var(--r-text-secondary);
}

.ide-tab__editor { margin-bottom: 10px; }

.ide-tab__result {
  margin-top: 8px; border: 1px solid var(--r-border); border-radius: 6px; overflow: hidden;
}

.ide-tab__result-header {
  display: flex; align-items: center; gap: 4px;
  padding: 0 10px; height: 32px; background: var(--r-bg-panel); border-bottom: 1px solid var(--r-border-light);

  span {
    padding: 3px 10px; font-size: 12px; color: var(--r-text-muted); cursor: pointer; border-radius: 4px;
    &:hover { color: var(--r-text-secondary); }
    &.active { color: var(--r-accent); background: var(--r-accent-bg); font-weight: 500; }
  }
}

.ide-tab__result-body { height: 160px; overflow: auto; }

.ide-tab__log {
  padding: 8px 12px; height: 100%; overflow: auto;
  pre { margin: 0; font-family: var(--r-font-mono); font-size: 12px; line-height: 1.7; color: var(--r-text-secondary); white-space: pre-wrap; }
}

.ide-tab__table { height: 100%; }
.ide-tab__empty {
  display: flex; align-items: center; justify-content: center;
  height: 100%; color: var(--r-text-disabled); font-size: 13px;
}

/* Context Menu */
.dag-ctx-overlay {
  position: fixed; inset: 0; z-index: 2999;
}
.dag-ctx-menu {
  position: fixed; z-index: 3000;
  background: var(--r-bg-overlay); border-radius: 8px;
  box-shadow: var(--r-shadow-overlay);
  border: 1px solid var(--r-border-light);
  padding: 4px; min-width: 140px;
}
.dag-ctx-menu__item {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 12px; font-size: 13px; color: var(--r-text-secondary);
  cursor: pointer; border-radius: 5px; transition: all 0.15s;
  .el-icon { font-size: 14px; }
  &:hover { background: var(--r-bg-hover); color: var(--r-text-primary); }
}

</style>
