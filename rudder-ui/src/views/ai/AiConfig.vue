<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AiAdminLlm from './admin/AiAdminLlm.vue'
import AiAdminEmbedding from './admin/AiAdminEmbedding.vue'
import AiAdminVector from './admin/AiAdminVector.vue'
import AiAdminRag from './admin/AiAdminRag.vue'
import AiAdminSkills from './admin/AiAdminSkills.vue'
import AiAdminMcp from './admin/AiAdminMcp.vue'
import AiAdminDocuments from './admin/AiAdminDocuments.vue'
import AiAdminEvals from './admin/AiAdminEvals.vue'
import AiAdminTools from './admin/AiAdminTools.vue'
import AiAdminDialects from './admin/AiAdminDialects.vue'
import AiAdminRagDebug from './admin/AiAdminRagDebug.vue'

const { t } = useI18n()
const activeTab = ref<string>('llm')

interface TabDef { key: string; labelKey: string; dot: string; comp: any }

const tabs = computed<TabDef[]>(() => [
  // 基础 provider(配完才能用)
  { key: 'llm',             labelKey: 'aiAdmin.llm.tab',         dot: 'var(--r-accent)',  comp: AiAdminLlm },
  { key: 'embedding',       labelKey: 'aiAdmin.embedding.tab',   dot: 'var(--r-purple)',  comp: AiAdminEmbedding },
  { key: 'vector',          labelKey: 'aiAdmin.vector.tab',      dot: 'var(--r-cyan)',    comp: AiAdminVector },
  { key: 'rag',             labelKey: 'aiAdmin.rag.tab',         dot: 'var(--r-yellow)',  comp: AiAdminRag },
  { key: 'mcp',             labelKey: 'aiAdmin.mcp',             dot: 'var(--r-pink)',    comp: AiAdminMcp },
  // 内容与数据
  { key: 'documents',       labelKey: 'aiAdmin.documents',       dot: 'var(--r-success)', comp: AiAdminDocuments },
  { key: 'dialects',        labelKey: 'aiAdmin.dialects.tab',    dot: 'var(--r-teal)',    comp: AiAdminDialects },
  // AI 行为
  { key: 'skills',          labelKey: 'aiAdmin.skills',          dot: 'var(--r-orange)',  comp: AiAdminSkills },
  { key: 'tools',           labelKey: 'aiAdmin.tools.tab',       dot: 'var(--r-danger)',  comp: AiAdminTools },
  // 质量
  { key: 'evals',           labelKey: 'aiAdmin.evals',           dot: 'var(--r-warning)', comp: AiAdminEvals },
  { key: 'rag-debug',       labelKey: 'aiAdmin.ragDebug.tab',     dot: 'var(--r-info)',    comp: AiAdminRagDebug },
])

const activePane = computed(() => tabs.value.find(t => t.key === activeTab.value)?.comp ?? AiAdminLlm)
</script>

<template>
  <div class="ai-config">
    <header class="ai-config__head">
      <h3>{{ t('admin.aiConfig') }}</h3>
      <span class="ai-config__hint">{{ t('aiAdmin.hint') }}</span>
    </header>

    <nav class="ai-config__tabs" role="tablist">
      <button
        v-for="tab in tabs" :key="tab.key" role="tab"
        class="ai-config__tab"
        :class="{ 'is-active': tab.key === activeTab }"
        :style="{ '--tab-dot': tab.dot }"
        @click="activeTab = tab.key"
      >
        <span class="ai-config__tab-dot" />
        {{ t(tab.labelKey) }}
      </button>
    </nav>

    <section class="ai-config__pane">
      <KeepAlive>
        <component :is="activePane" />
      </KeepAlive>
    </section>
  </div>
</template>

<style scoped lang="scss">
.ai-config {
  height: 100%;
  overflow-y: auto;
  padding: var(--r-space-5) var(--r-space-6);
  background: var(--r-bg-panel);
}

.ai-config__head {
  display: flex;
  align-items: baseline;
  gap: var(--r-space-3);
  margin-bottom: var(--r-space-4);

  h3 {
    margin: 0;
    font-size: var(--r-font-lg);
    font-weight: var(--r-weight-bold);
    color: var(--r-text-primary);
    letter-spacing: -0.01em;
  }
}

.ai-config__hint {
  font-size: var(--r-font-sm);
  color: var(--r-text-muted);
}

/* --- Tab row: a quiet segmented strip with a single coloured underline --- */
.ai-config__tabs {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  gap: var(--r-space-1);
  margin-bottom: var(--r-space-4);
  border-bottom: 1px solid var(--r-border);
}

.ai-config__tab {
  all: unset;
  display: inline-flex;
  align-items: center;
  gap: var(--r-space-2);
  padding: var(--r-space-2) var(--r-space-3);
  font-size: var(--r-font-base);
  color: var(--r-text-tertiary);
  font-weight: var(--r-weight-medium);
  cursor: pointer;
  border-radius: var(--r-radius-sm) var(--r-radius-sm) 0 0;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  white-space: nowrap;
  transition: color 0.15s ease, background-color 0.15s ease, border-color 0.15s ease;

  &:hover {
    color: var(--r-text-primary);
    background: var(--r-bg-hover);
  }

  &.is-active {
    color: var(--r-text-primary);
    border-bottom-color: var(--tab-dot);
    font-weight: var(--r-weight-semibold);
    background: transparent;

    .ai-config__tab-dot {
      background: var(--tab-dot);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--tab-dot) 18%, transparent);
    }
  }
}

.ai-config__tab-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--r-border-dark);
  transition: background-color 0.15s ease, box-shadow 0.15s ease;
}

.ai-config__pane {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-lg);
  overflow: hidden;
  min-height: 420px;
  box-shadow: var(--r-shadow-sm);
}

/* ---------------------------------------------------------------- */
/* Shared layout for list-style sub-panes                            */
/* (Skills / MCP / Documents / Evals / Tools / Dialects)             */
/* Each sub-pane just wraps its content in .tab-pane + .tab-bar;    */
/* all spacing, typography, tables and pager styling lives here.    */
/* ---------------------------------------------------------------- */
.ai-config__pane :deep(.tab-pane) {
  padding: var(--r-space-4) var(--r-space-5) var(--r-space-5);
}

.ai-config__pane :deep(.tab-bar) {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  flex-wrap: wrap;
  margin-bottom: var(--r-space-3);
}

.ai-config__pane :deep(.bar-hint) {
  margin-left: auto;
  color: var(--r-text-muted);
  font-size: var(--r-font-xs);
  line-height: var(--r-leading-snug);
}

.ai-config__pane :deep(.hint),
.ai-config__pane :deep(.field-hint) {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  margin-top: var(--r-space-1);
  line-height: var(--r-leading-snug);
}

.ai-config__pane :deep(.muted) { color: var(--r-text-muted); }

.ai-config__pane :deep(.section-head) {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  margin-bottom: var(--r-space-2);

  h4 {
    margin: 0;
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
    letter-spacing: -0.005em;
  }
}
.ai-config__pane :deep(.section-hint) {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  flex: 1;
}

.ai-config__pane :deep(.el-table) {
  --el-table-border-color: var(--r-border-light);
  border-radius: var(--r-radius-md);
  overflow: hidden;
  border: 1px solid var(--r-border-light);

  &::before { display: none; }

  th.el-table__cell {
    background: var(--r-bg-panel);
    font-size: var(--r-font-sm);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-muted);
    letter-spacing: 0.02em;
    padding: var(--r-space-2) 0;
  }

  td.el-table__cell {
    font-size: var(--r-font-base);
    color: var(--r-text-primary);
    padding: var(--r-space-2) 0;
    border-bottom-color: var(--r-border-light);
  }

  .el-table__row:last-child td { border-bottom: none; }
}

.ai-config__pane :deep(.pager) {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--r-space-3);
}
</style>
