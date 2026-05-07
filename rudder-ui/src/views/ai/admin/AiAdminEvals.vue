<template>
  <div class="tab-pane">
    <div class="tab-bar">
      <el-button type="primary" size="small" @click="openEdit()">
        <el-icon><Plus /></el-icon>{{ t('common.create') }}
      </el-button>
      <el-button size="small" :loading="loading" @click="load">
        <el-icon><Refresh /></el-icon>{{ t('common.refresh') }}
      </el-button>
      <el-select v-model="categoryFilter" size="small" clearable :placeholder="t('aiAdmin.eval.categoryFilter')"
        style="width: 160px" @change="() => { pageNum = 1; load() }">
        <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
      </el-select>
      <el-divider direction="vertical" />
      <el-button size="small" type="primary" :loading="running" @click="handleRunBatch">
        {{ t('aiAdmin.eval.runBatch') }}
      </el-button>
    </div>

    <el-alert v-if="lastBatch" :type="lastBatch.failed ? 'warning' : 'success'" :closable="true"
      @close="lastBatch = null">
      <template #default>
        <strong>batch={{ lastBatch.batchId }}</strong>
        <span class="stats">
          total={{ lastBatch.total }}, passed={{ lastBatch.passed }}, failed={{ lastBatch.failed }}
          ({{ ((lastBatch.passed / Math.max(1, lastBatch.total)) * 100).toFixed(1) }}%)
        </span>
      </template>
    </el-alert>

    <el-table :data="rows" v-loading="loading" size="small" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="category" :label="t('aiAdmin.eval.category')" width="110" />
      <el-table-column :label="t('aiAdmin.eval.mode')" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.mode === 'AGENT' ? 'success' : 'info'">{{ row.mode || 'AGENT' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('aiAdmin.eval.datasource')" width="130">
        <template #default="{ row }">
          <span v-if="row.datasourceId" class="ds-label">{{ dsLabel(row.datasourceId) }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="taskType" :label="t('aiAdmin.eval.taskType')" width="120" />
      <el-table-column prop="prompt" :label="t('aiAdmin.eval.prompt')" show-overflow-tooltip />
      <el-table-column :label="t('common.status')" width="70">
        <template #default="{ row }">
          <el-tag size="small" :type="row.active ? 'success' : 'info'">{{ row.active ? 'ON' : 'OFF' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="200">
        <template #default="{ row }">
          <el-button link size="small" @click="viewHistory(row)">{{ t('aiAdmin.eval.history') }}</el-button>
          <el-button link size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button link size="small" type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" small background layout="total, prev, pager, next, sizes" :total="total"
      :page-size="pageSize" :current-page="pageNum" :page-sizes="[10, 20, 50, 100]" @current-change="onPageChange"
      @size-change="onSizeChange" />

    <!-- ==================== 编辑弹窗 ==================== -->
    <el-dialog v-model="editing" :title="form.id ? t('common.edit') : t('common.create')" width="760" top="5vh"
      destroy-on-close>
      <el-form label-position="top" class="case-form">
        <!-- 基础 -->
        <div class="form-section">
          <div class="form-section__title">{{ t('aiAdmin.eval.sectionBasic') }}</div>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item :label="t('aiAdmin.eval.category')" required>
                <el-select v-model="form.category" style="width: 100%">
                  <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('aiAdmin.eval.difficulty')">
                <el-select v-model="form.difficulty" style="width: 100%" clearable>
                  <el-option label="EASY" value="EASY" />
                  <el-option label="MEDIUM" value="MEDIUM" />
                  <el-option label="HARD" value="HARD" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item :label="t('aiAdmin.eval.mode')" required>
                <el-select v-model="form.mode" style="width: 100%">
                  <el-option label="AGENT" value="AGENT" />
                  <el-option label="CHAT" value="CHAT" />
                </el-select>
                <div class="field-hint">{{ t('aiAdmin.eval.modeHint') }}</div>
              </el-form-item>
            </el-col>
            <el-col :span="16">
              <el-form-item :label="t('aiAdmin.eval.datasource')">
                <el-select v-model="form.datasourceId" style="width: 100%" clearable filterable
                  @change="onDatasourceChange">
                  <el-option v-for="ds in datasources" :key="ds.id" :value="ds.id"
                    :label="`${ds.name} (${ds.datasourceType})`" />
                </el-select>
                <div class="field-hint">{{ t('aiAdmin.eval.datasourceHint') }}</div>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item :label="t('aiAdmin.eval.taskType')">
            <el-input v-model="form.taskType" :placeholder="t('aiAdmin.eval.taskTypePlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('aiAdmin.eval.prompt')" required>
            <el-input v-model="form.prompt" type="textarea" :rows="4"
              :placeholder="t('aiAdmin.eval.promptPlaceholder')" />
          </el-form-item>
        </div>

        <!-- 上下文 -->
        <div class="form-section">
          <div class="form-section__title">{{ t('aiAdmin.eval.sectionContext') }}</div>
          <el-form-item :label="t('aiAdmin.eval.selection')">
            <el-input v-model="ctxForm.selection" type="textarea" :rows="2"
              :placeholder="t('aiAdmin.eval.selectionPlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('aiAdmin.eval.pinnedTables')">
            <el-select v-model="ctxForm.pinnedTables" multiple filterable allow-create default-first-option
              style="width: 100%" :placeholder="t('aiAdmin.eval.pinnedTablesPlaceholder')"
              :reserve-keyword="false" no-data-text="" />
          </el-form-item>
        </div>

        <!-- 断言 -->
        <div class="form-section">
          <div class="form-section__title">{{ t('aiAdmin.eval.sectionAssertions') }}</div>

          <div class="form-subsection">
            <div class="form-subsection__title">{{ t('aiAdmin.eval.assertText') }}</div>
            <el-form-item :label="t('aiAdmin.eval.sqlPattern')">
              <el-input v-model="expForm.sqlPattern" :placeholder="t('aiAdmin.eval.sqlPatternPlaceholder')" />
              <div class="field-hint">{{ t('aiAdmin.eval.sqlPatternHint') }}</div>
            </el-form-item>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item :label="t('aiAdmin.eval.mustContain')">
                  <el-select v-model="expForm.mustContain" multiple filterable allow-create default-first-option
                    style="width: 100%" :placeholder="t('aiAdmin.eval.mustContainPlaceholder')"
                    :reserve-keyword="false" no-data-text="" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="t('aiAdmin.eval.mustNotContain')">
                  <el-select v-model="expForm.mustNotContain" multiple filterable allow-create default-first-option
                    style="width: 100%" :placeholder="t('aiAdmin.eval.mustNotContainPlaceholder')"
                    :reserve-keyword="false" no-data-text="" />
                </el-form-item>
              </el-col>
            </el-row>
          </div>

          <div class="form-subsection" v-if="form.mode === 'AGENT'">
            <div class="form-subsection__title">{{ t('aiAdmin.eval.assertTools') }}</div>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item :label="t('aiAdmin.eval.mustCallTools')">
                  <el-select v-model="expForm.mustCallTools" multiple filterable style="width: 100%"
                    :placeholder="t('aiAdmin.eval.mustCallToolsPlaceholder')">
                    <el-option v-for="tool in availableTools" :key="tool.name" :label="tool.name" :value="tool.name" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="t('aiAdmin.eval.mustNotCallTools')">
                  <el-select v-model="expForm.mustNotCallTools" multiple filterable style="width: 100%"
                    :placeholder="t('aiAdmin.eval.mustNotCallToolsPlaceholder')">
                    <el-option v-for="tool in availableTools" :key="tool.name" :label="tool.name" :value="tool.name" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item :label="t('aiAdmin.eval.minToolCalls')">
                  <el-input-number v-model="expForm.minToolCalls" :min="0" style="width: 100%" controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="t('aiAdmin.eval.maxToolCalls')">
                  <el-input-number v-model="expForm.maxToolCalls" :min="0" style="width: 100%" controls-position="right" />
                </el-form-item>
              </el-col>
            </el-row>
          </div>

          <div class="form-subsection">
            <div class="form-subsection__title">{{ t('aiAdmin.eval.assertPerf') }}</div>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item :label="t('aiAdmin.eval.maxLatencyMs')">
                  <el-input-number v-model="expForm.maxLatencyMs" :min="0" :step="1000" style="width: 100%"
                    controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="t('aiAdmin.eval.maxTokens')">
                  <el-input-number v-model="expForm.maxTokens" :min="0" :step="500" style="width: 100%"
                    controls-position="right" />
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </div>

        <el-form-item>
          <el-switch v-model="form.active" :active-text="t('common.enabled')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editing = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 历史弹窗 ==================== -->
    <el-dialog v-model="historyOpen" :title="t('aiAdmin.eval.history')" width="880" top="5vh">
      <el-table :data="historyRows" size="small" max-height="480" @row-click="openRunDetail">
        <el-table-column :label="t('common.status')" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.passed ? 'success' : 'danger'">
              {{ row.passed ? 'PASS' : 'FAIL' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('aiAdmin.eval.score')" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.score != null" size="small" :type="scoreTagType(row.score)">
              {{ Number(row.score).toFixed(0) }}
            </el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="batchId" label="batch" width="220" show-overflow-tooltip />
        <el-table-column prop="model" label="model" width="140" />
        <el-table-column :label="t('aiAdmin.eval.toolCallsCount')" width="90">
          <template #default="{ row }">{{ runToolCount(row) }}</template>
        </el-table-column>
        <el-table-column prop="latencyMs" :label="t('aiAdmin.eval.latencyMs')" width="90" />
        <el-table-column :label="t('aiAdmin.eval.tokens')" width="120">
          <template #default="{ row }">{{ (row.promptTokens ?? 0) + (row.completionTokens ?? 0) || '—' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="t('common.time')" />
      </el-table>
      <el-pagination class="pager" small background layout="total, prev, pager, next" :total="historyTotal"
        :page-size="historyPageSize" :current-page="historyPageNum" @current-change="onHistoryPageChange" />
    </el-dialog>

    <!-- ==================== Run 详情 ==================== -->
    <el-drawer v-model="runDetailOpen" :size="640" :title="t('aiAdmin.eval.runDetail')">
      <div v-if="activeRun" class="run-detail">
        <div class="run-detail__head">
          <el-tag size="default" :type="activeRun.passed ? 'success' : 'danger'">
            {{ activeRun.passed ? 'PASS' : 'FAIL' }}
          </el-tag>
          <el-tag v-if="activeRun.score != null" size="default" :type="scoreTagType(activeRun.score)">
            {{ t('aiAdmin.eval.score') }}: {{ Number(activeRun.score).toFixed(0) }}
          </el-tag>
          <span class="muted">{{ activeRun.provider || '—' }} · {{ activeRun.model || '—' }}</span>
          <span class="muted">{{ activeRun.latencyMs ?? '—' }} ms · {{
            (activeRun.promptTokens ?? 0) + (activeRun.completionTokens ?? 0) }} tokens</span>
        </div>

        <section v-if="runFailReasons.length" class="run-detail__section run-detail__section--fail">
          <h4>{{ t('aiAdmin.eval.failReasons') }}</h4>
          <ul>
            <li v-for="(r, i) in runFailReasons" :key="i">{{ r }}</li>
          </ul>
        </section>

        <section class="run-detail__section">
          <h4>{{ t('aiAdmin.eval.finalText') }}</h4>
          <pre class="final-text">{{ activeRun.finalText || '—' }}</pre>
        </section>

        <section v-if="runToolCalls.length" class="run-detail__section">
          <h4>{{ t('aiAdmin.eval.toolCalls') }} <span class="muted">({{ runToolCalls.length }})</span></h4>
          <div v-for="(call, i) in runToolCalls" :key="i" class="tool-call">
            <div class="tool-call__head">
              <el-tag size="small" :type="call.success ? 'success' : 'danger'">#{{ i + 1 }} {{ call.name }}</el-tag>
              <span class="muted">{{ call.latencyMs ?? '—' }} ms</span>
            </div>
            <details class="tool-call__body">
              <summary>input / output</summary>
              <div class="tool-call__kv"><span>input</span>
                <pre>{{ formatJson(call.input) }}</pre>
              </div>
              <div class="tool-call__kv"><span>output</span>
                <pre>{{ call.output || '—' }}</pre>
              </div>
              <div v-if="call.errorMessage" class="tool-call__kv">
                <span>error</span>
                <pre class="err">{{ call.errorMessage }}</pre>
              </div>
            </details>
          </div>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminEvals, listTools,
  type AiEvalCaseVO, type AiEvalRunVO, type EvalBatchResultVO,
  type EvalToolInvocation, type ToolViewVO,
} from '@/api/ai'
import { listDatasources } from '@/api/datasource'

const { t } = useI18n()
const CATEGORIES = ['SQL_GEN', 'OPTIMIZE', 'DEBUG', 'EXPLAIN', 'DIALECT']

const rows = ref<AiEvalCaseVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const categoryFilter = ref<string>()
const editing = ref(false)
const saving = ref(false)
const form = reactive<AiEvalCaseVO>(emptyForm())

// 结构化 context / expected,保存时序列化回 JSON 字段
const ctxForm = reactive<{ selection: string; pinnedTables: string[] }>({
  selection: '',
  pinnedTables: [],
})
const expForm = reactive<{
  sqlPattern: string
  mustContain: string[]
  mustNotContain: string[]
  mustCallTools: string[]
  mustNotCallTools: string[]
  minToolCalls: number | null
  maxToolCalls: number | null
  maxLatencyMs: number | null
  maxTokens: number | null
}>({
  sqlPattern: '',
  mustContain: [],
  mustNotContain: [],
  mustCallTools: [],
  mustNotCallTools: [],
  minToolCalls: null,
  maxToolCalls: null,
  maxLatencyMs: null,
  maxTokens: null,
})

const datasources = ref<Array<{ id: number; name: string; datasourceType: string }>>([])
const availableTools = ref<ToolViewVO[]>([])

const running = ref(false)
const lastBatch = ref<EvalBatchResultVO | null>(null)

const historyOpen = ref(false)
const historyRows = ref<AiEvalRunVO[]>([])
const historyTotal = ref(0)
const historyPageNum = ref(1)
const historyPageSize = ref(20)
const currentHistoryCaseId = ref<number | null>(null)

const runDetailOpen = ref(false)
const activeRun = ref<AiEvalRunVO | null>(null)

const runToolCalls = computed<EvalToolInvocation[]>(() =>
  parseJsonArray<EvalToolInvocation>(activeRun.value?.toolCallsJson))
const runFailReasons = computed<string[]>(() =>
  parseJsonArray<string>(activeRun.value?.failReasonsJson))

function emptyForm(): AiEvalCaseVO {
  return { category: 'SQL_GEN', prompt: '', active: true, mode: 'AGENT' }
}

function resetSubForms() {
  ctxForm.selection = ''
  ctxForm.pinnedTables = []
  expForm.sqlPattern = ''
  expForm.mustContain = []
  expForm.mustNotContain = []
  expForm.mustCallTools = []
  expForm.mustNotCallTools = []
  expForm.minToolCalls = null
  expForm.maxToolCalls = null
  expForm.maxLatencyMs = null
  expForm.maxTokens = null
}

function parseJsonArray<T>(raw: string | null | undefined): T[] {
  if (!raw) return []
  try {
    const v = JSON.parse(raw)
    return Array.isArray(v) ? v as T[] : []
  } catch { return [] }
}

function parseJsonObject(raw: string | null | undefined): Record<string, unknown> | null {
  if (!raw) return null
  try {
    const v = JSON.parse(raw)
    return v && typeof v === 'object' && !Array.isArray(v) ? v as Record<string, unknown> : null
  } catch { return null }
}

function loadSubFormsFromCase(c: AiEvalCaseVO) {
  resetSubForms()
  const ctx = parseJsonObject(c.contextJson)
  if (ctx) {
    ctxForm.selection = typeof ctx.selection === 'string' ? ctx.selection : ''
    ctxForm.pinnedTables = Array.isArray(ctx.pinnedTables) ? ctx.pinnedTables.map(String) : []
  }
  const exp = parseJsonObject(c.expectedJson)
  if (exp) {
    expForm.sqlPattern = typeof exp.sqlPattern === 'string' ? exp.sqlPattern : ''
    expForm.mustContain = Array.isArray(exp.mustContain) ? exp.mustContain.map(String) : []
    expForm.mustNotContain = Array.isArray(exp.mustNotContain) ? exp.mustNotContain.map(String) : []
    expForm.mustCallTools = Array.isArray(exp.mustCallTools) ? exp.mustCallTools.map(String) : []
    expForm.mustNotCallTools = Array.isArray(exp.mustNotCallTools) ? exp.mustNotCallTools.map(String) : []
    expForm.minToolCalls = typeof exp.minToolCalls === 'number' ? exp.minToolCalls : null
    expForm.maxToolCalls = typeof exp.maxToolCalls === 'number' ? exp.maxToolCalls : null
    expForm.maxLatencyMs = typeof exp.maxLatencyMs === 'number' ? exp.maxLatencyMs : null
    expForm.maxTokens = typeof exp.maxTokens === 'number' ? exp.maxTokens : null
  }
}

function serializeSubForms() {
  // context: 只保留非空值
  const ctx: Record<string, unknown> = {}
  if (ctxForm.selection?.trim()) ctx.selection = ctxForm.selection.trim()
  if (ctxForm.pinnedTables.length) ctx.pinnedTables = ctxForm.pinnedTables
  form.contextJson = Object.keys(ctx).length ? JSON.stringify(ctx) : null

  // expected: 只保留非空 / 非零
  const exp: Record<string, unknown> = {}
  if (expForm.sqlPattern?.trim()) exp.sqlPattern = expForm.sqlPattern.trim()
  if (expForm.mustContain.length) exp.mustContain = expForm.mustContain
  if (expForm.mustNotContain.length) exp.mustNotContain = expForm.mustNotContain
  if (expForm.mustCallTools.length) exp.mustCallTools = expForm.mustCallTools
  if (expForm.mustNotCallTools.length) exp.mustNotCallTools = expForm.mustNotCallTools
  if (expForm.minToolCalls != null) exp.minToolCalls = expForm.minToolCalls
  if (expForm.maxToolCalls != null) exp.maxToolCalls = expForm.maxToolCalls
  if (expForm.maxLatencyMs != null) exp.maxLatencyMs = expForm.maxLatencyMs
  if (expForm.maxTokens != null) exp.maxTokens = expForm.maxTokens
  form.expectedJson = Object.keys(exp).length ? JSON.stringify(exp) : null
}

async function load() {
  loading.value = true
  try {
    const { data } = await adminEvals.listCases({
      category: categoryFilter.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    rows.value = data?.records ?? []
    total.value = data?.total ?? 0
  } finally { loading.value = false }
}

function onPageChange(n: number) { pageNum.value = n; load() }
function onSizeChange(s: number) { pageSize.value = s; pageNum.value = 1; load() }

async function loadReferenceData() {
  try {
    const [dsRes, toolRes] = await Promise.all([
      listDatasources() as unknown as Promise<{ data: Array<{ id: number; name: string; datasourceType: string }> }>,
      listTools({ excludeSkill: false }),
    ])
    datasources.value = Array.isArray(dsRes?.data) ? dsRes.data : []
    availableTools.value = toolRes?.data ?? []
  } catch { /* ignore */ }
}

function dsLabel(id: number | null | undefined): string {
  if (!id) return ''
  const ds = datasources.value.find(d => d.id === id)
  return ds ? ds.name : `#${id}`
}

function onDatasourceChange(id: number | null) {
  if (!id) {
    form.engineType = null
    return
  }
  const ds = datasources.value.find(d => d.id === id)
  form.engineType = ds?.datasourceType ?? null
}

function openEdit(row?: AiEvalCaseVO) {
  if (row) {
    Object.assign(form, { ...emptyForm(), ...row })
    loadSubFormsFromCase(row)
  } else {
    Object.assign(form, emptyForm())
    resetSubForms()
  }
  editing.value = true
}

async function save() {
  if (!form.prompt || !form.category) {
    ElMessage.warning(t('common.required')); return
  }
  serializeSubForms()
  saving.value = true
  try {
    if (form.id) await adminEvals.updateCase(form.id, form)
    else await adminEvals.createCase(form)
    editing.value = false
    await load()
    ElMessage.success(t('common.success'))
  } catch { ElMessage.error(t('common.failed')) } finally { saving.value = false }
}

async function remove(row: AiEvalCaseVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    await adminEvals.deleteCase(row.id); await load()
  } catch { /* cancel */ }
}

async function handleRunBatch() {
  try {
    await ElMessageBox.confirm(t('aiAdmin.eval.runBatchConfirm'), t('common.confirm'), { type: 'warning' })
    running.value = true
    const { data } = await adminEvals.runBatch(categoryFilter.value || undefined)
    lastBatch.value = data ?? null
    ElMessage.success(t('common.success'))
  } catch { /* cancel */ } finally { running.value = false }
}

async function viewHistory(row: AiEvalCaseVO) {
  if (!row.id) return
  currentHistoryCaseId.value = row.id
  historyPageNum.value = 1
  await loadHistory()
  historyOpen.value = true
}

async function loadHistory() {
  if (!currentHistoryCaseId.value) return
  const { data } = await adminEvals.caseHistory(currentHistoryCaseId.value, historyPageNum.value, historyPageSize.value)
  historyRows.value = data?.records ?? []
  historyTotal.value = data?.total ?? 0
}

function onHistoryPageChange(n: number) { historyPageNum.value = n; loadHistory() }

function runToolCount(run: AiEvalRunVO): number {
  return parseJsonArray<EvalToolInvocation>(run.toolCallsJson).length
}

/**
 * Score 颜色映射,对应 Spring AI Evaluator 的 0/60/100 三档语义分:
 *   100 = relevancy + fact-checking 全 pass
 *    60 = relevancy pass 但有幻觉(fact-checking fail)
 *     0 = relevancy fail
 *  其它(<60 / 60~99) 当作 warning,理论上不出现
 */
function scoreTagType(score: number | string | null | undefined): 'success' | 'warning' | 'danger' | 'info' {
  if (score == null) return 'info'
  const n = typeof score === 'string' ? Number(score) : score
  if (Number.isNaN(n)) return 'info'
  if (n >= 90) return 'success'
  if (n >= 50) return 'warning'
  return 'danger'
}

function openRunDetail(run: AiEvalRunVO) {
  activeRun.value = run
  runDetailOpen.value = true
}

function formatJson(v: unknown): string {
  if (v == null) return '—'
  try { return JSON.stringify(v, null, 2) } catch { return String(v) }
}

// 模式切换时,若切到 CHAT 则清空工具断言(CHAT 模式不会调工具,留着没意义)
watch(() => form.mode, (m) => {
  if (m === 'CHAT') {
    expForm.mustCallTools = []
    expForm.mustNotCallTools = []
    expForm.minToolCalls = null
    expForm.maxToolCalls = null
  }
})

onMounted(() => {
  load()
  loadReferenceData()
})
</script>

<style scoped lang="scss">
.stats {
  margin-left: var(--r-space-2);
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
}

.ds-label {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
}

.case-form {
  max-height: 70vh;
  overflow-y: auto;
  padding-right: var(--r-space-2);
}

.form-section + .form-section {
  margin-top: var(--r-space-4);
  padding-top: var(--r-space-3);
  border-top: 1px dashed var(--r-border-light);
}

.form-section__title {
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-bold);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--r-text-muted);
  margin-bottom: var(--r-space-3);
}

.form-subsection + .form-subsection {
  margin-top: var(--r-space-3);
  padding-top: var(--r-space-2);
  border-top: 1px solid var(--r-border-light);
}

.form-subsection__title {
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-secondary);
  margin-bottom: var(--r-space-2);
}

.field-hint {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
  margin-top: var(--r-space-1);
}

.run-detail {
  padding: 0 var(--r-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--r-space-4);
}

.run-detail__head {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);
  padding-bottom: var(--r-space-3);
  border-bottom: 1px solid var(--r-border-light);
  font-size: var(--r-font-sm);
}

.run-detail__section h4 {
  margin: 0 0 var(--r-space-2);
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
}

.run-detail__section--fail ul {
  margin: 0;
  padding-left: var(--r-space-5);
  color: var(--r-danger);
  font-size: var(--r-font-sm);
  line-height: var(--r-leading-snug);
}

.final-text {
  background: var(--r-bg-code);
  border-radius: var(--r-radius-sm);
  padding: var(--r-space-3);
  font-size: var(--r-font-sm);
  font-family: var(--r-font-mono);
  line-height: var(--r-leading-normal);
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  margin: 0;
}

.tool-call {
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-sm);
  padding: var(--r-space-2) var(--r-space-3);
  margin-bottom: var(--r-space-2);
}

.tool-call__head {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  font-size: var(--r-font-sm);
}

.tool-call__body summary {
  cursor: pointer;
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  padding: var(--r-space-1) 0;
}

.tool-call__kv {
  margin-top: var(--r-space-1);
}

.tool-call__kv > span {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.tool-call__kv pre {
  background: var(--r-bg-code);
  border-radius: var(--r-radius-sm);
  padding: var(--r-space-2);
  font-size: var(--r-font-xs);
  font-family: var(--r-font-mono);
  margin: var(--r-space-1) 0 0;
  max-height: 200px;
  overflow: auto;
}

.tool-call__kv pre.err {
  color: var(--r-danger);
}

.muted { color: var(--r-text-muted); }
</style>
