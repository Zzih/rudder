<template>
  <div class="tab-pane">
    <div class="tab-bar">
      <el-button type="primary" size="small" @click="openEdit()">
        <el-icon><Plus /></el-icon>{{ t('common.create') }}
      </el-button>
      <el-button size="small" :loading="loading" @click="load">
        <el-icon><Refresh /></el-icon>{{ t('common.refresh') }}
      </el-button>
      <el-radio-group v-model="filterType" size="small" class="type-filter">
        <el-radio-button label="ALL">{{ t('common.all') }}</el-radio-button>
        <el-radio-button label="TAG">TAG</el-radio-button>
        <el-radio-button label="COLUMN">COLUMN</el-radio-button>
        <el-radio-button label="TEXT">TEXT</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="filteredRows" v-loading="loading" size="small" stripe>
      <el-table-column :label="t('redaction.col.type')" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTagColor(row.type)">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="name" :label="t('redaction.col.name')" width="180" />
      <el-table-column prop="pattern" :label="t('redaction.col.pattern')" show-overflow-tooltip />
      <el-table-column prop="strategyCode" :label="t('redaction.col.strategy')" width="180" />
      <el-table-column prop="priority" :label="t('redaction.col.priority')" width="80" />
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
        <el-form-item :label="t('redaction.col.name')" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="form.description" />
        </el-form-item>
        <el-form-item :label="t('redaction.col.type')" required>
          <el-radio-group v-model="form.type">
            <el-radio-button label="TAG">TAG</el-radio-button>
            <el-radio-button label="COLUMN">COLUMN</el-radio-button>
            <el-radio-button label="TEXT">TEXT</el-radio-button>
          </el-radio-group>
          <div class="field-hint">{{ typeHint(form.type) }}</div>
        </el-form-item>
        <el-form-item :label="patternLabel(form.type)" required>
          <el-input v-model="form.pattern" type="textarea" :rows="2" :placeholder="patternPlaceholder(form.type)" />
        </el-form-item>
        <el-form-item :label="t('redaction.col.strategy')" required>
          <el-select v-model="form.strategyCode" filterable style="width: 100%">
            <el-option
              v-for="s in strategyOptions"
              :key="s.code"
              :label="`${s.name} (${s.code})`"
              :value="s.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('redaction.col.priority')">
          <el-input-number v-model="form.priority" :min="1" />
          <span class="field-hint">{{ t('redaction.priorityHint') }}</span>
        </el-form-item>
        <el-form-item>
          <el-switch v-model="form.enabled" :active-text="t('redaction.col.enabled')" />
        </el-form-item>

        <!-- 实时测试区 -->
        <el-divider content-position="left">{{ t('redaction.testZone') }}</el-divider>
        <el-form-item :label="t('redaction.sampleInput')">
          <el-input v-model="testSample" :placeholder="testPlaceholder(form.type)" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminRedaction, type RedactionRuleVO, type RedactionStrategyVO, type RedactionRuleType } from '@/api/ai'

const { t } = useI18n()

const rows = ref<RedactionRuleVO[]>([])
const strategyOptions = ref<RedactionStrategyVO[]>([])
const loading = ref(false)
const editing = ref(false)
const saving = ref(false)
const testing = ref(false)
const filterType = ref<'ALL' | RedactionRuleType>('ALL')
const form = reactive<RedactionRuleVO>(empty())
const testSample = ref('')
const testOutput = ref('')

function empty(): RedactionRuleVO {
  return {
    name: '',
    description: '',
    type: 'COLUMN',
    pattern: '',
    strategyCode: 'MASK_PHONE',
    priority: 100,
    enabled: true,
  }
}

const filteredRows = computed(() =>
  filterType.value === 'ALL' ? rows.value : rows.value.filter(r => r.type === filterType.value),
)

function typeTagColor(type: RedactionRuleType): 'danger' | 'warning' | 'success' | 'info' {
  return type === 'TAG' ? 'success' : type === 'COLUMN' ? 'warning' : 'info'
}

function typeHint(type: RedactionRuleType): string {
  if (type === 'TAG') return t('redaction.typeHint.tag')
  if (type === 'COLUMN') return t('redaction.typeHint.column')
  return t('redaction.typeHint.text')
}

function patternLabel(type: RedactionRuleType): string {
  if (type === 'TAG') return t('redaction.pattern.tag')
  if (type === 'COLUMN') return t('redaction.pattern.column')
  return t('redaction.pattern.text')
}

function patternPlaceholder(type: RedactionRuleType): string {
  if (type === 'TAG') return 'PII.Phone 或 PII.Contact.* 或 /^PII\\..+/'
  if (type === 'COLUMN') return '^(phone|mobile|tel)$'
  return '\\b1[3-9]\\d{9}\\b'
}

function testPlaceholder(type: RedactionRuleType): string {
  if (type === 'COLUMN') return '13800138000'
  if (type === 'TAG') return '13800138000'
  return '联系 13800138000 或者 test@x.com'
}

async function load() {
  loading.value = true
  try {
    const [{ data: r }, { data: s }] = await Promise.all([
      adminRedaction.listRules(),
      adminRedaction.listStrategies(),
    ])
    rows.value = r ?? []
    strategyOptions.value = (s ?? []).filter(x => x.enabled !== false)
  } finally {
    loading.value = false
  }
}

function openEdit(row?: RedactionRuleVO) {
  Object.assign(form, row ? { ...row } : empty())
  testSample.value = ''
  testOutput.value = ''
  editing.value = true
}

async function save() {
  if (!form.name || !form.pattern || !form.strategyCode) {
    ElMessage.warning(t('common.required'))
    return
  }
  saving.value = true
  try {
    if (form.id) await adminRedaction.updateRule(form.id, form)
    else await adminRedaction.createRule(form)
    editing.value = false
    await load()
    ElMessage.success(t('common.success'))
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row: RedactionRuleVO) {
  if (!row.id) return
  try {
    await adminRedaction.updateRule(row.id, row)
    ElMessage.success(t('common.success'))
  } catch {
    row.enabled = !row.enabled
    ElMessage.error(t('common.failed'))
  }
}

async function remove(row: RedactionRuleVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    await adminRedaction.deleteRule(row.id)
    await load()
  } catch { /* cancel */ }
}

async function runTest() {
  if (!testSample.value) {
    ElMessage.warning(t('redaction.sampleRequired'))
    return
  }
  const strategy = strategyOptions.value.find(s => s.code === form.strategyCode)
  if (!strategy) {
    ElMessage.warning(t('redaction.strategyNotFound'))
    return
  }
  testing.value = true
  try {
    const { data } = await adminRedaction.test({
      strategy,
      rulePattern: form.type === 'TEXT' ? form.pattern : null,
      sample: testSample.value,
    })
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
.tab-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  .type-filter { margin-left: auto; }
}
.field-hint {
  font-size: 12px;
  color: var(--r-text-muted);
  margin-top: 4px;
  line-height: 1.4;
}
</style>
