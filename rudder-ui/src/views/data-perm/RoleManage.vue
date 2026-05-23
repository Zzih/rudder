<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Plus, Search, Operation } from '@element-plus/icons-vue'
import {
  pageDataPermRoles,
  createDataPermRole,
  updateDataPermRole,
  deleteDataPermRole,
  type DataPermRole,
} from '@/api/data-perm'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'
import { usePagination } from '@/composables/usePagination'
import RolePermissionsDrawer from './components/RolePermissionsDrawer.vue'

const { t } = useI18n()
const { confirmDelete } = useDeleteConfirm()

const keyword = ref('')

const editorOpen = ref(false)
const editing = ref<DataPermRole>({ name: '', description: '' })
const saving = ref(false)

const drawerOpen = ref(false)
const drawerRole = ref<DataPermRole | null>(null)

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
} = usePagination<DataPermRole>({
  fetchApi: (params) => pageDataPermRoles({
    ...params,
    keyword: keyword.value || undefined,
  } as never),
})

function onSearch() { resetAndFetch() }

function openCreate() {
  editing.value = { name: '', description: '' }
  editorOpen.value = true
}

function openEdit(r: DataPermRole) {
  editing.value = { ...r }
  editorOpen.value = true
}

function openPermissions(r: DataPermRole) {
  drawerRole.value = r
  drawerOpen.value = true
}

function handleDelete(r: DataPermRole) {
  confirmDelete(
      t('dataPermRole.deleteConfirm', { name: r.name, count: r.activeGrantCount ?? 0 }),
      () => deleteDataPermRole(r.id!),
      () => load(),
  )
}

async function save() {
  if (!editing.value.name?.trim()) {
    ElMessage.error(t('dataPermRole.nameRequired'))
    return
  }
  saving.value = true
  try {
    if (editing.value.id) {
      await updateDataPermRole(editing.value.id, {
        name: editing.value.name,
        description: editing.value.description,
      })
    } else {
      await createDataPermRole({
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

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="page-header">
      <h3>{{ t('dataPerm.tabRoles') }}</h3>
      <div class="page-actions">
        <el-input v-model="keyword"
          :prefix-icon="Search"
          :placeholder="t('dataPermRole.searchPlaceholder')"
          clearable
          style="width: 240px"
          @keyup.enter="onSearch"
          @clear="onSearch" />
        <el-button type="primary" :icon="Plus" @click="openCreate">
          {{ t('dataPermRole.create') }}
        </el-button>
      </div>
    </div>

    <div class="admin-card">
      <el-table :data="roles" row-key="id">
        <el-table-column prop="name" :label="t('dataPermRole.name')" min-width="280">
          <template #default="{ row }">
            <div class="role-cell">
              <span class="role-cell__name">{{ row.name }}</span>
              <span v-if="row.description" class="role-cell__desc">{{ row.description }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('dataPermRole.activeGrantCount')" width="160" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="(row.activeGrantCount ?? 0) > 0 ? 'success' : 'info'">
              {{ row.activeGrantCount ?? 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="300" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" :icon="Operation"
              @click="openPermissions(row)">
              {{ t('dataPermRole.managePermissions') }}
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
          <span class="el-table__empty-text">{{ t('dataPermRole.tableEmpty') }}</span>
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
      :title="editing.id ? t('dataPermRole.edit') : t('dataPermRole.create')"
      width="520" :close-on-click-modal="false" destroy-on-close>
      <el-form label-position="top">
        <el-form-item :label="t('dataPermRole.name')" required>
          <el-input v-model="editing.name" maxlength="64" />
        </el-form-item>
        <el-form-item :label="t('dataPermRole.description')">
          <el-input v-model="editing.description" type="textarea" :rows="3" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <RolePermissionsDrawer
      v-if="drawerRole"
      v-model="drawerOpen"
      :role="drawerRole" />
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
</style>
