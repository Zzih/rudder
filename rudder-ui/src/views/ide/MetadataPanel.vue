<template>
  <div class="metadata-panel">
    <div class="metadata-panel__search">
      <el-input v-model="searchQuery" :placeholder="t('ide.metaSearchPlaceholder')" size="small" clearable>
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <div v-if="currentDatasource" class="metadata-panel__ds">
      <el-icon :size="14" style="color: var(--r-accent)"><Connection /></el-icon>
      <span>{{ currentDatasource.name }}</span>
      <el-button text size="small" :loading="refreshing" class="metadata-panel__refresh" @click="handleRefresh">
        <el-icon><Refresh /></el-icon>
      </el-button>
    </div>

    <!-- Search mode: flat results list -->
    <div v-if="currentDatasource && searchActive" class="metadata-panel__results">
      <div v-if="searching" class="metadata-panel__results-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
      <div v-else-if="!searchResults.length" class="metadata-panel__empty">
        {{ t('ide.metaNoMatch') }}
      </div>
      <div
        v-for="r in searchResults" :key="(r.database ?? '') + '.' + r.table"
        class="search-result"
        :title="r.comment ?? ''"
        @click="handleSearchResultClick(r)"
      >
        <el-icon class="search-result__icon"><Grid /></el-icon>
        <span class="search-result__name">
          <span v-if="r.database" class="search-result__db">{{ r.database }}.</span>{{ r.table }}
        </span>
        <span v-if="r.comment" class="search-result__comment">{{ r.comment }}</span>
      </div>
    </div>

    <el-tree
      v-else-if="currentDatasource"
      :key="treeKey"
      ref="treeRef"
      :props="treeProps"
      node-key="key"
      lazy
      :load="loadNode"
      @node-click="handleNodeClick"
    >
      <template #default="{ data }">
        <span
          class="meta-node"
          :class="{ 'meta-node--pk': data.isPrimaryKey, 'meta-node--load-more': data.nodeType === 'loadMore' }"
        >
          <el-icon class="meta-node__icon">
            <Refresh v-if="data.nodeType === 'loadMore'" />
            <Files v-else-if="data.nodeType === 'catalog'" />
            <Coin v-else-if="data.nodeType === 'database'" />
            <Grid v-else-if="data.nodeType === 'table'" />
            <Key v-else-if="data.isPrimaryKey" />
            <Tickets v-else />
          </el-icon>
          <el-tooltip v-if="data.comment" :content="data.comment" placement="right" :show-after="500">
            <span class="meta-node__label">{{ nodeLabel(data) }}</span>
          </el-tooltip>
          <span v-else class="meta-node__label">{{ nodeLabel(data) }}</span>
          <span v-if="data.colType" class="meta-node__type">{{ data.colType }}</span>
          <span v-if="data.comment" class="meta-node__comment">{{ data.comment }}</span>
          <!-- Table: Pin button (AI context) -->
          <el-button
            v-if="data.nodeType === 'table'"
            class="meta-node__action"
            text size="small"
            :title="isPinned(data) ? t('ide.unpinFromAi') : t('ide.pinToAi')"
            @click.stop="togglePin(data)"
          >
            <el-icon :size="13"><Star v-if="!isPinned(data)" /><StarFilled v-else /></el-icon>
          </el-button>
        </span>
      </template>
    </el-tree>

    <div v-else class="metadata-panel__empty">
      {{ t('ide.metaSelectDs') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, inject, computed, watch, onUnmounted } from 'vue'
import { IDE_STATE_KEY } from './ideState'
import { useI18n } from 'vue-i18n'
import { Search, Connection, Coin, Grid, Key, Tickets, Refresh, Loading, Star, StarFilled, Files } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { refreshMetaCache, searchMetaCatalogOptions, searchMetaDatabaseOptions, searchMetaTableOptions, searchMetaColumnOptions, searchMetaTables, type TableSearchResult } from '@/api/datasource'
import { useDatasourceStore } from '@/stores/datasource'
import { useWorkspaceStore } from '@/stores/workspace'
import { pinnedTables as pinnedApi, type AiPinnedTableVO } from '@/api/ai'

interface MetaNode {
  key: string
  label: string
  nodeType: 'catalog' | 'database' | 'table' | 'column' | 'loadMore'
  isLeaf: boolean
  isPrimaryKey?: boolean
  colType?: string
  comment?: string
  tableName?: string
  database?: string
  /** 三层引擎;两层引擎为 undefined。 */
  catalog?: string
  /** loadMore 节点专用:下一页 offset + 父级类型,点击时复用 */
  offset?: number
  parentLevel?: 'catalog' | 'database' | 'table' | 'column'
  /** loadMore 节点专用:总数,用于模板里实时 t() 渲染 label(避免构造时求值后 locale 切换不刷新) */
  totalCount?: number
}

// 三层引擎:root 先拉 catalog 再拉 database。两层引擎:root 直接拉 database。
// 与后端 io.github.zzih.rudder.dao.enums.DatasourceType.hasCatalog 一致。
const THREE_TIER_TYPES = new Set(['TRINO', 'STARROCKS'])
function hasCatalog(datasourceType: string | undefined | null): boolean {
  return !!datasourceType && THREE_TIER_TYPES.has(datasourceType.toUpperCase())
}

const { t } = useI18n()
const ideState = inject(IDE_STATE_KEY)!
const datasourceStore = useDatasourceStore()
const workspaceStore = useWorkspaceStore()
const workspaceId = computed(() => workspaceStore.currentWorkspace?.id)
const searchQuery = ref('')
const treeKey = ref(0)
const treeRef = ref<any>(null)
const treeProps = { label: 'label', children: 'children', isLeaf: 'isLeaf' }
// tree 节点分页大小,末尾 loadMore 伪节点拉下一页,避免 mount 几千节点
const TREE_PAGE_SIZE = 100
// in-flight loadMore 节点 key 集合,nodeLabel 据此切到"加载中"文案,handleLoadMore 据此防重复点击
const loadingMoreKeys = ref<Set<string>>(new Set())
// 三层引擎驱动 catalog API 异常时标记为两层走 fallback,避免每次 root reload 重复探测
const twoTierFallbackDsIds = new Set<number>()

const currentDatasource = computed(() => {
  const activeTab = ideState.tabs.find((t: any) => t.id === ideState.activeTabId)
  if (!activeTab?.datasourceId) return null
  return datasourceStore.datasources.find((ds) => ds.id === activeTab.datasourceId) ?? null
})

// 切 ds → tree 重建
watch(currentDatasource, () => { treeKey.value++ })

function insertAtCursor(text: string) {
  const editors = (window as any).monaco?.editor?.getEditors?.()
  if (!editors?.length) return
  const ed = editors[0]; const sel = ed.getSelection()
  if (sel) { ed.executeEdits('metadata-panel', [{ range: sel, text }]); ed.focus() }
}

// ========== Server-side table search ==========
const searchResults = ref<TableSearchResult[]>([])
const searching = ref(false)
const searchActive = computed(() => searchQuery.value.trim().length > 0)
let searchDebounce: number | null = null
let searchSeq = 0  // 单调序号,丢弃晚到的过期响应 + unmount 时 bump 作废

watch(searchQuery, (q) => {
  if (searchDebounce !== null) clearTimeout(searchDebounce)
  const trimmed = q.trim()
  if (!trimmed || !currentDatasource.value || !workspaceId.value) {
    searchResults.value = []
    searching.value = false
    return
  }
  searching.value = true
  const seq = ++searchSeq
  searchDebounce = window.setTimeout(async () => {
    // 防过时闭包:触发前再次读取当前 ds/ws;若用户已切换,放弃本次请求
    const ds = currentDatasource.value
    const ws = workspaceId.value
    if (!ds || !ws) { searching.value = false; return }
    try {
      const { data } = await searchMetaTables(ws, ds.id, trimmed)
      if (seq !== searchSeq) return
      searchResults.value = data ?? []
    } catch {
      if (seq === searchSeq) searchResults.value = []
    } finally {
      if (seq === searchSeq) searching.value = false
    }
  }, 250)
})

// 切换数据源时清掉搜索态,避免旧结果闪现
watch(currentDatasource, () => {
  if (searchDebounce !== null) { clearTimeout(searchDebounce); searchDebounce = null }
  searchSeq++
  searchResults.value = []
  searching.value = false
})

onUnmounted(() => {
  if (searchDebounce !== null) { clearTimeout(searchDebounce); searchDebounce = null }
  searchSeq++
})

function handleSearchResultClick(r: TableSearchResult) {
  insertAtCursor(r.table)
}

const refreshing = ref(false)
async function handleRefresh() {
  const ds = currentDatasource.value
  if (!ds) return
  refreshing.value = true
  try {
    await refreshMetaCache(workspaceId.value!, ds.id)
    // 清 fallback memo 让 catalog 重新探测 — 后端权限/同步状态变化后能恢复三层视图
    twoTierFallbackDsIds.delete(ds.id)
    treeKey.value++
  } catch { /* ignore */ } finally {
    refreshing.value = false
  }
}

// ==================== Tree lazy load (database / table / column 分页) ====================
// 每层走 paged search API + 末尾 loadMore 伪节点;点击 loadMore → fetch 下一页 → tree.append + 替换 loadMore

interface PageResp<T> { data?: T[]; total?: number }

async function fetchCatalogsPage(dsId: number, offset: number)
    : Promise<{ items: string[]; total: number }> {
  const res: any = await searchMetaCatalogOptions(workspaceId.value!, dsId,
    { keyword: '', offset, limit: TREE_PAGE_SIZE })
  return { items: ((res as PageResp<string>)?.data ?? []), total: res?.total ?? 0 }
}
async function fetchDatabasesPage(dsId: number, catalog: string | null, offset: number)
    : Promise<{ items: string[]; total: number }> {
  const res: any = await searchMetaDatabaseOptions(workspaceId.value!, dsId,
    { catalog, keyword: '', offset, limit: TREE_PAGE_SIZE })
  return { items: ((res as PageResp<string>)?.data ?? []), total: res?.total ?? 0 }
}
async function fetchTablesPage(dsId: number, catalog: string | undefined, database: string, offset: number)
    : Promise<{ items: { name: string; comment: string }[]; total: number }> {
  const res: any = await searchMetaTableOptions(workspaceId.value!, dsId, database,
    { catalog: catalog ?? null, keyword: '', offset, limit: TREE_PAGE_SIZE })
  return { items: ((res?.data as { name: string; comment: string }[]) ?? []), total: res?.total ?? 0 }
}
async function fetchColumnsPage(dsId: number, catalog: string | undefined, database: string, table: string,
                                offset: number)
    : Promise<{ items: { name: string; type: string; comment: string }[]; total: number }> {
  const res: any = await searchMetaColumnOptions(workspaceId.value!, dsId, database, table,
    { catalog: catalog ?? null, keyword: '', offset, limit: TREE_PAGE_SIZE })
  return { items: ((res?.data as { name: string; type: string; comment: string }[]) ?? []),
           total: res?.total ?? 0 }
}

function buildLoadMoreNode(
  parentLevel: 'catalog' | 'database' | 'table' | 'column',
  keyPrefix: string,
  nextOffset: number,
  total: number,
  catalog?: string,
): MetaNode {
  // label 留空,模板走 nodeLabel(data) 实时 t() 渲染,locale 切换才能刷新文案
  return {
    key: `loadmore-${keyPrefix}-${nextOffset}`,
    label: '',
    nodeType: 'loadMore',
    isLeaf: true,
    offset: nextOffset,
    parentLevel,
    catalog,
    totalCount: total,
  }
}

function nodeLabel(data: MetaNode): string {
  if (data.nodeType === 'loadMore') {
    if (loadingMoreKeys.value.has(data.key)) return t('common.loading')
    if (data.totalCount != null) {
      return t('common.loadMore', { shown: data.offset ?? 0, total: data.totalCount })
    }
  }
  return data.label
}

// items.length===0 时不 push loadMore,否则 offset 不前进会让用户点死循环
function buildCatalogNodes(dsId: number, items: string[], total: number, offset: number): MetaNode[] {
  const nodes: MetaNode[] = items.map(name => ({
    key: `cat-${dsId}-${name}`,
    label: name,
    nodeType: 'catalog' as const,
    isLeaf: false,
    catalog: name,
  }))
  const shown = offset + items.length
  if (items.length > 0 && shown < total) nodes.push(buildLoadMoreNode('catalog', `cat-${dsId}`, shown, total))
  return nodes
}
function buildDatabaseNodes(items: string[], catalog: string | null, total: number, offset: number): MetaNode[] {
  const nodes: MetaNode[] = items.map(name => ({
    key: `db-${catalog ?? ''}-${name}`,
    label: name,
    nodeType: 'database' as const,
    isLeaf: false,
    database: name,
    catalog: catalog ?? undefined,
  }))
  const shown = offset + items.length
  if (items.length > 0 && shown < total) {
    nodes.push(buildLoadMoreNode('database', `db-${catalog ?? ''}`, shown, total, catalog ?? undefined))
  }
  return nodes
}
function buildTableNodes(items: { name: string; comment: string }[], parent: MetaNode,
                         total: number, offset: number): MetaNode[] {
  const nodes: MetaNode[] = items.map(it => ({
    key: `table-${parent.catalog ?? ''}-${parent.label}-${it.name}`,
    label: it.name,
    nodeType: 'table' as const,
    isLeaf: false,
    tableName: it.name,
    database: parent.label,
    catalog: parent.catalog,
    comment: it.comment || undefined,
  }))
  const shown = offset + items.length
  if (items.length > 0 && shown < total) {
    nodes.push(buildLoadMoreNode('table', `tbl-${parent.catalog ?? ''}-${parent.label}`, shown, total, parent.catalog))
  }
  return nodes
}
function buildColumnNodes(items: { name: string; type: string; comment: string }[], parent: MetaNode,
                          total: number, offset: number): MetaNode[] {
  const nodes: MetaNode[] = items.map(c => ({
    key: `col-${parent.catalog ?? ''}-${parent.database}-${parent.label}-${c.name}`,
    label: c.name,
    nodeType: 'column' as const,
    isLeaf: true,
    colType: c.type,
    isPrimaryKey: false,
    comment: c.comment || undefined,
  }))
  const shown = offset + items.length
  if (items.length > 0 && shown < total) {
    nodes.push(buildLoadMoreNode('column',
        `col-${parent.catalog ?? ''}-${parent.database}-${parent.label}`, shown, total, parent.catalog))
  }
  return nodes
}

async function loadNode(node: any, resolve: (data: MetaNode[]) => void) {
  const ds = currentDatasource.value
  if (!ds || workspaceId.value == null) { resolve([]); return }

  if (node.level === 0) {
    // 三层引擎:catalog API 异常曾标记两层 fallback 走过的 ds 直接走两层,省一次 RTT
    const threeTier = hasCatalog(ds.datasourceType) && !twoTierFallbackDsIds.has(ds.id)
    if (threeTier) {
      try {
        const { items, total } = await fetchCatalogsPage(ds.id, 0)
        // 拉到 0 项不 fall-through:三层引擎应当显式 resolve([]),让用户看到空+刷新按钮重试,
        // 否则下面 fetchDatabasesPage(null) 在后端会调 getCatalogs() 把 catalog 名当 database 返回
        resolve(buildCatalogNodes(ds.id, items, total, 0))
        return
      } catch (e) {
        // 真异常才退两层兜底,记忆此 ds 防下次 reload 重复探测
        console.warn('[MetadataPanel] fetch catalogs failed, fallback to two-tier:', e)
        twoTierFallbackDsIds.add(ds.id)
      }
    }
    try {
      const { items, total } = await fetchDatabasesPage(ds.id, null, 0)
      resolve(buildDatabaseNodes(items, null, total, 0))
    } catch { resolve([]) }
    return
  }

  const nodeData = node.data as MetaNode
  if (nodeData.nodeType === 'catalog') {
    try {
      const catalog = nodeData.catalog ?? nodeData.label
      const { items, total } = await fetchDatabasesPage(ds.id, catalog, 0)
      resolve(buildDatabaseNodes(items, catalog, total, 0))
    } catch { resolve([]) }
    return
  }
  if (nodeData.nodeType === 'database') {
    try {
      const { items, total } = await fetchTablesPage(ds.id, nodeData.catalog, nodeData.label, 0)
      resolve(buildTableNodes(items, nodeData, total, 0))
    } catch { resolve([]) }
    return
  }
  if (nodeData.nodeType === 'table') {
    try {
      const { items, total } = await fetchColumnsPage(ds.id, nodeData.catalog, nodeData.database!, nodeData.label, 0)
      resolve(buildColumnNodes(items, nodeData, total, 0))
    } catch { resolve([]) }
    return
  }
  resolve([])
}

async function handleLoadMore(loadMoreData: MetaNode, loadMoreNode: any) {
  const ds = currentDatasource.value
  if (!ds || loadMoreData.offset == null) return
  if (loadingMoreKeys.value.has(loadMoreData.key)) return
  // snapshot 上下文:await 期间若 treeKey++ / ds 切换 / ws 切换,旧 parentKey 在新 store 失效,append 会静默丢数据
  const snapshotTk = treeKey.value
  const snapshotDsId = ds.id
  const snapshotWsId = workspaceId.value
  const stillSameContext = () =>
    snapshotTk === treeKey.value
    && snapshotDsId === currentDatasource.value?.id
    && snapshotWsId === workspaceId.value

  const parentNode = loadMoreNode.parent
  const parentData = (parentNode?.data ?? null) as MetaNode | null
  const offset = loadMoreData.offset
  // 不立即 remove,改为标记 in-flight 让 nodeLabel 显示"加载中",成功后才换真节点
  loadingMoreKeys.value = new Set(loadingMoreKeys.value).add(loadMoreData.key)
  const parentKey = parentData?.key ?? undefined

  try {
    let newNodes: MetaNode[] = []
    if (loadMoreData.parentLevel === 'catalog') {
      const { items, total } = await fetchCatalogsPage(ds.id, offset)
      if (!stillSameContext()) return
      newNodes = buildCatalogNodes(ds.id, items, total, offset)
    } else if (loadMoreData.parentLevel === 'database') {
      const catalog = loadMoreData.catalog ?? null
      const { items, total } = await fetchDatabasesPage(ds.id, catalog, offset)
      if (!stillSameContext()) return
      newNodes = buildDatabaseNodes(items, catalog, total, offset)
    } else if (loadMoreData.parentLevel === 'table' && parentData) {
      const { items, total } = await fetchTablesPage(ds.id, parentData.catalog, parentData.label, offset)
      if (!stillSameContext()) return
      newNodes = buildTableNodes(items, parentData, total, offset)
    } else if (loadMoreData.parentLevel === 'column' && parentData) {
      const { items, total } = await fetchColumnsPage(ds.id, parentData.catalog,
          parentData.database!, parentData.label, offset)
      if (!stillSameContext()) return
      newNodes = buildColumnNodes(items, parentData, total, offset)
    }
    treeRef.value?.remove(loadMoreData)
    // parentKey 为 undefined 时 el-tree 挂到 root
    for (const n of newNodes) {
      treeRef.value?.append(n, parentKey)
    }
  } catch { /* 失败保留 loadMore 节点,用户可重试 */ }
  finally {
    const next = new Set(loadingMoreKeys.value)
    next.delete(loadMoreData.key)
    loadingMoreKeys.value = next
  }
}

function handleNodeClick(data: MetaNode, node: any) {
  if (data.nodeType === 'loadMore') { handleLoadMore(data, node); return }
  if (data.nodeType === 'table' && data.tableName) insertAtCursor(data.tableName)
}

// ==================== Pin to AI ====================
// 持久化到 t_r_ai_pinned_table;ideState.pinnedTables 只是渲染用的缓存。

function tableRef(data: MetaNode): string {
  return `${data.database ?? ''}.${data.tableName ?? data.label}`
}
function isPinned(data: MetaNode): boolean {
  return ideState.pinnedTables.includes(tableRef(data))
}
// key = `${datasourceId}|${db}|${table}` → 记录后端 id,删除时用
const pinnedIdMap = ref<Map<string, number>>(new Map())
const pinnedKey = (dsId: number, db: string, table: string) => `${dsId}|${db}|${table}`

async function loadPinned() {
  try {
    const res: any = await pinnedApi.list('USER', 1, 200)
    pinnedIdMap.value = new Map()
    const refs: string[] = []
    for (const r of ((res.data ?? []) as AiPinnedTableVO[])) {
      if (!r.id || !r.datasourceId || !r.tableName) continue
      pinnedIdMap.value.set(pinnedKey(r.datasourceId, r.databaseName ?? '', r.tableName), r.id)
      const db = r.databaseName ?? ''
      refs.push(db ? `${db}.${r.tableName}` : r.tableName)
    }
    ideState.pinnedTables = refs
  } catch { /* 未登录或 403 */ }
}
onMounted(loadPinned)

async function togglePin(data: MetaNode) {
  const ds = currentDatasource.value
  if (!ds || !data.tableName) return
  const ref = tableRef(data)
  const key = pinnedKey(ds.id, data.database ?? '', data.tableName)
  const existingId = pinnedIdMap.value.get(key)
  try {
    if (existingId) {
      await pinnedApi.unpinById(existingId)
      pinnedIdMap.value.delete(key)
      const idx = ideState.pinnedTables.indexOf(ref)
      if (idx >= 0) ideState.pinnedTables.splice(idx, 1)
    } else {
      const { data: created } = await pinnedApi.pin({
        scope: 'USER',
        datasourceId: ds.id,
        databaseName: data.database ?? null,
        tableName: data.tableName,
      } as any)
      if (created?.id) pinnedIdMap.value.set(key, created.id)
      if (!ideState.pinnedTables.includes(ref)) ideState.pinnedTables.push(ref)
    }
  } catch { ElMessage.error(t('common.failed')) }
}

</script>

<style scoped lang="scss">
@use '@/styles/ide.scss' as *;

.metadata-panel { height: 100%; display: flex; flex-direction: column; background: $ide-panel-bg; }

.metadata-panel__search {
  padding: 8px 10px; flex-shrink: 0; border-bottom: 1px solid $ide-border;
}

.metadata-panel__ds {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 12px; font-size: 12px; color: $ide-text-muted;
  border-bottom: 1px solid $ide-border; flex-shrink: 0;
}
.metadata-panel__refresh { margin-left: auto; padding: 2px; }

:deep(.el-tree) {
  flex: 1; overflow-y: auto; background: transparent; color: $ide-text-secondary;
  --el-tree-node-hover-bg-color: #{$ide-hover-bg};
  --el-tree-node-content-height: 28px;
}

.meta-node { display: flex; align-items: center; gap: 5px; font-size: 12px; }
.meta-node--pk .meta-node__label { color: var(--r-danger); font-weight: 500; }
.meta-node--load-more .meta-node__label {
  color: var(--r-accent); font-style: italic; cursor: pointer;
}
.meta-node__icon { font-size: 13px; color: $ide-text-muted; }
.meta-node__label { color: $ide-text-secondary; }
.meta-node__type { color: var(--r-success); font-size: 11px; margin-left: 4px; }
.meta-node__comment {
  color: $ide-text-disabled; font-size: 11px; margin-left: 4px;
  max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.meta-node__action {
  margin-left: auto; padding: 0 4px; min-height: 18px;
  color: $ide-text-muted;
  &:hover { color: var(--r-accent); }
  .el-icon { vertical-align: middle; }
}
.metadata-panel__empty {
  display: flex; align-items: center; justify-content: center;
  flex: 1; font-size: 12px; color: $ide-text-disabled; padding: 24px; text-align: center;
}

.metadata-panel__results {
  flex: 1; overflow-y: auto; padding: 4px 0;
}
.metadata-panel__results-loading {
  display: flex; align-items: center; justify-content: center;
  padding: 16px; color: $ide-text-muted; font-size: 16px;
}
.search-result {
  display: flex; align-items: center; gap: 6px;
  padding: 5px 12px; font-size: 12px; color: $ide-text-secondary;
  cursor: pointer;
  transition: background 120ms ease, color 120ms ease;
  &:hover { background: $ide-hover-bg; color: $ide-text; }
}
.search-result__icon { font-size: 13px; color: $ide-text-muted; flex-shrink: 0; }
.search-result__name {
  flex-shrink: 0;
  font-family: var(--r-font-mono);
  font-size: 12px;
}
.search-result__db { color: $ide-text-muted; }
.search-result__comment {
  color: $ide-text-disabled; font-size: 11px; margin-left: 6px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
</style>
