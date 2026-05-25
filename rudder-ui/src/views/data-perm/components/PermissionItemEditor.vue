<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Delete } from '@element-plus/icons-vue'
import {
  searchMetaCatalogOptions,
  searchMetaDatabaseOptions,
  searchMetaTableOptions,
  searchMetaColumnOptions,
} from '@/api/datasource'
import { DANGEROUS_ACCESS_RE, type DataPermAdapter, type DataPermRolePermissionItem, type DataPermScope } from '@/api/data-perm'

const props = defineProps<{
  modelValue: DataPermRolePermissionItem
  /** 平台登记的数据权限域列表(来自 DataPermConfig.scopes)。 */
  scopes: DataPermScope[]
  /** pluginType → adapter(resourceLevels + accessTypes) 索引。 */
  adaptersByPluginType: Record<string, DataPermAdapter>
  workspaceId?: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: DataPermRolePermissionItem): void
  (e: 'remove'): void
}>()

const { t } = useI18n()
const STAR = '*'
// dropdown 单次返回上限 — 生产 10K+ catalog 时,用户必须用 keyword 缩范围;100 项也够无搜索时浏览
const DROPDOWN_LIMIT = 100

const catalogs = ref<string[]>([])
const databases = ref<string[]>([])
const tables = ref<string[]>([])
const columns = ref<string[]>([])
const loading = ref({ catalogs: false, databases: false, tables: false, columns: false })
// filter 后总数(不是后端全集大小;keyword 变了 total 跟着变)— 给底部 hint 用
const totals = ref({ catalogs: 0, databases: 0, tables: 0, columns: 0 })

/** 简易 debounce:300ms 内重复触发只跑最后一次 — 给 el-select :remote-method 用。 */
function debounced<T extends (...args: any[]) => void>(fn: T, ms = 300): T {
  let timer: ReturnType<typeof setTimeout> | null = null
  return ((...args: any[]) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn(...args), ms)
  }) as T
}

const service = computed<DataPermScope | null>(() =>
  props.scopes.find(s => s.code === props.modelValue.scopeCode) ?? null)

const adapter = computed<DataPermAdapter | null>(() =>
  service.value ? props.adaptersByPluginType[service.value.pluginType] ?? null : null)

function hasLevel(level: string): boolean {
  return !!adapter.value && adapter.value.resourceLevels.includes(level)
}

const accessOptions = computed<string[]>(() => adapter.value?.accessTypes ?? [])

const pathLevels = computed(() => {
  const out: Array<'catalog' | 'database' | 'table' | 'column'> = []
  if (hasLevel('catalog')) out.push('catalog')
  if (hasLevel('database') || hasLevel('schema')) out.push('database')
  if (hasLevel('table')) out.push('table')
  if (hasLevel('column')) out.push('column')
  return out
})

function update(patch: Partial<DataPermRolePermissionItem>) {
  emit('update:modelValue', { ...props.modelValue, ...patch })
}
function isAccessOn(a: string): boolean {
  return (props.modelValue.accesses ?? []).includes(a)
}
function toggleAccess(a: string) {
  const current = props.modelValue.accesses ?? []
  const isOn = current.includes(a)
  // 'all' 覆盖其它所有 access(Ranger 语义),互斥
  if (a === 'all') {
    update({ accesses: isOn ? [] : ['all'] })
    return
  }
  // 选具体 access 时若当前有 'all',先把 'all' 清掉
  const next = isOn
    ? current.filter(x => x !== a)
    : [...current.filter(x => x !== 'all'), a]
  update({ accesses: next })
}
function isWriteLike(a: string): boolean {
  return DANGEROUS_ACCESS_RE.test(a)
}
function toggleAll() {
  // 优先用 'all' 单选语义;无 'all' 选项则全选所有
  if (accessOptions.value.includes('all')) {
    update({ accesses: allOn.value ? [] : ['all'] })
    return
  }
  update({ accesses: allOn.value ? [] : [...accessOptions.value] })
}
const allOn = computed(() => {
  const current = props.modelValue.accesses ?? []
  if (accessOptions.value.includes('all')) {
    return current.length === 1 && current[0] === 'all'
  }
  return accessOptions.value.length > 0 && current.length === accessOptions.value.length
})

watch(() => props.modelValue.scopeCode, async () => {
  catalogs.value = []
  databases.value = []
  tables.value = []
  columns.value = []
  if (!service.value) return
  if (hasLevel('catalog')) {
    await loadCatalogs()
  } else {
    await loadDatabases()
  }
})

watch(() => props.modelValue.catalogName, async (val) => {
  databases.value = []
  tables.value = []
  columns.value = []
  if (service.value && hasLevel('catalog') && val) {
    await loadDatabases()
  }
})

watch(() => props.modelValue.databaseName, async (val) => {
  tables.value = []
  columns.value = []
  if (service.value && val && val !== STAR) {
    await loadTables()
  }
})

watch(() => props.modelValue.tableName, async (val) => {
  columns.value = []
  if (service.value && props.modelValue.databaseName
      && val && val !== STAR && hasLevel('column')) {
    await loadColumns()
  }
})

// 编辑现有行:buffer 已带 service/catalog/db/table,watch 不 fire,主动把 options 拉好
onMounted(async () => {
  if (!service.value) return
  if (hasLevel('catalog')) {
    await loadCatalogs()
    if (props.modelValue.catalogName && props.modelValue.catalogName !== STAR) {
      await loadDatabases()
    }
  } else {
    await loadDatabases()
  }
  if (props.modelValue.databaseName && props.modelValue.databaseName !== STAR) {
    await loadTables()
  }
  if (hasLevel('column') && props.modelValue.tableName && props.modelValue.tableName !== STAR) {
    await loadColumns()
  }
})

function metaDsId(): number | null {
  return service.value?.metadataDatasourceId ?? null
}

async function loadCatalogs(keyword = '') {
  const dsId = metaDsId()
  if (!dsId) return
  loading.value.catalogs = true
  try {
    const res: any = await searchMetaCatalogOptions(props.workspaceId, dsId, { keyword, limit: DROPDOWN_LIMIT })
    catalogs.value = (res?.data as string[]) ?? []
    totals.value.catalogs = (res?.total as number) ?? 0
  } catch { catalogs.value = []; totals.value.catalogs = 0 } finally { loading.value.catalogs = false }
}
async function loadDatabases(keyword = '') {
  const dsId = metaDsId()
  if (!dsId) return
  loading.value.databases = true
  try {
    const cat = props.modelValue.catalogName && props.modelValue.catalogName !== STAR
      ? props.modelValue.catalogName : null
    const res: any = await searchMetaDatabaseOptions(props.workspaceId, dsId, { catalog: cat, keyword, limit: DROPDOWN_LIMIT })
    databases.value = (res?.data as string[]) ?? []
    totals.value.databases = (res?.total as number) ?? 0
  } catch { databases.value = []; totals.value.databases = 0 } finally { loading.value.databases = false }
}
async function loadTables(keyword = '') {
  const dsId = metaDsId()
  if (!dsId) return
  loading.value.tables = true
  try {
    const cat = props.modelValue.catalogName && props.modelValue.catalogName !== STAR
      ? props.modelValue.catalogName : null
    const res: any = await searchMetaTableOptions(
      props.workspaceId, dsId, props.modelValue.databaseName!,
      { catalog: cat, keyword, limit: DROPDOWN_LIMIT })
    tables.value = ((res?.data as Array<{ name: string }>) ?? []).map(x => x.name)
    totals.value.tables = (res?.total as number) ?? 0
  } catch { tables.value = []; totals.value.tables = 0 } finally { loading.value.tables = false }
}
async function loadColumns(keyword = '') {
  const dsId = metaDsId()
  if (!dsId) return
  loading.value.columns = true
  try {
    const cat = props.modelValue.catalogName && props.modelValue.catalogName !== STAR
      ? props.modelValue.catalogName : null
    const res: any = await searchMetaColumnOptions(
      props.workspaceId, dsId, props.modelValue.databaseName!, props.modelValue.tableName!,
      { catalog: cat, keyword, limit: DROPDOWN_LIMIT })
    columns.value = ((res?.data as Array<{ name: string }>) ?? []).map(x => x.name)
    totals.value.columns = (res?.total as number) ?? 0
  } catch { columns.value = []; totals.value.columns = 0 } finally { loading.value.columns = false }
}

// 给 el-select :remote-method 用 — debounce 300ms 后调对应 load
const remoteSearchCatalogs = debounced((kw: string) => { loadCatalogs(kw) })
const remoteSearchDatabases = debounced((kw: string) => { loadDatabases(kw) })
const remoteSearchTables = debounced((kw: string) => { loadTables(kw) })
const remoteSearchColumns = debounced((kw: string) => { loadColumns(kw) })

function getRemoteMethod(level: 'catalog' | 'database' | 'table' | 'column') {
  if (level === 'catalog')  return remoteSearchCatalogs
  if (level === 'database') return remoteSearchDatabases
  if (level === 'table')    return remoteSearchTables
  return remoteSearchColumns
}

function getLevelTotal(level: 'catalog' | 'database' | 'table' | 'column'): number {
  if (level === 'catalog')  return totals.value.catalogs
  if (level === 'database') return totals.value.databases
  if (level === 'table')    return totals.value.tables
  return totals.value.columns
}

function getLevelValue(level: 'catalog' | 'database' | 'table' | 'column') {
  if (level === 'catalog')  return props.modelValue.catalogName
  if (level === 'database') return props.modelValue.databaseName
  if (level === 'table')    return props.modelValue.tableName
  return props.modelValue.columnName
}
function setLevelValue(level: 'catalog' | 'database' | 'table' | 'column', v: string) {
  // 上级为 * 时下级语义上必为 *,cascade 填充避免无效选择
  const cascade = v === STAR ? STAR : undefined
  if (level === 'catalog') {
    update({ catalogName: v, databaseName: cascade, tableName: cascade, columnName: cascade })
  } else if (level === 'database') {
    update({ databaseName: v, tableName: cascade, columnName: cascade })
  } else if (level === 'table') {
    update({ tableName: v, columnName: cascade })
  } else {
    update({ columnName: v })
  }
}
function getLevelOptions(level: 'catalog' | 'database' | 'table' | 'column') {
  if (level === 'catalog')  return catalogs.value
  if (level === 'database') return databases.value
  if (level === 'table')    return tables.value
  return columns.value
}
function getLevelLoading(level: 'catalog' | 'database' | 'table' | 'column') {
  if (level === 'catalog')  return loading.value.catalogs
  if (level === 'database') return loading.value.databases
  if (level === 'table')    return loading.value.tables
  return loading.value.columns
}
</script>

<template>
  <div class="perm-card" :class="{ 'is-empty': !modelValue.scopeCode }">
    <!-- Section 1: Service -->
    <section class="perm-card__section">
      <div class="perm-card__field">
        <label class="perm-card__field-label">
          {{ t('dataPerm.apply.serviceLabel') }}
          <span class="perm-card__required">*</span>
        </label>
        <div class="perm-card__field-row">
          <el-select
            :model-value="modelValue.scopeCode || undefined"
            :placeholder="t('dataPerm.apply.servicePlaceholder')"
            filterable class="perm-card__svc"
            @update:model-value="v => update({
              scopeCode: v as number,
              catalogName: undefined,
              databaseName: undefined,
              tableName: undefined,
              columnName: undefined,
              accesses: [],
            })"
          >
            <el-option v-for="s in scopes" :key="s.code" :value="s.code!" :label="s.name">
              <span class="svc-opt">
                <span class="svc-opt__name">{{ s.name }}</span>
                <el-tag size="small" type="info" effect="plain" round>{{ s.pluginType }}</el-tag>
              </span>
            </el-option>
          </el-select>
          <el-tag v-if="service" size="small" type="info" effect="plain" round class="perm-card__svc-type">
            {{ service.pluginType }}
          </el-tag>
          <button type="button" class="perm-card__close"
            :title="t('common.delete')" @click="emit('remove')">
            <el-icon><Delete /></el-icon>
          </button>
        </div>
      </div>
    </section>

    <!-- Section 2: Resource path -->
    <section v-if="modelValue.scopeCode" class="perm-card__section">
      <div class="perm-card__field">
        <label class="perm-card__field-label">
          {{ t('dataPerm.apply.resourcePath') }}
          <span class="perm-card__required">*</span>
          <span class="perm-card__field-hint">{{ t('dataPerm.apply.resourceHint') }}</span>
        </label>
        <div class="perm-card__grid" :style="{ '--cols': pathLevels.length }">
          <div v-for="lv in pathLevels" :key="lv" class="perm-card__sub-field">
            <label class="perm-card__sub-label">{{ t(`dataPerm.apply.${lv}`) }}</label>
            <el-select
              :model-value="getLevelValue(lv)"
              :placeholder="t('dataPerm.apply.placeholderHint')"
              filterable allow-create
              remote
              :remote-method="getRemoteMethod(lv)"
              reserve-keyword
              :loading="getLevelLoading(lv)"
              class="perm-card__pick"
              :class="{ 'is-invalid': !getLevelValue(lv), 'is-wildcard': getLevelValue(lv) === STAR }"
              @update:model-value="v => setLevelValue(lv, v as string)"
            >
              <el-option :value="STAR" :label="t('dataPerm.apply.allValue')" />
              <el-option v-for="x in getLevelOptions(lv)" :key="x" :value="x" :label="x" />
            </el-select>
            <div v-if="getLevelTotal(lv) > getLevelOptions(lv).length" class="perm-card__pick-hint">
              {{ t('dataPerm.apply.moreResultsHint', { total: getLevelTotal(lv), shown: getLevelOptions(lv).length }) }}
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- empty hint -->
    <section v-if="!modelValue.scopeCode" class="perm-card__empty">
      {{ t('dataPerm.apply.directNoServiceHint') }}
    </section>

    <!-- Section 3: Access -->
    <section v-if="modelValue.scopeCode && accessOptions.length" class="perm-card__section">
      <div class="perm-card__field">
        <label class="perm-card__field-label">
          {{ t('dataPerm.apply.actions') }}
          <span class="perm-card__required">*</span>
          <button type="button" class="perm-card__toggle-all"
            :class="{ 'is-on': allOn }" @click="toggleAll">
            {{ allOn ? t('dataPerm.apply.clearAll') : t('dataPerm.apply.selectAll') }}
          </button>
        </label>
        <div class="perm-card__access">
          <button
            v-for="a in accessOptions" :key="a"
            type="button"
            class="access-chip"
            :class="{ 'is-on': isAccessOn(a), 'is-write': isWriteLike(a) }"
            @click="toggleAccess(a)"
          >{{ a }}</button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.perm-card {
  display: flex;
  flex-direction: column;
  background: var(--r-bg-card);

  &.is-empty { background: var(--r-bg-panel); }

  &__section {
    padding: var(--r-space-3) var(--r-space-4);
    & + & { border-top: 1px solid var(--r-border-light); }
  }

  &__field {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;
  }

  &__field-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: var(--r-font-sm);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
  }
  &__field-hint {
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-normal);
    color: var(--r-text-muted);
  }
  &__field-row {
    display: flex;
    align-items: center;
    gap: var(--r-space-2);
  }
  &__required { color: var(--r-danger); }

  &__sub-field {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }
  &__sub-label {
    font-size: var(--r-font-xs);
    color: var(--r-text-muted);
  }

  &__svc { flex: 1; min-width: 0; }
  &__svc-type { flex-shrink: 0; }

  &__close {
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
    transition: color 0.12s, background 0.12s;

    &:hover { color: var(--r-danger); background: var(--r-danger-bg); }
    .el-icon { font-size: var(--r-font-sm); }
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(var(--cols, 4), minmax(0, 1fr));
    gap: var(--r-space-2);
  }

  &__pick { width: 100%; }
  &__pick:deep(.el-select__wrapper) {
    background: var(--r-bg-card);
    box-shadow: 0 0 0 1px var(--r-border-light) inset;
    min-height: 32px;
    transition: box-shadow 0.12s;
  }
  &__pick:deep(.el-select__wrapper:hover) {
    box-shadow: 0 0 0 1px var(--r-border) inset;
  }
  &__pick.is-wildcard:deep(.el-select__wrapper) {
    background: var(--r-accent-bg);
    box-shadow: 0 0 0 1px var(--r-accent-border) inset;
  }
  &__pick.is-wildcard:deep(.el-select__selected-item) {
    color: var(--r-accent);
    font-weight: var(--r-weight-semibold);
  }
  &__pick.is-invalid:deep(.el-select__wrapper) {
    box-shadow: 0 0 0 1px var(--r-danger) inset;
  }

  &__pick-hint {
    margin-top: 4px;
    font-size: var(--r-font-xs);
    color: var(--r-text-muted);
    line-height: 1.3;
  }

  &__access {
    display: flex;
    flex-wrap: wrap;
    gap: var(--r-space-2);
  }

  &__toggle-all {
    all: unset;
    margin-left: auto;
    cursor: pointer;
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-normal);
    color: var(--r-text-tertiary);
    transition: color 0.12s;

    &:hover { color: var(--r-accent); }
    &.is-on { color: var(--r-accent); }
  }

  &__empty {
    padding: var(--r-space-4);
    text-align: center;
    font-size: var(--r-font-sm);
    color: var(--r-text-muted);
  }
}

/* Service option (in dropdown) */
.svc-opt {
  display: inline-flex;
  align-items: center;
  gap: var(--r-space-2);
}
.svc-opt__name {
  font-weight: var(--r-weight-medium);
  color: var(--r-text-primary);
}

/* Access chip */
.access-chip {
  all: unset;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 var(--r-space-3);
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-sm);
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
  transition: all 0.12s;

  &:hover:not(.is-on) {
    border-color: var(--r-border);
    color: var(--r-text-primary);
    background: var(--r-bg-panel);
  }
  &.is-on {
    background: var(--r-accent-bg);
    border-color: var(--r-accent);
    color: var(--r-accent);
    font-weight: var(--r-weight-semibold);
  }
  &.is-on.is-write {
    background: var(--r-warning-bg);
    border-color: var(--r-warning);
    color: var(--r-warning);
  }
}
</style>
