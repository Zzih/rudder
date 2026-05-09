<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  listWorkspaces, deleteWorkspace,
  listMembers, addMember, updateMemberRole, removeMember,
  listProjects, updateProjectOwner,
} from '@/api/workspace'
import { listUsersSimple } from '@/api/admin'
import { usePagination } from '@/composables/usePagination'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'
import { usePermission } from '@/composables/usePermission'

const { t } = useI18n()
const { isSuperAdmin } = usePermission()

interface MemberRow { id: number; workspaceId: number; userId: number; username: string; role: string }
interface ProjectRow { id: number; code: string; name: string; description: string; createdBy: number; createdByUsername: string }
interface WorkspaceRow { id: number; name: string; description: string; createdAt: string }

const ROLES = ['WORKSPACE_OWNER', 'DEVELOPER', 'VIEWER']
const ROLE_STYLE: Record<string, { label: string; dotColor: string; tagType: 'warning' | 'primary' | 'info' }> = {
  WORKSPACE_OWNER: { label: 'Owner', dotColor: 'var(--r-warning)', tagType: 'warning' },
  DEVELOPER:       { label: 'Developer', dotColor: 'var(--r-accent)', tagType: 'primary' },
  VIEWER:          { label: 'Viewer', dotColor: 'var(--r-text-muted)', tagType: 'info' },
}

// ==================== Workspace list ====================
const searchText = ref('')
const { data: workspaces, loading, pageNum, pageSize, total, fetch: fetchWorkspaces, handlePageChange, resetAndFetch } = usePagination<WorkspaceRow>({
  fetchApi: (params) => listWorkspaces({ ...params, searchVal: searchText.value.trim() || undefined }),
})

// ==================== Drawer ====================
const drawerVisible = ref(false)
const drawerTab = ref('members')
const currentWs = ref<WorkspaceRow | null>(null)

const members = ref<MemberRow[]>([])
const membersLoading = ref(false)
const memberCount = computed(() => members.value.length)
const memberSearch = ref('')
const filteredMembers = computed(() => {
  const q = memberSearch.value.trim().toLowerCase()
  if (!q) return members.value
  return members.value.filter(m => m.username.toLowerCase().includes(q))
})

const projects = ref<ProjectRow[]>([])
const projectsLoading = ref(false)
const projectSearch = ref('')
const projectOwnerFilter = ref<number | null>(null)
const filteredProjects = computed(() => {
  let list = projects.value
  const q = projectSearch.value.trim().toLowerCase()
  if (q) {
    list = list.filter(p => p.name.toLowerCase().includes(q) || (p.description ?? '').toLowerCase().includes(q))
  }
  if (projectOwnerFilter.value) {
    list = list.filter(p => p.createdBy === projectOwnerFilter.value)
  }
  return list
})

// Add member dialog
const addDialogVisible = ref(false)
const allUsers = ref<{ id: number; username: string }[]>([])
const addForm = ref({ userId: null as number | null, role: 'DEVELOPER' })

// ==================== Fetch ====================

function handleSearch() { resetAndFetch() }

async function openDrawer(ws: WorkspaceRow) {
  currentWs.value = ws
  drawerTab.value = 'members'
  memberSearch.value = ''
  projectSearch.value = ''
  projectOwnerFilter.value = null
  drawerVisible.value = true
  await fetchMembers(ws.id)
  fetchProjects(ws.id)
}

// ==================== Members ====================

async function fetchMembers(wsId: number) {
  membersLoading.value = true
  try {
    const { data } = await listMembers(wsId)
    members.value = data ?? []
  } finally {
    membersLoading.value = false
  }
}

async function handleRoleChange(member: MemberRow, newRole: string) {
  try {
    await updateMemberRole(member.workspaceId, member.userId, newRole)
    ElMessage.success(t('common.success'))
    await fetchMembers(member.workspaceId)
  } catch { /* interceptor */ }
}

async function handleRemoveMember(member: MemberRow) {
  await ElMessageBox.confirm(
    t('admin.confirmRemoveMember', { name: member.username }),
    t('common.confirm'), { type: 'warning' },
  )
  try {
    await removeMember(member.workspaceId, member.userId)
    ElMessage.success(t('common.success'))
    await fetchMembers(member.workspaceId)
  } catch { /* interceptor */ }
}

async function openAddMember() {
  addForm.value = { userId: null, role: 'DEVELOPER' }
  if (!allUsers.value.length) {
    try {
      const { data } = await listUsersSimple()
      allUsers.value = data ?? []
    } catch { /* ignore */ }
  }
  addDialogVisible.value = true
}

const availableUsers = computed(() => {
  const existingIds = new Set(members.value.map(m => m.userId))
  return allUsers.value.filter(u => !existingIds.has(u.id))
})

async function handleAddMember() {
  if (!addForm.value.userId || !currentWs.value) return
  try {
    await addMember(currentWs.value.id, { userId: addForm.value.userId, role: addForm.value.role })
    ElMessage.success(t('common.success'))
    addDialogVisible.value = false
    await fetchMembers(currentWs.value.id)
  } catch { /* interceptor */ }
}

// ==================== Projects ====================

async function fetchProjects(wsId: number) {
  projectsLoading.value = true
  try {
    const res: any = await listProjects(wsId, { pageSize: 200 })
    projects.value = res.data ?? []
  } finally {
    projectsLoading.value = false
  }
}

async function handleOwnerChange(project: ProjectRow, newUserId: number) {
  if (!currentWs.value) return
  const newOwner = members.value.find(m => m.userId === newUserId)
  await ElMessageBox.confirm(
    t('admin.confirmChangeOwner', { project: project.name, user: newOwner?.username ?? String(newUserId) }),
    t('common.confirm'), { type: 'warning' },
  )
  try {
    await updateProjectOwner(currentWs.value.id, project.code, newUserId)
    ElMessage.success(t('common.success'))
    await fetchProjects(currentWs.value.id)
  } catch { /* interceptor */ }
}

const { confirmDelete } = useDeleteConfirm()
function handleDeleteWorkspace(ws: WorkspaceRow) {
  confirmDelete(t('admin.confirmDeleteWorkspace', { name: ws.name }), () => deleteWorkspace(ws.id), () => fetchWorkspaces())
}

function roleStyle(role: string) { return ROLE_STYLE[role] ?? ROLE_STYLE.VIEWER }

onMounted(fetchWorkspaces)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h3>{{ t('admin.workspaces') }}</h3>
      <div class="page-actions">
        <el-input v-model="searchText" :placeholder="t('common.search')" :prefix-icon="Search"
                  clearable style="width: 200px" @keyup.enter="handleSearch" @clear="handleSearch" />
      </div>
    </div>

    <div class="admin-card">
    <el-table :data="workspaces" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" :label="t('admin.workspaceName')" min-width="160">
        <template #default="{ row }">
          <span class="link-text" @click="openDrawer(row)">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="t('admin.description')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createdAt" :label="t('common.createdAt')" width="180" />
      <el-table-column :label="t('common.actions')" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="openDrawer(row)">{{ t('admin.manage') }}</el-button>
          <el-button v-if="isSuperAdmin" text size="small" type="danger" @click="handleDeleteWorkspace(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <el-pagination v-if="total > pageSize" layout="total, prev, pager, next"
                   :total="total" :page-size="pageSize" :current-page="pageNum"
                   @current-change="handlePageChange" class="admin-pagination" />

    <!-- ==================== Drawer ==================== -->
    <el-drawer v-model="drawerVisible" size="620" :with-header="false" class="ws-drawer">
      <div class="drawer-wrap">
        <!-- Header -->
        <div class="drawer-head">
          <div class="drawer-head__top">
            <div class="drawer-head__icon">{{ currentWs?.name?.charAt(0)?.toUpperCase() }}</div>
            <div class="drawer-head__meta">
              <h3>{{ currentWs?.name }}</h3>
              <p>{{ currentWs?.description || '--' }}</p>
            </div>
          </div>
          <div class="drawer-head__tags">
            <el-tag effect="plain" round>{{ t('admin.members') }} {{ memberCount }}</el-tag>
            <el-tag effect="plain" round type="info">{{ t('admin.projects') }} {{ projects.length }}</el-tag>
          </div>
        </div>

        <!-- Tabs -->
        <el-tabs v-model="drawerTab" class="drawer-tabs">
          <!-- ===== Members ===== -->
          <el-tab-pane name="members">
            <template #label>
              <span>{{ t('admin.members') }}<em class="tab-num">{{ memberCount }}</em></span>
            </template>

            <div class="section-bar">
              <el-input v-model="memberSearch" :placeholder="t('admin.searchMember')" :prefix-icon="Search"
                        clearable size="small" style="width: 180px" />
              <el-button type="primary" size="small" :icon="Plus" @click="openAddMember">{{ t('admin.addMember') }}</el-button>
            </div>

            <div v-loading="membersLoading" class="card-list">
              <div v-for="m in filteredMembers" :key="m.id" class="m-card">
                <div class="m-card__left">
                  <el-avatar :size="34" class="m-avatar">{{ m.username.charAt(0).toUpperCase() }}</el-avatar>
                  <div>
                    <div class="m-name">{{ m.username }}</div>
                    <div class="m-sub">ID {{ m.userId }}</div>
                  </div>
                </div>
                <div class="m-card__right">
                  <el-select :model-value="m.role" size="small" style="width: 155px"
                             @change="(val: string) => handleRoleChange(m, val)">
                    <el-option v-for="r in ROLES" :key="r" :value="r">
                      <span class="role-dot" :style="{ background: roleStyle(r).dotColor }" />
                      {{ roleStyle(r).label }}
                    </el-option>
                  </el-select>
                  <el-tooltip :content="t('admin.remove')" placement="top">
                    <el-button text circle size="small" class="btn-del" @click="handleRemoveMember(m)">
                      <el-icon><Close /></el-icon>
                    </el-button>
                  </el-tooltip>
                </div>
              </div>
              <el-empty v-if="!membersLoading && !filteredMembers.length" :description="t('admin.noMembers')" :image-size="60" />
            </div>
          </el-tab-pane>

          <!-- ===== Projects ===== -->
          <el-tab-pane name="projects">
            <template #label>
              <span>{{ t('admin.projects') }}<em class="tab-num">{{ projects.length }}</em></span>
            </template>

            <div class="section-bar">
              <el-input v-model="projectSearch" :placeholder="t('admin.searchProject')" :prefix-icon="Search"
                        clearable size="small" style="width: 180px" />
              <el-select v-model="projectOwnerFilter" :placeholder="t('admin.filterByOwner')" size="small"
                         clearable filterable style="width: 140px">
                <el-option v-for="m in members" :key="m.userId" :label="m.username" :value="m.userId" />
              </el-select>
            </div>

            <div v-loading="projectsLoading" class="card-list">
              <div v-for="p in filteredProjects" :key="p.id" class="p-card">
                <div class="p-card__left">
                  <div class="p-icon"><el-icon :size="15" style="color: var(--r-accent)"><Folder /></el-icon></div>
                  <div class="p-meta">
                    <div class="p-name">{{ p.name }}</div>
                    <div class="p-desc">{{ p.description || '--' }}</div>
                  </div>
                </div>
                <div class="p-card__right">
                  <el-select :model-value="p.createdBy" size="small" filterable style="width: 130px"
                             @change="(val: number) => handleOwnerChange(p, val)">
                    <el-option v-for="m in members" :key="m.userId" :label="m.username" :value="m.userId" />
                  </el-select>
                </div>
              </div>
              <el-empty v-if="!projectsLoading && !filteredProjects.length" :description="t('admin.noProjects')" :image-size="60" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <!-- ==================== Add Member ==================== -->
    <el-dialog v-model="addDialogVisible" :title="t('admin.addMember')" width="440" @submit.prevent>
      <el-form :model="addForm" label-position="top">
        <el-form-item :label="t('admin.user')">
          <el-select v-model="addForm.userId" filterable :placeholder="t('admin.selectUser')" style="width: 100%">
            <el-option v-for="u in availableUsers" :key="u.id" :label="u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('admin.role')">
          <el-radio-group v-model="addForm.role">
            <el-radio-button v-for="r in ROLES" :key="r" :value="r">{{ roleStyle(r).label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!addForm.userId" @click="handleAddMember">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/admin.scss';

.link-text {
  color: var(--r-accent); cursor: pointer; font-weight: 500;
  &:hover { text-decoration: underline; }
}

// ===== Drawer =====
.drawer-wrap { display: flex; flex-direction: column; height: 100%; }

// -- header --
.drawer-head {
  padding: 0 0 16px;
  border-bottom: 1px solid var(--r-border);
}

.drawer-head__top { display: flex; align-items: center; gap: 14px; margin-bottom: 18px; }

.drawer-head__icon {
  width: 42px; height: 42px; border-radius: 10px;
  background: var(--r-accent); color: #fff;
  font-size: 18px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}

.drawer-head__meta {
  h3 { margin: 0; font-size: 16px; font-weight: 600; color: var(--r-text-primary); }
  p  { margin: 3px 0 0; font-size: 13px; color: var(--r-text-muted); }
}

.drawer-head__tags { display: flex; gap: 8px; }

// -- tabs --
.drawer-tabs {
  flex: 1; overflow: hidden; display: flex; flex-direction: column;
  :deep(.el-tabs__content) { flex: 1; overflow-y: auto; }
}

.tab-num {
  font-style: normal; font-size: 11px; color: var(--r-text-muted);
  background: var(--r-bg-hover); border-radius: 8px;
  padding: 0 6px; margin-left: 6px;
}

.section-bar { display: flex; align-items: center; gap: 8px; margin: 4px 0 12px; }

// ===== Member cards =====
.card-list { display: flex; flex-direction: column; gap: 6px; }

.m-card {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 12px; border-radius: 8px;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
  &:hover { background: var(--r-bg-hover); border-color: var(--r-border); }
}

.m-card__left { display: flex; align-items: center; gap: 10px; }
.m-card__right { display: flex; align-items: center; gap: 6px; }

.m-avatar {
  background: linear-gradient(135deg, var(--r-accent), var(--r-accent-hover));
  font-size: 13px; color: #fff;
}

.m-name { font-size: 14px; font-weight: 500; color: var(--r-text-primary); line-height: 1.3; }
.m-sub  { font-size: 12px; color: var(--r-text-disabled); }

.btn-del {
  color: var(--r-text-disabled) !important;
  &:hover { color: var(--r-danger) !important; background: var(--r-danger-bg) !important; }
}

.role-dot {
  display: inline-block; width: 7px; height: 7px; border-radius: 50%;
  margin-right: 6px; vertical-align: middle;
}

// ===== Project cards =====
.p-card {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 12px; border-radius: 8px;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
  &:hover { background: var(--r-bg-hover); border-color: var(--r-border); }
}

.p-card__left { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; }
.p-card__right { flex-shrink: 0; }

.p-icon {
  width: 34px; height: 34px; border-radius: 8px; background: var(--r-accent-bg);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}

.p-meta { min-width: 0; }
.p-name { font-size: 14px; font-weight: 500; color: var(--r-text-primary); line-height: 1.3; }
.p-desc { font-size: 12px; color: var(--r-text-disabled); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
