<template>
  <div class="ide-layout">
    <!-- Left: File Tree + Metadata -->
    <aside class="ide-sidebar" :style="{ width: sidebarWidth + 'px' }">
      <div class="ide-sidebar__top">
        <FileTree v-if="workspaceReady" />
        <div v-else class="ide-sidebar__loading">
          <el-icon class="is-loading" :size="20"><Loading /></el-icon>
        </div>
      </div>
      <div class="ide-sidebar__bottom">
        <MetadataPanel v-if="workspaceReady" />
      </div>
      <div class="resize-handle resize-handle--right" @mousedown="startResizeSidebar" />
    </aside>

    <!-- Center -->
    <div class="ide-center">
      <div class="ide-editor-area" :style="{ flex: resultPanelVisible ? undefined : '1' }">
        <EditorTabs />
      </div>
      <template v-if="ideState.resultPanelVisible">
        <div class="resize-handle resize-handle--horizontal" @mousedown="startResizeResult" />
        <div class="ide-result-area" :style="{ height: resultHeight + 'px' }">
          <ResultPanel />
        </div>
      </template>
    </div>

    <!-- Right: AI Chat -->
    <div v-show="ideState.aiPanelVisible" class="ide-ai-panel" :style="{ width: aiPanelWidth + 'px' }">
      <div class="resize-handle resize-handle--ai-left" @mousedown="startResizeAi" />
      <AiChat />
    </div>

    <button v-show="!ideState.aiPanelVisible" class="ai-toggle-btn" @click="ideState.aiPanelVisible = true">
      <el-icon :size="16"><ChatDotRound /></el-icon>
    </button>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, provide, computed, watch, onMounted } from 'vue'
import { IDE_STATE_KEY, type IdeState } from './ideState'
import { ChatDotRound, Loading } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { useWorkspaceStore } from '@/stores/workspace'
import { useTaskTypesStore } from '@/stores/taskTypes'
import { getScript } from '@/api/script'
import { extractEditorContent, extractContentField } from '@/utils/scriptContent'
import FileTree from './FileTree.vue'
import MetadataPanel from './MetadataPanel.vue'
import EditorTabs from './EditorTabs.vue'
import ResultPanel from './ResultPanel.vue'
import AiChat from './AiChat.vue'


// Subset of Tab stored in localStorage (no sql content — reload from backend)
interface PersistedTab {
  id: string
  name: string
  scriptCode: string
  taskType: string
  datasourceId: number | null
  executionMode: string
  lastExecutionId: number | null
  params: Record<string, string>
}

const route = useRoute()
const currentWsId = computed(() => Number(route.params.workspaceId))
const tabsStorageKey = computed(() => `rudder_ide_tabs_${currentWsId.value}`)
const activeTabStorageKey = computed(() => `rudder_ide_active_tab_${currentWsId.value}`)

const ideState = reactive<IdeState>({
  tabs: [],
  activeTabId: null,
  aiPanelVisible: true,
  resultPanelVisible: true,
  fileTreeRefreshKey: 0,
  editorRefreshKey: 0,
  pinnedTables: [],
  aiSelectionText: '',
})

provide(IDE_STATE_KEY, ideState)

// Persist tabs to localStorage on change
watch(() => ideState.tabs, (tabs) => {
  const persisted: PersistedTab[] = tabs.map(t => ({
    id: t.id, name: t.name, scriptCode: t.scriptCode,
    taskType: t.taskType, datasourceId: t.datasourceId,
    executionMode: t.executionMode, lastExecutionId: t.lastExecutionId,
    params: t.params,
  }))
  localStorage.setItem(tabsStorageKey.value, JSON.stringify(persisted))
}, { deep: true })

watch(() => ideState.activeTabId, (id) => {
  if (id) localStorage.setItem(activeTabStorageKey.value, id)
  else localStorage.removeItem(activeTabStorageKey.value)
})

// Generation token: if workspace switches mid-restore, the stale restore must discard its result
// otherwise both the mounted restore and the watcher-triggered restore can push tabs (duplicates)
let restoreGen = 0

const taskTypesStore = useTaskTypesStore()

async function restoreTabs() {
  const myGen = ++restoreGen
  const myWsId = currentWsId.value
  try {
    const raw = localStorage.getItem(tabsStorageKey.value)
    if (!raw) return
    const persisted: PersistedTab[] = JSON.parse(raw).filter((p: PersistedTab) => p.scriptCode && p.scriptCode !== 'undefined')
    if (!persisted.length) return

    const results = await Promise.allSettled(
      persisted.map(p => getScript(myWsId, p.scriptCode))
    )

    // Abandoned by a newer restore or a workspace switch — don't mutate shared state
    if (myGen !== restoreGen || myWsId !== currentWsId.value) return

    for (let i = 0; i < persisted.length; i++) {
      const p = persisted[i]
      const result = results[i]
      if (result.status === 'rejected') continue // script deleted, skip
      const script = result.value.data
      if (!script) continue
      try {
        ideState.tabs.push({
          id: p.id,
          name: p.name,
          scriptCode: p.scriptCode,
          sql: extractEditorContent(script.content, taskTypesStore.categoryOf(p.taskType)),
          taskType: p.taskType,
          datasourceId: extractContentField(script.content, 'dataSourceId', p.datasourceId),
          executionMode: extractContentField(script.content, 'executionMode', p.executionMode || 'BATCH'),
          modified: false,
          lastExecutionId: p.lastExecutionId,
          params: script.params ? JSON.parse(script.params) : (p.params || {}),
          executionId: null,
          executionRunning: false,
          resultLog: null,
          resultTab: null,
        })
      } catch { /* skip one corrupt tab, don't abort the whole restore */ }
    }

    // Restore active tab
    const savedActiveId = localStorage.getItem(activeTabStorageKey.value)
    if (savedActiveId && ideState.tabs.some(t => t.id === savedActiveId)) {
      ideState.activeTabId = savedActiveId
    } else if (ideState.tabs.length) {
      ideState.activeTabId = ideState.tabs[0].id
    }
  } catch { /* ignore corrupt localStorage */ }
}

// Restore tabs from localStorage on mount
onMounted(restoreTabs)

const workspaceStore = useWorkspaceStore()
const workspaceReady = computed(() => !!workspaceStore.currentWorkspace)

watch(currentWsId, async (newId, oldId) => {
  if (!newId || newId === oldId) return
  ideState.tabs = []
  ideState.activeTabId = null
  ideState.resultPanelVisible = true
  await restoreTabs()
})

const resultPanelVisible = computed(() => ideState.resultPanelVisible)

const sidebarWidth = ref(240)
const aiPanelWidth = ref(340)
const resultHeight = ref(260)

function startResizeSidebar(e: MouseEvent) {
  const startX = e.clientX
  const startW = sidebarWidth.value
  const onMove = (ev: MouseEvent) => {
    sidebarWidth.value = Math.max(160, Math.min(480, startW + ev.clientX - startX))
  }
  const onUp = () => { document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp) }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function startResizeAi(e: MouseEvent) {
  const startX = e.clientX
  const startW = aiPanelWidth.value
  const onMove = (ev: MouseEvent) => {
    aiPanelWidth.value = Math.max(280, Math.min(700, startW + startX - ev.clientX))
  }
  const onUp = () => { document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp) }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function startResizeResult(e: MouseEvent) {
  const startY = e.clientY
  const startH = resultHeight.value
  const onMove = (ev: MouseEvent) => {
    resultHeight.value = Math.max(120, Math.min(600, startH + startY - ev.clientY))
  }
  const onUp = () => { document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp) }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}
</script>

<style scoped lang="scss">
@use '@/styles/ide.scss' as *;

.ide-layout {
  display: flex;
  height: 100%;
  width: 100%;
  overflow: hidden;
  background: $ide-bg;
  color: $ide-text;
  position: relative;
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    background:
      radial-gradient(1200px 600px at 12% -10%, $ide-spark-soft, transparent 55%),
      radial-gradient(900px 500px at 110% 110%, rgba(96, 165, 250, 0.05), transparent 55%);
    opacity: 0.55;
    z-index: 0;
  }
  // Grain — fine SVG noise layered on top of the ambient wash for texture/depth.
  // Kept dim (~3% in light, ~5% in dark) so it reads as paper/material rather than dirt.
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    z-index: 0;
    opacity: 0.035;
    mix-blend-mode: multiply;
    background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='180' height='180'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.92' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.7 0'/></filter><rect width='100%25' height='100%25' filter='url(%23n)'/></svg>");
    background-size: 180px 180px;
  }
  > * { position: relative; z-index: 1; }
}
html.dark .ide-layout::after { opacity: 0.06; mix-blend-mode: screen; }

.ide-sidebar {
  position: relative;
  flex-shrink: 0;
  background: $ide-panel-bg;
  border-right: 1px solid $ide-border;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  animation: ide-panel-in 380ms cubic-bezier(0.2, 0.9, 0.3, 1) 40ms both;
}

@keyframes ide-panel-in {
  from { opacity: 0; transform: translateX(-4px); }
}

.ide-sidebar__top {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.ide-sidebar__bottom {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border-top: 1px solid $ide-border;
}

.ide-sidebar__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: $ide-text-disabled;
}

.ide-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  animation: ide-panel-in 380ms cubic-bezier(0.2, 0.9, 0.3, 1) 100ms both;
}

.ide-editor-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 200px;
  overflow: hidden;
}

.ide-result-area {
  flex-shrink: 0;
  background: $ide-bg;
  border-top: 1px solid $ide-border;
  overflow: hidden;
}

.ide-ai-panel {
  position: relative;
  flex-shrink: 0;
  background: $ide-panel-bg;
  border-left: 1px solid $ide-border;
  overflow: hidden;
  animation: ide-panel-in 380ms cubic-bezier(0.2, 0.9, 0.3, 1) 160ms both;
}

.resize-handle {
  position: absolute;
  z-index: 10;
  &--right, &--ai-left {
    top: 0; width: 8px; height: 100%;
    cursor: col-resize;
    &::after {
      content: ''; position: absolute; top: 0; width: 1px; height: 100%;
      background: transparent;
      transition: background 180ms ease, box-shadow 180ms ease;
    }
    &:hover::after {
      background: $ide-spark;
      box-shadow: 0 0 0 1px $ide-spark-soft, 0 0 12px $ide-spark-glow;
    }
  }
  &--right { right: -4px; &::after { left: 3px; } }
  &--ai-left { left: -4px; &::after { right: 3px; } }
  &--horizontal {
    position: relative;
    height: 6px; cursor: row-resize; flex-shrink: 0;
    background: transparent;
    &::after {
      content: ''; position: absolute; left: 0; top: 2px; width: 100%; height: 1px;
      background: transparent;
      transition: background 180ms ease, box-shadow 180ms ease;
    }
    &:hover::after {
      background: $ide-spark;
      box-shadow: 0 0 0 1px $ide-spark-soft, 0 0 12px $ide-spark-glow;
    }
  }
}

.ai-toggle-btn {
  position: absolute;
  top: 10px; right: 10px; z-index: 20;
  width: 34px; height: 34px;
  border-radius: 10px;
  border: 1px solid $ide-border;
  background: var(--r-bg-card-translucent);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: $ide-text-muted;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  box-shadow: var(--r-shadow-sm);
  transition: transform 220ms cubic-bezier(0.2, 0.9, 0.3, 1),
              background 180ms ease, color 180ms ease,
              border-color 180ms ease, box-shadow 220ms ease;
  &:hover {
    color: $ide-spark;
    border-color: $ide-spark;
    box-shadow: 0 0 0 4px $ide-spark-soft, var(--r-shadow-md);
    transform: translateY(-1px);
  }
  &:active { transform: translateY(0); }
}
</style>
