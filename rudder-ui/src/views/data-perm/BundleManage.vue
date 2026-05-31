<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Plus, Search, Operation, OfficeBuilding, Select, Close } from '@element-plus/icons-vue'
import {
  pageDataPermBundles,
  createDataPermBundle,
  updateDataPermBundle,
  deleteDataPermBundle,
  listBundleWorkspaces,
  setBundleWorkspaces,
  type DataPermBundle,
  type BundleWorkspaceGrant,
} from '@/api/data-perm'
import { listWorkspaces } from '@/api/workspace'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'
import { usePagination } from '@/composables/usePagination'
import BundleStatementsDrawer from './components/BundleStatementsDrawer.vue'

const { t } = useI18n()
const { confirmDelete } = useDeleteConfirm()

const keyword = ref('')

const editorOpen = ref(false)
const editing = ref<DataPermBundle>({ name: '', description: '' })
const saving = ref(false)

const drawerOpen = ref(false)
const drawerBundle = ref<DataPermBundle | null>(null)

const {
  data: roles,
  loading,
  pageNum,
  pageSize,
  total,
  fetch: load,
  handlePageChange: onPageChange,
  handleSizeChange: onPageSizeChange,
  resetAndFetch,
} = usePagination<DataPermBundle>({
  fetchApi: (params) => pageDataPermBundles({
    ...params,
    keyword: keyword.value || undefined,
  } as never),
})

function onSearch() { resetAndFetch() }

function openCreate() {
  editing.value = { name: '', description: '' }
  editorOpen.value = true
}

function openEdit(r: DataPermBundle) {
  editing.value = { ...r }
  editorOpen.value = true
}

function openPermissions(r: DataPermBundle) {
  drawerBundle.value = r
  drawerOpen.value = true
}

function handleDelete(r: DataPermBundle) {
  confirmDelete(
      t('dataPermBundle.deleteConfirm', { name: r.name, count: r.activeGrantCount ?? 0 }),
      () => deleteDataPermBundle(r.id!),
      () => load(),
  )
}

async function save() {
  if (!editing.value.name?.trim()) {
    ElMessage.error(t('dataPermBundle.nameRequired'))
    return
  }
  saving.value = true
  try {
    if (editing.value.id) {
      await updateDataPermBundle(editing.value.id, {
        name: editing.value.name,
        description: editing.value.description,
      })
    } else {
      await createDataPermBundle({
        name: editing.value.name,
        description: editing.value.description,
      })
    }
    ElMessage.success(t('common.success'))
    editorOpen.value = false
    await load()
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    saving.value = false
  }
}

// ==================== 可见工作空间授权 ====================
const grantOpen = ref(false)
const grantTarget = ref<DataPermBundle | null>(null)
const grantSubmitting = ref(false)
const grantLoading = ref(false)
const grantWorkspaces = ref<{ id: number; name: string }[]>([])
const grantSelected = ref<Set<number>>(new Set())
const grantSearch = ref('')

const filteredGrantWorkspaces = computed(() => {
  const q = grantSearch.value.trim().toLowerCase()
  if (!q) return grantWorkspaces.value
  return grantWorkspaces.value.filter(w => w.name.toLowerCase().includes(q))
})
const allFilteredSelected = computed(() =>
  filteredGrantWorkspaces.value.length > 0
  && filteredGrantWorkspaces.value.every(w => grantSelected.value.has(w.id)))

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
async function openGrantDialog(r: DataPermBundle) {
  grantTarget.value = r
  grantSearch.value = ''
  grantOpen.value = true
  grantLoading.value = true
  try {
    const [wsRes, currentRes]: any[] = await Promise.all([
      listWorkspaces({ pageNum: 1, pageSize: 500 }) as any,
      listBundleWorkspaces(r.id!),
    ])
    grantWorkspaces.value = (wsRes.data ?? []).map((w: any) => ({ id: w.id, name: w.name }))
    grantSelected.value = new Set((currentRes.data ?? []).map((g: BundleWorkspaceGrant) => g.workspaceId))
  } finally {
    grantLoading.value = false
  }
}
async function handleGrantSubmit() {
  if (!grantTarget.value) return
  grantSubmitting.value = true
  try {
    await setBundleWorkspaces(grantTarget.value.id!, Array.from(grantSelected.value))
    ElMessage.success(t('common.success'))
    grantOpen.value = false
  } catch { /* interceptor 已提示 */ } finally {
    grantSubmitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="page-header">
      <h3>{{ t('dataPerm.tabRoles') }}</h3>
      <div class="page-actions">
        <el-input v-model="keyword"
          :prefix-icon="Search"
          :placeholder="t('dataPermBundle.searchPlaceholder')"
          clearable
          style="width: 240px"
          @keyup.enter="onSearch"
          @clear="onSearch" />
        <el-button type="primary" :icon="Plus" @click="openCreate">
          {{ t('dataPermBundle.create') }}
        </el-button>
      </div>
    </div>

    <div class="admin-card">
      <el-table :data="roles" row-key="id">
        <el-table-column prop="name" :label="t('dataPermBundle.name')" min-width="280">
          <template #default="{ row }">
            <div class="role-cell">
              <span class="role-cell__name">{{ row.name }}</span>
              <span v-if="row.description" class="role-cell__desc">{{ row.description }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('dataPermBundle.activeGrantCount')" width="160" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="(row.activeGrantCount ?? 0) > 0 ? 'success' : 'info'">
              {{ row.activeGrantCount ?? 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="420" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" :icon="Operation"
              @click="openPermissions(row)">
              {{ t('dataPermBundle.managePermissions') }}
            </el-button>
            <el-button text size="small" type="primary" :icon="OfficeBuilding"
              @click="openGrantDialog(row)">
              {{ t('dataPermBundle.visibleWorkspaces') }}
            </el-button>
            <el-button text size="small" type="primary" @click="openEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button text size="small" type="danger" @click="handleDelete(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <span class="el-table__empty-text">{{ t('dataPermBundle.tableEmpty') }}</span>
        </template>
      </el-table>
    </div>

    <el-pagination v-if="total > 0"
      class="admin-pagination"
      :current-page="pageNum"
      :page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      background
      @current-change="onPageChange"
      @size-change="onPageSizeChange" />

    <el-dialog v-model="editorOpen"
      :title="editing.id ? t('dataPermBundle.edit') : t('dataPermBundle.create')"
      width="520" :close-on-click-modal="false" destroy-on-close>
      <el-form label-position="top">
        <el-form-item :label="t('dataPermBundle.name')" required>
          <el-input v-model="editing.name" maxlength="64" />
        </el-form-item>
        <el-form-item :label="t('dataPermBundle.description')">
          <el-input v-model="editing.description" type="textarea" :rows="3" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <BundleStatementsDrawer
      v-if="drawerBundle"
      v-model="drawerOpen"
      :bundle="drawerBundle" />

    <el-dialog v-model="grantOpen"
      :title="t('dataPermBundle.visibleWorkspacesTitle', { name: grantTarget?.name ?? '' })"
      width="520" :close-on-click-modal="false" destroy-on-close>
      <p class="grant-hint">{{ t('dataPermBundle.visibleWorkspacesHint') }}</p>
      <div class="grant-toolbar">
        <el-input v-model="grantSearch" :prefix-icon="Search"
          :placeholder="t('common.search')" clearable class="grant-search" />
        <span class="grant-counter">
          {{ t('dataPermBundle.grantSelectedCount', { selected: grantSelected.size, total: grantWorkspaces.length }) }}
        </span>
      </div>
      <div class="grant-bulkbar">
        <el-button text size="small" :icon="Select"
          :disabled="!filteredGrantWorkspaces.length || allFilteredSelected" @click="selectAllFiltered">
          {{ t('dataPermBundle.grantSelectAll') }}
        </el-button>
        <el-button text size="small" :icon="Close"
          :disabled="!filteredGrantWorkspaces.length" @click="clearAllFiltered">
          {{ t('dataPermBundle.grantClearAll') }}
        </el-button>
      </div>
      <div v-loading="grantLoading" class="grant-list">
        <div v-for="ws in filteredGrantWorkspaces" :key="ws.id"
          class="grant-row" :class="{ 'is-active': grantSelected.has(ws.id) }"
          @click="toggleGrant(ws.id)">
          <el-checkbox :model-value="grantSelected.has(ws.id)" style="pointer-events: none" />
          <span class="grant-row__name">{{ ws.name }}</span>
        </div>
        <el-empty v-if="!grantLoading && !filteredGrantWorkspaces.length"
          :description="t('common.noData')" :image-size="60" />
      </div>
      <template #footer>
        <el-button @click="grantOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="grantSubmitting" @click="handleGrantSubmit">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/admin.scss';

.role-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  &__name {
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
    font-size: var(--r-font-base);
  }
  &__desc {
    font-size: var(--r-font-xs);
    color: var(--r-text-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.grant-hint { margin: 0 0 var(--r-space-3); font-size: var(--r-font-sm); color: var(--r-text-muted); }
.grant-toolbar { display: flex; align-items: center; gap: var(--r-space-3); margin-bottom: var(--r-space-2); }
.grant-search { flex: 1; }
.grant-counter { font-size: var(--r-font-xs); color: var(--r-text-muted); white-space: nowrap; }
.grant-bulkbar { display: flex; gap: var(--r-space-2); margin-bottom: var(--r-space-2); }
.grant-list { max-height: 320px; overflow-y: auto; display: flex; flex-direction: column; gap: 4px; }
.grant-row {
  display: flex; align-items: center; gap: var(--r-space-2); padding: 6px 10px;
  border: 1px solid var(--r-border-light); border-radius: var(--r-radius-sm); cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
  &:hover { border-color: var(--r-border); background: var(--r-bg-hover); }
  &.is-active { border-color: var(--r-accent); background: var(--r-accent-bg); }
}
.grant-row__name { font-size: var(--r-font-sm); color: var(--r-text-primary); }
</style>
