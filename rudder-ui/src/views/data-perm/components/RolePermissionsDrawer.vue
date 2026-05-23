<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Delete, Edit, ArrowRight, Check, Close } from '@element-plus/icons-vue'
import {
  pageRolePermissions,
  addRolePermission,
  updateRolePermission,
  deleteRolePermission,
  listDataPermPluginTypes,
  type DataPermRole,
  type DataPermRolePermissionItem,
  type DataPermAdapter,
  type DataPermScope,
  type PluginType,
  PLUGIN_TYPES,
  DANGEROUS_ACCESS_RE,
} from '@/api/data-perm'
import { useDataPermScopes } from '@/composables/useDataPermScopes'
import PermissionItemEditor from './PermissionItemEditor.vue'

interface PermRow extends DataPermRolePermissionItem {
  id: number
  _localUid: number
}

interface EditBuffer extends DataPermRolePermissionItem {
  /** null = 新增 draft;number = 编辑现有行 */
  id: number | null
  _localUid: number
}

const props = defineProps<{
  modelValue: boolean
  role: DataPermRole
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v),
})

const loading = ref(false)
const rows = ref<PermRow[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const pluginType = ref<PluginType | ''>('')

const { services: allScopes, refresh: refreshScopes } = useDataPermScopes()
const scopes = computed<DataPermScope[]>(() => allScopes.value.filter(s => s.enabled))
const adaptersByPluginType = ref<Record<string, DataPermAdapter>>({})

let _localUidSeq = 1
const buffer = ref<EditBuffer | null>(null)
const bufferSaving = ref(false)

function isEditingRow(row: PermRow): boolean {
  return buffer.value !== null && buffer.value.id === row.id
}
const isDraftBuffer = computed(() => buffer.value !== null && buffer.value.id === null)

watch(visible, (v) => {
  if (v) {
    pageNum.value = 1
    keyword.value = ''
    pluginType.value = ''
    buffer.value = null
    Promise.all([loadStatic(), load()])
  }
}, { immediate: true })

async function loadStatic() {
  const [, adaptersRes] = await Promise.all([
    refreshScopes(),
    listDataPermPluginTypes(),
  ]) as any[]
  const list = (adaptersRes?.data as DataPermAdapter[]) ?? []
  adaptersByPluginType.value = Object.fromEntries(list.map(a => [a.pluginType, a]))
}

async function load() {
  loading.value = true
  try {
    const res: any = await pageRolePermissions(props.role.id!, {
      keyword: keyword.value || undefined,
      pluginType: pluginType.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    const records = ((res?.data ?? []) as Array<DataPermRolePermissionItem & { id: number }>)
    rows.value = records.map(r => ({
      ...r,
      _localUid: _localUidSeq++,
    }))
    total.value = Number(res?.total ?? 0)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNum.value = 1
  load()
}
function onPageChange(p: number) {
  pageNum.value = p
  load()
}
function onPageSizeChange(s: number) {
  pageSize.value = s
  pageNum.value = 1
  load()
}

function svcOf(item: DataPermRolePermissionItem): DataPermScope | undefined {
  return scopes.value.find(s => s.code === item.scopeCode)
}
function pathSegments(item: DataPermRolePermissionItem): string[] {
  return [item.catalogName, item.databaseName, item.tableName, item.columnName]
      .filter((p): p is string => !!p)
}

function addDraft() {
  buffer.value = {
    id: null,
    _localUid: _localUidSeq++,
    scopeCode: 0,
    accesses: [],
  }
  nextTick(() => {
    const el = document.querySelector(`[data-perm-row="draft"]`)
    el?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  })
}

function startEdit(row: PermRow) {
  buffer.value = {
    id: row.id,
    _localUid: row._localUid,
    scopeCode: row.scopeCode,
    catalogName: row.catalogName,
    databaseName: row.databaseName,
    tableName: row.tableName,
    columnName: row.columnName,
    accesses: row.accesses ? [...row.accesses] : [],
  }
}

function cancelEdit() {
  buffer.value = null
}

function updateBuffer(patch: DataPermRolePermissionItem) {
  if (!buffer.value) return
  buffer.value = { ...buffer.value, ...patch }
}

async function saveBuffer() {
  if (!buffer.value) return
  const b = buffer.value
  if (!b.scopeCode) {
    ElMessage.error(t('dataPerm.apply.serviceRequired'))
    return
  }
  const svc = scopes.value.find(s => s.code === b.scopeCode)
  const adapter = svc ? adaptersByPluginType.value[svc.pluginType] : null
  if (adapter) {
    for (const lv of adapter.resourceLevels) {
      const v = lv === 'catalog' ? b.catalogName
        : (lv === 'database' || lv === 'schema') ? b.databaseName
        : lv === 'table' ? b.tableName
        : lv === 'column' ? b.columnName
        : null
      if (!v) {
        ElMessage.error(t('dataPermRole.permLevelRequired', { level: lv }))
        return
      }
    }
  }
  if (!b.accesses?.length) {
    ElMessage.error(t('dataPerm.apply.actionsRequired'))
    return
  }
  const payload: DataPermRolePermissionItem = {
    scopeCode: b.scopeCode,
    catalogName: b.catalogName,
    databaseName: b.databaseName,
    tableName: b.tableName,
    columnName: b.columnName,
    accesses: b.accesses,
  }
  bufferSaving.value = true
  try {
    if (b.id === null) {
      await addRolePermission(props.role.id!, payload)
    } else {
      await updateRolePermission(props.role.id!, b.id, payload)
    }
    buffer.value = null
    ElMessage.success(t('common.success'))
    await load()
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    bufferSaving.value = false
  }
}

async function removeRow(row: PermRow) {
  try {
    await ElMessageBox.confirm(
        t('dataPermRole.permDeleteConfirm'),
        t('common.confirm'), { type: 'warning' })
  } catch { return }
  try {
    await deleteRolePermission(props.role.id!, row.id!)
    ElMessage.success(t('common.success'))
    rows.value = rows.value.filter(r => r._localUid !== row._localUid)
    total.value = Math.max(0, total.value - 1)
  } catch {
    ElMessage.error(t('common.failed'))
  }
}
</script>

<template>
  <el-drawer v-model="visible" size="68%" :show-close="false"
    :close-on-click-modal="false" destroy-on-close
    class="rpd">
    <template #header>
      <div class="rpd__head">
        <div class="rpd__head-text">
          <span class="rpd__kicker">PERMISSIONS</span>
          <h3 class="rpd__title">{{ role.name }}</h3>
          <p v-if="role.description" class="rpd__desc">{{ role.description }}</p>
        </div>
        <button class="rpd__close" @click="visible = false" :aria-label="t('common.close')">
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </template>

    <div v-loading="loading" class="rpd__body">
      <!-- Toolbar -->
      <div class="rpd__toolbar">
        <el-input v-model="keyword"
          :prefix-icon="Search"
          :placeholder="t('dataPermRole.permsSearchPlaceholder')"
          clearable
          class="rpd__search"
          @keyup.enter="onSearch"
          @clear="onSearch" />
        <el-select v-model="pluginType" :placeholder="t('dataPermConfig.svcDialogPluginType')"
          clearable class="rpd__filter" @change="onSearch">
          <el-option v-for="p in PLUGIN_TYPES" :key="p" :label="p" :value="p" />
        </el-select>
        <el-button type="primary" :icon="Plus"
          :disabled="scopes.length === 0 || buffer !== null"
          @click="addDraft">
          {{ t('dataPermRole.addPermission') }}
        </el-button>
      </div>

      <!-- Empty -->
      <div v-if="scopes.length === 0" class="rpd__empty">
        {{ t('dataPermRole.noRangerServiceHint') }}
      </div>
      <div v-else-if="!rows.length && !loading && !isDraftBuffer" class="rpd__empty">
        {{ keyword || pluginType
            ? t('dataPermRole.permsNoMatch')
            : t('dataPermRole.permsEmpty') }}
      </div>

      <!-- List -->
      <ul v-else class="rpd__list">
        <!-- 新增 draft 行(独立顶部位置) -->
        <li v-if="isDraftBuffer"
          data-perm-row="draft"
          class="rpd__row is-editing">
          <div class="rpd__editor">
            <div class="rpd__editor-head">
              <el-icon class="rpd__editor-caret"><ArrowRight /></el-icon>
              <span class="rpd__editor-label">{{ t('dataPermRole.permNewDraft') }}</span>
              <div class="rpd__editor-actions">
                <el-button size="small" :icon="Close" @click="cancelEdit()">
                  {{ t('common.cancel') }}
                </el-button>
                <el-button type="primary" size="small" :icon="Check"
                  :loading="bufferSaving"
                  @click="saveBuffer()">
                  {{ t('common.save') }}
                </el-button>
              </div>
            </div>
            <PermissionItemEditor
              :model-value="buffer!"
              :scopes="scopes"
              :adapters-by-plugin-type="adaptersByPluginType"
              @update:model-value="updateBuffer($event)"
              @remove="cancelEdit()"
            />
          </div>
        </li>

        <li v-for="row in rows" :key="row._localUid"
          :data-perm-row="row._localUid"
          class="rpd__row"
          :class="{ 'is-editing': isEditingRow(row) }">
          <!-- Summary -->
          <div v-if="!isEditingRow(row)" class="rpd__summary"
            :data-plugin="svcOf(row)?.pluginType ?? 'UNKNOWN'">
            <aside class="rpd__plugin-bar">
              <span class="rpd__plugin-name">{{ svcOf(row)?.pluginType ?? '—' }}</span>
            </aside>
            <div class="rpd__main">
              <div class="rpd__main-top">
                <span class="rpd__svc-label">SERVICE</span>
                <span class="rpd__svc-name">
                  {{ svcOf(row)?.name ?? `svc#${row.scopeCode}` }}
                </span>
                <nav v-if="pathSegments(row).length" class="rpd__crumbs">
                  <template v-for="(seg, i) in pathSegments(row)" :key="i">
                    <span v-if="i > 0" class="rpd__crumb-sep" aria-hidden="true">›</span>
                    <span class="rpd__crumb" :class="{ 'is-wildcard': seg === '*' }">{{ seg }}</span>
                  </template>
                </nav>
              </div>
              <div class="rpd__main-bot">
                <span class="rpd__access-label">ACCESS</span>
                <span v-for="a in row.accesses ?? []" :key="a"
                  class="rpd__access-chip"
                  :class="{ 'is-write': DANGEROUS_ACCESS_RE.test(a) }">{{ a }}</span>
              </div>
            </div>
            <div class="rpd__row-actions">
              <el-button text size="small" :icon="Edit" type="primary"
                :disabled="buffer !== null"
                @click="startEdit(row)" />
              <el-button text size="small" :icon="Delete" type="danger"
                :disabled="buffer !== null"
                @click="removeRow(row)" />
            </div>
          </div>

          <!-- Editor(编辑现有行) -->
          <div v-else class="rpd__editor">
            <div class="rpd__editor-head">
              <el-icon class="rpd__editor-caret"><ArrowRight /></el-icon>
              <span class="rpd__editor-label">{{ t('dataPermRole.permEditing') }}</span>
              <div class="rpd__editor-actions">
                <el-button size="small" :icon="Close" @click="cancelEdit()">
                  {{ t('common.cancel') }}
                </el-button>
                <el-button type="primary" size="small" :icon="Check"
                  :loading="bufferSaving"
                  @click="saveBuffer()">
                  {{ t('common.save') }}
                </el-button>
              </div>
            </div>
            <PermissionItemEditor
              :model-value="buffer!"
              :scopes="scopes"
              :adapters-by-plugin-type="adaptersByPluginType"
              @update:model-value="updateBuffer($event)"
              @remove="cancelEdit()"
            />
          </div>
        </li>
      </ul>

      <!-- Pagination -->
      <el-pagination v-if="total > 0"
        class="rpd__pagination"
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        small
        @current-change="onPageChange"
        @size-change="onPageSizeChange" />
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.rpd :deep(.el-drawer__header) {
  margin: 0;
  padding: var(--r-space-4) var(--r-space-6) var(--r-space-3);
  border-bottom: 1px solid var(--r-border-light);
}
.rpd :deep(.el-drawer__body) {
  padding: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

/* ============ HEADER ============ */
.rpd__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--r-space-4);
  width: 100%;
}
.rpd__head-text { min-width: 0; }
.rpd__kicker {
  display: block;
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--r-text-muted);
  margin-bottom: 2px;
}
.rpd__title {
  margin: 0;
  font-size: var(--r-font-lg);
  font-weight: var(--r-weight-bold);
  color: var(--r-text-primary);
  letter-spacing: -0.015em;
}
.rpd__desc {
  margin: 4px 0 0;
  font-size: var(--r-font-sm);
  color: var(--r-text-tertiary);
  line-height: var(--r-leading-snug);
  max-width: 720px;
}
.rpd__close {
  all: unset;
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-radius-sm);
  color: var(--r-text-muted);
  cursor: pointer;
  &:hover { color: var(--r-text-primary); background: var(--r-bg-hover); }
  .el-icon { font-size: 14px; }
}

/* ============ BODY ============ */
.rpd__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: var(--r-space-4) var(--r-space-6) var(--r-space-3);
  overflow-y: auto;
}

/* ============ TOOLBAR ============ */
.rpd__toolbar {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  margin-bottom: var(--r-space-4);
}
.rpd__search { flex: 1; max-width: 360px; }
.rpd__filter { width: 180px; flex-shrink: 0; }

/* ============ EMPTY ============ */
.rpd__empty {
  margin: var(--r-space-5) 0;
  padding: var(--r-space-5) var(--r-space-3);
  background: var(--r-bg-panel);
  border: 1px dashed var(--r-border-light);
  border-radius: var(--r-radius-md);
  color: var(--r-text-muted);
  font-size: var(--r-font-sm);
  text-align: center;
}

/* ============ LIST ============ */
.rpd__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}
.rpd__row {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  transition: border-color 0.15s, box-shadow 0.15s;

  &:hover:not(.is-editing) { border-color: var(--r-border); }
  &.is-editing {
    border-color: var(--r-accent);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-accent) 8%, transparent);
  }
  &.is-saving { opacity: 0.7; pointer-events: none; }
}

/* Summary — grid: [plugin-bar | main | actions] */
.rpd__summary {
  display: grid;
  grid-template-columns: 88px 1fr auto;
  align-items: stretch;
  min-height: 60px;
}
.rpd__plugin-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid var(--r-border-light);
  background: var(--plugin-bg, var(--r-bg-panel));
  color: var(--plugin-fg, var(--r-text-secondary));
}
.rpd__plugin-name {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-bold);
  letter-spacing: 0.10em;
  text-transform: uppercase;
}
.rpd__main {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 10px var(--r-space-3);
  min-width: 0;
}
.rpd__main-top,
.rpd__main-bot {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  row-gap: 4px;
  column-gap: var(--r-space-2);
  min-width: 0;
}
.rpd__svc-label,
.rpd__access-label {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--r-text-muted);
  flex-shrink: 0;
}
.rpd__svc-name {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  flex-shrink: 0;
}
.rpd__crumbs {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  min-width: 0;
  margin-left: var(--r-space-2);
  padding-left: var(--r-space-2);
  border-left: 1px solid var(--r-border-light);
}
.rpd__crumb {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
  &.is-wildcard {
    color: var(--r-text-muted);
    font-style: italic;
  }
}
.rpd__crumb-sep {
  font-family: var(--r-font-mono);
  color: var(--r-text-disabled);
  font-weight: var(--r-weight-bold);
  user-select: none;
}
.rpd__access-chip {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.04em;
  color: var(--r-text-tertiary);
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-sm);
  padding: 1px 8px;
  text-transform: lowercase;
  &.is-write {
    color: var(--r-warning);
    background: var(--r-warning-bg);
    border-color: var(--r-warning-border);
  }
}
.rpd__row-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding-right: var(--r-space-2);
  flex-shrink: 0;
  border-left: 1px solid var(--r-border-light);
  padding-left: var(--r-space-1);
}


/* Editor */
.rpd__editor { background: var(--r-bg-panel); }
.rpd__editor-head {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  padding: 8px 12px;
  border-bottom: 1px dashed var(--r-border-light);
}
.rpd__editor-caret { color: var(--r-accent); font-size: 12px; transform: rotate(90deg); }
.rpd__editor-label {
  flex: 1;
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--r-text-muted);
}
.rpd__editor-actions { display: inline-flex; gap: var(--r-space-2); }
.rpd__editor :deep(.perm-card) {
  border: none;
  border-radius: 0;
}

/* Pagination */
.rpd__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--r-space-4);
  padding-top: var(--r-space-3);
  border-top: 1px solid var(--r-border-light);
}
</style>
