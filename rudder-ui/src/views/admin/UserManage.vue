<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  listUsers, createUser,
  resetUserPassword, toggleSuperAdmin, deleteUser, listUserWorkspaces,
} from '@/api/admin'
import { usePagination } from '@/composables/usePagination'
import { useFormDialog } from '@/composables/useFormDialog'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'

const { t } = useI18n()

interface WorkspaceMember { workspaceId: number; workspaceName: string; role: string }
interface UserRow {
  id: number
  username: string
  email: string
  isSuperAdmin: boolean
  createdAt: string
  workspaces?: WorkspaceMember[]
  wsLoading?: boolean
}

const searchText = ref('')

const { data: users, loading, pageNum, pageSize, total, fetch: fetchUsers, handlePageChange, resetAndFetch } = usePagination<UserRow>({
  fetchApi: (params) => listUsers({ ...params, searchVal: searchText.value.trim() || undefined }),
})

function handleSearch() { resetAndFetch() }

// Create dialog
const { visible: createVisible, formRef: createFormRef, form: createForm, open: openCreate, close: closeCreate, submit: submitCreate } = useFormDialog({ username: '', password: '', email: '' })
async function handleCreate() {
  await submitCreate(data => createUser(data), () => fetchUsers())
}

// Reset password dialog
const { visible: resetPwdVisible, form: resetPwdForm, open: openResetPwdDialog, close: closeResetPwd } = useFormDialog({ password: '' })
const resetPwdUserId = ref<number>(0)
function openResetPwd(id: number) {
  resetPwdUserId.value = id
  openResetPwdDialog()
}
async function handleResetPwd() {
  if (!resetPwdForm.value.password) return
  try {
    await resetUserPassword(resetPwdUserId.value, resetPwdForm.value.password)
    ElMessage.success(t('common.success'))
    closeResetPwd()
  } catch { /* interceptor */ }
}

async function handleToggleSuperAdmin(row: UserRow) {
  try {
    await toggleSuperAdmin(row.id, !row.isSuperAdmin)
    ElMessage.success(t('common.success'))
    fetchUsers()
  } catch { /* interceptor */ }
}

const { confirmDelete } = useDeleteConfirm()
function handleDelete(row: UserRow) {
  confirmDelete(t('admin.confirmDeleteUser', { name: row.username }), () => deleteUser(row.id), () => fetchUsers())
}

async function handleExpandChange(row: UserRow) {
  if (row.workspaces) return
  row.wsLoading = true
  try {
    const { data } = await listUserWorkspaces(row.id)
    row.workspaces = data ?? []
  } catch {
    row.workspaces = []
  } finally {
    row.wsLoading = false
  }
}

onMounted(fetchUsers)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h3>{{ t('admin.users') }}</h3>
      <div class="page-actions">
        <el-input v-model="searchText" :placeholder="t('common.search')" :prefix-icon="Search"
                  clearable style="width: 200px" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-button type="primary" :icon="Plus" @click="openCreate()">{{ t('admin.createUser') }}</el-button>
      </div>
    </div>

    <div class="admin-card">
    <el-table :data="users" v-loading="loading" row-key="id" @expand-change="handleExpandChange">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div style="padding: 8px 48px;">
            <div v-if="row.wsLoading" style="color: var(--r-text-muted);">Loading...</div>
            <div v-else-if="!row.workspaces || row.workspaces.length === 0" style="color: var(--r-text-muted);">{{ t('admin.noWorkspaces') }}</div>
            <el-table v-else :data="row.workspaces" size="small" :show-header="true" style="width: 100%">
              <el-table-column prop="workspaceName" :label="t('admin.workspaceName')" width="200" />
              <el-table-column prop="role" :label="t('admin.role')" width="180">
                <template #default="{ row: m }">
                  <el-tag :type="m.role === 'WORKSPACE_OWNER' ? 'warning' : 'info'" size="small">{{ m.role }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="username" :label="t('admin.username')" min-width="260" show-overflow-tooltip />
      <el-table-column prop="email" :label="t('admin.email')" min-width="240" show-overflow-tooltip />
      <el-table-column :label="t('admin.superAdmin')" width="120" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isSuperAdmin" type="danger" size="small">SUPER</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" :label="t('common.createdAt')" width="180" />
      <el-table-column :label="t('common.actions')" width="280" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="openResetPwd(row.id)">{{ t('admin.resetPassword') }}</el-button>
          <el-button text size="small" @click="handleToggleSuperAdmin(row)">
            {{ row.isSuperAdmin ? t('admin.removeSuperAdmin') : t('admin.setSuperAdmin') }}
          </el-button>
          <el-button text size="small" type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <el-pagination v-if="total > pageSize" layout="total, prev, pager, next"
                   :total="total" :page-size="pageSize" :current-page="pageNum"
                   @current-change="handlePageChange" class="admin-pagination" />

    <!-- Create User Dialog -->
    <el-dialog v-model="createVisible" :title="t('admin.createUser')" width="440" @submit.prevent>
      <el-form :ref="(el: any) => createFormRef = el" :model="createForm" label-width="80px">
        <el-form-item :label="t('admin.username')" prop="username" :rules="[{ required: true, message: t('admin.usernameRequired') }]">
          <el-input v-model="createForm.username" />
        </el-form-item>
        <el-form-item :label="t('admin.password')" prop="password" :rules="[{ required: true, message: t('admin.passwordRequired') }]">
          <el-input v-model="createForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item :label="t('admin.email')">
          <el-input v-model="createForm.email" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeCreate()">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreate">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Reset Password Dialog -->
    <el-dialog v-model="resetPwdVisible" :title="t('admin.resetPassword')" width="400" @submit.prevent>
      <el-form :model="resetPwdForm" label-width="80px">
        <el-form-item :label="t('admin.newPassword')">
          <el-input v-model="resetPwdForm.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeResetPwd()">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleResetPwd">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/admin.scss';
</style>
