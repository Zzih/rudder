<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { debugRagRetrieval, type RagDebugTrace, type RagStageTrace } from '@/api/ai-rag-debug'
import { loadWorkspaceOptions, type WorkspaceOption } from '@/api/workspace'
import { useTaskTypesStore } from '@/stores/taskTypes'

const { t } = useI18n()

const queryText = ref('')
const workspaceId = ref<number | null>(null)
const taskType = ref<string>('')

const workspaces = ref<WorkspaceOption[]>([])

const taskTypesStore = useTaskTypesStore()
const { dialectAware } = storeToRefs(taskTypesStore)
const taskTypeOptions = computed(() => dialectAware.value
  .map(t => ({ value: t.value, label: t.label })))

onMounted(async () => {
  workspaces.value = await loadWorkspaceOptions()
  taskTypesStore.ensureLoaded()
})

const loading = ref(false)
const trace = ref<RagDebugTrace | null>(null)

async function runDebug() {
  if (!queryText.value.trim()) {
    ElMessage.warning(t('aiAdmin.ragDebug.queryRequired'))
    return
  }
  loading.value = true
  trace.value = null
  try {
    const res = (await debugRagRetrieval({
      query: queryText.value,
      workspaceId: workspaceId.value,
      taskType: taskType.value || null,
    })) as any
    trace.value = res?.data ?? null
  } catch { /* interceptor handled */ }
  finally { loading.value = false }
}

function stageStatusType(stage: RagStageTrace): 'info' | 'success' | 'warning' | 'danger' {
  if (stage.error) return 'danger'
  if (stage.skipped) return 'info'
  return 'success'
}

function stageStatusLabel(stage: RagStageTrace): string {
  if (stage.error) return t('aiAdmin.ragDebug.error')
  if (stage.skipped) return t('aiAdmin.ragDebug.skipped')
  return t('aiAdmin.ragDebug.ok')
}

function formatJson(v: unknown): string {
  if (v == null) return '—'
  if (typeof v === 'string') return v
  try { return JSON.stringify(v, null, 2) }
  catch { return String(v) }
}

const totalDuration = computed(() => trace.value?.totalLatencyMs ?? 0)

const activeStageCount = computed(() => trace.value
  ? trace.value.stages.filter(s => !s.skipped).length
  : 0)

const erroredStageCount = computed(() => trace.value
  ? trace.value.stages.filter(s => !!s.error).length
  : 0)
</script>

<template>
  <div class="rag-debug" v-loading="loading">
    <!-- ============== 输入面板 ============== -->
    <section class="panel panel--input">
      <header class="panel__head">
        <div>
          <h3 class="panel__title">{{ t('aiAdmin.ragDebug.title') }}</h3>
          <p class="panel__hint">{{ t('aiAdmin.ragDebug.hint') }}</p>
        </div>
      </header>

      <el-form label-position="top" class="input-form">
        <el-form-item :label="t('aiAdmin.ragDebug.query')">
          <el-input
            v-model="queryText"
            type="textarea"
            :rows="3"
            :placeholder="t('aiAdmin.ragDebug.queryPlaceholder')"
          />
        </el-form-item>

        <div class="input-form__row">
          <el-form-item :label="t('aiAdmin.ragDebug.workspace')">
            <el-select
              v-model="workspaceId"
              filterable
              clearable
              :placeholder="t('aiAdmin.ragDebug.workspacePlaceholder')"
              class="full"
            >
              <el-option
                v-for="ws in workspaces"
                :key="ws.id"
                :label="ws.name"
                :value="ws.id"
              />
            </el-select>
            <span class="field-hint">{{ t('aiAdmin.ragDebug.workspaceHint') }}</span>
          </el-form-item>

          <el-form-item :label="t('aiAdmin.ragDebug.taskType')">
            <el-select
              v-model="taskType"
              filterable
              clearable
              :placeholder="t('aiAdmin.ragDebug.taskTypePlaceholder')"
              class="full"
            >
              <el-option
                v-for="opt in taskTypeOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <span class="field-hint">{{ t('aiAdmin.ragDebug.taskTypeHint') }}</span>
          </el-form-item>
        </div>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="runDebug">
            ▶ {{ t('aiAdmin.ragDebug.run') }}
          </el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- ============== Trace 概览 + Pipeline 快照 ============== -->
    <section v-if="trace" class="panel panel--summary">
      <header class="panel__head panel__head--inline">
        <h3 class="panel__title">{{ t('aiAdmin.ragDebug.summary') }}</h3>
        <div class="summary-stats">
          <span class="stat">
            <span class="stat__value">{{ totalDuration }}<span class="stat__unit">ms</span></span>
            <span class="stat__label">{{ t('aiAdmin.ragDebug.totalDuration') }}</span>
          </span>
          <span class="stat">
            <span class="stat__value">{{ activeStageCount }}</span>
            <span class="stat__label">{{ t('aiAdmin.ragDebug.activeStages') }}</span>
          </span>
          <span class="stat" :class="{ 'stat--danger': erroredStageCount > 0 }">
            <span class="stat__value">{{ erroredStageCount }}</span>
            <span class="stat__label">{{ t('aiAdmin.ragDebug.errors') }}</span>
          </span>
        </div>
      </header>

      <details class="snapshot">
        <summary>{{ t('aiAdmin.ragDebug.pipelineSnapshot') }}</summary>
        <pre class="code-block">{{ formatJson(trace.pipelineSnapshot) }}</pre>
      </details>
    </section>

    <!-- ============== Stage Trace 列表 ============== -->
    <section v-if="trace" class="panel">
      <header class="panel__head">
        <h3 class="panel__title">{{ t('aiAdmin.ragDebug.stages') }}</h3>
      </header>

      <ol class="stages">
        <li
          v-for="(stage, i) in trace.stages"
          :key="i"
          class="stage"
          :class="[
            `stage--${stageStatusType(stage)}`,
            { 'stage--skipped': stage.skipped }
          ]"
        >
          <div class="stage__rail">
            <span class="stage__no">{{ i + 1 }}</span>
            <span class="stage__connector" v-if="i < trace.stages.length - 1" />
          </div>

          <div class="stage__body">
            <header class="stage__head">
              <span class="stage__name">{{ stage.name }}</span>
              <el-tag :type="stageStatusType(stage)" size="small" effect="plain">
                {{ stageStatusLabel(stage) }}
              </el-tag>
              <span v-if="!stage.skipped && stage.durationMs != null" class="stage__duration">
                {{ stage.durationMs }} ms
              </span>
              <span v-if="stage.skipReason" class="stage__reason">
                · {{ stage.skipReason }}
              </span>
            </header>

            <div v-if="stage.error" class="stage__error">
              <span class="stage__error-label">{{ t('aiAdmin.ragDebug.error') }}</span>
              <code>{{ stage.error }}</code>
            </div>

            <div v-if="!stage.skipped" class="stage__io">
              <div class="io-block">
                <h5 class="io-block__title">{{ t('aiAdmin.ragDebug.input') }}</h5>
                <pre class="code-block code-block--io">{{ formatJson(stage.input) }}</pre>
              </div>
              <div class="io-block">
                <h5 class="io-block__title">{{ t('aiAdmin.ragDebug.output') }}</h5>
                <pre class="code-block code-block--io">{{ formatJson(stage.output) }}</pre>
              </div>
            </div>
          </div>
        </li>
      </ol>
    </section>

    <!-- ============== 最终 Prompt ============== -->
    <section v-if="trace?.finalPrompt" class="panel">
      <header class="panel__head">
        <h3 class="panel__title">{{ t('aiAdmin.ragDebug.finalPrompt') }}</h3>
        <span class="panel__hint panel__hint--inline">{{ t('aiAdmin.ragDebug.finalPromptHint') }}</span>
      </header>
      <pre class="code-block code-block--prompt">{{ trace.finalPrompt }}</pre>
    </section>
  </div>
</template>

<style scoped lang="scss">
.rag-debug {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-4);
}

// ===== Panel skeleton (与 AiAdminEvals / SpiConfigPage 风格一致) =====
.panel {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-lg);
  padding: var(--r-space-5) var(--r-space-6);
}

.panel__head {
  margin-bottom: var(--r-space-4);

  &--inline {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--r-space-4);
    margin-bottom: var(--r-space-3);
  }
}

.panel__title {
  margin: 0 0 var(--r-space-1);
  font-size: var(--r-font-lg);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
}

.panel__hint {
  margin: 0;
  font-size: var(--r-font-sm);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);

  &--inline {
    margin-top: var(--r-space-2);
  }
}

// ===== 输入表单 =====
.input-form {
  .full {
    width: 100%;
  }
}

.input-form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--r-space-4);
}

.field-hint {
  display: block;
  margin-top: var(--r-space-1);
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
}

// ===== 概览统计 =====
.summary-stats {
  display: flex;
  align-items: stretch;
  gap: var(--r-space-5);
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  padding-left: var(--r-space-4);
  border-left: 1px solid var(--r-border-light);

  &:first-child {
    border-left: none;
    padding-left: 0;
  }

  &--danger .stat__value {
    color: var(--r-danger);
  }
}

.stat__value {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xl);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  line-height: 1;
}

.stat__unit {
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-regular);
  color: var(--r-text-muted);
  margin-left: 2px;
}

.stat__label {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.snapshot {
  border-top: 1px dashed var(--r-border-light);
  padding-top: var(--r-space-3);
}

.snapshot summary {
  cursor: pointer;
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-medium);
  color: var(--r-text-secondary);
  padding: var(--r-space-1) 0;

  &:hover {
    color: var(--r-text-primary);
  }
}

// ===== Stage 列表 (timeline 样式) =====
.stages {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
}

.stage {
  display: grid;
  grid-template-columns: 32px 1fr;
  gap: var(--r-space-3);

  &--skipped .stage__body {
    opacity: 0.55;
  }
}

.stage__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.stage__no {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border);
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-medium);
  color: var(--r-text-secondary);
  flex-shrink: 0;
}

.stage--success .stage__no {
  background: var(--r-success-bg);
  border-color: var(--r-success-border);
  color: var(--r-success);
}

.stage--danger .stage__no {
  background: var(--r-danger-bg);
  border-color: var(--r-danger-border);
  color: var(--r-danger);
}

.stage--info .stage__no {
  background: var(--r-info-bg);
  border-color: var(--r-info-border);
  color: var(--r-info);
}

.stage__connector {
  flex: 1;
  width: 1px;
  background: var(--r-border-light);
  margin-top: var(--r-space-1);
}

.stage__body {
  flex: 1;
  padding-bottom: var(--r-space-4);
  min-width: 0;
}

.stage__head {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  flex-wrap: wrap;
  margin-bottom: var(--r-space-2);
}

.stage__name {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-md);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  letter-spacing: 0.01em;
}

.stage__duration {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  color: var(--r-text-tertiary);
  margin-left: var(--r-space-1);
}

.stage__reason {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  font-style: italic;
}

.stage__error {
  background: var(--r-danger-bg);
  border: 1px solid var(--r-danger-border);
  border-radius: var(--r-radius-sm);
  padding: var(--r-space-2) var(--r-space-3);
  margin-bottom: var(--r-space-2);
}

.stage__error-label {
  display: inline-block;
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-bold);
  color: var(--r-danger);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-right: var(--r-space-2);
}

.stage__error code {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  color: var(--r-danger);
}

.stage__io {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--r-space-3);

  @media (max-width: 1080px) {
    grid-template-columns: 1fr;
  }
}

.io-block__title {
  margin: 0 0 var(--r-space-1);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-bold);
  color: var(--r-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

// ===== code block (复用 AiAdminEvals 风格) =====
.code-block {
  margin: 0;
  background: var(--r-bg-code);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-sm);
  padding: var(--r-space-3);
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  line-height: var(--r-leading-normal);
  white-space: pre-wrap;
  word-break: break-word;
  overflow: auto;
  max-height: 320px;
  color: var(--r-text-primary);
}

.code-block--io {
  font-size: var(--r-font-xs);
  max-height: 240px;
}

.code-block--prompt {
  max-height: 480px;
}
</style>
