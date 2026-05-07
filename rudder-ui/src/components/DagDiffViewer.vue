<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface DiffLine {
  lineNumber: number
  action: 'ADDED' | 'REMOVED' | 'MODIFIED' | 'UNCHANGED'
  content: string
}

interface NodeDiff {
  nodeCode: string
  label: string
  oldLabel?: string
  taskType?: string
  action: 'ADDED' | 'REMOVED' | 'MODIFIED' | 'UNCHANGED'
  contentDiff?: DiffLine[]
}

interface EdgeDiff {
  source: string
  target: string
  action: 'ADDED' | 'REMOVED' | 'MODIFIED' | 'UNCHANGED'
}

interface DagDiffResult {
  nodes: NodeDiff[]
  edges: EdgeDiff[]
  oldNodeCount: number
  newNodeCount: number
}

const props = defineProps<{
  dagDiff: DagDiffResult | null
}>()

const diff = computed(() => {
  const d = props.dagDiff
  if (!d) return { nodes: [], edges: [], oldNodeCount: 0, newNodeCount: 0 }
  return d
})

const hasChanges = computed(() =>
  diff.value.nodes.length > 0 || diff.value.edges.length > 0
)

function nodeType(action: string): string {
  if (action === 'ADDED') return 'added'
  if (action === 'REMOVED') return 'removed'
  return 'modified'
}
</script>

<template>
  <div class="dag-diff">
    <div class="dag-diff__stats">
      <span>{{ diff.oldNodeCount }} {{ t('dagDiff.nodes') }} → {{ diff.newNodeCount }} {{ t('dagDiff.nodes') }}</span>
      <span v-if="diff.nodes.length" class="dag-diff__badge">{{ diff.nodes.length }} {{ t('dagDiff.nodeChanges') }}</span>
      <span v-if="diff.edges.length" class="dag-diff__badge">{{ diff.edges.length }} {{ t('dagDiff.edgeChanges') }}</span>
    </div>

    <div v-if="!hasChanges" class="dag-diff__empty">{{ t('dagDiff.noChanges') }}</div>

    <div v-if="diff.nodes.length" class="dag-diff__section">
      <div class="dag-diff__section-title">{{ t('dagDiff.nodeChangesTitle') }}</div>
      <div v-for="(node, idx) in diff.nodes" :key="'n' + idx" class="dag-diff__item" :class="'dag-diff__item--' + nodeType(node.action)">
        <span class="dag-diff__sign">
          <template v-if="node.action === 'ADDED'">+</template>
          <template v-else-if="node.action === 'REMOVED'">-</template>
          <template v-else>~</template>
        </span>
        <span class="dag-diff__label">
          <template v-if="node.action === 'ADDED'">{{ t('dagDiff.newNode') }}: <strong>{{ node.label }}</strong></template>
          <template v-else-if="node.action === 'REMOVED'">{{ t('dagDiff.removedNode') }}: <strong>{{ node.label }}</strong></template>
          <template v-else-if="node.oldLabel">{{ t('dagDiff.renamed') }}: <strong>{{ node.oldLabel }}</strong> → <strong>{{ node.label }}</strong></template>
          <template v-else>{{ t('dagDiff.modifiedNode', { fallback: 'Modified' }) }}: <strong>{{ node.label }}</strong></template>
          <span v-if="node.taskType" class="dag-diff__task-type">{{ node.taskType }}</span>
          <span v-if="node.contentDiff && node.contentDiff.length" class="dag-diff__detail">{{ t('dagDiff.scriptChanged') }}</span>
        </span>
      </div>
    </div>

    <div v-if="diff.edges.length" class="dag-diff__section">
      <div class="dag-diff__section-title">{{ t('dagDiff.edgeChangesTitle') }}</div>
      <div v-for="(edge, idx) in diff.edges" :key="'e' + idx" class="dag-diff__item" :class="'dag-diff__item--' + nodeType(edge.action)">
        <span class="dag-diff__sign">{{ edge.action === 'ADDED' ? '+' : '-' }}</span>
        <span class="dag-diff__label">
          <strong>{{ edge.source }}</strong> → <strong>{{ edge.target }}</strong>
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dag-diff {
  height: 100%;
  overflow: auto;
  padding: 16px;
  font-size: 13px;
  font-family: var(--r-font-mono);
}

.dag-diff__stats {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--r-border);
  margin-bottom: 16px;
  font-size: 12px;
  color: var(--r-text-muted);
}

.dag-diff__badge {
  background: var(--r-bg-hover);
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 600;
}

.dag-diff__empty {
  color: var(--r-text-muted);
  text-align: center;
  padding: 40px 0;
}

.dag-diff__section { margin-bottom: 20px; }

.dag-diff__section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--r-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.dag-diff__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 4px;
  margin-bottom: 4px;
  line-height: 20px;
}

.dag-diff__sign {
  width: 16px;
  font-weight: 700;
  text-align: center;
  flex-shrink: 0;
}

.dag-diff__item--added {
  background: var(--r-success-bg);
  color: var(--r-success);
}
.dag-diff__item--added .dag-diff__sign { color: var(--r-success); }

.dag-diff__item--removed {
  background: var(--r-danger-bg);
  color: var(--r-danger);
}
.dag-diff__item--removed .dag-diff__sign { color: var(--r-danger); }

.dag-diff__item--modified {
  background: var(--r-warning-bg);
  color: var(--r-warning);
}
.dag-diff__item--modified .dag-diff__sign { color: var(--r-warning); }

.dag-diff__task-type {
  font-size: 11px;
  background: var(--r-bg-hover);
  padding: 1px 6px;
  border-radius: 3px;
  margin-left: 6px;
  color: var(--r-text-muted);
}

.dag-diff__detail {
  margin-left: 8px;
  color: var(--r-text-muted);
  font-style: italic;
}
</style>
