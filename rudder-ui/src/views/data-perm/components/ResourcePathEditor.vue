<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Delete } from '@element-plus/icons-vue'
import {
  searchMetaCatalogOptions,
  searchMetaDatabaseOptions,
  searchMetaTableOptions,
  searchMetaColumnOptions,
} from '@/api/datasource'
import {
  pathLevelsOf,
  type DataPermAdapter,
  type DataPermScope,
  type ResourcePathDraft,
  type ResourceLevelKey,
} from '@/api/data-perm'

const props = defineProps<{
  modelValue: ResourcePathDraft
  scope: DataPermScope
  adapter: DataPermAdapter | null
  workspaceId?: number
  /** 可删除(块内多于一条时);为 false 时隐藏删除按钮。 */
  removable?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: ResourcePathDraft): void
  (e: 'remove'): void
}>()

const { t } = useI18n()
const STAR = '*'
const DROPDOWN_LIMIT = 100

const catalogs = ref<string[]>([])
const databases = ref<string[]>([])
const tables = ref<string[]>([])
const columns = ref<string[]>([])
const loading = ref({ catalogs: false, databases: false, tables: false, columns: false })
const totals = ref({ catalogs: 0, databases: 0, tables: 0, columns: 0 })
const keywords = ref({ catalogs: '', databases: '', tables: '', columns: '' })
const seqs = { catalogs: 0, databases: 0, tables: 0, columns: 0 }

function debounced<T extends (...args: any[]) => void>(fn: T, ms = 300): T {
  let timer: ReturnType<typeof setTimeout> | null = null
  return ((...args: any[]) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn(...args), ms)
  }) as T
}

function hasLevel(level: string): boolean {
  return !!props.adapter && props.adapter.resourceLevels.includes(level)
}

const pathLevels = computed<ResourceLevelKey[]>(() => pathLevelsOf(props.adapter?.resourceLevels ?? []))

const FIELD: Record<ResourceLevelKey, keyof ResourcePathDraft> = {
  catalog: 'catalogNames', database: 'databaseNames', table: 'tableNames', column: 'columnNames',
}

function levelArr(lv: ResourceLevelKey): string[] {
  return props.modelValue[FIELD[lv]] ?? []
}
/** 该层恰好选了一个具体值时下级才可枚举。 */
function concrete(lv: ResourceLevelKey): string | null {
  const arr = levelArr(lv)
  return arr.length === 1 && arr[0] !== STAR ? arr[0] : null
}
function isWildcardLevel(lv: ResourceLevelKey): boolean {
  const arr = levelArr(lv)
  return arr.length === 1 && arr[0] === STAR
}
function isMultiLevel(lv: ResourceLevelKey): boolean {
  return levelArr(lv).length > 1
}
/** 任一祖先不是"单一具体值"→ 本层强制全部 *,禁用。 */
function forcedAll(lv: ResourceLevelKey): boolean {
  const idx = pathLevels.value.indexOf(lv)
  for (let i = 0; i < idx; i++) {
    if (concrete(pathLevels.value[i]) === null) return true
  }
  return false
}
function deeperLevels(lv: ResourceLevelKey): ResourceLevelKey[] {
  const idx = pathLevels.value.indexOf(lv)
  return pathLevels.value.slice(idx + 1)
}

/** 某层在给定草稿下是否被祖先强制为全部(任一祖先非单一具体值)。 */
function forcedAllIn(draft: ResourcePathDraft, lv: ResourceLevelKey): boolean {
  const idx = pathLevels.value.indexOf(lv)
  for (let i = 0; i < idx; i++) {
    const a = draft[FIELD[pathLevels.value[i]]] ?? []
    if (!(a.length === 1 && a[0] !== STAR)) return true
  }
  return false
}

/**
 * 强制为全部的层显式落 ['*'],而非留空 []。否则存储为空、展示却显示 *,
 * 物化时后端按"适用层为空 → *"展开,导致 My Permissions 展示窄于实际授权。
 */
function normalizeForced(draft: ResourcePathDraft): ResourcePathDraft {
  const out: ResourcePathDraft = { ...draft }
  for (const lv of pathLevels.value) {
    if (forcedAllIn(out, lv)) {
      const arr = out[FIELD[lv]] ?? []
      if (!(arr.length === 1 && arr[0] === STAR)) out[FIELD[lv]] = [STAR]
    }
  }
  return out
}

function setLevel(lv: ResourceLevelKey, raw: string[]) {
  const vals = raw.includes(STAR) ? [STAR] : Array.from(new Set(raw))
  const next: ResourcePathDraft = { ...props.modelValue, [FIELD[lv]]: vals }
  for (const d of deeperLevels(lv)) {
    next[FIELD[d]] = []
  }
  emit('update:modelValue', normalizeForced(next))
}

const cCatalog = computed(() => concrete('catalog'))
const cDatabase = computed(() => concrete('database'))
const cTable = computed(() => concrete('table'))

watch(cCatalog, async (val) => {
  databases.value = []
  tables.value = []
  columns.value = []
  if (hasLevel('catalog') && val) await loadDatabases()
})
watch(cDatabase, async (val) => {
  tables.value = []
  columns.value = []
  if (val) await loadTables()
})
watch(cTable, async (val) => {
  columns.value = []
  if (val && hasLevel('column')) await loadColumns()
})

// adapter 异步就绪后 pathLevels 才确定;此刻把已加载/初始草稿里强制为全部的层补成 ['*']。
// 仅监听 pathLevels(不监听 modelValue),避免与自身 emit 形成回环。
watch(pathLevels, () => {
  const n = normalizeForced(props.modelValue)
  if (JSON.stringify(n) !== JSON.stringify(props.modelValue)) {
    emit('update:modelValue', n)
  }
}, { immediate: true })

onMounted(async () => {
  if (hasLevel('catalog')) {
    await loadCatalogs()
    if (cCatalog.value) await loadDatabases()
  } else {
    await loadDatabases()
  }
  if (cDatabase.value) await loadTables()
  if (hasLevel('column') && cTable.value) await loadColumns()
})

function metaDsId(): number | null {
  return props.scope?.metadataDatasourceId ?? null
}

async function loadCatalogs(keyword = '', offset = 0) {
  const dsId = metaDsId()
  if (!dsId) return
  const mySeq = ++seqs.catalogs
  loading.value.catalogs = true
  try {
    const res: any = await searchMetaCatalogOptions(props.workspaceId, dsId,
      { keyword, offset, limit: DROPDOWN_LIMIT })
    if (mySeq !== seqs.catalogs) return
    const items = (res?.data as string[]) ?? []
    catalogs.value = offset === 0 ? items : [...catalogs.value, ...items]
    totals.value.catalogs = (res?.total as number) ?? 0
  } catch {
    if (mySeq !== seqs.catalogs) return
    if (offset === 0) { catalogs.value = []; totals.value.catalogs = 0 }
  } finally { if (mySeq === seqs.catalogs) loading.value.catalogs = false }
}
async function loadDatabases(keyword = '', offset = 0) {
  const dsId = metaDsId()
  if (!dsId) return
  const mySeq = ++seqs.databases
  loading.value.databases = true
  try {
    const res: any = await searchMetaDatabaseOptions(props.workspaceId, dsId,
      { catalog: cCatalog.value, keyword, offset, limit: DROPDOWN_LIMIT })
    if (mySeq !== seqs.databases) return
    const items = (res?.data as string[]) ?? []
    databases.value = offset === 0 ? items : [...databases.value, ...items]
    totals.value.databases = (res?.total as number) ?? 0
  } catch {
    if (mySeq !== seqs.databases) return
    if (offset === 0) { databases.value = []; totals.value.databases = 0 }
  } finally { if (mySeq === seqs.databases) loading.value.databases = false }
}
async function loadTables(keyword = '', offset = 0) {
  const dsId = metaDsId()
  if (!dsId || !cDatabase.value) return
  const mySeq = ++seqs.tables
  loading.value.tables = true
  try {
    const res: any = await searchMetaTableOptions(
      props.workspaceId, dsId, cDatabase.value,
      { catalog: cCatalog.value, keyword, offset, limit: DROPDOWN_LIMIT })
    if (mySeq !== seqs.tables) return
    const items = ((res?.data as Array<{ name: string }>) ?? []).map(x => x.name)
    tables.value = offset === 0 ? items : [...tables.value, ...items]
    totals.value.tables = (res?.total as number) ?? 0
  } catch {
    if (mySeq !== seqs.tables) return
    if (offset === 0) { tables.value = []; totals.value.tables = 0 }
  } finally { if (mySeq === seqs.tables) loading.value.tables = false }
}
async function loadColumns(keyword = '', offset = 0) {
  const dsId = metaDsId()
  if (!dsId || !cDatabase.value || !cTable.value) return
  const mySeq = ++seqs.columns
  loading.value.columns = true
  try {
    const res: any = await searchMetaColumnOptions(
      props.workspaceId, dsId, cDatabase.value, cTable.value,
      { catalog: cCatalog.value, keyword, offset, limit: DROPDOWN_LIMIT })
    if (mySeq !== seqs.columns) return
    const items = ((res?.data as Array<{ name: string }>) ?? []).map(x => x.name)
    columns.value = offset === 0 ? items : [...columns.value, ...items]
    totals.value.columns = (res?.total as number) ?? 0
  } catch {
    if (mySeq !== seqs.columns) return
    if (offset === 0) { columns.value = []; totals.value.columns = 0 }
  } finally { if (mySeq === seqs.columns) loading.value.columns = false }
}

const doSearchCatalogs = debounced((kw: string) => { loadCatalogs(kw, 0) })
const doSearchDatabases = debounced((kw: string) => { loadDatabases(kw, 0) })
const doSearchTables = debounced((kw: string) => { loadTables(kw, 0) })
const doSearchColumns = debounced((kw: string) => { loadColumns(kw, 0) })
function remoteSearchCatalogs(kw: string) { keywords.value.catalogs = kw; doSearchCatalogs(kw) }
function remoteSearchDatabases(kw: string) { keywords.value.databases = kw; doSearchDatabases(kw) }
function remoteSearchTables(kw: string) { keywords.value.tables = kw; doSearchTables(kw) }
function remoteSearchColumns(kw: string) { keywords.value.columns = kw; doSearchColumns(kw) }

const PERM_DROPDOWN_PREFIX = 'rp-dd-' + Math.random().toString(36).slice(2, 8)
const popperClassFor = (lv: ResourceLevelKey) => `r-stable-dropdown ${PERM_DROPDOWN_PREFIX}-${lv}`
const scrollEls: Record<ResourceLevelKey, HTMLElement | null> = {
  catalog: null, database: null, table: null, column: null,
}
const onScrollHandlers: Record<ResourceLevelKey, () => void> = {
  catalog: () => maybeLoadMore('catalog'),
  database: () => maybeLoadMore('database'),
  table: () => maybeLoadMore('table'),
  column: () => maybeLoadMore('column'),
}
function maybeLoadMore(lv: ResourceLevelKey) {
  const el = scrollEls[lv]
  if (!el || getLevelLoading(lv)) return
  if (el.scrollTop + el.clientHeight < el.scrollHeight - 24) return
  const shown = getLevelOptions(lv).length
  if (shown >= getLevelTotal(lv)) return
  const kw = (keywords.value as Record<string, string>)[lv + 's']
  if (lv === 'catalog')  loadCatalogs(kw, shown)
  else if (lv === 'database') loadDatabases(kw, shown)
  else if (lv === 'table')    loadTables(kw, shown)
  else                        loadColumns(kw, shown)
}
function detachScroll(lv: ResourceLevelKey) {
  scrollEls[lv]?.removeEventListener('scroll', onScrollHandlers[lv])
  scrollEls[lv] = null
}
function onDropdownVisibleFor(lv: ResourceLevelKey, visible: boolean) {
  if (!visible) { detachScroll(lv); return }
  const cls = `${PERM_DROPDOWN_PREFIX}-${lv}`
  let tries = 0
  const tryAttach = () => {
    const wrap = document.querySelector(`.${cls} .el-select-dropdown__wrap`) as HTMLElement | null
    if (wrap) {
      detachScroll(lv)
      scrollEls[lv] = wrap
      wrap.addEventListener('scroll', onScrollHandlers[lv])
      return
    }
    if (++tries < 5) setTimeout(tryAttach, 50)
  }
  nextTick(tryAttach)
}
onUnmounted(() => {
  (['catalog', 'database', 'table', 'column'] as ResourceLevelKey[]).forEach(detachScroll)
})

function getRemoteMethod(lv: ResourceLevelKey) {
  if (lv === 'catalog')  return remoteSearchCatalogs
  if (lv === 'database') return remoteSearchDatabases
  if (lv === 'table')    return remoteSearchTables
  return remoteSearchColumns
}
function getLevelTotal(lv: ResourceLevelKey): number {
  if (lv === 'catalog')  return totals.value.catalogs
  if (lv === 'database') return totals.value.databases
  if (lv === 'table')    return totals.value.tables
  return totals.value.columns
}
function getLevelOptions(lv: ResourceLevelKey): string[] {
  if (lv === 'catalog')  return catalogs.value
  if (lv === 'database') return databases.value
  if (lv === 'table')    return tables.value
  return columns.value
}
function getLevelLoading(lv: ResourceLevelKey): boolean {
  if (lv === 'catalog')  return loading.value.catalogs
  if (lv === 'database') return loading.value.databases
  if (lv === 'table')    return loading.value.tables
  return loading.value.columns
}
</script>

<template>
  <div class="rp-row">
    <div class="rp-row__grid" :style="{ '--cols': pathLevels.length }">
      <div v-for="lv in pathLevels" :key="lv" class="rp-row__field">
        <label class="rp-row__label">{{ t(`dataPerm.apply.${lv}`) }}</label>
        <el-select
          :model-value="forcedAll(lv) ? [STAR] : levelArr(lv)"
          multiple
          :disabled="forcedAll(lv)"
          collapse-tags collapse-tags-tooltip
          :placeholder="t('dataPerm.apply.placeholderHint')"
          filterable allow-create
          remote
          :remote-method="getRemoteMethod(lv)"
          reserve-keyword
          :popper-class="popperClassFor(lv)"
          class="rp-row__pick"
          :class="{
            'is-invalid': !forcedAll(lv) && levelArr(lv).length === 0,
            'is-wildcard': forcedAll(lv) || isWildcardLevel(lv),
          }"
          @update:model-value="v => setLevel(lv, v as string[])"
          @visible-change="(v: boolean) => onDropdownVisibleFor(lv, v)"
        >
          <el-option :value="STAR" :label="t('dataPerm.apply.allValue')" />
          <el-option v-for="x in getLevelOptions(lv)" :key="x" :value="x" :label="x" />
        </el-select>
        <div class="rp-row__hint">
          <template v-if="forcedAll(lv)">{{ t('dataPerm.apply.lockedAll') }}</template>
          <template v-else-if="isMultiLevel(lv)">{{
            t('dataPerm.apply.multiLockHint', { n: levelArr(lv).length })
          }}</template>
          <template v-else-if="getLevelLoading(lv)">{{ t('common.loading') }}</template>
          <template v-else-if="getLevelTotal(lv) > getLevelOptions(lv).length">{{
            t('common.dropdownLoadHint',
              { total: getLevelTotal(lv), shown: getLevelOptions(lv).length })
          }}</template>
        </div>
      </div>
    </div>
    <button v-if="removable" type="button" class="rp-row__close"
      :title="t('common.delete')" @click="emit('remove')">
      <el-icon><Delete /></el-icon>
    </button>
  </div>
</template>

<style scoped lang="scss">
.rp-row {
  display: flex;
  align-items: flex-start;
  gap: var(--r-space-2);
}
.rp-row__grid {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(var(--cols, 4), minmax(0, 1fr));
  gap: var(--r-space-2);
}
.rp-row__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.rp-row__label {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
}
.rp-row__pick { width: 100%; }
.rp-row__pick:deep(.el-select__wrapper) {
  background: var(--r-bg-card);
  box-shadow: 0 0 0 1px var(--r-border-light) inset;
  min-height: 32px;
  transition: box-shadow 0.12s;
}
.rp-row__pick:deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--r-border) inset;
}
.rp-row__pick.is-wildcard:deep(.el-select__wrapper) {
  background: var(--r-accent-bg);
  box-shadow: 0 0 0 1px var(--r-accent-border) inset;
}
.rp-row__pick.is-wildcard:deep(.el-select__selected-item) {
  color: var(--r-accent);
  font-weight: var(--r-weight-semibold);
}
.rp-row__pick.is-invalid:deep(.el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--r-danger) inset;
}
.rp-row__hint {
  margin-top: 4px;
  min-height: calc(var(--r-font-xs) * 1.3);
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: 1.3;
}
.rp-row__close {
  all: unset;
  flex-shrink: 0;
  margin-top: 20px;
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
</style>
