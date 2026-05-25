<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus, Connection, MoreFilled, Select, Close } from '@element-plus/icons-vue'
import {
  listDatasources,
  listDatasourceTypes,
  createDatasource,
  updateDatasource,
  deleteDatasource,
  testConnection,
  listDatasourceWorkspaces,
  setDatasourceWorkspaces,
  type DatasourceWorkspaceGrant,
  type DatasourceTypeMeta,
  type PluginParamDefinition,
} from '@/api/datasource'
import { listWorkspaces } from '@/api/workspace'
import { cardColor } from '@/utils/colorMeta'

interface DatasourceRow {
  id: number
  name: string
  datasourceType: string
  host: string
  port: number
  defaultPath?: string
  params?: string
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
  datasourceType: 'MYSQL',
  host: '',
  port: 3306,
  defaultPath: '',
  username: '',
  password: '',
  params: '',
})

const rules: FormRules = {
  name: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  datasourceType: [{ required: true, message: t('common.required'), trigger: 'change' }],
  host: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  port: [{ required: true, message: t('common.required'), trigger: 'blur' }],
}

const typesMeta = ref<DatasourceTypeMeta[]>([])
const currentTypeMeta = computed(() => typesMeta.value.find(m => m.type === form.datasourceType))
/** 当前 type 的 rawJson PluginParamDefinition(label/defaultValue 作为示例 placeholder)。 */
const rawJsonParam = computed<PluginParamDefinition | null>(
  () => currentTypeMeta.value?.params?.find(p => p.type === 'rawJson') ?? null)

const defaultPorts: Record<string, number> = {
  MYSQL: 3306,
  HIVE: 10000,
  STARROCKS: 9030,
  TRINO: 8443,
  SPARK: 10000,
  FLINK: 8081,
  POSTGRES: 5432,
  CLICKHOUSE: 8123,
  DORIS: 9030,
}

const typeColors: Record<string, string> = {
  MYSQL: '#4479A1',
  HIVE: '#FDEE21',
  STARROCKS: '#5B8FF9',
  TRINO: '#DD00A1',
  SPARK: '#E25A1C',
  FLINK: '#E6526F',
  POSTGRES: '#336791',
  CLICKHOUSE: '#FFCC01',
  DORIS: '#0075FF',
}

function getTypeColor(type: string): string {
  return typeColors[type?.toUpperCase()] || '#94a3b8'
}

function getTypeIconBg(type: string): string {
  return getTypeColor(type) + '14'
}

function getTypeTextColor(type: string): string {
  return type?.toUpperCase() === 'HIVE' ? '#333' : '#fff'
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

async function fetchTypes() {
  // 拉不到走空 — UI 退化为只渲染基础字段,不阻塞页面。
  try {
    const { data } = await listDatasourceTypes()
    typesMeta.value = data ?? []
  } catch { /* swallow */ }
}

let typesLoadPromise: Promise<void> | null = null
function ensureTypesLoaded(): Promise<void> {
  if (typesMeta.value.length) return Promise.resolve()
  if (typesLoadPromise) return typesLoadPromise
  typesLoadPromise = fetchTypes().finally(() => { typesLoadPromise = null })
  return typesLoadPromise
}

/** 把后端存的 JSON 字符串 pretty-print 成 textarea 友好的多行格式;解析失败直接返回原文。 */
function prettyPrintJson(raw: string | undefined): string {
  if (typeof raw !== 'string' || !raw.trim()) return ''
  try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw }
}

function resetForm() {
  Object.assign(form, {
    id: null, name: '', datasourceType: 'MYSQL',
    host: '', port: 3306, defaultPath: '', username: '', password: '',
    params: '',
  })
}

function onTypeChange(type: string) {
  if (isEdit.value) return
  if (defaultPorts[type]) {
    form.port = defaultPorts[type]
  }
  // 切换 type 时清空 params:旧 type 的 JSON schema 不适用新 type(edit 模式 type 锁定,不会触发)
  form.params = ''
}

async function openCreateDialog() {
  await ensureTypesLoaded()
  resetForm()
  isEdit.value = false
  dialogVisible.value = true
}

async function openEditDialog(row: DatasourceRow) {
  await ensureTypesLoaded()
  resetForm()
  Object.assign(form, row)
  form.params = prettyPrintJson(row.params)
  isEdit.value = true
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // params 是 freeform JSON 字符串,提交前预校验 syntax;空串/全空白发送 null
  let paramsPayload: string | null = null
  const raw = form.params?.trim() ?? ''
  if (raw) {
    try {
      JSON.parse(raw)
    } catch (e: any) {
      ElMessage.error(`${t('datasource.invalidJsonHint')}: ${e?.message ?? ''}`)
      return
    }
    paramsPayload = raw
  }

  submitting.value = true
  try {
    const payload = {
      ...form,
      params: paramsPayload,
    }
    if (form.id) {
      await updateDatasource(undefined, form.id, payload)
    } else {
      await createDatasource(payload)
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

// ==================== Workspace 授权 ====================
const grantVisible = ref(false)
const grantTargetDs = ref<DatasourceRow | null>(null)
const grantSubmitting = ref(false)
const grantWorkspaces = ref<{ id: number; name: string }[]>([])
const grantSelected = ref<Set<number>>(new Set())
const grantSearch = ref('')
const grantLoading = ref(false)

const filteredGrantWorkspaces = computed(() => {
  const q = grantSearch.value.trim().toLowerCase()
  if (!q) return grantWorkspaces.value
  return grantWorkspaces.value.filter(w => w.name.toLowerCase().includes(q))
})

const allFilteredSelected = computed(() =>
  filteredGrantWorkspaces.value.length > 0
  && filteredGrantWorkspaces.value.every(w => grantSelected.value.has(w.id)),
)

function toggleGrant(id: number) {
  const next = new Set(grantSelected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  grantSelected.value = next
}

function selectAllFiltered() {
  const next = new Set(grantSelected.value)
  filteredGrantWorkspaces.value.forEach(w => next.add(w.id))
  grantSelected.value = next
}

function clearAllFiltered() {
  const next = new Set(grantSelected.value)
  filteredGrantWorkspaces.value.forEach(w => next.delete(w.id))
  grantSelected.value = next
}

async function openGrantDialog(row: DatasourceRow) {
  grantLoading.value = true
  try {
    const [wsRes, currentRes] = await Promise.all([
      listWorkspaces({ pageNum: 1, pageSize: 500 }) as any,
      listDatasourceWorkspaces(row.id),
    ])
    grantTargetDs.value = row
    grantWorkspaces.value = (wsRes.data ?? []).map((w: any) => ({ id: w.id, name: w.name }))
    grantSelected.value = new Set(
      (currentRes.data ?? []).map((g: DatasourceWorkspaceGrant) => g.workspaceId),
    )
    grantSearch.value = ''
    grantVisible.value = true
  } catch { /* interceptor */ } finally {
    grantLoading.value = false
  }
}

async function handleGrantSubmit() {
  if (!grantTargetDs.value) return
  grantSubmitting.value = true
  try {
    await setDatasourceWorkspaces(grantTargetDs.value.id, Array.from(grantSelected.value))
    ElMessage.success(t('common.success'))
    grantVisible.value = false
  } catch { /* interceptor */ } finally {
    grantSubmitting.value = false
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

onMounted(() => {
  Promise.all([ensureTypesLoaded(), fetchDatasources()])
})
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
              else if (cmd === 'grant') openGrantDialog(ds)
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
                  <el-dropdown-item command="grant">
                    <el-icon><Share /></el-icon>{{ t('datasource.grantWorkspaces') }}
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
            <el-option v-for="m in typesMeta" :key="m.type" :label="m.type" :value="m.type" />
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

        <el-form-item :label="t('datasource.jdbcConnectParameters')">
          <el-input
            v-model="form.params"
            type="textarea"
            :rows="6"
            autocomplete="off"
            spellcheck="false"
            class="dp-params-textarea"
            :placeholder="rawJsonParam?.defaultValue || '{}'"
          />
          <div class="dp-params-hint">{{ t('datasource.jdbcConnectParametersHint') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Grant Workspaces Dialog -->
    <el-dialog
      v-model="grantVisible"
      :title="t('datasource.grantWorkspacesTitle', { name: grantTargetDs?.name ?? '' })"
      width="520px"
      destroy-on-close
    >
      <div class="grant-hint">{{ t('datasource.grantWorkspacesHint') }}</div>

      <div class="grant-toolbar">
        <el-input
          v-model="grantSearch"
          :placeholder="t('common.search')"
          :prefix-icon="Search"
          clearable
          size="default"
          class="grant-search"
        />
        <div class="grant-counter">
          {{ t('datasource.grantSelectedCount', { selected: grantSelected.size, total: grantWorkspaces.length }) }}
        </div>
      </div>

      <div class="grant-bulkbar">
        <button
          type="button"
          class="grant-bulk"
          :disabled="filteredGrantWorkspaces.length === 0 || allFilteredSelected"
          @click="selectAllFiltered"
        >
          <el-icon><Select /></el-icon>{{ t('datasource.grantSelectAll') }}
        </button>
        <button
          type="button"
          class="grant-bulk"
          :disabled="filteredGrantWorkspaces.length === 0"
          @click="clearAllFiltered"
        >
          <el-icon><Close /></el-icon>{{ t('datasource.grantClearAll') }}
        </button>
      </div>

      <div v-loading="grantLoading" class="grant-list">
        <button
          v-for="ws in filteredGrantWorkspaces"
          :key="ws.id"
          type="button"
          class="grant-row"
          :class="{ 'grant-row--active': grantSelected.has(ws.id) }"
          :style="{ '--ws-accent': cardColor(ws.id) }"
          @click="toggleGrant(ws.id)"
        >
          <span class="grant-row__avatar">{{ ws.name.charAt(0).toUpperCase() }}</span>
          <span class="grant-row__name">{{ ws.name }}</span>
          <el-checkbox
            :model-value="grantSelected.has(ws.id)"
            tabindex="-1"
            @click.stop="toggleGrant(ws.id)"
          />
        </button>
        <el-empty
          v-if="!grantLoading && filteredGrantWorkspaces.length === 0"
          :description="grantSearch ? t('common.noData') : t('common.noData')"
          :image-size="60"
          class="grant-empty"
        />
      </div>

      <template #footer>
        <el-button @click="grantVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="grantSubmitting" @click="handleGrantSubmit">
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

.plugin-params-divider {
  margin: var(--r-space-4) 0 var(--r-space-2);
  font-size: var(--r-font-sm);
  font-weight: 600;
  color: var(--r-text-muted);
  border-top: 1px dashed var(--r-border);
  padding-top: var(--r-space-3);
}

.grant-hint {
  font-size: var(--r-font-sm);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
  margin-bottom: var(--r-space-4);
}

.grant-toolbar {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);
  margin-bottom: var(--r-space-2);
}
.grant-search { flex: 1; }
.grant-counter {
  font-size: var(--r-font-sm);
  color: var(--r-text-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.grant-bulkbar {
  display: flex;
  align-items: center;
  gap: var(--r-space-1);
  margin-bottom: var(--r-space-3);
}
.grant-bulk {
  display: inline-flex;
  align-items: center;
  gap: var(--r-space-1);
  padding: var(--r-space-1) var(--r-space-2);
  border: none;
  background: transparent;
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
  cursor: pointer;
  border-radius: var(--r-radius-sm);
  transition: background-color 0.15s, color 0.15s;
  &:hover:not(:disabled) {
    background: var(--r-bg-hover);
    color: var(--r-text-primary);
  }
  &:disabled {
    color: var(--r-text-disabled);
    cursor: not-allowed;
  }
}

.grant-list {
  display: flex;
  flex-direction: column;
  max-height: 320px;
  overflow-y: auto;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-md);
}

.grant-row {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  width: 100%;
  padding: 6px var(--r-space-3);
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  position: relative;
  transition: background-color 0.12s;

  &:not(:last-child)::after {
    content: '';
    position: absolute;
    left: var(--r-space-3);
    right: var(--r-space-3);
    bottom: 0;
    height: 1px;
    background: var(--r-border-light);
  }

  &:hover { background: var(--r-bg-hover); }

  &--active {
    background: color-mix(in srgb, var(--ws-accent, var(--r-accent)) 8%, transparent);
    .grant-row__name { color: var(--r-text-primary); font-weight: var(--r-weight-semibold); }
    &:hover { background: color-mix(in srgb, var(--ws-accent, var(--r-accent)) 12%, transparent); }
  }
}

.grant-row__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  border-radius: var(--r-radius-sm);
  background: color-mix(in srgb, var(--ws-accent, var(--r-accent)) 14%, transparent);
  color: var(--ws-accent, var(--r-accent));
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-bold);
  letter-spacing: -0.02em;
}

.grant-row__name {
  flex: 1;
  min-width: 0;
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grant-empty {
  margin: var(--r-space-4) 0;
}

.dp-params-hint {
  margin-top: var(--r-space-1);
  color: var(--r-text-tertiary);
  font-size: var(--r-font-sm);
  line-height: var(--r-leading-snug);
}

.dp-params-textarea :deep(.el-textarea__inner) {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  line-height: 1.55;
}
</style>
