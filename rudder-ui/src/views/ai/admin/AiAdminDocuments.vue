<template>
  <div class="tab-pane">
    <!-- ==================== 文档列表 ==================== -->
    <section>
      <div class="section-head">
        <h4>{{ t('aiAdmin.doc.title') }}</h4>
        <span class="section-hint">{{ t('aiAdmin.doc.hint') }}</span>
      </div>
      <div class="tab-bar">
        <el-button type="primary" size="small" @click="openEdit()"><el-icon><Plus /></el-icon>{{ t('common.create') }}</el-button>
        <el-upload :show-file-list="false" :before-upload="beforeUpload" :http-request="handleUpload" :accept="UPLOAD_ACCEPT">
          <el-button size="small" :loading="uploading">
            <el-icon><Upload /></el-icon>{{ t('aiAdmin.doc.upload') }}
          </el-button>
        </el-upload>
        <el-button size="small" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>{{ t('common.refresh') }}</el-button>
        <el-select v-model="docTypeFilter" size="small" clearable :placeholder="t('aiAdmin.doc.filterType')" style="width: 160px"
          @change="() => { docPageNum = 1; load() }">
          <el-option v-for="d in DOC_TYPES" :key="d" :label="d" :value="d" />
        </el-select>
        <el-button size="small" :loading="reindexing" @click="handleReindex">
          <el-icon><Refresh /></el-icon>{{ t('aiAdmin.doc.reindex') }}
        </el-button>
        <el-divider direction="vertical" />
        <el-input v-model="searchQuery" size="small" style="width: 220px" :placeholder="t('aiAdmin.doc.searchPreview')" clearable @keyup.enter="handleSearch" />
        <el-button size="small" :loading="searching" @click="handleSearch">{{ t('common.search') }}</el-button>
      </div>

      <el-alert v-if="searchResults.length" :closable="true" type="info" @close="searchResults = []">
        <template #default>
          <div v-for="(r, i) in searchResults" :key="i" class="search-hit">
            <strong>[{{ r.docType }}] {{ r.title }}</strong>
            <span class="search-hit__score">score={{ r.score.toFixed(3) }}</span>
            <div class="search-hit__text">{{ r.chunkText }}</div>
          </div>
        </template>
      </el-alert>

      <el-table :data="rows" v-loading="loading" size="small" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="docType" :label="t('aiAdmin.doc.docType')" width="110" />
        <el-table-column prop="engineType" :label="t('aiAdmin.doc.engine')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.engineType" size="small">{{ row.engineType }}</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" :label="t('aiAdmin.doc.title')" />
        <el-table-column prop="sourceRef" :label="t('aiAdmin.doc.source')" width="220">
          <template #default="{ row }">
            <span v-if="row.sourceRef" class="source-ref">{{ row.sourceRef }}</span>
            <el-tag v-else type="info" size="small">{{ t('aiAdmin.doc.manual') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('aiAdmin.doc.indexed')" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.indexedAt" type="success" size="small">{{ row.indexedAt }}</el-tag>
            <el-tag v-else type="warning" size="small">{{ t('aiAdmin.doc.notIndexed') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="150">
          <template #default="{ row }">
            <el-button link size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link size="small" type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" small background layout="total, prev, pager, next, sizes"
        :total="docTotal" :page-size="docPageSize" :current-page="docPageNum" :page-sizes="[10, 20, 50, 100]"
        @current-change="onDocPageChange" @size-change="onDocSizeChange" />
    </section>

    <el-divider />

    <!-- ==================== 表结构同步 ==================== -->
    <section class="ks-section">
      <div class="section-head">
        <h4>{{ t('aiAdmin.metaSync.title') }}</h4>
        <span class="section-hint">{{ t('aiAdmin.metaSync.hint') }}</span>
        <el-button type="primary" size="small" @click="openSync()">
          <el-icon><Plus /></el-icon>{{ t('aiAdmin.metaSync.addSync') }}
        </el-button>
        <el-button size="small" :loading="loadingSync" @click="loadSync"><el-icon><Refresh /></el-icon></el-button>
      </div>
      <el-table :data="syncRows" v-loading="loadingSync" size="small" stripe>
        <el-table-column :label="t('aiAdmin.metaSync.datasource')" width="260">
          <template #default="{ row }">
            <span>{{ dsLabel(row.datasourceId) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('aiAdmin.metaSync.scheduleCron')" width="160">
          <template #default="{ row }">
            <code v-if="row.scheduleCron" class="cron">{{ row.scheduleCron }}</code>
            <span v-else class="muted">{{ t('aiAdmin.metaSync.manualOnly') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('aiAdmin.metaSync.lastSync')" width="240">
          <template #default="{ row }">
            <div v-if="row.lastSyncAt">
              <el-tag size="small" :type="statusTag(row.lastSyncStatus)">{{ row.lastSyncStatus }}</el-tag>
              <span class="sync-time">{{ row.lastSyncAt }}</span>
            </div>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastSyncMessage" :label="t('aiAdmin.metaSync.lastMessage')" min-width="200">
          <template #default="{ row }">
            <span class="msg">{{ row.lastSyncMessage || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.enabled')" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.enabled" type="success" size="small">ON</el-tag>
            <el-tag v-else type="info" size="small">OFF</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="220">
          <template #default="{ row }">
            <el-button link size="small" :loading="syncingDsId === row.datasourceId" @click="doSync(row)">
              {{ t('aiAdmin.metaSync.syncNow') }}
            </el-button>
            <el-button link size="small" @click="openSync(row)">{{ t('common.edit') }}</el-button>
            <el-button link size="small" type="danger" @click="removeSync(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ==================== 文档编辑弹窗 ==================== -->
    <el-dialog v-model="editing" :title="form.id ? t('common.edit') : t('common.create')" width="720">
      <el-form label-position="top">
        <div class="form-row">
          <el-form-item :label="t('aiAdmin.doc.docType')" required>
            <el-select v-model="form.docType" style="width: 100%">
              <el-option v-for="d in DOC_TYPES" :key="d" :label="d" :value="d" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('aiAdmin.doc.engine')" :required="form.docType === 'SCHEMA'">
            <el-select v-model="form.engineType" clearable style="width: 100%"
              :placeholder="form.docType === 'SCHEMA' ? t('aiAdmin.doc.engineRequired') : t('aiAdmin.doc.engineOptional')">
              <el-option v-for="e in ENGINE_TYPES" :key="e" :label="e" :value="e" />
            </el-select>
            <div class="field-hint">{{ t('aiAdmin.doc.engineHint') }}</div>
          </el-form-item>
        </div>
        <el-form-item :label="t('aiAdmin.doc.scope')" required>
          <el-select v-model="scopeMode" size="default" style="width: 100%; margin-bottom: 8px"
            :disabled="form.docType === 'SCHEMA' || form.docType === 'SCRIPT'">
            <el-option :label="t('aiAdmin.doc.scopeAll')" value="all"
              :disabled="form.docType === 'SCRIPT'" />
            <el-option :label="t('aiAdmin.doc.scopePick')" value="pick"
              :disabled="form.docType === 'SCHEMA'" />
          </el-select>
          <el-select v-if="scopeMode === 'pick'" v-model="scopeWorkspaceIds" multiple filterable collapse-tags
            collapse-tags-tooltip style="width: 100%" :placeholder="t('aiAdmin.doc.scopePickPlaceholder')">
            <el-option v-for="ws in workspaces" :key="ws.id" :label="ws.name" :value="ws.id" />
          </el-select>
          <div class="field-hint">
            {{ form.docType === 'SCHEMA' ? t('aiAdmin.doc.scopeSchemaHint')
               : form.docType === 'SCRIPT' ? t('aiAdmin.doc.scopeScriptHint')
               : t('aiAdmin.doc.scopeHint') }}
          </div>
        </el-form-item>
        <el-form-item :label="t('aiAdmin.doc.title')" required>
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item :label="t('aiAdmin.doc.content')" required>
          <el-input v-model="form.content" type="textarea" :rows="14" />
        </el-form-item>
        <el-form-item :label="t('aiAdmin.doc.description')">
          <el-input v-model="form.description" type="textarea" :rows="3"
            :placeholder="t('aiAdmin.doc.descriptionPlaceholder')" />
          <div class="field-hint">{{ t('aiAdmin.doc.descriptionHint') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editing = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 元数据同步配置弹窗 ==================== -->
    <el-dialog
      v-model="syncEditing" class="sync-dialog"
      :title="syncForm.id ? t('common.edit') : t('aiAdmin.metaSync.addSync')"
      width="720" top="6vh"
    >
      <el-form label-position="top" class="sync-form">
        <el-form-item :label="t('aiAdmin.metaSync.datasource')" required>
          <el-select v-model="syncForm.datasourceId" :disabled="!!syncForm.id" filterable style="width: 100%"
            :placeholder="t('aiAdmin.metaSync.datasourcePlaceholder')">
            <el-option v-for="ds in datasources" :key="ds.id" :label="`${ds.name} (${ds.datasourceType})`" :value="ds.id" />
          </el-select>
        </el-form-item>

        <!-- ===== 调度 ===== -->
        <div class="sync-section">
          <div class="sync-section__title">{{ t('aiAdmin.metaSync.sectionSchedule') }}</div>
          <div class="sync-schedule">
            <div class="sync-schedule__toggle" @click="syncEnabledBool = !syncEnabledBool">
              <el-switch v-model="syncEnabledBool" @click.stop />
              <div class="sync-schedule__toggle-text">
                <span class="sync-schedule__toggle-label">{{ t('aiAdmin.metaSync.scheduleToggleLabel') }}</span>
                <span class="sync-schedule__toggle-desc">
                  {{ syncEnabledBool
                    ? t('aiAdmin.metaSync.scheduleEnabledDesc')
                    : t('aiAdmin.metaSync.scheduleDisabledDesc') }}
                </span>
              </div>
            </div>
            <div v-if="syncEnabledBool" class="sync-schedule__cron">
              <div class="sync-schedule__cron-expr">
                <span class="sync-schedule__cron-label">CRON</span>
                <code>{{ syncForm.scheduleCron || '—' }}</code>
              </div>
              <CronEditor
                :model-value="syncForm.scheduleCron ?? ''"
                @update:model-value="(v: string) => (syncForm.scheduleCron = v)"
              />
            </div>
          </div>
        </div>

        <!-- ===== 同步范围(级联白名单) ===== -->
        <div class="sync-section">
          <div class="sync-section__title">{{ t('aiAdmin.metaSync.sectionScope') }}</div>

          <!-- Catalog 层:仅三层引擎显示 -->
          <div v-if="scopeHasCatalog" class="scope-row">
            <ScopeSelector
              :label="t('aiAdmin.metaSync.scopeCatalogs')"
              :desc="t('aiAdmin.metaSync.scopeCatalogsDesc')"
              :all-label="t('aiAdmin.metaSync.scopeAllCatalogs')"
              :selected="includeCatalogsList"
              :options="catalogOptions"
              :loading="loadingCatalogs"
              @update:selected="setIncludeCatalogs"
            />
          </div>

          <!-- Database 层 -->
          <div class="scope-row">
            <ScopeSelector
              :label="t('aiAdmin.metaSync.scopeDatabases')"
              :desc="scopeHasCatalog
                ? t('aiAdmin.metaSync.scopeDatabasesDescWithCatalog')
                : t('aiAdmin.metaSync.scopeDatabasesDesc')"
              :all-label="t('aiAdmin.metaSync.scopeAllDatabases')"
              :selected="includeDatabasesList"
              :options="databaseOptions"
              :loading="loadingDatabases"
              :disabled="!syncForm.datasourceId"
              @update:selected="setIncludeDatabases"
            />
          </div>

          <!-- Table 层 -->
          <div class="scope-row">
            <ScopeSelector
              :label="t('aiAdmin.metaSync.scopeTables')"
              :desc="t('aiAdmin.metaSync.scopeTablesDesc')"
              :all-label="t('aiAdmin.metaSync.scopeAllTables')"
              :selected="includeTablesList"
              :options="tableOptions"
              :loading="loadingTables"
              :disabled="!syncForm.datasourceId"
              @update:selected="setIncludeTables"
            />
          </div>

          <!-- 排除关键字 -->
          <el-form-item :label="t('aiAdmin.metaSync.excludeKeywords')" class="scope-exclude">
            <el-input
              v-model="excludeKeywordsText"
              :placeholder="t('aiAdmin.metaSync.excludeKeywordsPlaceholder')"
            />
            <div class="field-hint">{{ t('aiAdmin.metaSync.excludeKeywordsHint') }}</div>
          </el-form-item>

          <el-form-item :label="t('aiAdmin.metaSync.maxColumnsPerTable')">
            <el-input-number v-model="syncForm.maxColumnsPerTable" :min="1" :max="500" />
          </el-form-item>
        </div>

        <!-- ===== 高级 ===== -->
        <div class="sync-section">
          <div class="sync-section__title">{{ t('aiAdmin.metaSync.sectionAdvanced') }}</div>
          <el-form-item :label="t('aiAdmin.metaSync.accessPaths')">
            <el-input
              v-model="syncForm.accessPaths" type="textarea" :rows="3" class="sync-mono"
              placeholder='[{"engine":"STARROCKS","template":"hive_catalog.{db}.{table}"}]'
            />
            <div class="hint">{{ t('aiAdmin.metaSync.accessPathsHint') }}</div>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="syncEditing = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="syncSaving" @click="saveSync">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminDocuments, adminMetadataSync, type AiDocumentVO, type RetrievedChunkVO,
  type AiMetadataSyncConfigVO } from '@/api/ai'
import { listDatasources, listMetaCatalogs, listMetaDatabases, listMetaTables } from '@/api/datasource'
import { listWorkspaces } from '@/api/workspace'
import CronEditor from '@/components/CronEditor.vue'
import ScopeSelector from './ScopeSelector.vue'

const { t } = useI18n()
const DOC_TYPES = ['WIKI', 'SCRIPT', 'SCHEMA', 'METRIC_DEF', 'RUNBOOK']
const ENGINE_TYPES = ['HIVE', 'STARROCKS', 'TRINO', 'SPARK', 'FLINK', 'MYSQL', 'CLICKHOUSE', 'POSTGRES']
const UPLOAD_ACCEPT = '.pdf,.doc,.docx,.md,.txt,.html,.htm,.xml,.json,.csv,.rtf'

const rows = ref<AiDocumentVO[]>([])
const docTotal = ref(0)
const docPageNum = ref(1)
const docPageSize = ref(20)
const loading = ref(false)
const reindexing = ref(false)
const docTypeFilter = ref<string>()
const editing = ref(false)
const saving = ref(false)
const form = reactive<AiDocumentVO>(emptyDoc())

// 作用域(跟 t_r_ai_tool_config 同模型):'all' = workspaceIds=null 平台共享;'pick' = 指定工作区
const scopeMode = ref<'all' | 'pick'>('all')
const scopeWorkspaceIds = ref<number[]>([])
const workspaces = ref<Array<{ id: number; name: string }>>([])

const searchQuery = ref('')
const searching = ref(false)
const searchResults = ref<RetrievedChunkVO[]>([])

// ==================== metadata sync state ====================
const syncRows = ref<AiMetadataSyncConfigVO[]>([])
const loadingSync = ref(false)
const syncEditing = ref(false)
const syncSaving = ref(false)
const syncingDsId = ref<number | null>(null)
const syncForm = reactive<AiMetadataSyncConfigVO>(emptySync())
const datasources = ref<Array<{ id: number; name: string; datasourceType: string }>>([])
const syncEnabledBool = computed({
  get: () => syncForm.enabled !== 0,
  set: (v: boolean) => { syncForm.enabled = v ? 1 : 0 },
})

type SyncFormJsonArrayKey = 'includeCatalogs' | 'includeDatabases' | 'includeTables' | 'excludeTables'

function readJsonArray(raw: string | null | undefined): string[] {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    if (Array.isArray(arr)) return arr.map(String)
  } catch { /* ignore */ }
  return []
}

function writeJsonArray(key: SyncFormJsonArrayKey, values: string[]) {
  const cleaned = (values ?? []).map(s => String(s).trim()).filter(Boolean)
  syncForm[key] = cleaned.length ? JSON.stringify(cleaned) : undefined
}

const includeCatalogsList = computed(() => readJsonArray(syncForm.includeCatalogs))
const includeDatabasesList = computed(() => readJsonArray(syncForm.includeDatabases))
const includeTablesList = computed(() => readJsonArray(syncForm.includeTables))

/** 排除关键字:UI 用 ; 分隔字符串,后端存 JSON 数组。 */
const excludeKeywordsText = computed<string>({
  get: () => readJsonArray(syncForm.excludeTables).join(';'),
  set: (v: string) => {
    const parts = (v ?? '').split(';').map(s => s.trim()).filter(Boolean)
    writeJsonArray('excludeTables', parts)
  },
})

function setIncludeCatalogs(v: string[]) {
  writeJsonArray('includeCatalogs', v)
  // 清掉下游选择:catalog 变了,原来的 db/table 限定名可能不再有效
  writeJsonArray('includeDatabases', [])
  writeJsonArray('includeTables', [])
  void refreshDatabaseOptions()
  void refreshTableOptions()
}
function setIncludeDatabases(v: string[]) {
  writeJsonArray('includeDatabases', v)
  writeJsonArray('includeTables', [])
  void refreshTableOptions()
}
function setIncludeTables(v: string[]) {
  writeJsonArray('includeTables', v)
}

// ========== Scope 级联 options(从 MetadataClient 拉) ==========

const THREE_TIER_TYPES = new Set(['TRINO', 'STARROCKS'])
const scopeHasCatalog = computed(() => {
  const ds = datasources.value.find(d => d.id === syncForm.datasourceId)
  return ds ? THREE_TIER_TYPES.has(ds.datasourceType) : false
})

const catalogOptions = ref<string[]>([])
const databaseOptions = ref<string[]>([])
const tableOptions = ref<string[]>([])
const loadingCatalogs = ref(false)
const loadingDatabases = ref(false)
const loadingTables = ref(false)

async function refreshCatalogOptions() {
  if (!syncForm.datasourceId || !scopeHasCatalog.value) {
    catalogOptions.value = []
    return
  }
  loadingCatalogs.value = true
  try {
    const { data } = await listMetaCatalogs(undefined, syncForm.datasourceId)
    catalogOptions.value = data ?? []
  } catch { catalogOptions.value = [] } finally { loadingCatalogs.value = false }
}

async function refreshDatabaseOptions() {
  if (!syncForm.datasourceId) {
    databaseOptions.value = []
    return
  }
  loadingDatabases.value = true
  try {
    if (!scopeHasCatalog.value) {
      const { data } = await listMetaDatabases(undefined, syncForm.datasourceId, null)
      databaseOptions.value = data ?? []
    } else {
      // 3 层:要么遍历已选 catalog 拉 db,要么全部 catalog 拉
      const cats = includeCatalogsList.value.length ? includeCatalogsList.value : catalogOptions.value
      const all: string[] = []
      const results = await Promise.all(
        cats.map(c => listMetaDatabases(undefined, syncForm.datasourceId!, c).then(r => ({ c, list: r.data ?? [] })).catch(() => ({ c, list: [] }))),
      )
      for (const { c, list } of results) {
        for (const db of list) all.push(`${c}.${db}`)
      }
      databaseOptions.value = Array.from(new Set(all)).sort()
    }
  } catch { databaseOptions.value = [] } finally { loadingDatabases.value = false }
}

async function refreshTableOptions() {
  if (!syncForm.datasourceId) {
    tableOptions.value = []
    return
  }
  const dbs = includeDatabasesList.value
  if (!dbs.length) {
    tableOptions.value = []
    return
  }
  loadingTables.value = true
  try {
    const all: string[] = []
    const tasks = dbs.map(qualifiedDb => {
      const { catalog, db } = parseQualifiedDb(qualifiedDb)
      return listMetaTables(undefined, syncForm.datasourceId!, db, catalog)
        .then(r => ({ qualifiedDb, tables: r.data ?? [] }))
        .catch(() => ({ qualifiedDb, tables: [] }))
    })
    const results = await Promise.all(tasks)
    for (const { qualifiedDb, tables } of results) {
      for (const t of tables) all.push(`${qualifiedDb}.${t.name}`)
    }
    tableOptions.value = Array.from(new Set(all)).sort()
  } catch { tableOptions.value = [] } finally { loadingTables.value = false }
}

/** "catalog.db" → {catalog, db};"db" → {catalog:null, db}。 */
function parseQualifiedDb(qualified: string): { catalog: string | null; db: string } {
  if (!scopeHasCatalog.value) {
    return { catalog: null, db: qualified }
  }
  const idx = qualified.indexOf('.')
  if (idx < 0) return { catalog: null, db: qualified }
  return { catalog: qualified.slice(0, idx), db: qualified.slice(idx + 1) }
}

watch(() => syncForm.datasourceId, () => {
  catalogOptions.value = []
  databaseOptions.value = []
  tableOptions.value = []
  if (syncForm.datasourceId) {
    void refreshCatalogOptions().then(() => refreshDatabaseOptions()).then(() => refreshTableOptions())
  }
})

function emptyDoc(): AiDocumentVO {
  return { docType: 'WIKI', title: '', content: '', description: '' }
}

function emptySync(): AiMetadataSyncConfigVO {
  return { datasourceId: null, enabled: 1, maxColumnsPerTable: 50 }
}

function dsLabel(id: number | null | undefined): string {
  if (!id) return '—'
  const ds = datasources.value.find(d => d.id === id)
  return ds ? `${ds.name} (${ds.datasourceType})` : `#${id}`
}

function statusTag(s: string | null | undefined): 'success' | 'warning' | 'danger' | 'info' {
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'RUNNING') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const { data } = await adminDocuments.list({
      docType: docTypeFilter.value || undefined,
      pageNum: docPageNum.value,
      pageSize: docPageSize.value,
    })
    rows.value = data?.records ?? []
    docTotal.value = data?.total ?? 0
  } finally { loading.value = false }
}

function onDocPageChange(n: number) { docPageNum.value = n; load() }
function onDocSizeChange(s: number) { docPageSize.value = s; docPageNum.value = 1; load() }

async function loadSync() {
  loadingSync.value = true
  try {
    const { data } = await adminMetadataSync.list({ pageSize: 100 })
    syncRows.value = data?.records ?? []
  } finally { loadingSync.value = false }
}

async function loadDatasources() {
  // Admin 页是 SUPER_ADMIN 视角,不带 workspaceId 让后端返回全部数据源
  try {
    const { data } = await listDatasources() as any
    datasources.value = Array.isArray(data) ? data : (data?.list ?? [])
  } catch { /* ignore */ }
}

async function loadWorkspaces() {
  try {
    const { data } = await listWorkspaces({ pageSize: 200 }) as any
    const list = Array.isArray(data) ? data : (data?.records ?? data?.list ?? [])
    workspaces.value = list.map((w: any) => ({ id: w.id, name: w.name }))
  } catch { /* ignore */ }
}

function openEdit(row?: AiDocumentVO) {
  Object.assign(form, row ? { ...row } : emptyDoc())
  // 解析 workspaceIds JSON 字符串 → mode + list
  const parsed = parseWorkspaceIds(form.workspaceIds)
  if (parsed.length === 0) {
    scopeMode.value = 'all'
    scopeWorkspaceIds.value = []
  } else {
    scopeMode.value = 'pick'
    scopeWorkspaceIds.value = parsed
  }
  // SCHEMA 强制平台共享;SCRIPT 强制指定工作区
  syncScopeByDocType()
  editing.value = true
}

function parseWorkspaceIds(raw: string | null | undefined): number[] {
  if (!raw || raw === 'null') return []
  try {
    const v = JSON.parse(raw)
    return Array.isArray(v) ? v.filter((x: unknown) => typeof x === 'number') : []
  } catch { return [] }
}

/** 根据 docType 自动调整 scope:SCHEMA→all(禁选);SCRIPT→pick;其它自由。 */
function syncScopeByDocType() {
  if (form.docType === 'SCHEMA') {
    scopeMode.value = 'all'
    scopeWorkspaceIds.value = []
  } else if (form.docType === 'SCRIPT' && scopeMode.value === 'all') {
    scopeMode.value = 'pick'
  }
}

watch(() => form.docType, syncScopeByDocType)

async function save() {
  if (!form.title || !form.content || !form.docType) {
    ElMessage.warning(t('common.required'))
    return
  }
  if (form.docType === 'SCHEMA' && !form.engineType) {
    ElMessage.warning(t('aiAdmin.doc.engineRequired'))
    return
  }
  if (scopeMode.value === 'pick' && scopeWorkspaceIds.value.length === 0) {
    ElMessage.warning(t('aiAdmin.doc.scopePickRequired'))
    return
  }
  // 平台 / 指定工作区 合成 workspaceIds JSON 字符串(null=平台共享)
  form.workspaceIds = scopeMode.value === 'all' ? null : JSON.stringify(scopeWorkspaceIds.value)
  saving.value = true
  try {
    if (form.id) await adminDocuments.update(form.id, form)
    else await adminDocuments.create(form)
    editing.value = false
    await load()
    ElMessage.success(t('common.success'))
  } catch { ElMessage.error(t('common.failed')) } finally { saving.value = false }
}

async function remove(row: AiDocumentVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    await adminDocuments.delete(row.id)
    await load()
  } catch { /* cancel */ }
}

async function handleReindex() {
  try {
    await ElMessageBox.confirm(t('aiAdmin.doc.reindexConfirm'), t('common.confirm'), { type: 'warning' })
    reindexing.value = true
    const { data } = await adminDocuments.reindex(docTypeFilter.value || undefined)
    ElMessage.success(t('aiAdmin.doc.reindexDone', { n: data ?? 0 }))
    await load()
  } catch { /* cancel or error */ } finally { reindexing.value = false }
}

async function handleSearch() {
  const q = searchQuery.value.trim()
  if (!q) { searchResults.value = []; return }
  searching.value = true
  try {
    const { data } = await adminDocuments.search(q, docTypeFilter.value || undefined, 5)
    searchResults.value = data ?? []
    if (!searchResults.value.length) ElMessage.info(t('aiAdmin.doc.searchEmpty'))
  } catch { ElMessage.error(t('common.failed')) } finally { searching.value = false }
}

// ==================== 文件上传(Tika 解析) ====================
const uploading = ref(false)

function beforeUpload(file: File): boolean {
  const maxMb = 50
  if (file.size > maxMb * 1024 * 1024) {
    ElMessage.error(t('aiAdmin.doc.uploadTooLarge', { mb: maxMb }))
    return false
  }
  return true
}

async function handleUpload(options: any) {
  const file: File = options.file
  uploading.value = true
  try {
    await adminDocuments.upload(file, { docType: docTypeFilter.value || 'WIKI' })
    ElMessage.success(t('common.success'))
    await load()
  } catch { ElMessage.error(t('common.failed')) } finally { uploading.value = false }
}

// ==================== metadata sync actions ====================

function openSync(row?: AiMetadataSyncConfigVO) {
  Object.assign(syncForm, row ? { ...row } : emptySync())
  syncEditing.value = true
  if (syncForm.datasourceId) {
    // 编辑已有配置:立即把 options 拉起来,方便级联选择器回显已选值
    void refreshCatalogOptions()
      .then(() => refreshDatabaseOptions())
      .then(() => refreshTableOptions())
  }
}

async function saveSync() {
  if (!syncForm.datasourceId) {
    ElMessage.warning(t('common.required')); return
  }
  if (syncEnabledBool.value && !syncForm.scheduleCron?.trim()) {
    ElMessage.warning(t('aiAdmin.metaSync.cronRequired')); return
  }
  syncSaving.value = true
  try {
    if (syncForm.id) await adminMetadataSync.update(syncForm.id, syncForm)
    else await adminMetadataSync.save(syncForm)
    syncEditing.value = false
    await loadSync()
    ElMessage.success(t('common.success'))
  } catch { ElMessage.error(t('common.failed')) } finally { syncSaving.value = false }
}

async function removeSync(row: AiMetadataSyncConfigVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    await adminMetadataSync.delete(row.id)
    await loadSync()
  } catch { /* cancel */ }
}

async function doSync(row: AiMetadataSyncConfigVO) {
  if (!row.datasourceId) return
  syncingDsId.value = row.datasourceId
  try {
    const { data } = await adminMetadataSync.sync(row.datasourceId)
    ElMessage.success(`inserted=${data?.inserted ?? 0} updated=${data?.updated ?? 0} skipped=${data?.skipped ?? 0} deleted=${data?.deleted ?? 0}`)
    await Promise.all([loadSync(), load()])
  } catch { ElMessage.error(t('common.failed')) } finally { syncingDsId.value = null }
}

onMounted(async () => {
  await Promise.all([load(), loadSync(), loadDatasources(), loadWorkspaces()])
})
</script>

<style scoped lang="scss">
.ks-section { margin-bottom: var(--r-space-2); }

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--r-space-3);
}

.cron,
.source-ref {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  background: var(--r-bg-panel);
  padding: 1px var(--r-space-2);
  border-radius: 3px;
}
.source-ref { color: var(--r-text-secondary); }

.sync-time {
  margin-left: var(--r-space-2);
  font-size: var(--r-font-xs);
  color: var(--r-text-secondary);
}
.msg {
  font-size: var(--r-font-xs);
  color: var(--r-text-secondary);
}

.search-hit {
  padding: var(--r-space-1) 0;
  border-bottom: 1px dashed var(--r-border);
  &:last-child { border-bottom: none; }

  &__score {
    margin-left: var(--r-space-2);
    color: var(--r-text-muted);
    font-size: var(--r-font-xs);
    font-family: var(--r-font-mono);
  }
  &__text {
    margin-top: var(--r-space-1);
    font-size: var(--r-font-sm);
    color: var(--r-text-secondary);
    white-space: pre-wrap;
    max-height: 80px;
    overflow: auto;
  }
}

/* ==================== Metadata sync dialog layout ==================== */
.sync-form :deep(.el-form-item) { margin-bottom: 14px; }
.sync-form :deep(.el-form-item:last-child) { margin-bottom: 0; }

.sync-section {
  & + & {
    margin-top: 20px;
    padding-top: 18px;
    border-top: 1px dashed var(--r-border-light);
  }
}
.sync-section__title {
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-bold);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--r-text-muted);
  margin-bottom: var(--r-space-3);
}

.scope-row {
  padding: var(--r-space-3) 0;
  border-bottom: 1px dashed var(--r-border-light);

  &:last-of-type { border-bottom: none; }
}
.scope-exclude { margin-top: var(--r-space-3); }

.sync-schedule {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-3);
}
.sync-schedule__toggle {
  display: flex;
  align-items: flex-start;
  gap: var(--r-space-3);
  padding: var(--r-space-3) var(--r-space-4);
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-md);
  background: var(--r-bg-card);
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;

  &:hover {
    border-color: var(--r-border-dark);
    background: var(--r-bg-hover);
  }
}
.sync-schedule__toggle :deep(.el-switch) { margin-top: 2px; }
.sync-schedule__toggle-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.sync-schedule__toggle-label {
  font-size: var(--r-font-base);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
}
.sync-schedule__toggle-desc {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
}
.sync-schedule__cron {
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-md);
  background: var(--r-bg-card);
  padding: var(--r-space-3) var(--r-space-4) var(--r-space-4);
}
.sync-schedule__cron-expr {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  margin-bottom: var(--r-space-3);
  padding-bottom: var(--r-space-3);
  border-bottom: 1px solid var(--r-border-light);

  code {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-base);
    color: var(--r-text-primary);
    background: transparent;
    letter-spacing: 0.02em;
  }
}
.sync-schedule__cron-label {
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-muted);
  letter-spacing: 0.08em;
}

.sync-mono :deep(.el-textarea__inner) {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  line-height: var(--r-leading-snug);
}

.sync-dialog :deep(.el-dialog__body) {
  padding: 18px 22px 6px;
  max-height: calc(100vh - 220px);
  overflow: auto;
}
</style>
