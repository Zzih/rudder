<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getProject, updateProject } from '@/api/workspace'

interface Property {
  prop: string
  direct: 'IN' | 'OUT'
  type: string
  value: string
}

const DATA_TYPES = ['VARCHAR', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DATE', 'TIME', 'TIMESTAMP', 'BOOLEAN', 'LIST', 'FILE']

const { t } = useI18n()
const route = useRoute()
const workspaceId = Number(route.params.workspaceId)
const projectCode = route.params.projectCode as string

const loading = ref(false)
const saving = ref(false)
const projectName = ref('')
const params = ref<Property[]>([])

// Search
const searchName = ref('')
const searchType = ref('')

const filteredParams = computed(() => {
  let list = params.value
  if (searchName.value.trim()) {
    const q = searchName.value.toLowerCase()
    list = list.filter(p => p.prop.toLowerCase().includes(q))
  }
  if (searchType.value) {
    list = list.filter(p => p.type === searchType.value)
  }
  return list
})

// Modal
const modalVisible = ref(false)
const editingIndex = ref(-1)
const form = ref<Property>({ prop: '', direct: 'IN', type: 'VARCHAR', value: '' })

function parseParams(raw: any): Property[] {
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string' && raw.trim()) {
    try { return JSON.parse(raw) } catch { return [] }
  }
  return []
}

async function fetchParams() {
  loading.value = true
  try {
    const { data } = await getProject(workspaceId, projectCode)
    projectName.value = data?.name ?? ''
    params.value = parseParams(data?.params)
  } catch { ElMessage.error(t('common.failed')) }
  finally { loading.value = false }
}

function openCreate() {
  editingIndex.value = -1
  form.value = { prop: '', direct: 'IN', type: 'VARCHAR', value: '' }
  modalVisible.value = true
}

function openEdit(row: Property) {
  const idx = params.value.indexOf(row)
  editingIndex.value = idx
  form.value = { ...row }
  modalVisible.value = true
}

async function handleConfirm() {
  if (!form.value.prop.trim()) {
    ElMessage.warning(t('projectParam.nameRequired'))
    return
  }
  const dup = params.value.findIndex((p, i) => p.prop === form.value.prop.trim() && i !== editingIndex.value)
  if (dup >= 0) {
    ElMessage.warning(t('projectParam.nameDuplicate'))
    return
  }

  const newParams = [...params.value]
  const entry: Property = { ...form.value, prop: form.value.prop.trim() }
  if (editingIndex.value >= 0) {
    newParams[editingIndex.value] = entry
  } else {
    newParams.push(entry)
  }

  saving.value = true
  try {
    await updateProject(workspaceId, projectCode, { name: projectName.value, params: newParams })
    params.value = newParams
    modalVisible.value = false
    ElMessage.success(t('common.success'))
  } catch { ElMessage.error(t('common.failed')) }
  finally { saving.value = false }
}

async function handleDelete(row: Property) {
  const idx = params.value.indexOf(row)
  if (idx < 0) return
  const newParams = [...params.value]
  newParams.splice(idx, 1)
  saving.value = true
  try {
    await updateProject(workspaceId, projectCode, { name: projectName.value, params: newParams })
    params.value = newParams
    ElMessage.success(t('common.success'))
  } catch { ElMessage.error(t('common.failed')) }
  finally { saving.value = false }
}

onMounted(fetchParams)
</script>

<template>
  <div class="pp">
    <!-- Toolbar -->
    <div class="pp-bar">
      <div class="pp-bar__left">
        <el-input v-model="searchName" :placeholder="t('projectParam.searchName')" clearable style="width: 180px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="searchType" :placeholder="t('projectParam.searchType')" clearable style="width: 130px">
          <el-option v-for="dt in DATA_TYPES" :key="dt" :label="dt" :value="dt" />
        </el-select>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>{{ t('projectParam.create') }}
      </el-button>
    </div>

    <!-- Hint -->
    <p class="pp-hint">{{ t('projectParam.hint') }}</p>

    <!-- Table -->
    <div class="pp-table" v-loading="loading">
      <table v-if="filteredParams.length || loading">
        <thead>
          <tr>
            <th class="pp-th" style="width: 48px; text-align: center">#</th>
            <th class="pp-th" style="min-width: 140px">{{ t('projectParam.name') }}</th>
            <th class="pp-th" style="min-width: 180px">{{ t('projectParam.value') }}</th>
            <th class="pp-th" style="width: 120px; text-align: center">{{ t('projectParam.type') }}</th>
            <th class="pp-th" style="width: 80px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, idx) in filteredParams" :key="row.prop" class="pp-row">
            <td class="pp-td" style="text-align: center; color: var(--r-text-disabled); font-size: 12px">{{ idx + 1 }}</td>
            <td class="pp-td">
              <code class="pp-code">{{ row.prop }}</code>
            </td>
            <td class="pp-td">
              <span class="pp-val">{{ row.value || '-' }}</span>
            </td>
            <td class="pp-td" style="text-align: center">
              <span class="pp-type">{{ row.type }}</span>
            </td>
            <td class="pp-td pp-td--actions">
              <button class="pp-act-btn" @click="openEdit(row)" :title="t('common.edit')">
                <el-icon size="14"><Edit /></el-icon>
              </button>
              <el-popconfirm :title="t('projectParam.deleteConfirm')" @confirm="handleDelete(row)">
                <template #reference>
                  <button class="pp-act-btn pp-act-btn--danger" :title="t('common.delete')">
                    <el-icon size="14"><Delete /></el-icon>
                  </button>
                </template>
              </el-popconfirm>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Empty -->
      <div v-if="!loading && filteredParams.length === 0" class="pp-empty">
        <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
          <rect x="10" y="12" width="28" height="24" rx="4" stroke="var(--r-border-dark)" stroke-width="1.4" fill="none"/>
          <path d="M18 21h6M18 26h10" stroke="var(--r-text-disabled)" stroke-width="1.4" stroke-linecap="round"/>
          <rect x="30" y="18" width="8" height="8" rx="2" fill="var(--r-accent-bg)" stroke="var(--r-accent)" stroke-width="1.1"/>
          <path d="M33 21h2M34 20v2" stroke="var(--r-accent)" stroke-width="1.1" stroke-linecap="round"/>
        </svg>
        <p>{{ t('projectParam.empty') }}</p>
      </div>
    </div>

    <!-- Create / Edit Modal -->
    <el-dialog
      v-model="modalVisible"
      :title="editingIndex >= 0 ? t('projectParam.edit') : t('projectParam.create')"
      width="440px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item :label="t('projectParam.name')" required>
          <el-input v-model="form.prop" :placeholder="t('projectParam.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('projectParam.value')">
          <el-input v-model="form.value" :placeholder="t('projectParam.valuePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('projectParam.type')">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="dt in DATA_TYPES" :key="dt" :label="dt" :value="dt" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modalVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" :disabled="!form.prop.trim() || !form.type" @click="handleConfirm">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.pp { padding: 20px 24px; }

/* ── Toolbar ── */
.pp-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  gap: 12px;
}

.pp-bar__left { display: flex; gap: 8px; }

/* ── Hint ── */
.pp-hint {
  margin: 0 0 14px;
  font-size: 12px;
  color: var(--r-text-muted);
  line-height: 1.5;
}

/* ── Table ── */
.pp-table {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 10px;
  overflow: hidden;

  table {
    width: 100%;
    border-collapse: collapse;
  }
}

.pp-th {
  padding: 9px 16px;
  font-size: 11px;
  font-weight: 600;
  color: var(--r-text-muted);
  text-align: left;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--r-border);
  background: var(--r-bg-panel);
  white-space: nowrap;
  user-select: none;
}

.pp-row {
  transition: background 0.12s ease;

  &:nth-child(even) { background: var(--r-bg-panel); }

  &:hover {
    background: var(--r-bg-hover) !important;
    .pp-act-btn { opacity: 1; }
  }

  .pp-td {
    border-bottom: 1px solid var(--r-border-light);
  }
}

.pp-td {
  padding: 10px 16px;
  font-size: 13px;
  vertical-align: middle;
}

.pp-td--actions {
  display: flex;
  align-items: center;
  gap: 2px;
  justify-content: flex-end;
}

/* ── Cells ── */
.pp-code {
  font-family: var(--r-font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--r-text-primary);
  background: var(--r-bg-hover);
  padding: 2px 8px;
  border-radius: 4px;
}

.pp-val {
  color: var(--r-text-secondary);
}

.pp-type {
  font-size: 11px;
  font-weight: 600;
  color: var(--r-text-tertiary);
  background: var(--r-bg-hover);
  padding: 2px 8px;
  border-radius: 4px;
  letter-spacing: 0.02em;
}

/* ── Action buttons ── */
.pp-act-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--r-text-muted);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.12s, background 0.12s, color 0.12s;

  &:hover {
    background: var(--r-bg-active);
    color: var(--r-text-primary);
  }

  &--danger:hover {
    background: var(--r-danger-bg);
    color: var(--r-danger);
  }
}

/* ── Empty ── */
.pp-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 20px;
  gap: 10px;

  p {
    margin: 0;
    font-size: 13px;
    color: var(--r-text-muted);
    text-align: center;
    max-width: 280px;
  }
}
</style>
