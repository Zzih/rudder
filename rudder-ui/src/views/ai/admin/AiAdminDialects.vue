<template>
  <div class="tab-pane">
    <div class="tab-bar">
      <el-button size="small" :loading="loading" @click="load">
        <el-icon><Refresh /></el-icon>{{ t('common.refresh') }}
      </el-button>
      <span class="bar-hint">{{ t('aiAdmin.dialects.hint') }}</span>
    </div>

    <el-table :data="rows" v-loading="loading" size="small" stripe>
      <el-table-column :label="t('aiAdmin.dialects.taskType')" width="180">
        <template #default="{ row }">
          <code class="task-type">{{ row.taskType }}</code>
        </template>
      </el-table-column>
      <el-table-column :label="t('aiAdmin.dialects.label')" prop="label" width="180" />
      <el-table-column :label="t('aiAdmin.dialects.source')" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.overridden" size="small" type="warning">{{ t('aiAdmin.dialects.overridden') }}</el-tag>
          <el-tag v-else size="small" type="info">{{ t('aiAdmin.dialects.default') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('aiAdmin.dialects.preview')" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="preview">{{ firstLine(row.content) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="180">
        <template #default="{ row }">
          <el-button link size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button v-if="row.overridden" link size="small" type="warning" @click="reset(row)">
            {{ t('aiAdmin.dialects.reset') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editing" :title="editTitle" width="780" top="6vh">
      <div v-if="editForm" class="dialect-edit">
        <div class="dialect-edit__meta">
          <code class="task-type">{{ editForm.taskType }}</code>
          <span class="dialect-edit__label">{{ editForm.label }}</span>
          <el-tag v-if="editForm.overridden" size="small" type="warning">{{ t('aiAdmin.dialects.overridden') }}</el-tag>
          <el-tag v-else size="small" type="info">{{ t('aiAdmin.dialects.default') }}</el-tag>
        </div>
        <div class="dialect-edit__hint">{{ t('aiAdmin.dialects.editHint') }}</div>
        <el-input v-model="editForm.content" type="textarea" :rows="22" resize="vertical"
          class="dialect-edit__text" :placeholder="t('aiAdmin.dialects.placeholder')" />
      </div>
      <template #footer>
        <el-button @click="editing = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminDialect, type DialectSlotVO } from '@/api/ai'

const { t } = useI18n()
const rows = ref<DialectSlotVO[]>([])
const loading = ref(false)
const editing = ref(false)
const saving = ref(false)

interface EditForm { taskType: string; label: string; content: string; overridden: boolean }
const editForm = reactive<EditForm>({ taskType: '', label: '', content: '', overridden: false })

const editTitle = computed(() => t('aiAdmin.dialects.editTitle', { taskType: editForm.taskType }))

function firstLine(content: string | null | undefined): string {
  if (!content) return ''
  const line = content.split('\n').find(l => l.trim())
  return line ? line.trim() : ''
}

async function load() {
  loading.value = true
  try {
    const { data } = await adminDialect.list()
    rows.value = data ?? []
  } finally { loading.value = false }
}

function openEdit(row: DialectSlotVO) {
  Object.assign(editForm, { ...row })
  editing.value = true
}

async function save() {
  if (!editForm.content.trim()) { ElMessage.warning(t('common.required')); return }
  saving.value = true
  try {
    await adminDialect.update(editForm.taskType, editForm.content, true)
    editing.value = false
    await load()
    ElMessage.success(t('common.success'))
  } catch { ElMessage.error(t('common.failed')) } finally { saving.value = false }
}

async function reset(row: DialectSlotVO) {
  try {
    await ElMessageBox.confirm(
      t('aiAdmin.dialects.resetConfirm', { taskType: row.taskType }),
      t('common.confirm'),
      { type: 'warning' },
    )
  } catch { return /* user cancelled */ }
  try {
    await adminDialect.reset(row.taskType)
    ElMessage.success(t('common.success'))
    await load()
  } catch { ElMessage.error(t('common.failed')) }
}

onMounted(load)
</script>

<style scoped lang="scss">
.task-type {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
  background: var(--r-bg-panel);
  padding: 1px var(--r-space-2);
  border-radius: 3px;
}
.preview {
  color: var(--r-text-muted);
  font-size: var(--r-font-sm);
}

.dialect-edit {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-3);
}
.dialect-edit__meta {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);
}
.dialect-edit__label {
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
}
.dialect-edit__hint {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
}
.dialect-edit__text :deep(.el-textarea__inner) {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  line-height: var(--r-leading-normal);
}
</style>
