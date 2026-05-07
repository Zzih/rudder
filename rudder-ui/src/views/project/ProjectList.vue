<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Upload } from '@element-plus/icons-vue'
import { listProjects, createProject, updateProject, deleteProject } from '@/api/workspace'
import { listWorkflowDefinitions, listWorkflowDefinitionVersions, publishProject } from '@/api/workflow'
import { formatDateShort } from '@/utils/dateFormat'
import { cardColor } from '@/utils/colorMeta'
import { usePermission } from '@/composables/usePermission'

interface Project { id: number; code: string; name: string; description: string; createdAt: string }

const { t } = useI18n()
// create/delete 在后端要 WORKSPACE_OWNER+;update 是 DEVELOPER+ 但还要校验创建者身份,
// 前端简化为按宽口径区分:OWNER 才能新建/删,DEVELOPER 看到编辑/发布但点了可能被后端 403
const { hasRole, canEdit } = usePermission()
const canManageProject = computed(() => hasRole('WORKSPACE_OWNER'))
const route = useRoute()
const router = useRouter()
const workspaceId = Number(route.params.workspaceId)

const projects = ref<Project[]>([])
const loading = ref(false)
const searchText = ref('')
const dialogVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = ref({ name: '', description: '' })
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const editDialogVisible = ref(false)
const editForm = ref({ code: '' as string, name: '', description: '' })

// Project publish
const publishDialogVisible = ref(false)
const publishLoading = ref(false)
const publishing = ref(false)
const publishRemark = ref('')
const publishProjectCode = ref<string>('')
interface PublishRow { code: string; name: string; description: string; versionId: number; versions: { id: number; versionNo: number; remark: string; createdAt: string }[]; versionsLoading: boolean }
const publishRows = ref<PublishRow[]>([])

async function fetchProjects() {
  loading.value = true
  try {
    const res: any = await listProjects(workspaceId, {
      searchVal: searchText.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    projects.value = res.data ?? []
    total.value = res.total ?? 0
  }
  catch { ElMessage.error(t('common.failed')) }
  finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; fetchProjects() }

function handlePageChange(page: number) { pageNum.value = page; fetchProjects() }
function handleSizeChange(size: number) { pageSize.value = size; pageNum.value = 1; fetchProjects() }

function openProject(p: Project) { router.push(`/workspaces/${workspaceId}/projects/${p.code}/workflow-definitions`) }
function openCreateDialog() { createForm.value = { name: '', description: '' }; dialogVisible.value = true }

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try { await createProject(workspaceId, createForm.value); ElMessage.success(t('common.success')); dialogVisible.value = false; await fetchProjects() }
  catch { ElMessage.error(t('common.failed')) }
  finally { creating.value = false }
}

function openEditDialog(p: Project, e: Event) {
  e.stopPropagation()
  editForm.value = { code: p.code, name: p.name, description: p.description ?? '' }
  editDialogVisible.value = true
}

async function handleEdit() {
  if (!editForm.value.name.trim()) return
  try { await updateProject(workspaceId, editForm.value.code, { name: editForm.value.name, description: editForm.value.description }); ElMessage.success(t('common.success')); editDialogVisible.value = false; await fetchProjects() }
  catch { ElMessage.error(t('common.failed')) }
}

async function handleDelete(p: Project, e: Event) {
  e.stopPropagation()
  try { await ElMessageBox.confirm(t('project.deleteConfirm', { name: p.name }), t('common.confirm'), { type: 'warning' }); await deleteProject(workspaceId, p.code); ElMessage.success(t('common.success')); await fetchProjects() }
  catch { /* cancelled */ }
}

async function openPublishDialog(p: Project, e: Event) {
  e.stopPropagation()
  publishProjectCode.value = p.code
  publishRemark.value = ''
  publishRows.value = []
  publishDialogVisible.value = true

  publishLoading.value = true
  try {
    const res: any = await listWorkflowDefinitions(workspaceId, p.code, { pageNum: 1, pageSize: 200 })
    const wfList: any[] = res.data ?? []
    publishRows.value = wfList.map(wf => ({ code: wf.code, name: wf.name, description: wf.description ?? '', versionId: 0, versions: [], versionsLoading: true }))
    await Promise.allSettled(publishRows.value.map(async (row) => {
      try {
        const vRes: any = await listWorkflowDefinitionVersions(workspaceId, p.code, row.code, { pageNum: 1, pageSize: 50 })
        row.versions = vRes.data ?? []
      } catch { /* ignore */ }
      finally { row.versionsLoading = false }
    }))
  } catch { ElMessage.error(t('common.failed')) }
  finally { publishLoading.value = false }
}

async function doProjectPublish() {
  if (publishRows.value.length === 0) return
  publishing.value = true
  try {
    const items = publishRows.value.map(row => {
      const item: { workflowDefinitionCode: string; versionId?: number } = { workflowDefinitionCode: row.code }
      if (row.versionId !== 0) item.versionId = row.versionId
      return item
    })
    await publishProject(workspaceId, publishProjectCode.value, {
      items,
      remark: publishRemark.value.trim() || undefined,
    })
    ElMessage.success(t('common.success'))
    publishDialogVisible.value = false
  } catch { ElMessage.error(t('common.failed')) }
  finally { publishing.value = false }
}

function formatDate(d: string) { return formatDateShort(d) }

onMounted(fetchProjects)
</script>

<template>
  <div class="page-container">
    <div class="list-head">
      <div class="list-head__left">
        <h2 class="list-title">{{ t('project.title') }}</h2>
        <span v-if="total > 0" class="list-count">{{ total }}</span>
      </div>
      <div class="list-head__right">
        <el-input
          v-model="searchText"
          :placeholder="t('common.search')"
          clearable
          :prefix-icon="Search"
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button v-if="canManageProject" type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>{{ t('project.newProject') }}
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="project-grid">
      <div
        v-for="(p, idx) in projects"
        :key="p.id"
        class="project-card"
        :style="{ '--accent': cardColor(p.id), '--i': idx }"
        @click="openProject(p)"
      >
        <div class="project-card__body">
          <div class="project-card__top">
            <div class="project-card__icon">
              <el-icon size="18" color="#fff"><Folder /></el-icon>
            </div>
            <el-dropdown v-if="canEdit" trigger="click" @click.stop>
              <div class="project-card__more" @click.stop><el-icon size="16"><MoreFilled /></el-icon></div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click.stop="openPublishDialog(p, $event)"><el-icon><Upload /></el-icon>{{ t('project.publish') }}</el-dropdown-item>
                  <el-dropdown-item @click.stop="openEditDialog(p, $event)"><el-icon><Edit /></el-icon>{{ t('common.edit') }}</el-dropdown-item>
                  <el-dropdown-item v-if="canManageProject" divided @click.stop="handleDelete(p, $event)">
                    <span style="color: var(--r-danger)"><el-icon><Delete /></el-icon>{{ t('common.delete') }}</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="project-card__name">{{ p.name }}</div>
          <div class="project-card__desc">{{ p.description || t('project.noDescription') }}</div>
          <div class="project-card__footer">
            <div class="project-card__footer-left">
              <el-icon size="12"><Calendar /></el-icon>
              <span>{{ formatDate(p.createdAt) }}</span>
            </div>
            <span class="project-card__enter">
              <el-icon size="12"><Right /></el-icon>
            </span>
          </div>
        </div>
      </div>

      <div v-if="canManageProject" class="project-card project-card--new" :style="{ '--i': projects.length }" @click="openCreateDialog">
        <div class="new-inner">
          <div class="new-icon"><el-icon size="22"><Plus /></el-icon></div>
          <span>{{ t('project.newProject') }}</span>
        </div>
      </div>
    </div>

    <div v-if="!loading && projects.length === 0" class="empty-state">
      <el-empty :description="t('common.noData')" />
    </div>

    <div v-if="total > pageSize" class="pagination-bar" style="border-top: none; padding: 20px 0 0; justify-content: center">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="t('project.createTitle')" width="460px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" label-position="top" @submit.prevent="handleCreate">
        <el-form-item :label="t('common.name')" prop="name" :rules="[{ required: true, message: t('project.nameRequired'), trigger: 'blur' }]">
          <el-input v-model="createForm.name" :placeholder="t('project.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="createForm.description" type="textarea" :rows="3" :placeholder="t('project.descPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" :title="t('project.editTitle')" width="460px" destroy-on-close>
      <el-form :model="editForm" label-position="top">
        <el-form-item :label="t('common.name')"><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item :label="t('common.description')"><el-input v-model="editForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!editForm.name.trim()" @click="handleEdit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Project Publish dialog -->
    <el-dialog v-model="publishDialogVisible" :title="t('project.publishProject')" width="600px" destroy-on-close>
      <div v-loading="publishLoading" class="publish-body">
        <div class="publish-section-label">
          {{ t('project.workflow') }}
          <span v-if="publishRows.length" class="publish-section-count">{{ publishRows.length }}</span>
        </div>
        <div class="publish-list">
          <div v-for="row in publishRows" :key="row.code" class="publish-row">
            <div class="publish-row__info">
              <div class="publish-row__name">
                <el-icon size="14" style="color: var(--r-text-muted)"><Share /></el-icon>
                <span>{{ row.name }}</span>
              </div>
              <div v-if="row.description" class="publish-row__desc">{{ row.description }}</div>
            </div>
            <el-select v-model="row.versionId" :loading="row.versionsLoading" size="small" class="publish-row__select">
              <el-option :value="0" :label="t('workflow.currentVersion')" />
              <el-option v-for="v in row.versions" :key="v.id" :value="v.id" :label="'v' + v.versionNo + (v.remark ? ' - ' + v.remark : '')" />
            </el-select>
          </div>
          <div v-if="!publishLoading && publishRows.length === 0" class="publish-empty">
            <el-empty :image-size="60" :description="t('common.noData')" />
          </div>
        </div>
        <div class="publish-section-label" style="margin-top: 16px">{{ t('workflow.remark') }}</div>
        <el-input v-model="publishRemark" type="textarea" :rows="2" :placeholder="t('workflow.publishRemarkPlaceholder')" />
      </div>
      <template #footer>
        <el-button @click="publishDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="publishing" :disabled="publishRows.length === 0" @click="doProjectPublish">{{ t('project.publish') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
/* ===== Header ===== */
.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.list-head__left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.list-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--r-text-primary);
}
.list-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 11px;
  background: var(--r-bg-hover);
  font-size: 12px;
  font-weight: 500;
  color: var(--r-text-tertiary);
}
.list-head__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ===== Grid ===== */
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

/* ===== Card ===== */
.project-card {
  position: relative;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
  animation: card-in 0.4s cubic-bezier(0.23, 1, 0.32, 1) backwards;
  animation-delay: calc(var(--i, 0) * 0.05s);

  &:not(.project-card--new)::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 4px;
    background: var(--accent, var(--r-border));
    transition: width 0.2s ease;
  }

  &:hover {
    border-color: var(--r-border-dark);
    box-shadow: var(--r-shadow-md);
    transform: translateY(-3px);

    &:not(.project-card--new)::before { width: 6px; }
    .project-card__more { opacity: 1; }
    .project-card__enter { opacity: 1; }
  }
}

.project-card__body {
  padding: 16px 20px 14px 22px;
}

.project-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.project-card__icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent);
}

.project-card__more {
  opacity: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: var(--r-text-tertiary);
  cursor: pointer;
  transition: opacity 0.15s, background 0.15s;

  &:hover {
    background: var(--r-bg-hover);
    color: var(--r-text-secondary);
  }
}

.project-card__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--r-text-primary);
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-card__desc {
  font-size: 12px;
  color: var(--r-text-muted);
  line-height: 1.6;
  height: 38px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.project-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid var(--r-border-light);
  font-size: 12px;
  color: var(--r-text-muted);
}

.project-card__footer-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.project-card__enter {
  opacity: 0;
  color: var(--r-accent);
  transition: opacity 0.15s;
}

/* ===== New-project card ===== */
.project-card--new {
  border-style: dashed;
  border-color: var(--r-border-dark);
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px;

  &:hover {
    border-color: var(--r-accent);
    background: var(--r-accent-bg);
    transform: translateY(-3px);
    box-shadow: var(--r-shadow-md);

    .new-icon { background: var(--r-accent); color: var(--r-text-inverse); }
    span { color: var(--r-accent); }
  }
}

.new-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--r-text-tertiary);
}

.new-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--r-bg-hover);
  color: var(--r-text-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease, color 0.2s ease;
}

/* ===== Publish dialog ===== */
.publish-body {
  min-height: 120px;
}

.publish-section-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--r-text-secondary);
  margin-bottom: 10px;
}

.publish-section-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--r-bg-hover);
  font-size: 11px;
  font-weight: 500;
  color: var(--r-text-tertiary);
}

.publish-list {
  background: var(--r-bg-hover);
  border-radius: 8px;
  padding: 4px 0;
  max-height: 300px;
  overflow-y: auto;
}

.publish-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  transition: background 0.15s;

  &:hover {
    background: var(--r-bg-hover);
  }

  & + .publish-row {
    border-top: 1px solid var(--r-border);
  }
}

.publish-row__info {
  min-width: 0;
  flex: 1;
}

.publish-row__name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--r-text-primary);

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.publish-row__desc {
  margin-top: 3px;
  padding-left: 22px;
  font-size: 12px;
  color: var(--r-text-muted);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-row__select {
  width: 220px;
  flex-shrink: 0;
}

.publish-empty {
  padding: 20px 0;
}

/* ===== Empty state ===== */
.empty-state {
  margin-top: 80px;
}
</style>
