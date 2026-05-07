<script setup lang="ts">
import { reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Graph } from '@antv/x6'
import { getTaskIconUrl } from '@/utils/taskIconUrl'
import { useThemeStore } from '@/stores/theme'

interface DagNode {
  taskCode: string; label: string
  position: { x: number; y: number }
}
interface DagEdge { source: string; target: string }
interface NodeExec { nodeId: string; nodeType: string; status: string; startedAt?: string; finishedAt?: string }

const props = defineProps<{
  dagJson: string
  nodeStatuses: NodeExec[]
}>()

const emit = defineEmits<{ 'view-log': [nodeId: string]; 'view-node': [nodeId: string] }>()
const { t } = useI18n()

const themeStore = useThemeStore()
const containerId = `dag-viewer-${Date.now()}`
let graph: Graph | null = null

// Tooltip
const tooltip = reactive({ visible: false, x: 0, y: 0, name: '', status: '', type: '', start: '', end: '' })

// Context menu
const ctxMenu = reactive({ visible: false, x: 0, y: 0, nodeId: '' })

// Status → color mapping (resolved from CSS variables for dark mode support)
function resolveStatusColors() {
  const s = getComputedStyle(document.documentElement)
  const get = (name: string) => s.getPropertyValue(name).trim()
  return {
    colors: {
      SUCCESS:   { accent: get('--r-success'),  body: get('--r-success-bg'),  border: get('--r-success-border') },
      FAILED:    { accent: get('--r-danger'),   body: get('--r-danger-bg'),   border: get('--r-danger-border') },
      RUNNING:   { accent: get('--r-info'),     body: get('--r-info-bg'),     border: get('--r-info-border') },
      WAITING:   { accent: get('--r-text-muted'), body: get('--r-bg-hover'),  border: get('--r-border-light') },
      SKIPPED:   { accent: get('--r-text-muted'), body: get('--r-bg-hover'),  border: get('--r-border-light') },
      CANCELLED: { accent: get('--r-warning'),  body: get('--r-warning-bg'),  border: get('--r-warning-border') },
    } as Record<string, { accent: string; body: string; border: string }>,
    default: { accent: get('--r-text-muted'), body: get('--r-bg-hover'), border: get('--r-border-light') },
  }
}
let statusColors: Record<string, { accent: string; body: string; border: string }> = {}
let defaultColors = { accent: '#d9d9d9', body: 'rgba(217,217,217,0.08)', border: 'rgba(217,217,217,0.25)' }

const nodeExecMap = computed(() => {
  const map = new Map<string, NodeExec>()
  for (const n of props.nodeStatuses) map.set(n.nodeId, n)
  return map
})

function getNodeExec(nodeId: string): NodeExec | undefined {
  return nodeExecMap.value.get(nodeId)
}

function getNodeColors(nodeId: string) {
  const ns = getNodeExec(nodeId)
  return ns ? (statusColors[ns.status] || defaultColors) : defaultColors
}

function getNodeStatus(nodeId: string): string {
  return getNodeExec(nodeId)?.status || 'WAITING'
}

function formatTime(t?: string): string {
  if (!t) return '-'
  return new Date(t).toLocaleString()
}

function hideTooltip() { tooltip.visible = false }
function hideCtxMenu() { ctxMenu.visible = false }

function renderDag() {
  if (!graph) return
  graph.clearCells()

  let dag: { nodes: DagNode[]; edges: DagEdge[] }
  try { dag = JSON.parse(props.dagJson) } catch { return }
  if (!dag?.nodes) return

  const textPrimary = getComputedStyle(document.documentElement).getPropertyValue('--r-text-primary').trim()

  for (const n of dag.nodes) {
    const nid = String(n.taskCode)
    const colors = getNodeColors(nid)
    const status = getNodeStatus(nid)
    const nodeType = getNodeExec(nid)?.nodeType || ''
    const iconUrl = getTaskIconUrl(nodeType)

    graph.addNode({
      id: nid,
      x: n.position?.x ?? 100, y: n.position?.y ?? 100,
      width: 220, height: 48, shape: 'rect',
      markup: [
        { tagName: 'rect', selector: 'body' },
        { tagName: 'rect', selector: 'accent' },
        { tagName: 'image', selector: 'icon' },
        { tagName: 'text', selector: 'label' },
        { tagName: 'text', selector: 'statusText' },
      ],
      attrs: {
        body: { fill: colors.body, stroke: colors.border, strokeWidth: 1.5, rx: 6, ry: 6, width: 220, height: 48 },
        accent: { fill: colors.accent, width: 4, height: 48, rx: 6, ry: 0, x: 0, y: 0 },
        icon: { 'xlink:href': iconUrl, width: 30, height: 30, x: 12, y: 9 },
        label: { text: n.label || nodeType, fontSize: 13, fontWeight: 500, fill: textPrimary, refX: 48, refY: 14, textAnchor: 'start' },
        statusText: { text: status, fontSize: 10, fill: colors.accent, fontWeight: 600, refX: 48, refY: 32, textAnchor: 'start' },
      },
      data: { nodeId: nid, nodeType, label: n.label || nodeType },
    })
  }

  for (const e of (dag.edges || [])) {
    const srcColors = getNodeColors(String(e.source))
    graph.addEdge({
      source: String(e.source), target: String(e.target),
      router: { name: 'manhattan' },
      connector: { name: 'rounded', args: { radius: 6 } },
      attrs: { line: { stroke: srcColors.accent, strokeWidth: 1.5, targetMarker: { name: 'block', width: 8, height: 6 } } },
    })
  }

  nextTick(() => graph?.zoomToFit({ padding: 30, maxScale: 1 }))
}

onMounted(() => {
  const resolved = resolveStatusColors()
  statusColors = resolved.colors
  defaultColors = resolved.default

  const container = document.getElementById(containerId)
  if (!container) return

  graph = new Graph({
    container,
    autoResize: true,
    panning: { enabled: true, eventTypes: ['leftMouseDown'] },
    mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'] },
    interacting: { nodeMovable: false, edgeMovable: false, edgeLabelMovable: false },
  })

  // Hover → show tooltip
  graph.on('node:mouseenter', ({ e, node }) => {
    const data = node.getData() || {}
    const exec = getNodeExec(data.nodeId)
    tooltip.name = data.label || data.nodeId
    tooltip.type = data.nodeType || ''
    tooltip.status = exec?.status || 'WAITING'
    tooltip.start = formatTime(exec?.startedAt)
    tooltip.end = formatTime(exec?.finishedAt)
    const evt = e.originalEvent as MouseEvent
    tooltip.x = evt.clientX + 12
    tooltip.y = evt.clientY + 12
    tooltip.visible = true
  })
  graph.on('node:mouseleave', () => { hideTooltip() })
  graph.on('node:mousemove', ({ e }) => {
    const evt = e.originalEvent as MouseEvent
    tooltip.x = evt.clientX + 12
    tooltip.y = evt.clientY + 12
  })

  // Right-click → context menu (view log)
  graph.on('node:contextmenu', ({ e, node }) => {
    const evt = e.originalEvent as MouseEvent
    evt.preventDefault()
    const data = node.getData() || {}
    ctxMenu.nodeId = data.nodeId
    ctxMenu.x = evt.clientX
    ctxMenu.y = evt.clientY
    ctxMenu.visible = true
  })

  // Double-click → view node detail
  graph.on('node:dblclick', ({ node }) => {
    const data = node.getData() || {}
    if (data.nodeId) emit('view-node', data.nodeId)
  })

  // Click blank → hide menus
  graph.on('blank:click', () => { hideCtxMenu(); hideTooltip() })
  graph.on('node:click', () => { hideCtxMenu() })

  renderDag()
})

// 父组件按 immutable 风格更新 nodeStatuses(整体替换),不需要 deep 全量遍历
watch(() => [props.dagJson, props.nodeStatuses], () => renderDag())
watch(() => themeStore.isDark, () => {
  nextTick(() => {
    const resolved = resolveStatusColors()
    statusColors = resolved.colors
    defaultColors = resolved.default
    renderDag()
  })
})

onUnmounted(() => { graph?.dispose(); graph = null })
</script>

<template>
  <div class="dag-viewer" @click="hideCtxMenu">
    <div :id="containerId" class="dag-viewer__canvas" />

    <!-- Legend -->
    <div class="dag-viewer__legend">
      <span v-for="(color, status) in statusColors" :key="status" class="legend-item">
        <span class="legend-dot" :style="{ background: color.accent }" />
        {{ status }}
      </span>
    </div>

    <!-- Tooltip (follows mouse on node hover) -->
    <Teleport to="body">
      <div v-if="tooltip.visible" class="dv-tooltip" :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }">
        <div class="dv-tooltip__row"><span class="dv-tooltip__label">{{ t('common.name') }}:</span> {{ tooltip.name }}</div>
        <div class="dv-tooltip__row"><span class="dv-tooltip__label">{{ t('common.status') }}:</span> <span :style="{ color: (statusColors[tooltip.status] || defaultColors).accent, fontWeight: 600 }">{{ tooltip.status }}</span></div>
        <div class="dv-tooltip__row"><span class="dv-tooltip__label">{{ t('common.type') }}:</span> {{ tooltip.type }}</div>
        <div class="dv-tooltip__row"><span class="dv-tooltip__label">{{ t('instance.started') }}:</span> {{ tooltip.start }}</div>
        <div class="dv-tooltip__row"><span class="dv-tooltip__label">{{ t('instance.finished') }}:</span> {{ tooltip.end }}</div>
      </div>
    </Teleport>

    <!-- Right-click context menu -->
    <Teleport to="body">
      <div v-if="ctxMenu.visible" class="dv-ctx-overlay" @click="hideCtxMenu" @contextmenu.prevent="hideCtxMenu" />
      <div v-if="ctxMenu.visible" class="dv-ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }" @click.stop>
        <div class="dv-ctx-item" @click="emit('view-log', ctxMenu.nodeId); hideCtxMenu()">
          <el-icon><Document /></el-icon> {{ t('instance.log') }}
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped lang="scss">
.dag-viewer { position: relative; width: 100%; height: 100%; min-height: 300px; }
.dag-viewer__canvas { width: 100%; height: 100%; }

.dag-viewer__legend {
  position: absolute; bottom: 12px; left: 12px;
  display: flex; gap: 12px; background: var(--r-bg-card-translucent);
  padding: 6px 12px; border-radius: 6px; border: 1px solid var(--r-border-light);
}
.legend-item { display: flex; align-items: center; gap: 4px; font-size: 11px; color: var(--r-text-secondary); }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; }
</style>

<style>
/* Tooltip & context menu — global styles (teleported) */
.dv-tooltip {
  position: fixed; z-index: 9999; pointer-events: none;
  background: var(--r-bg-tooltip); color: #fff; border-radius: 6px;
  padding: 8px 12px; font-size: 12px; line-height: 1.8; max-width: 360px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.dv-tooltip__row { white-space: nowrap; }
.dv-tooltip__label { color: rgba(255,255,255,0.6); margin-right: 4px; }

.dv-ctx-overlay { position: fixed; inset: 0; z-index: 9998; }
.dv-ctx-menu {
  position: fixed; z-index: 9999; background: var(--r-bg-card); border-radius: 8px;
  box-shadow: 0 3px 14px rgba(0,0,0,0.08), 0 1px 4px rgba(0,0,0,0.04);
  border: 1px solid var(--r-border-light); padding: 4px; min-width: 120px;
}
.dv-ctx-item {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 12px; font-size: 13px; color: var(--r-text-secondary);
  cursor: pointer; border-radius: 5px; transition: all 0.15s;
}
.dv-ctx-item:hover { background: var(--r-bg-hover); color: var(--r-text-primary); }
</style>
