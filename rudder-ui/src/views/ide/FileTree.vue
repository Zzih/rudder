<template>
  <div
    class="file-tree"
    :class="{ 'is-drop-forbidden': dropForbidden }"
    tabindex="0"
    @keydown="handleKeyDown"
  >
    <div class="file-tree__header">
      <span class="file-tree__title">
        <el-icon :size="13" class="file-tree__title-icon"><FolderOpened /></el-icon>
        <span>{{ t('ide.explorer') }}</span>
      </span>
      <el-button text size="small" @click="openNewDirDialog(null)"><el-icon><FolderAdd /></el-icon></el-button>
      <el-button text size="small" @click="openNewScriptDialog(null)"><el-icon><DocumentAdd /></el-icon></el-button>
    </div>

    <el-scrollbar class="file-tree__body" @contextmenu.prevent="handleBlankContextMenu">
      <el-tree
        ref="treeRef"
        :data="treeData"
        :props="treeProps"
        node-key="key"
        highlight-current
        :expand-on-click-node="false"
        :indent="16"
        draggable
        :allow-drag="allowDrag"
        :allow-drop="allowDrop"
        @node-drag-start="handleNodeDragStart"
        @node-drag-over="handleNodeDragOver"
        @node-drag-leave="handleNodeDragLeave"
        @node-drag-end="handleNodeDragEnd"
        @node-drop="handleNodeDrop"
        @node-contextmenu="(e: any, data: any) => handleContextMenu(e, data)"
      >
        <template #default="{ data, node }">
          <span
            class="tree-node"
            :class="{ 'is-cut': clipboardKey === data.key }"
            @dblclick.stop="handleNodeDblClick(data, node)"
          >
            <el-icon v-if="data.isDir" class="tree-node__icon" style="color: var(--r-warning)"><Folder /></el-icon>
            <TaskIcon v-else-if="data.dsType" :type="data.dsType" :size="16" />
            <span class="tree-node__label">{{ data.label }}</span>
          </span>
        </template>
      </el-tree>

      <div v-if="!loading && treeData.length === 0" class="file-tree__empty">
        {{ t('common.noData') }}
      </div>
    </el-scrollbar>

    <!-- Context menu — Teleport 到 body 避开任何祖先 transform/filter/animation 造成的 containing block 偏移 -->
    <Teleport to="body">
      <div
        v-if="contextMenu.visible" class="context-menu"
        :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }"
      >
        <template v-if="!contextMenu.node || contextMenu.node.isDir">
          <div class="context-menu__item" @click="openNewDirDialog(contextMenu.node)"><el-icon><FolderAdd /></el-icon> {{ t('ide.newFolder') }}</div>
          <div class="context-menu__item" @click="openNewScriptDialog(contextMenu.node)"><el-icon><DocumentAdd /></el-icon> {{ t('ide.newScript') }}</div>
        </template>
        <template v-if="contextMenu.node">
          <div v-if="contextMenu.node.isDir" class="context-menu__divider" />
          <div class="context-menu__item" @click="handleRename(contextMenu.node)"><el-icon><Edit /></el-icon> {{ t('ide.rename') }}</div>
          <div class="context-menu__item" @click="openMoveDialog(contextMenu.node)"><el-icon><Rank /></el-icon> {{ t('ide.move') }}</div>
          <div class="context-menu__item context-menu__item--danger" @click="handleDelete(contextMenu.node)"><el-icon><Delete /></el-icon> {{ t('common.delete') }}</div>
        </template>
      </div>
    </Teleport>

    <!-- Move Script Dialog -->
    <el-dialog v-model="moveDialogVisible" :title="t('ide.moveTitle')" width="420px" destroy-on-close append-to-body>
      <el-form label-position="top" @submit.prevent>
        <el-form-item :label="t('ide.moveTarget')" required>
          <el-tree-select
            v-model="moveTargetKey"
            :data="moveTreeData"
            :props="{ label: 'label', children: 'children' }"
            node-key="key"
            :placeholder="t('ide.moveTarget')"
            style="width: 100%"
            check-strictly
            default-expand-all
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moveDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!moveTargetKey || moving" :loading="moving" @click="submitMove">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- New Folder Dialog -->
    <el-dialog v-model="dirDialogVisible" :title="t('ide.newFolder')" width="400px" destroy-on-close append-to-body>
      <el-form label-position="top" @submit.prevent>
        <el-form-item :label="t('ide.folderName')" required>
          <el-input v-model="newDirName" :placeholder="t('ide.folderNamePlaceholder')" @keyup.enter="submitNewDir" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dirDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!newDirName.trim()" @click="submitNewDir">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>

    <!-- New Script Dialog -->
    <el-dialog v-model="scriptDialogVisible" :title="t('ide.newScript')" width="460px" destroy-on-close append-to-body>
      <el-form label-position="top" @submit.prevent>
        <el-form-item :label="t('ide.taskType')" required>
          <el-select :model-value="newScript.taskType" :placeholder="t('ide.taskTypePlaceholder')" style="width: 100%" @change="onTaskTypeChange">
            <el-option v-for="tp in taskTypes.filter(t => t.category !== 'CONTROL')" :key="tp.value" :label="tp.label" :value="tp.value">
              <span>{{ tp.label }}</span>
              <span style="float: right; color: var(--r-text-disabled); font-size: 12px">{{ tp.ext }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item :label="t('ide.scriptName')" required>
          <el-input v-model="newScript.name" :placeholder="t('ide.scriptNamePlaceholder')">
            <template v-if="selectedTaskType" #append>{{ selectedTaskType.ext }}</template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="needsDatasource" :label="t('ide.datasource')">
          <el-select v-model="newScript.datasourceId" :placeholder="t('ide.datasourcePlaceholder')" style="width: 100%" clearable>
            <el-option v-for="ds in filteredNewDatasources" :key="ds.id" :label="ds.name" :value="ds.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scriptDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!newScript.name.trim() || !newScript.taskType" @click="submitNewScript">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, shallowRef, h, inject, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { IDE_STATE_KEY } from './ideState'
import { useI18n } from 'vue-i18n'
import { Folder, FolderAdd, FolderOpened, DocumentAdd, Edit, Delete, Rank } from '@element-plus/icons-vue'
import TaskIcon from '@/components/TaskIcon.vue'
import { ElMessage, ElMessageBox, ElNotification, ElButton } from 'element-plus'
import { listScriptDirs, listScripts, createScriptDir, createScript, deleteScript, deleteScriptDir, renameScript, renameScriptDir, moveScript, moveScriptDir, getScript, listExecutionsByScript } from '@/api/script'
import { extractEditorContent, extractContentField } from '@/utils/scriptContent'
import { useWorkspaceStore } from '@/stores/workspace'
import { useDatasourceStore } from '@/stores/datasource'
import { useTaskTypesStore } from '@/stores/taskTypes'

interface TreeNode {
  key: string
  /** Display label — for scripts this is raw name + taskType ext. */
  label: string
  /** Raw name as stored in DB (no ext for scripts, unchanged for dirs). */
  name: string
  isDir: boolean
  id: number
  code: string
  isLeaf?: boolean
  dsType?: string
  children?: TreeNode[]
}

const { t } = useI18n()
const ideState = inject(IDE_STATE_KEY)!
const workspaceStore = useWorkspaceStore()
const datasourceStore = useDatasourceStore()
const treeRef = ref()
const treeData = ref<TreeNode[]>([])
const treeProps = { label: 'label', children: 'children', isLeaf: 'isLeaf' }
const contextMenu = reactive({ visible: false, x: 0, y: 0, node: null as TreeNode | null })
const loading = ref(false)

const taskTypesStore = useTaskTypesStore()
const taskTypes = computed(() => taskTypesStore.list)

const workspaceId = computed(() => workspaceStore.currentWorkspace?.id)

// Reload tree when workspace changes
watch(workspaceId, () => { loadTree() })

// Refresh tree when agent creates files/folders
watch(() => ideState.fileTreeRefreshKey, () => { loadTree() })

// Sync tree highlight with active tab
watch(() => ideState.activeTabId, () => {
  const tab = ideState.tabs.find((t: any) => t.id === ideState.activeTabId)
  if (tab) revealTreeNode(tab.scriptCode)
})

function revealTreeNode(scriptCode: string) {
  const tree = treeRef.value
  if (!tree) return
  const nodeKey = `script-${scriptCode}`
  // Expand ancestor dirs so the node is visible
  const node = tree.getNode(nodeKey)
  if (!node) return
  let parent = node.parent
  while (parent && parent.data) {
    parent.expanded = true
    parent = parent.parent
  }
  tree.setCurrentKey(nodeKey)
  // Scroll the highlighted node into view
  nextTick(() => {
    const el = tree.$el?.querySelector('.is-current')
    if (el) el.scrollIntoView({ block: 'nearest' })
  })
}

// ========== Load full tree ==========

async function loadTree() {
  const pid = workspaceId.value
  if (!pid) return
  loading.value = true
  try {
    const [dirsRes, scriptsRes] = await Promise.all([
      listScriptDirs(pid),
      listScripts(pid),
    ])
    const allDirs = dirsRes.data ?? []
    const allScripts = scriptsRes.data ?? []
    treeData.value = buildTree(allDirs, allScripts)
    // Highlight active tab after tree loads (e.g. page refresh with restored tabs)
    const tab = ideState.tabs.find((t: any) => t.id === ideState.activeTabId)
    if (tab) nextTick(() => revealTreeNode(tab.scriptCode))
  } catch {
    treeData.value = []
  } finally {
    loading.value = false
  }
}

function buildTree(dirs: any[], scripts: any[]): TreeNode[] {
  const extMap = new Map(taskTypes.value.map(t => [t.value, t.ext || '']))
  const dirMap = new Map<number, TreeNode>()
  for (const d of dirs) {
    dirMap.set(d.id, {
      key: `dir-${d.id}`,
      label: d.name,
      name: d.name,
      isDir: true,
      id: d.id,
      code: String(d.id),
      children: [],
    })
  }

  // Nest dirs
  const rootDirs: TreeNode[] = []
  for (const d of dirs) {
    const node = dirMap.get(d.id)!
    if (d.parentId && dirMap.has(d.parentId)) {
      dirMap.get(d.parentId)!.children!.push(node)
    } else {
      rootDirs.push(node)
    }
  }

  // Attach scripts to their dirs
  const rootScripts: TreeNode[] = []
  for (const s of scripts) {
    const scriptNode: TreeNode = {
      key: `script-${s.code ?? s.id}`,
      label: s.name + (extMap.get(s.taskType) ?? ''),
      name: s.name,
      isDir: false,
      id: s.id,
      code: String(s.code ?? s.id),
      isLeaf: true,
      dsType: s.taskType,
    }
    if (s.dirId && dirMap.has(s.dirId)) {
      dirMap.get(s.dirId)!.children!.push(scriptNode)
    } else {
      rootScripts.push(scriptNode)
    }
  }

  return [...rootDirs, ...rootScripts]
}

// ========== Init ==========

onMounted(loadTree)

// ========== Node dblclick ==========

function handleNodeDblClick(data: TreeNode, node: any) {
  if (data.isDir) {
    node.expanded = !node.expanded
  } else {
    openScript(data)
  }
}

const MAX_OPEN_TABS = 25
const openingScripts = new Set<string>()

async function openScript(data: TreeNode) {
  const existing = ideState.tabs.find((t: any) => t.scriptCode === data.code)
  if (existing) { ideState.activeTabId = existing.id; return }
  if (openingScripts.has(data.code)) return
  if (ideState.tabs.length >= MAX_OPEN_TABS) {
    ElMessage.warning(t('ide.maxTabsReached', { max: MAX_OPEN_TABS }))
    return
  }
  openingScripts.add(data.code)
  try {
    const res = await getScript(workspaceId.value!, data.code)
    const script = res.data
    // 可能在等待期间已被其他入口打开，做一次二次校验
    const raced = ideState.tabs.find((t: any) => t.scriptCode === data.code)
    if (raced) { ideState.activeTabId = raced.id; return }
    // 查询最近一次执行,恢复运行状态;如果在跑,直接把 executionId 写到新 tab,
    // ResultPanel 会在 tab 激活时 watch 触发 polling
    let lastExecId: number | null = null
    let runningExecId: number | null = null
    try {
      const execRes = await listExecutionsByScript(data.code)
      const executions = execRes.data || []
      if (executions.length > 0) {
        const latest = executions[0]
        lastExecId = latest.id
        if (latest.status === 'RUNNING' || latest.status === 'PENDING') {
          runningExecId = latest.id
          ideState.resultPanelVisible = true
        }
      }
    } catch { /* ignore */ }

    ideState.tabs.push({
      id: `tab-${data.code}-${crypto.randomUUID()}`,
      name: data.label,
      scriptCode: data.code,
      sql: extractEditorContent(script.content, taskTypesStore.categoryOf(script.taskType ?? '')),
      taskType: script.taskType ?? '',
      datasourceId: extractContentField(script.content, 'dataSourceId', null),
      executionMode: extractContentField(script.content, 'executionMode', 'BATCH'),
      modified: false,
      lastExecutionId: lastExecId,
      params: script.params ? JSON.parse(script.params) : {},
      executionId: runningExecId,
      executionRunning: runningExecId !== null,
      resultLog: null,
      resultTab: null,
    })
    ideState.activeTabId = ideState.tabs[ideState.tabs.length - 1].id
  } catch { ElMessage.error(t('common.failed')) }
  finally { openingScripts.delete(data.code) }
}

// ========== Context menu ==========

function handleContextMenu(event: MouseEvent, data: TreeNode) {
  event.preventDefault()
  event.stopPropagation()
  contextMenu.visible = true
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.node = data
}
function handleBlankContextMenu(event: MouseEvent) {
  contextMenu.visible = true
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.node = null // null means blank area — only show new folder/script
}
function closeContextMenu() { contextMenu.visible = false; contextMenu.node = null }
// capture 阶段监听,避免被下游 stopPropagation 拦截;点到菜单内部时交给菜单自己的 @click 处理
function onDocumentMousedown(ev: MouseEvent) {
  if (!contextMenu.visible) return
  if ((ev.target as HTMLElement | null)?.closest('.context-menu')) return
  closeContextMenu()
}
onMounted(() => { document.addEventListener('mousedown', onDocumentMousedown, true) })
onUnmounted(() => {
  document.removeEventListener('mousedown', onDocumentMousedown, true)
  clearHoverExpand()
  stopAutoScroll()
  if (flashTimer !== null) { clearTimeout(flashTimer); flashTimer = null }
})

// ========== New Folder ==========

const dirDialogVisible = ref(false)
const newDirName = ref('')
let newDirParent: TreeNode | null = null

function openNewDirDialog(parent: TreeNode | null) {
  closeContextMenu()
  newDirParent = parent?.isDir ? parent : null
  newDirName.value = ''
  dirDialogVisible.value = true
}

async function submitNewDir() {
  if (!newDirName.value.trim() || !workspaceId.value) return
  try {
    const res = await createScriptDir(workspaceId.value, {
      name: newDirName.value.trim(),
      parentId: newDirParent?.id,
    })
    dirDialogVisible.value = false
    const dirName = res.data.name ?? newDirName.value.trim()
    const newNode: TreeNode = {
      key: `dir-${res.data.id}`,
      label: dirName,
      name: dirName,
      isDir: true,
      id: res.data.id,
      code: String(res.data.id),
      children: [],
    }
    insertNode(newDirParent, newNode)
    ElMessage.success(t('ide.folderCreated'))
  } catch { ElMessage.error(t('common.failed')) }
}

// ========== New Script ==========

const scriptDialogVisible = ref(false)
const newScript = reactive({ name: '', taskType: '', datasourceId: null as number | null })
let newScriptParent: TreeNode | null = null

const selectedTaskType = computed(() => taskTypes.value.find(t => t.value === newScript.taskType))
const needsDatasource = computed(() => selectedTaskType.value?.needsDatasource ?? false)
const filteredNewDatasources = computed(() => {
  const def = selectedTaskType.value
  if (!def?.datasourceType) return datasourceStore.datasources
  return datasourceStore.datasources.filter(
    ds => ds.datasourceType.toUpperCase() === def.datasourceType!.toUpperCase()
  )
})

function extFor(taskType: string | null | undefined): string {
  if (!taskType) return ''
  return taskTypes.value.find(t => t.value === taskType)?.ext || ''
}

function onTaskTypeChange(val: string) {
  newScript.taskType = val
  newScript.datasourceId = null
}

function openNewScriptDialog(parent: TreeNode | null) {
  closeContextMenu()
  newScriptParent = parent?.isDir ? parent : null
  newScript.name = ''
  newScript.taskType = ''
  newScript.datasourceId = null
  scriptDialogVisible.value = true
}

async function submitNewScript() {
  const name = newScript.name.trim()
  if (!name || !newScript.taskType || !workspaceId.value) {
    if (!name) ElMessage.warning(t('workspace.nameRequired'))
    return
  }
  const def = taskTypes.value.find(t => t.value === newScript.taskType)
  try {
    const res = await createScript(workspaceId.value, {
      name,
      dirId: newScriptParent?.id,
      taskType: newScript.taskType,
      datasourceId: def?.needsDatasource ? newScript.datasourceId : null,
      content: '',
    })
    scriptDialogVisible.value = false
    const savedName = res.data.name ?? name
    const newNode: TreeNode = {
      key: `script-${res.data.code ?? res.data.id}`,
      label: savedName + extFor(newScript.taskType),
      name: savedName,
      isDir: false,
      id: res.data.id,
      code: String(res.data.code ?? res.data.id),
      isLeaf: true,
      dsType: newScript.taskType,
    }
    insertNode(newScriptParent, newNode)
    ElMessage.success(t('ide.scriptCreated'))
    // Auto-open the new script
    openScript(newNode)
  } catch { ElMessage.error(t('common.failed')) }
}

// ========== Rename / Delete ==========

async function handleRename(node: TreeNode | null) {
  closeContextMenu()
  if (!node) return
  let value: string
  try {
    const r = await ElMessageBox.prompt(t('ide.newName'), t('ide.rename'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      inputValue: node.name,
    })
    value = r.value
  } catch { return /* user cancelled */ }
  const newName = value?.trim()
  if (!newName) return
  try {
    if (node.isDir) {
      await renameScriptDir(workspaceId.value!, node.id, newName)
      node.name = newName
      node.label = newName
    } else {
      await renameScript(workspaceId.value!, node.code, newName)
      node.name = newName
      node.label = newName + extFor(node.dsType)
      const tab = ideState.tabs.find((t: any) => t.scriptCode === node.code)
      if (tab) tab.name = node.label
    }
    ElMessage.success(t('ide.renamed'))
  } catch { ElMessage.error(t('common.failed')) }
}

async function handleDelete(node: TreeNode | null) {
  closeContextMenu()
  if (!node) return
  try {
    await ElMessageBox.confirm(
      t('ide.deleteConfirm', { name: node.label }),
      t('common.confirm'),
      { confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel'), type: 'warning' },
    )
  } catch { return /* user cancelled */ }
  try {
    if (node.isDir) {
      await deleteScriptDir(workspaceId.value!, node.id)
      closeTabsInDir(node)
    } else {
      await deleteScript(workspaceId.value!, node.code)
      closeTabByScriptCode(node.code)
    }
    removeNode(node.key)
    ElMessage.success(t('ide.deleted'))
  } catch { ElMessage.error(t('common.failed')) }
}

// ========== Tab helpers ==========

function closeTabByScriptCode(scriptCode: string) {
  const idx = ideState.tabs.findIndex((t: any) => t.scriptCode === scriptCode)
  if (idx === -1) return
  const tabId = ideState.tabs[idx].id
  ideState.tabs.splice(idx, 1)
  if (ideState.activeTabId === tabId) {
    ideState.activeTabId = ideState.tabs.length ? ideState.tabs[0].id : null
  }
}

function closeTabsInDir(dirNode: TreeNode) {
  // Collect all script codes under this dir recursively
  const scriptCodes: string[] = []
  function collect(node: TreeNode) {
    if (!node.isDir) { scriptCodes.push(node.code); return }
    if (node.children) node.children.forEach(collect)
  }
  collect(dirNode)
  for (const code of scriptCodes) closeTabByScriptCode(code)
}

// ========== Move ==========

const ROOT_KEY = '__root__'
const moveDialogVisible = ref(false)
const moveTargetKey = ref<string | null>(null)
const moving = ref(false)
const movingNodeRef = shallowRef<TreeNode | null>(null)

function buildDirOnlyTree(list: TreeNode[], excludeKey: string | null): any[] {
  const out: any[] = []
  for (const n of list) {
    if (!n.isDir) continue
    if (excludeKey && n.key === excludeKey) continue
    out.push({
      key: n.key,
      label: n.label,
      id: n.id,
      children: n.children ? buildDirOnlyTree(n.children, excludeKey) : [],
    })
  }
  return out
}

const moveTreeData = computed(() => {
  const movingNode = movingNodeRef.value
  return [
    {
      key: ROOT_KEY,
      label: t('ide.moveRoot'),
      id: null,
      children: buildDirOnlyTree(
        treeData.value,
        movingNode && movingNode.isDir ? movingNode.key : null,
      ),
    },
  ]
})

const treeIndex = computed(() => {
  const parentByKey = new Map<string, string>()
  const namesByDirKey = new Map<string, Map<string, string>>()  // dirKey -> name -> nodeKey
  function walk(list: TreeNode[], parentKey: string) {
    let nameMap = namesByDirKey.get(parentKey)
    if (!nameMap) { nameMap = new Map(); namesByDirKey.set(parentKey, nameMap) }
    for (const n of list) {
      parentByKey.set(n.key, parentKey)
      nameMap.set(n.name, n.key)
      if (n.children?.length) walk(n.children, n.key)
    }
  }
  walk(treeData.value, ROOT_KEY)
  return { parentByKey, namesByDirKey }
})

function hasSiblingConflict(parentDirId: number | null, name: string, excludeKey: string): boolean {
  const parentKey = parentDirId == null ? ROOT_KEY : `dir-${parentDirId}`
  const existingKey = treeIndex.value.namesByDirKey.get(parentKey)?.get(name)
  return existingKey !== undefined && existingKey !== excludeKey
}

function findParentDirKey(key: string): string {
  return treeIndex.value.parentByKey.get(key) ?? ROOT_KEY
}

function parentDirIdFromKey(key: string): number | null {
  return key === ROOT_KEY ? null : Number(key.replace(/^dir-/, ''))
}

function parentDirIdOfNode(key: string): number | null {
  return parentDirIdFromKey(findParentDirKey(key))
}

async function performMove(node: TreeNode, targetDirId: number | null) {
  if (node.isDir) {
    await moveScriptDir(workspaceId.value!, node.id, targetDirId)
  } else {
    await moveScript(workspaceId.value!, node.code, targetDirId)
  }
}

function relocateInTree(node: TreeNode, targetDirId: number | null) {
  treeData.value = removeFromList(treeData.value, node.key)
  if (targetDirId == null) {
    treeData.value.push(node)
  } else {
    const target = findNode(treeData.value, `dir-${targetDirId}`)
    if (target) target.children = target.children ? [...target.children, node] : [node]
  }
  treeData.value = [...treeData.value]
}

let flashTimer: number | null = null
async function flashNode(key: string) {
  await nextTick()
  const tree = treeRef.value
  if (!tree) return
  const node = tree.getNode(key)
  let p = node?.parent
  while (p && p.data && !Array.isArray(p.data)) { p.expanded = true; p = p.parent }
  tree.setCurrentKey(key)
  await nextTick()
  const el = tree.$el?.querySelector('.is-current') as HTMLElement | null
  if (!el) return
  el.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  el.classList.add('is-flash')
  if (flashTimer !== null) clearTimeout(flashTimer)
  flashTimer = window.setTimeout(() => {
    el.classList.remove('is-flash')
    flashTimer = null
  }, 1200)
}

/**
 * 统一移动入口:
 * - mode='dialog' | 'paste':API 成功后在本地树上显式 relocate
 * - mode='drag':el-tree 已就地移动,成功不动,失败本地回迁到原父
 */
async function commitMove(
  node: TreeNode,
  targetDirId: number | null,
  originalParentId: number | null,
  mode: 'dialog' | 'drag' | 'paste',
  successToast: 'notify' | 'undoDone' = 'notify',
): Promise<boolean> {
  if (targetDirId === originalParentId) return false
  if (hasSiblingConflict(targetDirId, node.name, node.key)) {
    ElMessage.warning(t('ide.moveNameConflict'))
    if (mode === 'drag') relocateInTree(node, originalParentId)
    return false
  }
  try {
    await performMove(node, targetDirId)
    if (mode !== 'drag') relocateInTree(node, targetDirId)
    if (successToast === 'undoDone') {
      ElMessage.success(t('ide.undoDone'))
      flashNode(node.key)
    } else {
      notifyMoved(node, originalParentId)
    }
    return true
  } catch {
    ElMessage.error(t('common.failed'))
    if (mode === 'drag') relocateInTree(node, originalParentId)
    return false
  }
}

function notifyMoved(node: TreeNode, originalParentDirId: number | null) {
  flashNode(node.key)
  const notif = ElNotification.success({
    title: t('ide.moved'),
    message: h('div', { style: 'display:flex;align-items:center;gap:12px;' }, [
      h('span', { style: 'font-size: 12px;' }, node.label),
      h(ElButton, {
        size: 'small', type: 'primary', text: true,
        onClick: () => {
          notif.close()
          commitMove(node, originalParentDirId, parentDirIdOfNode(node.key), 'dialog', 'undoDone')
        },
      }, () => t('ide.undo')),
    ]),
    duration: 5000,
  })
}

function openMoveDialog(node: TreeNode | null) {
  closeContextMenu()
  if (!node) return
  movingNodeRef.value = node
  moveTargetKey.value = findParentDirKey(node.key)
  moveDialogVisible.value = true
}

async function submitMove() {
  const movingNode = movingNodeRef.value
  if (!movingNode || !moveTargetKey.value || !workspaceId.value) return
  const originalParentId = parentDirIdOfNode(movingNode.key)
  const targetDirId = parentDirIdFromKey(moveTargetKey.value)
  moving.value = true
  try {
    const moved = await commitMove(movingNode, targetDirId, originalParentId, 'dialog')
    if (moved || targetDirId === originalParentId) moveDialogVisible.value = false
  } finally {
    moving.value = false
    movingNodeRef.value = null
  }
}

// ========== Drag & drop ==========

let dragOriginalParentId: number | null = null
const dropForbidden = ref(false)
let hoverExpandTimer: number | null = null
let hoverExpandNode: any = null
let autoScrollDir: -1 | 0 | 1 = 0
let autoScrollHandle: number | null = null

function allowDrag(_node: any) { return true }

function isAncestorOrSelf(maybeAncestor: any, node: any): boolean {
  let cur = node
  while (cur) {
    if (cur === maybeAncestor) return true
    cur = cur.parent
  }
  return false
}

function targetDirIdFor(dropNode: any, type: 'prev' | 'next' | 'inner'): number | null | 'invalid' {
  if (type === 'inner') {
    if (!dropNode.data?.isDir) return 'invalid'
    return Number(dropNode.data.id)
  }
  return resolveParentDirId(dropNode)
}

function allowDrop(draggingNode: any, dropNode: any, type: 'prev' | 'next' | 'inner') {
  if (isAncestorOrSelf(draggingNode, dropNode)) return false
  return targetDirIdFor(dropNode, type) !== 'invalid'
}

function resolveParentDirId(node: any): number | null {
  const data = node?.parent?.data
  if (!data || Array.isArray(data)) return null
  return data.isDir ? Number(data.id) : null
}

function clearHoverExpand() {
  if (hoverExpandTimer !== null) { clearTimeout(hoverExpandTimer); hoverExpandTimer = null }
  hoverExpandNode = null
}

function autoScrollLoop() {
  const scroller = treeRef.value?.$el?.closest('.el-scrollbar__wrap') as HTMLElement | null
  if (scroller && autoScrollDir !== 0) {
    scroller.scrollTop += autoScrollDir * 10
    autoScrollHandle = requestAnimationFrame(autoScrollLoop)
  } else {
    autoScrollHandle = null
  }
}

function updateAutoScroll(ev: DragEvent) {
  const scroller = treeRef.value?.$el?.closest('.el-scrollbar__wrap') as HTMLElement | null
  if (!scroller) { autoScrollDir = 0; return }
  const rect = scroller.getBoundingClientRect()
  const EDGE = 28
  const dir: -1 | 0 | 1 = ev.clientY < rect.top + EDGE ? -1
    : ev.clientY > rect.bottom - EDGE ? 1 : 0
  if (dir !== autoScrollDir) {
    autoScrollDir = dir
    if (dir !== 0 && autoScrollHandle === null) autoScrollHandle = requestAnimationFrame(autoScrollLoop)
  }
}

function stopAutoScroll() {
  autoScrollDir = 0
  if (autoScrollHandle !== null) { cancelAnimationFrame(autoScrollHandle); autoScrollHandle = null }
}

function handleNodeDragStart(node: any) {
  dragOriginalParentId = resolveParentDirId(node)
}

function handleNodeDragOver(draggingNode: any, dropNode: any, ev: DragEvent) {
  updateAutoScroll(ev)
  dropForbidden.value = !(['prev', 'inner', 'next'] as const).some(
    t => allowDrop(draggingNode, dropNode, t),
  )
  if (dropNode?.data?.isDir && !dropNode.expanded) {
    if (hoverExpandNode !== dropNode) {
      clearHoverExpand()
      hoverExpandNode = dropNode
      hoverExpandTimer = window.setTimeout(() => {
        if (hoverExpandNode) hoverExpandNode.expanded = true
        hoverExpandTimer = null
        hoverExpandNode = null
      }, 500)
    }
  } else {
    clearHoverExpand()
  }
}

function handleNodeDragLeave() {
  clearHoverExpand()
}

function handleNodeDragEnd() {
  dragOriginalParentId = null
  dropForbidden.value = false
  clearHoverExpand()
  stopAutoScroll()
}

async function handleNodeDrop(draggingNode: any, dropNode: any, dropType: 'before' | 'after' | 'inner') {
  clearHoverExpand()
  stopAutoScroll()
  dropForbidden.value = false
  const data = draggingNode.data as TreeNode | undefined
  if (!data || !workspaceId.value) return
  const newParentDirId = dropType === 'inner'
    ? (dropNode.data?.isDir ? Number(dropNode.data.id) : null)
    : resolveParentDirId(dropNode)
  const original = dragOriginalParentId
  dragOriginalParentId = null
  await commitMove(data, newParentDirId, original, 'drag')
}

// ========== Keyboard & clipboard ==========

const clipboardKey = ref<string | null>(null)

async function pasteFromClipboard(destination: TreeNode | null) {
  if (!clipboardKey.value || !workspaceId.value) {
    ElMessage.info(t('ide.pasteNothing'))
    return
  }
  const src = findNode(treeData.value, clipboardKey.value)
  if (!src) { clipboardKey.value = null; return }
  const targetDirId: number | null = destination
    ? (destination.isDir ? destination.id : parentDirIdOfNode(destination.key))
    : null
  const originalParentId = parentDirIdOfNode(src.key)
  const ok = await commitMove(src, targetDirId, originalParentId, 'paste')
  if (ok || targetDirId === originalParentId) clipboardKey.value = null
}

function handleKeyDown(ev: KeyboardEvent) {
  const tag = (ev.target as HTMLElement | null)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA') return
  const current = (treeRef.value?.getCurrentNode() as TreeNode | undefined) ?? null
  if (ev.key === 'F2') {
    if (!current) return
    ev.preventDefault()
    handleRename(current)
  } else if (ev.key === 'Delete' || (ev.key === 'Backspace' && (ev.metaKey || ev.ctrlKey))) {
    if (!current) return
    ev.preventDefault()
    handleDelete(current)
  } else if (ev.key === 'Enter') {
    if (!current) return
    ev.preventDefault()
    if (current.isDir) {
      const n = treeRef.value?.getNode(current.key)
      if (n) n.expanded = !n.expanded
    } else {
      openScript(current)
    }
  } else if ((ev.metaKey || ev.ctrlKey) && ev.key.toLowerCase() === 'x') {
    if (!current) return
    ev.preventDefault()
    clipboardKey.value = current.key
    ElMessage.info(t('ide.cut', { name: current.label }))
  } else if ((ev.metaKey || ev.ctrlKey) && ev.key.toLowerCase() === 'v') {
    ev.preventDefault()
    pasteFromClipboard(current)
  }
}

// ========== Local tree mutation helpers ==========

function insertNode(parent: TreeNode | null, node: TreeNode) {
  if (parent) {
    // Find parent in tree and push
    const target = findNode(treeData.value, parent.key)
    if (target && target.children) {
      target.children.push(node)
    } else if (target) {
      target.children = [node]
    } else {
      treeData.value.push(node)
    }
  } else {
    treeData.value.push(node)
  }
  // Trigger reactivity
  treeData.value = [...treeData.value]
}

function removeNode(key: string) {
  treeData.value = removeFromList(treeData.value, key)
}

function removeFromList(list: TreeNode[], key: string): TreeNode[] {
  return list
    .filter(n => n.key !== key)
    .map(n => n.children ? { ...n, children: removeFromList(n.children, key) } : n)
}

function findNode(list: TreeNode[], key: string): TreeNode | null {
  for (const n of list) {
    if (n.key === key) return n
    if (n.children) {
      const found = findNode(n.children, key)
      if (found) return found
    }
  }
  return null
}
</script>

<style scoped lang="scss">
@use '@/styles/ide.scss' as *;

.file-tree {
  height: 100%; display: flex; flex-direction: column; user-select: none;
  outline: none;
  &.is-drop-forbidden,
  &.is-drop-forbidden * { cursor: not-allowed !important; }
}

.file-tree__header {
  @extend %section-header;
  padding: 0 8px 0 12px;
  gap: 2px;
  :deep(.el-button) {
    height: 24px; width: 24px; padding: 0;
    border-radius: 5px;
    transition: background 140ms ease, color 140ms ease;
    &:hover { background: $ide-hover-bg; color: $ide-spark; }
  }
}
.file-tree__title {
  @extend %section-title;
  flex: 1;
  display: inline-flex; align-items: center; gap: 7px;
}
.file-tree__title-icon { color: $ide-text-muted; flex-shrink: 0; }

.file-tree__body { flex: 1; }

.file-tree__empty {
  text-align: center; padding: 36px 16px;
  font-size: var(--r-font-sm);
  color: $ide-text-disabled;
}

:deep(.el-tree) {
  background: transparent;
  color: $ide-text-secondary;
  font-size: 13px;
  --el-tree-node-hover-bg-color: #{$ide-hover-bg};
  --el-tree-node-content-height: 28px;
  .el-tree-node__content {
    border-radius: 5px;
    margin: 0 6px;
    transition: background 120ms ease;
  }
  .el-tree-node.is-current > .el-tree-node__content {
    background: $ide-active-bg;
    color: $ide-text;
    position: relative;
    &::before {
      content: ''; position: absolute; left: -6px; top: 4px; bottom: 4px;
      width: 2px; border-radius: 0 2px 2px 0;
      background: $ide-spark;
    }
  }
  .el-tree-node.is-flash > .el-tree-node__content { animation: tree-flash 1.2s ease-out; }
  // the expand caret: subtler
  .el-tree-node__expand-icon {
    color: $ide-text-disabled;
    transition: color 140ms ease, transform 180ms ease;
  }
  .el-tree-node.is-current > .el-tree-node__content .el-tree-node__expand-icon,
  .el-tree-node__content:hover .el-tree-node__expand-icon {
    color: $ide-text-secondary;
  }
}

@keyframes tree-flash {
  0%, 40% { background: $ide-spark-soft; box-shadow: inset 2px 0 0 $ide-spark; }
  100% { background: transparent; box-shadow: inset 2px 0 0 transparent; }
}

.tree-node {
  display: flex; align-items: center; gap: 7px;
  font-size: 13px; line-height: 28px;
  transition: opacity 150ms ease;
  &.is-cut {
    opacity: 0.45; font-style: italic;
    .tree-node__label { text-decoration: line-through; text-decoration-color: $ide-text-disabled; }
  }
}
.tree-node__icon { font-size: 14px; }
.tree-node__label { font-weight: 500; letter-spacing: 0.005em; }
.tree-node__tag {
  font-family: var(--r-font-mono);
  font-size: 11px; color: $ide-text-muted; background: $ide-hover-bg;
  padding: 1px 5px; border-radius: 3px; margin-left: auto;
  letter-spacing: 0.02em;
}

.context-menu { @extend %context-menu; }
.context-menu__item {
  @extend %context-menu-item;
  &--danger { color: var(--r-danger); }
  &--danger:hover { background: var(--r-danger-bg); color: var(--r-danger); }
}
.context-menu__divider { @extend %context-menu-divider; }
</style>
