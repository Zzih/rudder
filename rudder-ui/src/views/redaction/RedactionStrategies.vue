<template>
  <div class="tab-pane">
    <div class="tab-bar">
      <el-button type="primary" size="small" @click="openEdit()">
        <el-icon><Plus /></el-icon>{{ t('common.create') }}
      </el-button>
      <el-button size="small" :loading="loading" @click="load">
        <el-icon><Refresh /></el-icon>{{ t('common.refresh') }}
      </el-button>
    </div>

    <el-table :data="rows" v-loading="loading" size="small" stripe>
      <el-table-column prop="code" :label="t('redaction.strategy.code')" width="180" />

      <el-table-column prop="name" :label="t('redaction.col.name')" width="160" />
      <el-table-column :label="t('redaction.strategy.executor')" width="160">
        <template #default="{ row }">
          <el-tag size="small" :type="executorColor(row.executorType)">{{ row.executorType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('redaction.strategy.config')" show-overflow-tooltip>
        <template #default="{ row }">{{ configSummary(row) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.status')" width="90">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" size="small" @change="toggleEnabled(row)" />
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button link size="small" type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editing" :title="form.id ? t('common.edit') : t('common.create')" width="640">
      <el-form label-position="top">
        <el-form-item :label="t('redaction.strategy.code')" required>
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="MASK_CUSTOM" />
          <div class="field-hint">{{ t('redaction.strategy.codeHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('redaction.col.name')" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="form.description" />
        </el-form-item>
        <el-form-item :label="t('redaction.strategy.executor')" required>
          <el-select v-model="form.executorType" style="width: 100%">
            <el-option label="REGEX_REPLACE - 正则 + 模板" value="REGEX_REPLACE" />
            <el-option label="PARTIAL - 保留前后,中间遮盖" value="PARTIAL" />
            <el-option label="REPLACE - 整体替换" value="REPLACE" />
            <el-option label="HASH - SHA256 短哈希" value="HASH" />
            <el-option label="REMOVE - 置 null" value="REMOVE" />
          </el-select>
          <div class="field-hint">{{ executorHint(form.executorType) }}</div>
        </el-form-item>

        <!-- REGEX_REPLACE -->
        <template v-if="form.executorType === 'REGEX_REPLACE'">
          <el-form-item label="match_regex" required>
            <el-input v-model="form.matchRegex" placeholder="^(\d{3})\d{4}(\d{4})$" />
          </el-form-item>
          <el-form-item label="replacement (模板,可用 $1 $2)" required>
            <el-input v-model="form.replacement" placeholder="$1****$2" />
          </el-form-item>
        </template>

        <!-- PARTIAL -->
        <template v-if="form.executorType === 'PARTIAL'">
          <div class="inline-fields">
            <el-form-item label="keep_prefix">
              <el-input-number v-model="form.keepPrefix" :min="0" :max="100" />
            </el-form-item>
            <el-form-item label="keep_suffix">
              <el-input-number v-model="form.keepSuffix" :min="0" :max="100" />
            </el-form-item>
            <el-form-item label="mask_char">
              <el-input v-model="form.maskChar" placeholder="*" style="width: 80px" />
            </el-form-item>
          </div>
        </template>

        <!-- REPLACE -->
        <template v-if="form.executorType === 'REPLACE'">
          <el-form-item label="replace_value" required>
            <el-input v-model="form.replaceValue" placeholder="***" />
          </el-form-item>
        </template>

        <!-- HASH -->
        <template v-if="form.executorType === 'HASH'">
          <el-form-item label="hash_length">
            <el-input-number v-model="form.hashLength" :min="4" :max="64" />
            <span class="field-hint">{{ t('redaction.strategy.hashLenHint') }}</span>
          </el-form-item>
        </template>

        <el-form-item>
          <el-switch v-model="form.enabled" :active-text="t('redaction.col.enabled')" />
        </el-form-item>

        <!-- 实时测试区 -->
        <el-divider content-position="left">{{ t('redaction.testZone') }}</el-divider>
        <el-form-item :label="t('redaction.sampleInput')">
          <el-input v-model="testSample" placeholder="13800138000" />
        </el-form-item>
        <el-form-item :label="t('redaction.sampleOutput')">
          <el-input v-model="testOutput" readonly />
        </el-form-item>
        <el-button size="small" :loading="testing" @click="runTest">{{ t('redaction.runTest') }}</el-button>
      </el-form>
      <template #footer>
        <el-button @click="editing = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminRedaction, type RedactionStrategyVO, type RedactionExecutorType } from '@/api/ai'

const { t } = useI18n()

const rows = ref<RedactionStrategyVO[]>([])
const loading = ref(false)
const editing = ref(false)
const saving = ref(false)
const testing = ref(false)
const form = reactive<RedactionStrategyVO>(empty())
const testSample = ref('')
const testOutput = ref('')

function empty(): RedactionStrategyVO {
  return {
    code: '',
    name: '',
    description: '',
    executorType: 'REGEX_REPLACE',
    matchRegex: '',
    replacement: '',
    keepPrefix: 3,
    keepSuffix: 4,
    maskChar: '*',
    replaceValue: '***',
    hashLength: 8,
    enabled: true,
  }
}

function executorColor(type: RedactionExecutorType): 'success' | 'primary' | 'warning' | 'info' | 'danger' {
  switch (type) {
    case 'REGEX_REPLACE': return 'primary'
    case 'PARTIAL': return 'warning'
    case 'REPLACE': return 'info'
    case 'HASH': return 'success'
    case 'REMOVE': return 'danger'
  }
}

function executorHint(type?: RedactionExecutorType): string {
  if (!type) return ''
  return t(`redaction.strategy.executorHint.${type}`)
}

function configSummary(row: RedactionStrategyVO): string {
  switch (row.executorType) {
    case 'REGEX_REPLACE':
      return `${row.matchRegex ?? ''} → ${row.replacement ?? ''}`
    case 'PARTIAL':
      return `prefix=${row.keepPrefix} suffix=${row.keepSuffix} mask=${row.maskChar ?? '*'}`
    case 'REPLACE':
      return `→ ${row.replaceValue ?? ''}`
    case 'HASH':
      return `SHA256[0..${row.hashLength ?? 8}]`
    case 'REMOVE':
      return 'null'
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await adminRedaction.listStrategies()
    rows.value = data ?? []
  } finally {
    loading.value = false
  }
}

function openEdit(row?: RedactionStrategyVO) {
  Object.assign(form, row ? { ...empty(), ...row } : empty())
  testSample.value = ''
  testOutput.value = ''
  editing.value = true
}

async function save() {
  if (!form.code || !form.name) {
    ElMessage.warning(t('common.required'))
    return
  }
  saving.value = true
  try {
    if (form.id) await adminRedaction.updateStrategy(form.id, form)
    else await adminRedaction.createStrategy(form)
    editing.value = false
    await load()
    ElMessage.success(t('common.success'))
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row: RedactionStrategyVO) {
  if (!row.id) return
  try {
    await adminRedaction.updateStrategy(row.id, row)
    ElMessage.success(t('common.success'))
  } catch {
    row.enabled = !row.enabled
    ElMessage.error(t('common.failed'))
  }
}

async function remove(row: RedactionStrategyVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    await adminRedaction.deleteStrategy(row.id)
    await load()
  } catch { /* cancel */ }
}

async function runTest() {
  if (!testSample.value) {
    ElMessage.warning(t('redaction.sampleRequired'))
    return
  }
  testing.value = true
  try {
    const { data } = await adminRedaction.test({ strategy: form, sample: testSample.value })
    testOutput.value = data?.output ?? ''
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.tab-pane { padding: 8px 0; }
.tab-bar { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; }
.field-hint { font-size: 12px; color: var(--r-text-muted); margin-top: 4px; line-height: 1.4; }
.inline-fields { display: flex; gap: 12px; }
</style>
