<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus, Connection, MoreFilled } from '@element-plus/icons-vue'
import {
  listDatasources,
  createDatasource,
  updateDatasource,
  deleteDatasource,
  testConnection,
} from '@/api/datasource'

interface DatasourceRow {
  id: number
  name: string
  datasourceType: string
  host: string
  port: number
  defaultPath?: string
}

const { t } = useI18n()
const loading = ref(false)
const datasources = ref<DatasourceRow[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const testingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const searchText = ref('')
const form = reactive({
  id: null as number | null,
  name: '',
  datasourceType: 'MySQL',
  host: '',
  port: 3306,
  defaultPath: '',
  username: '',
  password: '',
})

const rules: FormRules = {
  name: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  datasourceType: [{ required: true, message: t('common.required'), trigger: 'change' }],
  host: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  port: [{ required: true, message: t('common.required'), trigger: 'blur' }],
}

const dbTypes = ['MySQL', 'Hive', 'StarRocks', 'Trino', 'Spark', 'Flink']

const defaultPorts: Record<string, number> = {
  MySQL: 3306,
  Hive: 10000,
  StarRocks: 9030,
  Trino: 8443,
  Spark: 10000,
  Flink: 8081,
}

const typeColors: Record<string, string> = {
  MySQL: '#4479A1',
  Hive: '#FDEE21',
  StarRocks: '#5B8FF9',
  Trino: '#DD00A1',
  Spark: '#E25A1C',
  Flink: '#E6526F',
}

function getTypeColor(type: string): string {
  return typeColors[type] || '#94a3b8'
}

function getTypeIconBg(type: string): string {
  return getTypeColor(type) + '14'
}

function getTypeTextColor(type: string): string {
  return type === 'Hive' ? '#333' : '#fff'
}

const filteredDatasources = computed(() => {
  if (!searchText.value.trim()) return datasources.value
  const q = searchText.value.toLowerCase()
  return datasources.value.filter(
    ds => ds.name.toLowerCase().includes(q) || ds.datasourceType.toLowerCase().includes(q) || ds.host.toLowerCase().includes(q),
  )
})

async function fetchDatasources() {
  loading.value = true
  try {
    const { data } = await listDatasources()
    datasources.value = data ?? []
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    id: null, name: '', datasourceType: 'MySQL',
    host: '', port: 3306, defaultPath: '', username: '', password: '',
  })
}

function onTypeChange(type: string) {
  if (!isEdit.value && defaultPorts[type]) {
    form.port = defaultPorts[type]
  }
}

function openCreateDialog() {
  resetForm()
  isEdit.value = false
  dialogVisible.value = true
}

function openEditDialog(row: DatasourceRow) {
  resetForm()
  Object.assign(form, row)
  isEdit.value = true
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (form.id) {
      await updateDatasource(undefined, form.id, { ...form })
    } else {
      await createDatasource({ ...form })
    }
    ElMessage.success(t('common.success'))
    dialogVisible.value = false
    await fetchDatasources()
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    submitting.value = false
  }
}

async function handleTestConnection(row: DatasourceRow) {
  testingId.value = row.id
  try {
    await testConnection(undefined, row.id)
    ElMessage.success(t('datasource.testSuccess', { name: row.name }))
  } catch { /* error already shown by request interceptor */ }
  finally {
    testingId.value = null
  }
}

async function handleDelete(row: DatasourceRow) {
  try {
    await ElMessageBox.confirm(
      t('datasource.deleteConfirm', { name: row.name }),
      t('common.confirm'),
      { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') },
    )
    await deleteDatasource(undefined, row.id)
    ElMessage.success(t('common.success'))
    await fetchDatasources()
  } catch { /* cancelled */ }
}

onMounted(fetchDatasources)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h3>{{ t('datasource.title') }}</h3>
      <div class="page-actions">
        <el-input
          v-model="searchText"
          :placeholder="t('common.search')"
          clearable
          :prefix-icon="Search"
          style="width: 200px"
        />
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>{{ t('datasource.create') }}
        </el-button>
      </div>
    </div>

    <!-- Card grid -->
    <div v-loading="loading" class="grid">
      <div v-for="ds in filteredDatasources" :key="ds.id" class="card">
        <div class="card__accent" :style="{ background: getTypeColor(ds.datasourceType) }" />
        <div class="card__inner">
          <div class="card__row1">
            <div class="card__icon" :style="{ background: getTypeIconBg(ds.datasourceType) }">
              <span class="card__icon-letter" :style="{ color: getTypeColor(ds.datasourceType) }">
                {{ ds.datasourceType.charAt(0) }}
              </span>
            </div>
            <el-dropdown trigger="click" @command="(cmd: string) => {
              if (cmd === 'edit') openEditDialog(ds)
              else if (cmd === 'delete') handleDelete(ds)
            }">
              <div class="card__menu">
                <el-icon size="14"><MoreFilled /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">
                    <el-icon><Edit /></el-icon>{{ t('common.edit') }}
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>
                    <span style="color: var(--r-danger)"><el-icon><Delete /></el-icon>{{ t('common.delete') }}</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="card__name">{{ ds.name }}</div>
          <div class="card__info">
            <span class="card__host">{{ ds.host }}:{{ ds.port }}</span>
            <span v-if="ds.defaultPath" class="card__db">/ {{ ds.defaultPath }}</span>
          </div>
          <div class="card__footer">
            <span class="card__type-badge"
              :style="{ background: getTypeColor(ds.datasourceType), color: getTypeTextColor(ds.datasourceType) }"
            >{{ ds.datasourceType }}</span>
            <el-button type="primary" text size="small"
              :loading="testingId === ds.id" @click="handleTestConnection(ds)">
              <el-icon v-if="testingId !== ds.id"><Connection /></el-icon>
              {{ t('datasource.test') }}
            </el-button>
          </div>
        </div>
      </div>
      <div class="card card--new" @click="openCreateDialog">
        <div class="card--new__body">
          <div class="card--new__icon"><el-icon size="20"><Plus /></el-icon></div>
          <span class="card--new__label">{{ t('datasource.create') }}</span>
        </div>
      </div>
    </div>

    <!-- Empty -->
    <div v-if="!loading && !filteredDatasources.length" class="empty">
      <el-empty :description="searchText ? t('common.noData') : t('datasource.emptyHint')" />
    </div>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? t('datasource.editTitle') : t('datasource.createTitle')"
      width="520px"
      destroy-on-close
    >
      <el-form
        ref="formRef" :model="form" :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item :label="t('common.name')" prop="name">
          <el-input
            v-model="form.name"
            :disabled="isEdit"
            placeholder="e.g. prod-mysql"
          />
          <div v-if="isEdit" class="field-hint">{{ t('datasource.nameImmutable') }}</div>
        </el-form-item>
        <el-form-item :label="t('common.type')" prop="datasourceType">
          <el-select v-model="form.datasourceType" style="width: 100%" @change="onTypeChange">
            <el-option v-for="tp in dbTypes" :key="tp" :label="tp" :value="tp" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('datasource.host')" prop="host">
          <el-input v-model="form.host" placeholder="e.g. 10.0.0.1" />
        </el-form-item>
        <el-form-item :label="t('datasource.port')" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('datasource.database')">
          <el-input v-model="form.defaultPath" placeholder="default" />
        </el-form-item>
        <el-form-item :label="t('datasource.username')">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item :label="t('datasource.password')">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/admin.scss';

/* ===== Grid ===== */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.empty { margin-top: 60px; }

/* ===== Card ===== */
.card {
  position: relative;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 10px;
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;

  &:hover {
    border-color: var(--r-border-dark);
    box-shadow: 0 6px 16px rgb(0 0 0 / 6%);
    transform: translateY(-2px);
    .card__menu { opacity: 1; }
  }
}

.card__accent { height: 3px; }
.card__inner { padding: 16px 18px 14px; }

.card__row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.card__icon {
  width: 36px; height: 36px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
}

.card__icon-letter {
  font-size: 16px; font-weight: 600;
}

.card__menu {
  opacity: 0; padding: 4px 6px; border-radius: 6px; color: var(--r-text-muted);
  cursor: pointer;
  transition: opacity 0.15s, background 0.15s;
  &:hover { background: var(--r-bg-hover); color: var(--r-text-secondary); }
}

.card__name {
  font-size: 14px; font-weight: 500; color: var(--r-text-primary);
  margin-bottom: 6px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.card__info {
  font-size: 12px; color: var(--r-text-muted); line-height: 1.6;
  height: 38px; overflow: hidden;
  font-family: var(--r-font-mono);
}

.card__db { color: var(--r-text-secondary); }

.card__footer {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 14px; padding-top: 10px; border-top: 1px solid var(--r-bg-hover);
}

.card__type-badge {
  display: inline-block;
  padding: 1px 8px; border-radius: 4px;
  font-size: 11px; font-weight: 600; letter-spacing: 0.3px;
}

/* New datasource card */
.card--new {
  border-style: dashed; border-color: var(--r-border-dark);
  display: flex; align-items: center; justify-content: center;
  min-height: 170px; cursor: pointer;

  &:hover {
    border-color: var(--r-accent);
    transform: translateY(-2px);
    .card--new__icon { background: var(--r-accent-bg); color: var(--r-accent); }
    .card--new__label { color: var(--r-accent); }
  }
}
.card--new__body { display: flex; flex-direction: column; align-items: center; gap: 10px; }
.card--new__icon {
  width: 44px; height: 44px; border-radius: 50%;
  background: var(--r-bg-hover); color: var(--r-text-muted);
  display: flex; align-items: center; justify-content: center;
  transition: all 0.2s;
}
.card--new__label { font-size: 13px; color: var(--r-text-muted); transition: color 0.2s; }

.field-hint {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
  margin-top: var(--r-space-1);
}
</style>
