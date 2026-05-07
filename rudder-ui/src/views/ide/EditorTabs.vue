<template>
  <div class="editor-tabs">
    <!-- Tab bar -->
    <div ref="tabBarRef" class="tab-bar">
      <div
        v-for="tab in ideState.tabs" :key="tab.id"
        :ref="(el: any) => { if (tab.id === ideState.activeTabId) activeTabEl = el }"
        class="tab-item" :class="{ 'tab-item--active': tab.id === ideState.activeTabId }"
        @click="ideState.activeTabId = tab.id"
        @contextmenu.prevent="openTabContextMenu($event, tab.id)"
      >
        <TaskIcon v-if="tab.taskType" :type="tab.taskType" :size="14" />
        <span class="tab-item__name">{{ tab.name }}</span>
        <span v-if="tab.modified" class="tab-item__dot" />
        <el-icon class="tab-item__close" @click.stop="closeTab(tab.id)"><Close /></el-icon>
      </div>
      <div v-if="!ideState.tabs.length" class="tab-bar__empty">
        <span class="tab-bar__empty-mark">·</span>
        <span class="tab-bar__empty-text">{{ t('ide.noFilesOpen') }}</span>
      </div>
    </div>

    <!-- Tab context menu — Teleport 到 body 避开祖先 containing block 偏移 -->
    <Teleport to="body">
      <div
        v-if="tabCtx.visible" class="tab-context-menu"
        :style="{ top: tabCtx.y + 'px', left: tabCtx.x + 'px' }"
      >
        <div class="tab-context-menu__item" @click="closeTab(tabCtx.tabId!)">{{ t('ide.tabClose') }}</div>
        <div class="tab-context-menu__item" @click="closeOtherTabs">{{ t('ide.tabCloseOthers') }}</div>
        <div class="tab-context-menu__item" @click="closeAllTabs">{{ t('ide.tabCloseAll') }}</div>
        <div class="tab-context-menu__divider" />
        <div class="tab-context-menu__item" @click="closeLeftTabs">{{ t('ide.tabCloseLeft') }}</div>
        <div class="tab-context-menu__item" @click="closeRightTabs">{{ t('ide.tabCloseRight') }}</div>
        <div class="tab-context-menu__divider" />
        <div class="tab-context-menu__item" @click="closeUnmodifiedTabs">{{ t('ide.tabCloseUnmodified') }}</div>
      </div>
    </Teleport>

    <!-- Toolbar -->
    <div v-if="activeTab" class="editor-toolbar">
      <el-button v-if="!activeTab?.executionRunning" type="primary" size="small" :loading="executing" @click="handleRun">
        <el-icon><VideoPlay /></el-icon><span>{{ t('ide.run') }}</span>
      </el-button>
      <el-button v-else type="danger" size="small" @click="handleCancel">
        <el-icon><VideoPause /></el-icon><span>{{ t('common.cancel') }}</span>
      </el-button>
      <el-button size="small" @click="handleSave">
        <el-icon><FolderChecked /></el-icon><span>{{ t('ide.save') }}</span>
      </el-button>
      <el-button size="small" @click="handleCommit">
        <el-icon><Promotion /></el-icon><span>{{ t('ide.commitVersion') }}</span>
      </el-button>
      <el-button v-if="activeTab && activeTab.binding === null" size="small" @click="pushDialogVisible = true">
        <el-icon><Upload /></el-icon><span>{{ t('ide.pushToWorkflow') }}</span>
      </el-button>
      <el-tag
        v-else-if="activeTab?.binding"
        size="small" type="success" effect="plain"
        style="margin-left: 6px; max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap"
      >
        <el-icon style="margin-right: 2px"><Link /></el-icon>
        {{ activeTab.binding.projectName }} / {{ activeTab.binding.workflowName }} / {{ activeTab.binding.taskName }}
      </el-tag>
      <template v-if="!isFormTask">
        <div class="editor-toolbar__sep" />
        <el-button text type="warning" size="small" @click="handleExplain">
          <el-icon><MagicStick /></el-icon><span>{{ t('ide.explain') }}</span>
        </el-button>
        <el-button text type="warning" size="small" @click="handleOptimize">
          <el-icon><MagicStick /></el-icon><span>{{ t('ide.optimize') }}</span>
        </el-button>
      </template>

      <div class="editor-toolbar__sep" />
      <el-popover
        v-model:visible="paramsOpen"
        trigger="click"
        placement="bottom-start"
        :width="420"
        :show-arrow="false"
        :offset="4"
        popper-class="params-popover"
        :teleported="true"
        append-to="body"
      >
        <template #reference>
          <el-button text size="small">
            <el-icon><Setting /></el-icon>
            <span>{{ t('ide.params') }}</span>
            <el-tag v-if="activeTab && Object.keys(activeTab.params || {}).length" size="small" round effect="plain" style="margin-left: 4px">
              {{ Object.keys(activeTab.params).length }}
            </el-tag>
          </el-button>
        </template>
        <ParamsPanel
          v-if="activeTab"
          :model-value="activeTab.params || {}"
          @update:model-value="onParamsChange($event)"
        />
      </el-popover>

      <div class="editor-toolbar__spacer" />

      <el-select v-if="showExecutionModeSelect" v-model="activeTab.executionMode" size="small" style="width: 120px; margin-right: 8px" @change="onExecutionModeChange">
        <el-option v-for="mode in activeTaskTypeDef!.executionModes" :key="mode" :label="mode.charAt(0) + mode.slice(1).toLowerCase()" :value="mode" />
      </el-select>
      <el-select v-if="needsDatasource" v-model="activeTab.datasourceId" :placeholder="t('ide.selectDatasource')" size="small" style="width: 180px" clearable>
        <el-option v-for="ds in filteredDatasources" :key="ds.id" :label="ds.name" :value="ds.id" />
      </el-select>
    </div>

    <JarTaskEditor
      v-if="activeTab && isJarTask"
      :model-value="activeTab.sql"
      :task-type="activeTab.taskType"
      @update:model-value="onJarParamsChange"
    />
    <HttpTaskEditor
      v-else-if="activeTab && isApiTask && activeTab.taskType === 'HTTP'"
      :model-value="activeTab.sql"
      @update:model-value="onJarParamsChange"
    />
    <div v-else id="monaco-editor-container" class="editor-container" />

    <!-- Push to Workflow dialog -->
    <el-dialog v-model="pushDialogVisible" :title="t('ide.pushToWorkflow')" width="520px" destroy-on-close append-to-body>
      <el-form label-position="top">
        <el-form-item :label="t('ide.selectProject')" required>
          <el-select v-model="pushForm.projectCode" :placeholder="t('ide.selectProject')" style="width: 100%" filterable @change="onPushProjectChange">
            <el-option v-for="p in pushProjects" :key="p.code" :label="p.name" :value="String(p.code)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('ide.selectWorkflow')" required>
          <el-select v-model="pushForm.workflowDefinitionCode" :placeholder="t('ide.selectWorkflow')" style="width: 100%" filterable :disabled="!pushForm.projectCode" @change="onPushWorkflowChange">
            <el-option v-for="w in pushWorkflows" :key="w.code" :label="w.name" :value="String(w.code)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('ide.pushMode')">
          <el-radio-group v-model="pushForm.mode">
            <el-radio value="NEW">{{ t('ide.pushModeNew') }}</el-radio>
            <el-radio value="REPLACE">{{ t('ide.pushModeReplace') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="pushForm.mode === 'REPLACE'" :label="t('ide.selectTask')" required>
          <el-select v-model="pushForm.taskDefinitionCode" :placeholder="t('ide.selectTask')" style="width: 100%" filterable :disabled="!pushForm.workflowDefinitionCode">
            <el-option v-for="td in pushTasks" :key="td.code" :label="td.name" :value="String(td.code)" />
          </el-select>
          <div style="color: var(--r-warning); font-size: 12px; margin-top: 4px">{{ t('ide.replaceWarning') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pushDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="pushing" :disabled="!canPush" @click="handlePush">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Commit Version dialog -->
    <el-dialog v-model="commitDialogVisible" :title="t('ide.commitVersion')" width="420px" destroy-on-close append-to-body @submit.prevent>
      <el-form @submit.prevent>
        <el-form-item :label="t('ide.commitMessage')" required>
          <el-input v-model="commitMessage" type="textarea" :rows="3" :placeholder="t('ide.commitMessagePlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="commitDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="committing" :disabled="!commitMessage.trim()" @click="doCommit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, inject, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { IDE_STATE_KEY, buildTurnContext } from './ideState'
import { useI18n } from 'vue-i18n'
import { Close, VideoPlay, VideoPause, FolderChecked, Setting, Upload, Link, Promotion } from '@element-plus/icons-vue'
import TaskIcon from '@/components/TaskIcon.vue'
import JarTaskEditor from './JarTaskEditor.vue'
import HttpTaskEditor from './HttpTaskEditor.vue'
import ParamsPanel from './ParamsPanel.vue'
import { ElMessage } from 'element-plus'
import { executeScript, updateScript, cancelExecution, getScriptBinding, pushToWorkflow, commitScriptVersion } from '@/api/script'
import { listProjects } from '@/api/workspace'
import { listWorkflowDefinitions, listTaskDefinitions } from '@/api/workflow'
import { wrapEditorContent } from '@/utils/scriptContent'
import { useTaskTypesStore } from '@/stores/taskTypes'
import { useAiChatStore } from '@/stores/aiChat'
import { useDatasourceStore } from '@/stores/datasource'
import { useWorkspaceStore } from '@/stores/workspace'
import { useMonacoTheme } from '@/composables/useMonacoTheme'
import { editor as monacoEditor, KeyCode, KeyMod } from 'monaco-editor'
import type * as MonacoTypes from 'monaco-editor'
import { registerCompletionProviders, setSyntaxMap, setCurrentDialect, getMonacoLanguage } from '@/utils/sqlCompletion'
import { getAllSyntax } from '@/api/config'

import type { Tab } from './ideState'

const { t } = useI18n()
const ideState = inject(IDE_STATE_KEY)!
const datasourceStore = useDatasourceStore()
const workspaceStore = useWorkspaceStore()
const monacoTheme = useMonacoTheme()
const workspaceId = computed(() => workspaceStore.currentWorkspace?.id)

const executing = ref(false)
const paramsOpen = ref(false)
let editor: MonacoTypes.editor.IStandaloneCodeEditor | null = null
let suppressModified = false
const taskTypesStore = useTaskTypesStore()
const taskTypes = computed(() => taskTypesStore.list)
const tabBarRef = ref<HTMLElement>()
let activeTabEl: any = null

// ========== Tab context menu ==========
const tabCtx = reactive({ visible: false, x: 0, y: 0, tabId: null as string | null })

function openTabContextMenu(e: MouseEvent, tabId: string) {
  tabCtx.visible = true
  tabCtx.x = e.clientX
  tabCtx.y = e.clientY
  tabCtx.tabId = tabId
}
function closeTabContextMenu() { tabCtx.visible = false; tabCtx.tabId = null }

function closeOtherTabs() {
  const id = tabCtx.tabId
  closeTabContextMenu()
  ideState.tabs = ideState.tabs.filter((t: Tab) => t.id === id)
  if (!ideState.tabs.find((t: Tab) => t.id === ideState.activeTabId)) {
    ideState.activeTabId = ideState.tabs.length ? ideState.tabs[0].id : null
  }
}

function closeAllTabs() {
  closeTabContextMenu()
  ideState.tabs = []
  ideState.activeTabId = null
}

function closeLeftTabs() {
  const id = tabCtx.tabId
  closeTabContextMenu()
  const idx = ideState.tabs.findIndex((t: Tab) => t.id === id)
  if (idx <= 0) return
  const removed = ideState.tabs.splice(0, idx) as Tab[]
  if (removed.some((t: Tab) => t.id === ideState.activeTabId)) {
    ideState.activeTabId = id
  }
}

function closeRightTabs() {
  const id = tabCtx.tabId
  closeTabContextMenu()
  const idx = ideState.tabs.findIndex((t: Tab) => t.id === id)
  if (idx === -1 || idx === ideState.tabs.length - 1) return
  const removed = ideState.tabs.splice(idx + 1) as Tab[]
  if (removed.some((t: Tab) => t.id === ideState.activeTabId)) {
    ideState.activeTabId = id
  }
}

function closeUnmodifiedTabs() {
  closeTabContextMenu()
  const removed = ideState.tabs.filter((t: Tab) => !t.modified)
  ideState.tabs = ideState.tabs.filter((t: Tab) => t.modified)
  if (removed.some((t: Tab) => t.id === ideState.activeTabId)) {
    ideState.activeTabId = ideState.tabs.length ? ideState.tabs[0].id : null
  }
}

function onDocTabClick() { tabCtx.visible = false }
onMounted(() => document.addEventListener('click', onDocTabClick))
onUnmounted(() => document.removeEventListener('click', onDocTabClick))

const activeTab = computed<Tab | undefined>(() =>
  ideState.tabs.find((t: Tab) => t.id === ideState.activeTabId)
)

const activeTaskTypeDef = computed(() => {
  const taskType = activeTab.value?.taskType
  return taskType ? taskTypes.value.find(t => t.value === taskType) : undefined
})

const needsDatasource = computed(() => activeTaskTypeDef.value?.needsDatasource ?? true)
const activeCategory = computed(() => activeTaskTypeDef.value?.category ?? 'OTHER')

const isJarTask = computed(() => activeTaskTypeDef.value?.category === 'JAR')
// API category：当前只有 HTTP,未来 Webhook / gRPC / MQ 同样走这里
const isApiTask = computed(() => activeTaskTypeDef.value?.category === 'API')
// 是否使用参数化表单编辑器（非 Monaco 文本编辑器）
const isFormTask = computed(() => isJarTask.value || isApiTask.value)

const showExecutionModeSelect = computed(() => {
  const modes = activeTaskTypeDef.value?.executionModes
  return modes && modes.length > 1
})

const filteredDatasources = computed(() => {
  const def = activeTaskTypeDef.value
  if (!def?.datasourceType) return datasourceStore.datasources
  return datasourceStore.byType(def.datasourceType)
})

onMounted(async () => {
  const { data } = await getAllSyntax().catch(() => ({ data: null }))
  if (data) setSyntaxMap(data)
  registerCompletionProviders()

  // Set initial dialect
  if (activeTab.value?.taskType) {
    setCurrentDialect(activeTab.value.taskType)
  }

  ensureEditor()
})

onUnmounted(() => { editor?.dispose() })


// Create or re-create the Monaco editor when the container is available
function ensureEditor() {
  const container = document.getElementById('monaco-editor-container')
  if (!container) return
  // Already mounted on this DOM element
  if (editor && container.contains(editor.getDomNode()!)) return
  // Dispose stale instance (container was recreated by v-if/v-else)
  editor?.dispose()

  const initialLang = activeTab.value ? getMonacoLanguage(activeTab.value.taskType) : 'sql'
  editor = monacoEditor.create(container, {
    value: activeTab.value?.sql ?? '',
    language: initialLang,
    theme: monacoTheme,
    minimap: { enabled: false },
    fontSize: 13,
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    automaticLayout: true,
    tabSize: 2,
    wordWrap: 'on',
    renderLineHighlight: 'line',
    lineDecorationsWidth: 8,
    padding: { top: 8 },
    suggestOnTriggerCharacters: true,
    quickSuggestions: true,
    suggest: {
      showKeywords: true,
      showSnippets: true,
      showFunctions: true,
    },
  })

  editor.onDidChangeModelContent(() => {
    if (activeTab.value) {
      activeTab.value.sql = editor!.getValue()
      if (!suppressModified) activeTab.value.modified = true
    }
  })

  editor.onDidChangeCursorSelection(() => {
    const text = getSelectedSql()
    ideState.aiSelectionText = text ?? ''
  })

  editor.addCommand(KeyMod.CtrlCmd | KeyCode.KeyS, () => { handleSave() })
  editor.addCommand(KeyMod.CtrlCmd | KeyCode.Enter, () => { handleRun() })
}

// 从表单任务（JAR / HTTP）切回文本任务时，monaco 容器被重新挂载，需要重建
watch(isFormTask, (form) => {
  if (!form) nextTick(() => ensureEditor())
})

// 回滚等操作修改 tab.sql 后，通过 editorRefreshKey 触发编辑器同步
watch(() => ideState.editorRefreshKey, () => {
  if (!editor || !activeTab.value) return
  suppressModified = true
  editor.setValue(activeTab.value.sql)
  suppressModified = false
})

watch(() => ideState.activeTabId, () => {
  paramsOpen.value = false
  if (!activeTab.value) {
    if (editor) { suppressModified = true; editor.setValue(''); suppressModified = false }
    return
  }
  // Scroll tab bar to make active tab visible
  nextTick(() => {
    if (activeTabEl) activeTabEl.scrollIntoView({ inline: 'nearest', block: 'nearest' })
  })
  // Editor may not exist yet (async race or returning from JAR tab)
  nextTick(() => {
    ensureEditor()
    if (!editor) return

    const v = editor.getValue()
    if (v !== activeTab.value!.sql) {
      suppressModified = true
      editor.setValue(activeTab.value!.sql)
      suppressModified = false
    }

    // Switch language based on task type
    const lang = getMonacoLanguage(activeTab.value!.taskType)
    const model = editor.getModel()
    if (model && model.getLanguageId() !== lang) {
      monacoEditor.setModelLanguage(model, lang)
    }
    setCurrentDialect(activeTab.value!.taskType)
  })
})

function closeTab(tabId: string) {
  closeTabContextMenu()
  const idx = ideState.tabs.findIndex((t: Tab) => t.id === tabId)
  if (idx === -1) return
  ideState.tabs.splice(idx, 1)
  if (ideState.activeTabId === tabId) {
    ideState.activeTabId = ideState.tabs.length ? ideState.tabs[Math.min(idx, ideState.tabs.length - 1)].id : null
  }
}

function onExecutionModeChange() {
  if (!activeTab.value) return
  activeTab.value.modified = true
  // 表单任务（JAR / HTTP）：同步 executionMode 到 JSON content
  if (isFormTask.value && activeTab.value.sql) {
    try {
      const obj = JSON.parse(activeTab.value.sql)
      obj.executionMode = activeTab.value.executionMode || 'BATCH'
      activeTab.value.sql = JSON.stringify(obj, null, 2)
    } catch { /* ignore */ }
  }
}

function onParamsChange(params: Record<string, string>) {
  if (activeTab.value) {
    activeTab.value.params = params
  }
}

function onJarParamsChange(json: string) {
  if (activeTab.value) {
    // 注入 executionMode 到 JAR 参数 JSON
    try {
      const obj = JSON.parse(json)
      obj.executionMode = activeTab.value.executionMode || 'BATCH'
      activeTab.value.sql = JSON.stringify(obj, null, 2)
    } catch {
      activeTab.value.sql = json
    }
    activeTab.value.modified = true
  }
}

async function handleSave() {
  const tab = activeTab.value
  if (!tab) return
  // Sync latest editor content before saving
  if (editor && !isFormTask.value) tab.sql = editor.getValue()
  try {
    const hasParams = tab.params && Object.keys(tab.params).length > 0
    await updateScript(workspaceId.value!, tab.scriptCode, {
      content: wrapEditorContent(tab.sql, activeCategory.value, { dataSourceId: tab.datasourceId, executionMode: tab.executionMode || 'BATCH' }),
      params: hasParams ? JSON.stringify(tab.params) : null,
    })
    tab.modified = false
    ElMessage.success(t('ide.saved'))
  } catch { ElMessage.error(t('common.failed')) }
}

function getSelectedSql(): string | null {
  if (!editor) return null
  const selection = editor.getSelection()
  if (!selection || selection.isEmpty()) return null
  return editor.getModel()?.getValueInRange(selection) ?? null
}

async function handleRun() {
  const tab = activeTab.value
  if (!tab) return
  if (tab.executionRunning) { ElMessage.warning(t('ide.taskAlreadyRunning')); return }
  if (needsDatasource.value && !tab.datasourceId) { ElMessage.warning(t('ide.selectDatasourceFirst')); return }
  // Sync latest editor content
  if (editor && !isFormTask.value) tab.sql = editor.getValue()
  // Auto-save before execution
  if (tab.modified) {
    try {
      const hp = tab.params && Object.keys(tab.params).length > 0
      await updateScript(workspaceId.value!, tab.scriptCode, {
        content: wrapEditorContent(tab.sql, activeCategory.value, { dataSourceId: tab.datasourceId, executionMode: tab.executionMode || 'BATCH' }),
        params: hp ? JSON.stringify(tab.params) : null,
      })
      tab.modified = false
    } catch { ElMessage.error(t('common.failed')); return }
  }

  // Use selected text if any, otherwise full script
  const selectedSql = getSelectedSql()

  executing.value = true
  ideState.resultPanelVisible = true
  try {
    const hasParams = tab.params && Object.keys(tab.params).length > 0
    const { data } = await executeScript(workspaceId.value!, tab.scriptCode, {
      datasourceId: tab.datasourceId,
      sql: selectedSql || undefined,
      executionMode: tab.executionMode || 'BATCH',
      params: hasParams ? tab.params : undefined,
    })
    tab.executionId = data?.id ?? null
    tab.executionRunning = true
    if (data?.id) tab.lastExecutionId = data.id
    ElMessage.success(t(selectedSql ? 'ide.executionStartedSelection' : 'ide.executionStarted'))
  } catch { ElMessage.error(t('common.failed')) } finally { executing.value = false }
}

async function handleCancel() {
  const tab = activeTab.value
  if (!tab?.executionId) return
  try {
    await cancelExecution(tab.executionId)
    tab.executionRunning = false
  } catch { /* error shown by interceptor */ }
}

// ========== Commit Version ==========

const commitDialogVisible = ref(false)
const commitMessage = ref('')
const committing = ref(false)

function handleCommit() {
  const tab = activeTab.value
  if (!tab) return
  commitMessage.value = ''
  commitDialogVisible.value = true
}

async function doCommit() {
  const tab = activeTab.value
  if (!tab || !commitMessage.value.trim()) return
  // Auto-save before commit
  if (editor && !isFormTask.value) tab.sql = editor.getValue()
  if (tab.modified) {
    try {
      const hp = tab.params && Object.keys(tab.params).length > 0
      await updateScript(workspaceId.value!, tab.scriptCode, {
        content: wrapEditorContent(tab.sql, activeCategory.value, { dataSourceId: tab.datasourceId, executionMode: tab.executionMode || 'BATCH' }),
        params: hp ? JSON.stringify(tab.params) : null,
      })
      tab.modified = false
    } catch { ElMessage.error(t('common.failed')); return }
  }
  committing.value = true
  try {
    await commitScriptVersion(workspaceId.value!, tab.scriptCode, { message: commitMessage.value.trim() })
    commitDialogVisible.value = false
    ElMessage.success(t('ide.commitSuccess'))
    // 切换到版本历史 tab 并刷新
    ideState.resultPanelVisible = true
    if (activeTab.value) activeTab.value.resultTab = 'versions'
  } catch { ElMessage.error(t('common.failed')) }
  finally { committing.value = false }
}

// ========== Push to Workflow ==========

const pushDialogVisible = ref(false)
const pushing = ref(false)
const pushProjects = ref<any[]>([])
const pushWorkflows = ref<any[]>([])
const pushTasks = ref<any[]>([])
const pushForm = reactive({ projectCode: '' as string, workflowDefinitionCode: '' as string, mode: 'NEW' as string, taskDefinitionCode: '' as string })

const canPush = computed(() => {
  if (!pushForm.projectCode || !pushForm.workflowDefinitionCode) return false
  if (pushForm.mode === 'REPLACE' && !pushForm.taskDefinitionCode) return false
  return true
})

watch(pushDialogVisible, async (visible) => {
  if (!visible) return
  pushForm.projectCode = ''
  pushForm.workflowDefinitionCode = ''
  pushForm.mode = 'NEW'
  pushForm.taskDefinitionCode = ''
  pushWorkflows.value = []
  pushTasks.value = []
  try {
    const res: any = await listProjects(workspaceId.value!, { pageNum: 1, pageSize: 200 })
    pushProjects.value = res.data ?? []
  } catch { pushProjects.value = [] }
})

async function onPushProjectChange() {
  pushForm.workflowDefinitionCode = ''
  pushForm.taskDefinitionCode = ''
  pushTasks.value = []
  if (!pushForm.projectCode) { pushWorkflows.value = []; return }
  try {
    const res: any = await listWorkflowDefinitions(workspaceId.value!, pushForm.projectCode, { pageNum: 1, pageSize: 200 })
    pushWorkflows.value = res.data ?? []
  } catch { pushWorkflows.value = [] }
}

async function onPushWorkflowChange() {
  pushForm.taskDefinitionCode = ''
  if (!pushForm.workflowDefinitionCode) { pushTasks.value = []; return }
  try {
    const res: any = await listTaskDefinitions(workspaceId.value!, pushForm.projectCode, pushForm.workflowDefinitionCode)
    pushTasks.value = res.data ?? []
  } catch { pushTasks.value = [] }
}

async function handlePush() {
  const tab = activeTab.value
  if (!tab) return
  // Auto-save before push
  if (editor && !isFormTask.value) tab.sql = editor.getValue()
  try {
    const hp = tab.params && Object.keys(tab.params).length > 0
    await updateScript(workspaceId.value!, tab.scriptCode, {
      content: wrapEditorContent(tab.sql, activeCategory.value, { dataSourceId: tab.datasourceId, executionMode: tab.executionMode || 'BATCH' }),
      params: hp ? JSON.stringify(tab.params) : null,
    })
    tab.modified = false
  } catch { ElMessage.error(t('common.failed')); return }

  pushing.value = true
  try {
    const payload: any = { workflowDefinitionCode: pushForm.workflowDefinitionCode, mode: pushForm.mode }
    if (pushForm.mode === 'REPLACE') payload.taskDefinitionCode = pushForm.taskDefinitionCode
    const res: any = await pushToWorkflow(workspaceId.value!, pushForm.projectCode, tab.scriptCode, payload)
    tab.binding = res.data ?? null
    pushDialogVisible.value = false
    ElMessage.success(t('ide.pushSuccess'))
  } catch { /* error shown by interceptor */ }
  finally { pushing.value = false }
}

async function fetchBinding(tab: Tab) {
  if (!workspaceId.value) return
  try {
    const res: any = await getScriptBinding(workspaceId.value, tab.scriptCode)
    tab.binding = res.data ?? null
  } catch { tab.binding = null }
}

// Fetch binding when tab becomes active
watch(() => ideState.activeTabId, () => {
  const tab = activeTab.value
  if (tab && tab.binding === undefined) fetchBinding(tab)
}, { immediate: true })

const aiStore = useAiChatStore()

// IDE 按钮发到当前会话(没活动会话时新建 CHAT),走标准 turn 接口,刷新可恢复、agent toggle 自动锁定。
async function triggerAiButton(prompt: string) {
  ideState.aiPanelVisible = true
  await aiStore.sendTurn(prompt, {
    ...buildTurnContext(activeTab.value, ideState),
    forceModeIfNew: 'CHAT',
  })
}

async function handleExplain() {
  const tab = activeTab.value
  if (!tab?.sql) return
  await triggerAiButton(
    t('ide.explainRequest', { taskType: activeTaskTypeDef.value?.label || tab.taskType }),
  )
}

async function handleOptimize() {
  const tab = activeTab.value
  if (!tab?.sql) return
  await triggerAiButton(
    t('ide.optimizeRequest', { taskType: activeTaskTypeDef.value?.label || tab.taskType }),
  )
}
</script>

<style scoped lang="scss">
@use '@/styles/ide.scss' as *;

.editor-tabs {
  display: flex; flex-direction: column; height: 100%; overflow: hidden;
}

.tab-bar {
  display: flex; align-items: stretch;
  background: $ide-panel-bg;
  height: 36px; flex-shrink: 0; overflow-x: auto;
  border-bottom: 1px solid $ide-border;
  position: relative;
  &::-webkit-scrollbar { height: 0; }
  // fades at the scroll edges
  &::after {
    content: ''; position: absolute; right: 0; top: 0; bottom: 1px;
    width: 24px; pointer-events: none;
    background: linear-gradient(to right, transparent, $ide-panel-bg);
  }
}

.tab-bar__empty {
  display: flex; align-items: center; gap: 8px;
  padding: 0 16px; color: $ide-text-disabled;
}
.tab-bar__empty-mark {
  font-size: var(--r-font-sm);
  color: $ide-text-disabled;
}
.tab-bar__empty-text {
  font-size: var(--r-font-sm);
  color: $ide-text-muted;
}

.tab-item {
  display: flex; align-items: center; gap: 7px;
  height: 100%; padding: 0 14px 0 14px;
  font-size: var(--r-font-base);
  font-weight: var(--r-weight-medium);
  color: $ide-text-muted;
  cursor: pointer;
  white-space: nowrap; flex-shrink: 0;
  position: relative;
  transition: color 160ms ease, background 160ms ease;

  // left hairline separator, skipped for the active tab
  &::before {
    content: ''; position: absolute; left: 0; top: 8px; bottom: 8px;
    width: 1px; background: $ide-border-light;
  }
  &:first-child::before { display: none; }

  // top active hairline + warm wash beneath — replaces the old bottom gradient
  &::after {
    content: ''; position: absolute; left: 0; right: 0; top: 0;
    height: 1px;
    background: $ide-spark;
    box-shadow: 0 4px 14px -2px $ide-spark-glow;
    opacity: 0;
    transform: scaleX(0.6);
    transform-origin: center;
    transition: opacity 220ms ease, transform 240ms cubic-bezier(0.2, 0.9, 0.3, 1);
  }

  &:hover { color: $ide-text-secondary; background: $ide-hover-bg; }

  &--active {
    color: $ide-text;
    background: $ide-bg;
    &::after { opacity: 1; transform: scaleX(1); }
    // hide separator on active tab and the one immediately after
    &::before, & + .tab-item::before { background: transparent; }
  }
}
.tab-item__name {
  letter-spacing: 0.005em;
  font-feature-settings: 'cv11', 'ss01';
}
.tab-item__dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, #f4be7a, $ide-spark 70%);
  flex-shrink: 0;
  box-shadow: 0 0 0 1px rgba(232, 167, 91, 0.55), 0 0 0 4px $ide-spark-soft;
}
.tab-item__close {
  font-size: 12px; opacity: 0;
  cursor: pointer; border-radius: 4px; padding: 2px;
  transition: opacity 150ms ease, background 150ms ease, color 150ms ease;
  .tab-item:hover & { opacity: 0.55; }
  &:hover { opacity: 1 !important; background: $ide-border; color: $ide-text; }
}

.editor-toolbar {
  display: flex; align-items: center; gap: 6px;
  padding: 7px 14px;
  background: $ide-bg;
  border-bottom: 1px solid $ide-border;
  flex-shrink: 0;
  font-size: 13px;
}
.editor-toolbar__spacer { flex: 1; }
.editor-toolbar__sep {
  width: 1px; height: 18px;
  background: $ide-border;
  margin: 0 6px;
}

.editor-container { flex: 1; min-height: 0; }

.tab-context-menu { @extend %context-menu; }
.tab-context-menu__item {
  padding: 7px 14px; font-size: 12px; cursor: pointer; color: $ide-text-secondary;
  transition: background 100ms ease, color 100ms ease;
  &:hover { background: $ide-hover-bg; color: $ide-text; }
}
.tab-context-menu__divider { @extend %context-menu-divider; }
</style>
