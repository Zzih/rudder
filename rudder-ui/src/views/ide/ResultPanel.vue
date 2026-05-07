<template>
  <div class="result-panel">
    <div class="result-panel__header">
      <div class="result-panel__tabs">
        <button class="result-tab" :class="{ 'result-tab--active': currentTab === 'result' }" @click="currentTab = 'result'">{{ t('ide.result') }}</button>
        <button class="result-tab" :class="{ 'result-tab--active': currentTab === 'log' }" @click="currentTab = 'log'">{{ t('ide.log') }}</button>
        <button class="result-tab" :class="{ 'result-tab--active': currentTab === 'history' }" @click="currentTab = 'history'; loadHistory()">{{ t('ide.history') }}</button>
        <button class="result-tab" :class="{ 'result-tab--active': currentTab === 'jobs' }" @click="currentTab = 'jobs'; loadRunningJobs()">
          {{ t('jobs.running') }}
          <el-badge v-if="runningJobCount > 0" :value="runningJobCount" :max="99" class="jobs-badge" />
        </button>
        <button class="result-tab" :class="{ 'result-tab--active': currentTab === 'versions' }" @click="currentTab = 'versions'; loadVersions(true)">{{ t('ide.versionHistory') }}</button>
      </div>
      <el-tag v-if="executionStatus" :type="statusType" size="small" effect="light" class="result-panel__status">{{ executionStatus }}</el-tag>
      <el-button v-if="executionStatus === 'FAILED'" type="warning" text size="small" :loading="diagnosing" @click="handleDiagnose">
        <el-icon><MagicStick /></el-icon>{{ t('ide.aiDiagnose') }}
      </el-button>
      <div class="result-panel__spacer" />
      <el-button text size="small" @click="ideState.resultPanelVisible = false"><el-icon><ArrowDown /></el-icon></el-button>
    </div>

    <div v-show="currentTab === 'result'" class="result-panel__content result-panel__content--table">
      <template v-if="resultColumns.length">
        <el-table :data="resultRows" size="small" border stripe height="100%" :scrollbar-always-on="true">
          <el-table-column type="index" :index="(i: number) => resultOffset + i + 1" label="#" width="60" fixed="left" />
          <el-table-column v-for="col in resultColumns" :key="col" :prop="col" :label="col" min-width="150" show-overflow-tooltip />
        </el-table>
        <div class="result-panel__pager">
          <span class="result-panel__limit-notice">{{ t('ide.resultLimitNotice') }}</span>
          <div class="result-panel__pager-spacer" />
          <el-pagination
            small layout="total, sizes, prev, pager, next"
            :total="resultTotal" :page-sizes="[50, 100, 500]" :page-size="resultPageSize" :current-page="resultPage"
            @current-change="(p: number) => loadResultPage(currentExecutionId!, p, resultPageSize)"
            @size-change="(s: number) => loadResultPage(currentExecutionId!, 1, s)"
          />
        </div>
      </template>
      <div v-else class="result-panel__empty">{{ t('ide.noResults') }}</div>
    </div>

    <div v-show="currentTab === 'log'" class="result-panel__content">
      <LogViewer :content="logData" />
    </div>

    <div v-show="currentTab === 'history'" class="result-panel__content history-panel">
      <!-- Left: history list -->
      <div class="history-list">
        <div
          v-for="item in historyList" :key="item.id"
          class="history-item" :class="{ active: historySelected?.id === item.id }"
          @click="selectHistoryItem(item)"
        >
          <div class="history-item__head">
            <el-tag :type="item.status === 'SUCCESS' ? 'success' : item.status === 'FAILED' ? 'danger' : 'warning'" size="small">{{ item.status }}</el-tag>
            <span class="history-item__time">{{ item.createdAt ? new Date(item.createdAt).toLocaleString() : '' }}</span>
          </div>
          <code class="history-item__sql">{{ item.content?.substring(0, 60) || '-' }}</code>
          <span v-if="item.duration" class="history-item__dur">{{ (item.duration / 1000).toFixed(1) }}s</span>
        </div>
        <div v-if="!historyList.length" class="result-panel__empty">{{ t('ide.noHistory') }}</div>
      </div>
      <!-- Right: selected item detail -->
      <div class="history-detail">
        <template v-if="historySelected">
          <div class="history-detail__tabs">
            <button class="result-tab" :class="{ 'result-tab--active': historyDetailTab === 'content' }" @click="historyDetailTab = 'content'">{{ t('ide.executedContent') }}</button>
            <button class="result-tab" :class="{ 'result-tab--active': historyDetailTab === 'result' }" @click="historyDetailTab = 'result'">{{ t('ide.result') }}</button>
            <button class="result-tab" :class="{ 'result-tab--active': historyDetailTab === 'log' }" @click="historyDetailTab = 'log'">{{ t('ide.log') }}</button>
            <span class="history-detail__status">
              <el-tag :type="historySelected.status === 'SUCCESS' ? 'success' : historySelected.status === 'FAILED' ? 'danger' : 'warning'" size="small" effect="light">{{ historySelected.status }}</el-tag>
              <span v-if="historySelected.duration" style="font-size:11px;color:var(--r-text-muted);margin-left:6px">{{ (historySelected.duration / 1000).toFixed(1) }}s</span>
              <el-button v-if="historySelected.status === 'FAILED'" type="warning" text size="small" :loading="diagnosingHistory" style="margin-left:6px" @click="handleHistoryDiagnose">
                <el-icon><MagicStick /></el-icon>{{ t('ide.aiDiagnose') }}
              </el-button>
            </span>
          </div>
          <div v-show="historyDetailTab === 'content'" class="history-detail__body">
            <div class="log-viewer">
              <pre class="log-viewer__content">{{ historySelected.content || '-' }}</pre>
            </div>
          </div>
          <div v-show="historyDetailTab === 'result'" class="history-detail__body history-detail__body--table">
            <template v-if="historyResultCols.length">
              <el-table :data="historyResultRows" size="small" border height="100%" :scrollbar-always-on="true">
                <el-table-column type="index" :index="(i: number) => historyOffset + i + 1" label="#" width="60" fixed="left" />
                <el-table-column v-for="col in historyResultCols" :key="col" :prop="col" :label="col" min-width="140" show-overflow-tooltip />
              </el-table>
              <div class="result-panel__pager">
                <span class="result-panel__limit-notice">{{ t('ide.resultLimitNotice') }}</span>
                <div class="result-panel__pager-spacer" />
                <el-pagination
                  small layout="total, sizes, prev, pager, next"
                  :total="historyTotal" :page-sizes="[50, 100, 500]" :page-size="historyPageSize" :current-page="historyPage"
                  @current-change="(p: number) => loadHistoryResultPage(historySelected!.id, p, historyPageSize)"
                  @size-change="(s: number) => loadHistoryResultPage(historySelected!.id, 1, s)"
                />
              </div>
            </template>
            <div v-else class="result-panel__empty">{{ t('ide.noResults') }}</div>
          </div>
          <div v-show="historyDetailTab === 'log'" class="history-detail__body">
            <div class="log-viewer">
              <pre class="log-viewer__content">{{ historyLogContent || historySelected.errorMessage || t('ide.noLogs') }}</pre>
            </div>
          </div>
        </template>
        <div v-else class="result-panel__empty">{{ t('ide.selectHistory') }}</div>
      </div>
    </div>

    <!-- Running Jobs Tab -->
    <div v-show="currentTab === 'jobs'" class="result-panel__content">
      <el-table :data="runningJobs" size="small" border stripe height="100%" v-loading="jobsLoading">
        <el-table-column prop="name" :label="t('jobs.name')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="taskType" :label="t('jobs.taskType')" width="110">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.taskType }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="runtimeType" :label="t('jobs.runtimeType')" width="120">
          <template #default="{ row }"><el-tag v-if="row.runtimeType" size="small">{{ runtimeLabel(row.runtimeType) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="status" :label="t('jobs.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RUNNING' ? 'primary' : 'warning'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" :label="t('jobs.startedAt')" width="150">
          <template #default="{ row }">{{ row.startedAt ? new Date(row.startedAt).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('jobs.operations')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" text size="small" @click="handleKillJob(row)">{{ t('jobs.kill') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!jobsLoading && runningJobs.length === 0" class="result-panel__empty">{{ t('jobs.noRunningJobs') }}</div>
    </div>

    <div v-show="currentTab === 'versions'" class="result-panel__content">
      <div class="versions-list">
        <el-table :data="versionList" size="small" stripe height="100%">
          <el-table-column prop="versionNo" :label="t('workflow.versionNo')" width="80" align="center">
            <template #default="{ row }">v{{ row.versionNo }}</template>
          </el-table-column>
          <el-table-column prop="remark" :label="t('workflow.remark')" min-width="200" show-overflow-tooltip />
          <el-table-column prop="createdAt" :label="t('common.createdAt')" width="170">
            <template #default="{ row }">{{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '' }}</template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="160" align="center">
            <template #default="{ row, $index }">
              <el-button v-if="$index > 0" text type="primary" size="small" :loading="scriptDiffLoading" @click="handleDiffVersion(row)">{{ t('ide.compare') }}</el-button>
              <el-popconfirm :title="t('ide.rollbackConfirm')" @confirm="handleRollback(row)">
                <template #reference>
                  <el-button v-if="$index > 0" text type="warning" size="small" :loading="scriptRollbackLoading">{{ t('ide.rollback') }}</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="versionTotal > 10"
          small layout="prev, pager, next"
          :total="versionTotal" :page-size="10"
          :current-page="versionPage"
          style="margin-top: 8px; justify-content: center"
          @current-change="(p: number) => { versionPage = p; loadVersions() }"
        />
      </div>
    </div>

    <!-- Version Diff Dialog -->
    <el-dialog v-model="diffDialogVisible" :title="diffDialogTitle" width="900px" destroy-on-close append-to-body top="6vh">
      <div style="height: 480px">
        <SqlDiffViewer :old-sql="diffOldContent" :new-sql="diffNewContent" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, inject, computed, watch, onUnmounted } from 'vue'
import { IDE_STATE_KEY, type Tab, type ResultTab } from './ideState'
import { useI18n } from 'vue-i18n'
import { ArrowDown, MagicStick } from '@element-plus/icons-vue'
import { getExecution, getExecutionLog, getExecutionResult, listExecutionsByScript, listScriptVersions, rollbackScript, getScriptVersionContent } from '@/api/script'
import { listRunningByScript, killJob } from '@/api/jobs'
import { getRuntimeTypes, type RuntimeTypeDef } from '@/api/config'
import SqlDiffViewer from '@/components/SqlDiffViewer.vue'
import LogViewer from '@/components/LogViewer.vue'
import { extractEditorContent, extractDiffContent, extractContentField } from '@/utils/scriptContent'
import { useTaskTypesStore } from '@/stores/taskTypes'
import { useAiChatStore } from '@/stores/aiChat'
import { useWorkspaceStore } from '@/stores/workspace'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()
const ideState = inject(IDE_STATE_KEY)!
const workspaceStore = useWorkspaceStore()
const taskTypesStore = useTaskTypesStore()
const workspaceId = computed(() => workspaceStore.currentWorkspace?.id)
const currentTab = ref<ResultTab>('result')

const executionStatus = ref('')
const resultColumns = ref<string[]>([])
const resultRows = ref<Record<string, any>[]>([])
const resultTotal = ref(0)
const resultPage = ref(1)
const resultPageSize = ref(100)
const resultOffset = computed(() => (resultPage.value - 1) * resultPageSize.value)
const currentExecutionId = ref<number | null>(null)
const logData = ref('')
let logOffsetLine = 0

const statusType = computed(() => {
  switch (executionStatus.value) {
    case 'SUCCESS': return 'success'
    case 'FAILED': case 'CANCELLED': return 'danger'
    case 'RUNNING': return 'warning'
    default: return 'info'
  }
})

const activeTab = computed<Tab | undefined>(() =>
  ideState.tabs.find((t: Tab) => t.id === ideState.activeTabId)
)

let pollTimer: ReturnType<typeof setInterval> | null = null

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

function clearDisplay() {
  executionStatus.value = ''
  resultColumns.value = []
  resultRows.value = []
  resultTotal.value = 0
  resultPage.value = 1
  currentExecutionId.value = null
  logData.value = ''
  logOffsetLine = 0
  historySelected.value = null
  historyResultCols.value = []
  historyResultRows.value = []
  historyTotal.value = 0
  historyPage.value = 1
  historyLogContent.value = ''
}

async function loadResultPage(executionId: number, page: number, pageSize: number) {
  resultPage.value = page
  resultPageSize.value = pageSize
  currentExecutionId.value = executionId
  try {
    const { data: r } = await getExecutionResult(executionId, (page - 1) * pageSize, pageSize)
    resultColumns.value = r?.columns ?? []
    resultRows.value = r?.rows ?? []
    resultTotal.value = r?.totalRows ?? 0
  } catch { /* ignore */ }
}

onUnmounted(stopPolling)

// 切换 tab 或同 tab 内 executionId 变化时,统一 dispatch 显示。
watch(
  () => {
    const t = activeTab.value
    return [t?.id, t?.executionId, t?.lastExecutionId] as const
  },
  ([tabId, execId, lastId], oldVals) => {
    const [oldTabId, oldExecId] = oldVals ?? []
    const switchedTab = tabId !== oldTabId
    const newExecution = !switchedTab && execId !== oldExecId && execId != null
    if (!switchedTab && !newExecution) return

    stopPolling()
    clearDisplay()
    if (!activeTab.value) return

    // 当前在跑 > 上次执行的快照
    if (execId) {
      loadExecutionResult(execId)
    } else if (lastId) {
      restoreOrFollow(lastId)
    }
    if (currentTab.value === 'history') loadHistory()
  },
  { immediate: true }
)

watch(() => activeTab.value?.resultTab, (tab) => {
  if (!tab || !activeTab.value) return
  currentTab.value = tab
  if (tab === 'versions') loadVersions(true)
  activeTab.value.resultTab = null
})

// 外部(AI explain/optimize 等)通过 tab.resultLog 注入临时日志
watch(() => activeTab.value?.resultLog, (val) => {
  if (val == null || !activeTab.value) return
  logData.value = val
  executionStatus.value = ''
  activeTab.value.resultLog = null
})

function loadExecutionResult(executionId: number) {
  executionStatus.value = 'RUNNING'
  resultColumns.value = []
  resultRows.value = []
  logData.value = ''
  logOffsetLine = 0
  currentTab.value = 'log'

  pollTimer = setInterval(async () => {
    try {
      const [res, logRes] = await Promise.all([
        getExecution(executionId),
        getExecutionLog(executionId, logOffsetLine).catch(() => ({ data: null })),
      ])
      const exec = res.data
      executionStatus.value = exec.status

      const logResult = logRes.data
      if (logResult && logResult.lines) {
        logData.value += logResult.lines
        logOffsetLine = logResult.offsetLine
      }
      if (!logData.value && exec.errorMessage) {
        logData.value = exec.errorMessage
      }

      const tab = activeTab.value
      if (exec.status === 'SUCCESS') {
        stopPolling()
        if (tab) { tab.executionRunning = false; tab.executionId = null }
        await loadResultPage(executionId, 1, resultPageSize.value)
        if (resultColumns.value.length > 0) currentTab.value = 'result'
        refreshHistoryIfOpen()
      } else if (exec.status === 'FAILED' || exec.status === 'CANCELLED') {
        stopPolling()
        if (tab) { tab.executionRunning = false; tab.executionId = null }
        currentTab.value = 'log'
        refreshHistoryIfOpen()
      }
    } catch {
      stopPolling()
      const tab = activeTab.value
      if (tab) { tab.executionRunning = false; tab.executionId = null }
    }
  }, 1500)
}

// 拿历史 execution 显示快照;还在跑则把 executionId 投回 tab,watcher 自然接管 polling。
async function restoreOrFollow(executionId: number) {
  try {
    const res = await getExecution(executionId)
    const exec = res.data
    executionStatus.value = exec.status

    if (exec.status === 'RUNNING' || exec.status === 'PENDING') {
      ideState.resultPanelVisible = true
      const tab = activeTab.value
      if (tab) {
        tab.executionRunning = true
        tab.executionId = executionId
      }
      return
    }
    // 终态: log 与 result 互不依赖,并发拉
    const [logRes] = await Promise.all([
      getExecutionLog(executionId, 0).catch(() => ({ data: null })),
      loadResultPage(executionId, 1, resultPageSize.value),
    ])
    logData.value = logRes.data?.lines || exec.errorMessage || ''
    currentTab.value = resultColumns.value.length > 0 ? 'result' : 'log'
  } catch { /* ignore */ }
}

// === History ===
const historyList = ref<any[]>([])

function refreshHistoryIfOpen() {
  if (currentTab.value === 'history') {
    historySelected.value = null
    loadHistory()
  }
}

async function loadHistory() {
  const activeTab = ideState.tabs?.find((t: any) => t.id === ideState.activeTabId)
  const scriptCode = activeTab?.scriptCode
  if (!scriptCode) return
  try {
    const { data } = await listExecutionsByScript(scriptCode)
    historyList.value = data ?? []
    // Auto-select the latest history item
    if (historyList.value.length && !historySelected.value) {
      selectHistoryItem(historyList.value[0])
    }
  } catch { historyList.value = [] }
}

// History detail (independent from current execution)
const historySelected = ref<any>(null)
const historyDetailTab = ref<'content' | 'result' | 'log'>('content')
const historyResultCols = ref<string[]>([])
const historyResultRows = ref<any[]>([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historyPageSize = ref(100)
const historyOffset = computed(() => (historyPage.value - 1) * historyPageSize.value)
const historyLogContent = ref('')
let historySelectGen = 0

async function loadHistoryResultPage(historyId: number, page: number, pageSize: number) {
  historyPage.value = page
  historyPageSize.value = pageSize
  try {
    const { data: r } = await getExecutionResult(historyId, (page - 1) * pageSize, pageSize)
    historyResultCols.value = r?.columns ?? []
    historyResultRows.value = r?.rows ?? []
    historyTotal.value = r?.totalRows ?? 0
  } catch { /* ignore */ }
}

async function selectHistoryItem(row: any) {
  const gen = ++historySelectGen
  historySelected.value = row
  historyResultCols.value = []
  historyResultRows.value = []
  historyTotal.value = 0
  historyPage.value = 1
  historyLogContent.value = ''
  if (row.rowCount > 0 || row.resultPath) {
    await loadHistoryResultPage(row.id, 1, historyPageSize.value)
  }
  // Fetch log from execution node
  try {
    const { data } = await getExecutionLog(row.id, 0)
    if (gen !== historySelectGen) return // stale response
    historyLogContent.value = data?.lines || row.errorMessage || ''
  } catch {
    if (gen !== historySelectGen) return
    historyLogContent.value = row.errorMessage || ''
  }
  // Smart default tab: result if has data, log if failed, content otherwise
  if (row.status === 'FAILED' || row.status === 'CANCELLED') {
    historyDetailTab.value = 'log'
  } else if (historyResultCols.value.length > 0) {
    historyDetailTab.value = 'result'
  } else {
    historyDetailTab.value = 'log'
  }
}

// === AI Diagnose ===
const aiStore = useAiChatStore()
const diagnosing = ref(false)
const diagnosingHistory = ref(false)

async function sendDiagnoseToAi(taskType: string, content: string, errorMessage: string, log: string) {
  const logTail = log.length > 3000 ? '...\n' + log.slice(-3000) : log
  // 把 error/log/content 拼进 user message —— 新建会话默认 CHAT(无工具),只能内联。
  const prompt = [
    t('ide.aiDiagnoseRequest', { taskType }),
    '',
    '[error]',
    errorMessage || '(empty)',
    '',
    '[logs (last 3000 chars)]',
    logTail || '(empty)',
    '',
    '[content]',
    content || '(empty)',
  ].join('\n')
  ideState.aiPanelVisible = true
  await aiStore.sendTurn(prompt, {
    taskType,
    forceModeIfNew: 'CHAT',
  })
}

async function handleDiagnose() {
  const activeTab = ideState.tabs?.find((t: any) => t.id === ideState.activeTabId)
  if (!activeTab?.lastExecutionId) return
  diagnosing.value = true
  try {
    const res = await getExecution(activeTab.lastExecutionId)
    const exec = res.data
    await sendDiagnoseToAi(
      exec.taskType || activeTab.taskType || 'UNKNOWN',
      exec.content || '',
      exec.errorMessage || '',
      logData.value || '',
    )
  } catch { /* ignore */ } finally {
    diagnosing.value = false
  }
}

async function handleHistoryDiagnose() {
  const row = historySelected.value
  if (!row) return
  diagnosingHistory.value = true
  try {
    await sendDiagnoseToAi(
      row.taskType || 'UNKNOWN',
      row.content || '',
      row.errorMessage || '',
      historyLogContent.value || '',
    )
  } catch { /* ignore */ } finally {
    diagnosingHistory.value = false
  }
}

// ==================== Running Jobs Tab ====================

const runningJobs = ref<any[]>([])
const jobsLoading = ref(false)
const runningJobCount = computed(() => runningJobs.value.length)
const runtimeTypes = ref<RuntimeTypeDef[]>([])

function runtimeLabel(value: string) {
  const found = runtimeTypes.value.find(rt => rt.value === value)
  return found ? found.label : value || '-'
}

let jobsTimer: ReturnType<typeof setInterval> | null = null

// 加载一次即可（枚举数据不变）
getRuntimeTypes().then(res => { runtimeTypes.value = res.data ?? [] }).catch(() => {})

async function loadRunningJobs() {
  const activeTab = ideState.tabs.find(t => t.id === ideState.activeTabId)
  const scriptCode = activeTab?.scriptCode
  if (!scriptCode) {
    runningJobs.value = []
    return
  }

  jobsLoading.value = true
  try {
    const res = await listRunningByScript(scriptCode)
    runningJobs.value = res.data ?? []
  } catch { /* ignore */ } finally {
    jobsLoading.value = false
  }
}

// Auto-refresh when jobs tab is active
watch(currentTab, (tab) => {
  if (tab === 'jobs') {
    loadRunningJobs()
    if (!jobsTimer) {
      jobsTimer = setInterval(loadRunningJobs, 5000)
    }
  } else {
    if (jobsTimer) { clearInterval(jobsTimer); jobsTimer = null }
  }
})

onUnmounted(() => {
  if (jobsTimer) { clearInterval(jobsTimer); jobsTimer = null }
})

async function handleKillJob(row: any) {
  try {
    await ElMessageBox.confirm(t('jobs.killConfirm'), { type: 'warning' })
    await killJob(row.id)
    ElMessage.success(t('jobs.killSuccess'))
    loadRunningJobs()
  } catch { /* cancelled */ }
}

// ==================== Version History Tab ====================

const versionList = ref<any[]>([])
const versionTotal = ref(0)
const versionPage = ref(1)

async function loadVersions(resetPage = false) {
  if (resetPage) versionPage.value = 1
  const tab = ideState.tabs.find((t: any) => t.id === ideState.activeTabId)
  if (!tab) return
  try {
    const res: any = await listScriptVersions(workspaceId.value!, tab.scriptCode, { pageNum: versionPage.value, pageSize: 10 })
    versionList.value = res.data?.records ?? []
    versionTotal.value = res.data?.total ?? 0
  } catch { versionList.value = [] }
}

const scriptRollbackLoading = ref(false)
const scriptDiffLoading = ref(false)

async function handleRollback(version: any) {
  const tab = ideState.tabs.find((t: any) => t.id === ideState.activeTabId)
  if (!tab) return
  scriptRollbackLoading.value = true
  try {
    const res: any = await rollbackScript(workspaceId.value!, tab.scriptCode, version.id)
    if (res.data?.content) {
      const content = res.data.content
      const category = taskTypesStore.categoryOf(tab.taskType)
      tab.sql = extractEditorContent(content, category)
      tab.datasourceId = extractContentField(content, 'dataSourceId', tab.datasourceId)
      tab.executionMode = extractContentField(content, 'executionMode', tab.executionMode)
      tab.modified = false
      ideState.editorRefreshKey++
    }
    ElMessage.success(t('ide.rollbackSuccess'))
    loadVersions()
  } catch { ElMessage.error(t('common.failed')) }
  finally { scriptRollbackLoading.value = false }
}

// Version diff dialog
const diffDialogVisible = ref(false)
const diffOldContent = ref('')
const diffNewContent = ref('')
const diffDialogTitle = ref('')

async function handleDiffVersion(version: any) {
  if (versionList.value.length < 2) return
  const latest = versionList.value[0]
  if (latest.id === version.id) return
  const tab = ideState.tabs.find(t => t.id === ideState.activeTabId)
  if (!tab) return
  scriptDiffLoading.value = true
  try {
    const [resOld, resNew]: any[] = await Promise.all([
      getScriptVersionContent(workspaceId.value!, tab.scriptCode, version.id),
      getScriptVersionContent(workspaceId.value!, tab.scriptCode, latest.id),
    ])
    const rawOld = resOld.data?.content ?? ''
    const rawNew = resNew.data?.content ?? ''
    const category = taskTypesStore.categoryOf(tab.taskType)
    diffOldContent.value = extractDiffContent(rawOld, category)
    diffNewContent.value = extractDiffContent(rawNew, category)
    diffDialogTitle.value = `v${version.versionNo} → v${latest.versionNo}`
    diffDialogVisible.value = true
  } catch { ElMessage.error(t('common.failed')) }
  finally { scriptDiffLoading.value = false }
}
</script>

<style scoped lang="scss">
@use '@/styles/ide.scss' as *;
.result-panel { height: 100%; display: flex; flex-direction: column; background: var(--r-bg-card); }

.result-panel__header {
  display: flex; align-items: center; height: 36px; padding: 0 10px;
  background: #{$ide-panel-bg}; border-bottom: 1px solid #{$ide-border}; flex-shrink: 0; gap: 8px;
}
.result-panel__tabs { display: flex; gap: 2px; }
.result-tab {
  padding: 4px 12px; font-size: 12px; border: none; background: transparent;
  color: var(--r-text-muted); cursor: pointer; border-radius: 4px; font-weight: 500;
  &:hover { color: var(--r-text-secondary); background: #{$ide-hover-bg}; }
  &--active { color: var(--r-accent); background: var(--r-accent-bg); }
}
.jobs-badge { margin-left: 4px; :deep(.el-badge__content) { font-size: 11px; } }
.result-panel__status { font-size: 11px; }
.result-panel__spacer { flex: 1; }

.result-panel__content { flex: 1; overflow: auto; min-height: 0; }
.result-panel__content--table {
  overflow: hidden;
  display: flex; flex-direction: column;
  :deep(.el-table) { flex: 1; height: auto !important; }
}
.result-panel__pager {
  display: flex; align-items: center; gap: 12px; padding: 4px 10px;
  border-top: 1px solid #{$ide-border}; background: #{$ide-panel-bg}; flex-shrink: 0;
}
.result-panel__pager-spacer { flex: 1; }
.result-panel__limit-notice { font-size: 11px; color: var(--r-text-muted); }
.result-panel__empty { display: flex; align-items: center; justify-content: center; height: 100%; font-size: 13px; color: var(--r-text-disabled); }


/* History panel — left/right split */
.history-panel { display: flex; height: 100%; }

.history-list {
  width: 280px; flex-shrink: 0; overflow-y: auto;
  border-right: 1px solid #{$ide-border}; background: #{$ide-panel-bg};
}
.history-item {
  padding: 8px 12px; cursor: pointer; border-bottom: 1px solid #{$ide-hover-bg};
  transition: background 0.1s;
  &:hover { background: var(--r-bg-hover); }
  &.active { background: var(--r-accent-bg); border-left: 2px solid var(--r-accent); }
}
.history-item__head { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.history-item__time { font-size: 11px; color: var(--r-text-muted); }
.history-item__sql {
  display: block; font-family: var(--r-font-mono); font-size: 11px; color: var(--r-text-secondary);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.history-item__dur { font-size: 11px; color: var(--r-text-muted); }

.history-detail { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.history-detail__tabs {
  display: flex; align-items: center; gap: 2px; padding: 4px 10px;
  background: #{$ide-panel-bg}; border-bottom: 1px solid #{$ide-hover-bg}; flex-shrink: 0;
}
.history-detail__status { margin-left: auto; display: flex; align-items: center; }
.history-detail__body { flex: 1; overflow: auto; min-height: 0; }
.history-detail__body--table {
  overflow: hidden;
  display: flex; flex-direction: column;
  :deep(.el-table) { flex: 1; height: auto !important; }
}

.versions-list { height: 100%; display: flex; flex-direction: column; padding: 8px; }
</style>
