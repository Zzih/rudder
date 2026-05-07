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
      :props="treeProps"
      node-key="key"
      lazy
      :load="loadNode"
      @node-click="handleNodeClick"
    >
      <template #default="{ data }">
        <span class="meta-node" :class="{ 'meta-node--pk': data.isPrimaryKey }">
          <el-icon class="meta-node__icon">
            <Files v-if="data.nodeType === 'catalog'" />
            <Coin v-else-if="data.nodeType === 'database'" />
            <Grid v-else-if="data.nodeType === 'table'" />
            <Key v-else-if="data.isPrimaryKey" />
            <Tickets v-else />
          </el-icon>
          <el-tooltip v-if="data.comment" :content="data.comment" placement="right" :show-after="500">
            <span class="meta-node__label">{{ data.label }}</span>
          </el-tooltip>
          <span v-else class="meta-node__label">{{ data.label }}</span>
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
import { listMetaCatalogs, listMetaDatabases, listMetaTables, listMetaColumns, refreshMetaCache, searchMetaTables, type TableSearchResult } from '@/api/datasource'
import { useDatasourceStore } from '@/stores/datasource'
import { useWorkspaceStore } from '@/stores/workspace'
import { pinnedTables as pinnedApi, type AiPinnedTableVO } from '@/api/ai'

interface MetaNode {
  key: string
  label: string
  nodeType: 'catalog' | 'database' | 'table' | 'column'
  isLeaf: boolean
  isPrimaryKey?: boolean
  colType?: string
  comment?: string
  tableName?: string
  database?: string
  /** 三层引擎;两层引擎为 undefined。 */
  catalog?: string
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
const treeProps = { label: 'label', children: 'children', isLeaf: 'isLeaf' }

const currentDatasource = computed(() => {
  const activeTab = ideState.tabs.find((t: any) => t.id === ideState.activeTabId)
  if (!activeTab?.datasourceId) return null
  return datasourceStore.datasources.find((ds) => ds.id === activeTab.datasourceId) ?? null
})

// Force tree rebuild when datasource changes
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
    treeKey.value++ // force tree reload
  } catch { /* ignore */ } finally {
    refreshing.value = false
  }
}

async function loadNode(node: any, resolve: (data: MetaNode[]) => void) {
  const ds = currentDatasource.value
  if (!ds) { resolve([]); return }

  // Root level → 三层引擎先拉 catalog,两层直接拉 database
  if (node.level === 0) {
    if (hasCatalog(ds.datasourceType)) {
      try {
        const { data } = await listMetaCatalogs(workspaceId.value!, ds.id)
        const cats = data ?? []
        if (cats.length > 0) {
          resolve(cats.map((cat: string) => ({
            key: `cat-${cat}`,
            label: cat,
            nodeType: 'catalog' as const,
            isLeaf: false,
            catalog: cat,
          })))
          return
        }
        // 三层声明但驱动返回空:回退成两层
      } catch { /* fall through to databases */ }
    }
    try {
      const { data } = await listMetaDatabases(workspaceId.value!, ds.id)
      resolve((data ?? []).map((dbName: string) => ({
        key: `db-${dbName}`,
        label: dbName,
        nodeType: 'database' as const,
        isLeaf: false,
        database: dbName,
      })))
    } catch { resolve([]) }
    return
  }

  const nodeData = node.data as MetaNode

  // Catalog level → load databases under catalog
  if (nodeData.nodeType === 'catalog') {
    try {
      const { data } = await listMetaDatabases(workspaceId.value!, ds.id, nodeData.catalog)
      resolve((data ?? []).map((dbName: string) => ({
        key: `db-${nodeData.catalog}-${dbName}`,
        label: dbName,
        nodeType: 'database' as const,
        isLeaf: false,
        database: dbName,
        catalog: nodeData.catalog,
      })))
    } catch { resolve([]) }
    return
  }

  // Database level → load tables
  if (nodeData.nodeType === 'database') {
    try {
      const { data: tables } = await listMetaTables(workspaceId.value!, ds.id, nodeData.label, nodeData.catalog)
      resolve((tables ?? []).map((t: any) => ({
        key: `table-${nodeData.catalog ?? ''}-${nodeData.label}-${t.name}`,
        label: t.name,
        nodeType: 'table' as const,
        isLeaf: false,
        tableName: t.name,
        database: nodeData.label,
        catalog: nodeData.catalog,
        comment: t.comment || undefined,
      })))
    } catch { resolve([]) }
    return
  }

  // Table level → load columns
  if (nodeData.nodeType === 'table') {
    try {
      const { data: columns } = await listMetaColumns(
        workspaceId.value!, ds.id, nodeData.database!, nodeData.label, nodeData.catalog)
      resolve((columns ?? []).map((c: any) => ({
        key: `col-${nodeData.catalog ?? ''}-${nodeData.database}-${nodeData.label}-${c.name}`,
        label: c.name,
        nodeType: 'column' as const,
        isLeaf: true,
        colType: c.type,
        isPrimaryKey: false,
        comment: c.comment || undefined,
      })))
    } catch { resolve([]) }
    return
  }

  resolve([])
}

function handleNodeClick(data: MetaNode) {
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
    const { data } = await pinnedApi.list('USER', 1, 200)
    pinnedIdMap.value = new Map()
    const refs: string[] = []
    for (const r of (data?.records ?? []) as AiPinnedTableVO[]) {
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
