<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { listWorkflowDefinitions, createWorkflowDefinition, deleteWorkflowDefinition, publishWorkflowDefinition, runWorkflowDefinition, listWorkflowDefinitionVersions } from '@/api/workflow'
import { formatDate } from '@/utils/dateFormat'
import { usePermission } from '@/composables/usePermission'

const { canEdit } = usePermission()

interface WorkflowDefinition {
  id: number; code: string; name: string; description: string; publishedVersionId: number | null; updatedAt: string
  cronExpression?: string; scheduleStatus?: string; timezone?: string
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const workspaceId = Number(route.params.workspaceId)
const projectCode = route.params.projectCode as string

const workflows = ref<WorkflowDefinition[]>([])
const loading = ref(false)
const searchText = ref('')
const dialogVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = ref({ name: '', description: '' })
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// Publish dialog
const publishDialogVisible = ref(false)
const publishLoading = ref(false)
const publishing = ref(false)
const publishTarget = ref<WorkflowDefinition | null>(null)
const publishForm = ref<{ versionId: number | null; remark: string }>({ versionId: -1, remark: '' })
const versionList = ref<{ id: number; versionNo: number; remark: string; createdAt: string }[]>([])

async function fetchWorkflows() {
  loading.value = true
  try {
    const res: any = await listWorkflowDefinitions(workspaceId, projectCode, {
      searchVal: searchText.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    workflows.value = res.data ?? []
    total.value = res.total ?? 0
  }
  catch { ElMessage.error(t('common.failed')) }
  finally { loading.value = false }
}

function handleSearch() { pageNum.value = 1; fetchWorkflows() }
function handlePageChange(page: number) { pageNum.value = page; fetchWorkflows() }
function handleSizeChange(size: number) { pageSize.value = size; pageNum.value = 1; fetchWorkflows() }

function openCreateDialog() { createForm.value = { name: '', description: '' }; dialogVisible.value = true }

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    await createWorkflowDefinition(workspaceId, projectCode, createForm.value)
    ElMessage.success(t('common.success')); dialogVisible.value = false; await fetchWorkflows()
  } catch { ElMessage.error(t('common.failed')) } finally { creating.value = false }
}

function handleEdit(row: WorkflowDefinition) {
  router.push(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions/${row.code}`)
}

async function handleRun(row: WorkflowDefinition) {
  try {
    await ElMessageBox.confirm(t('workflow.runConfirm', { name: row.name }), t('common.confirm'), { confirmButtonText: t('workflow.run'), type: 'info' })
    await runWorkflowDefinition(workspaceId, projectCode, row.code)
    ElMessage.success(t('workflow.runSuccess'))
  } catch (e) { if (e !== 'cancel') ElMessage.error(t('common.failed')) }
}

async function handlePublish(row: WorkflowDefinition) {
  publishTarget.value = row
  publishForm.value = { versionId: -1, remark: '' }
  versionList.value = []
  publishDialogVisible.value = true

  publishLoading.value = true
  try {
    const res: any = await listWorkflowDefinitionVersions(workspaceId, projectCode, row.code, { pageNum: 1, pageSize: 50 })
    versionList.value = res.data ?? []
  } catch { /* ignore */ }
  finally { publishLoading.value = false }
}

async function doPublish() {
  if (!publishTarget.value) return
  publishing.value = true
  try {
    const data: { versionId?: number; remark?: string } = {}
    if (publishForm.value.versionId && publishForm.value.versionId > 0) data.versionId = publishForm.value.versionId
    if (publishForm.value.remark.trim()) data.remark = publishForm.value.remark.trim()
    await publishWorkflowDefinition(workspaceId, projectCode, publishTarget.value.code, data)
    ElMessage.success(t('workflow.approvalSuccess'))
    publishDialogVisible.value = false
    await fetchWorkflows()
  } catch { ElMessage.error(t('common.failed')) }
  finally { publishing.value = false }
}

async function handleDelete(row: WorkflowDefinition) {
  try {
    await ElMessageBox.confirm(t('workflow.deleteConfirm', { name: row.name }), t('common.confirm'), { confirmButtonText: t('common.delete'), type: 'warning' })
    await deleteWorkflowDefinition(workspaceId, projectCode, row.code)
    ElMessage.success(t('common.success')); await fetchWorkflows()
  } catch (e) { if (e !== 'cancel') ElMessage.error(t('common.failed')) }
}

onMounted(fetchWorkflows)
</script>

<template>
  <div class="wf">
    <!-- Toolbar -->
    <div class="wf-bar">
      <el-input
        v-model="searchText"
        :placeholder="t('common.search')"
        clearable
        :prefix-icon="Search"
        class="wf-bar__search"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button v-if="canEdit" type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>{{ t('workflow.create') }}
      </el-button>
    </div>

    <!-- Table -->
    <div class="wf-table" v-loading="loading">
      <table v-if="workflows.length || loading">
        <thead>
          <tr>
            <th class="wf-th" style="min-width: 200px">{{ t('common.name') }}</th>
            <th class="wf-th" style="min-width: 140px">{{ t('common.description') }}</th>
            <th class="wf-th" style="width: 170px">{{ t('workflow.scheduleConfig') }}</th>
            <th class="wf-th" style="width: 90px; text-align: center">{{ t('common.status') }}</th>
            <th class="wf-th" style="width: 140px">{{ t('common.updatedAt') }}</th>
            <th class="wf-th" style="width: 52px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in workflows" :key="row.id" class="wf-row" @click="handleEdit(row)">
            <td class="wf-td">
              <div class="wf-name">
                <span class="wf-name__icon"><el-icon size="15"><Share /></el-icon></span>
                <span class="wf-name__text">{{ row.name }}</span>
              </div>
            </td>
            <td class="wf-td">
              <span class="wf-desc">{{ row.description || '-' }}</span>
            </td>
            <td class="wf-td">
              <template v-if="row.cronExpression">
                <code class="wf-cron">{{ row.cronExpression }}</code>
                <span :class="['wf-sched-dot', row.scheduleStatus === 'ONLINE' ? 'wf-sched-dot--on' : 'wf-sched-dot--off']" />
              </template>
              <span v-else class="wf-desc">-</span>
            </td>
            <td class="wf-td" style="text-align: center">
              <span :class="['wf-status', row.publishedVersionId ? 'wf-status--pub' : 'wf-status--draft']">
                <span class="wf-status__dot" />
                {{ row.publishedVersionId ? t('workflow.published') : t('workflow.draft') }}
              </span>
            </td>
            <td class="wf-td">
              <span class="wf-date">{{ formatDate(row.updatedAt) }}</span>
            </td>
            <td class="wf-td wf-td--actions" @click.stop>
              <el-dropdown v-if="canEdit" trigger="click" @command="(cmd: string) => {
                if (cmd === 'edit') handleEdit(row)
                else if (cmd === 'run') handleRun(row)
                else if (cmd === 'publish') handlePublish(row)
                else if (cmd === 'delete') handleDelete(row)
              }">
                <button class="wf-more"><el-icon size="16"><MoreFilled /></el-icon></button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit"><el-icon><Edit /></el-icon>{{ t('common.edit') }}</el-dropdown-item>
                    <el-dropdown-item command="run"><el-icon><VideoPlay /></el-icon>{{ t('workflow.run') }}</el-dropdown-item>
                    <el-dropdown-item command="publish"><el-icon><Upload /></el-icon>{{ t('workflow.publish') }}</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <span style="color: var(--r-danger)"><el-icon><Delete /></el-icon>{{ t('common.delete') }}</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Empty -->
      <div v-if="!loading && workflows.length === 0" class="wf-empty">
        <svg class="wf-empty__icon" width="48" height="48" viewBox="0 0 48 48" fill="none">
          <rect x="8" y="12" width="32" height="26" rx="4" stroke="var(--r-border-dark)" stroke-width="1.4" fill="none"/>
          <path d="M16 22h8M16 28h14" stroke="var(--r-text-disabled)" stroke-width="1.4" stroke-linecap="round"/>
          <circle cx="36" cy="14" r="6" fill="var(--r-accent-bg)" stroke="var(--r-accent)" stroke-width="1.2"/>
          <path d="M34 14h4M36 12v4" stroke="var(--r-accent)" stroke-width="1.2" stroke-linecap="round"/>
        </svg>
        <p>{{ t('common.noData') }}</p>
      </div>

      <!-- Pagination -->
      <div v-if="total > pageSize" class="wf-pager">
        <el-pagination
          background small
          layout="total, prev, pager, next"
          :total="total"
          :current-page="pageNum"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- Create dialog -->
    <el-dialog v-model="dialogVisible" :title="t('workflow.createTitle')" width="440px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" label-position="top" @submit.prevent="handleCreate">
        <el-form-item :label="t('common.name')" prop="name" :rules="[{ required: true, message: t('common.required'), trigger: 'blur' }]">
          <el-input v-model="createForm.name" :placeholder="t('workflow.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Publish dialog -->
    <el-dialog v-model="publishDialogVisible" :title="t('workflow.publishTitle')" width="440px" destroy-on-close>
      <el-form label-position="top" @submit.prevent>
        <el-form-item :label="t('workflow.selectVersion')" required>
          <el-select v-model="publishForm.versionId" :loading="publishLoading" :placeholder="t('workflow.selectVersionPlaceholder')" style="width: 100%">
            <el-option :value="-1" :label="t('workflow.currentVersion')" />
            <el-option v-for="v in versionList" :key="v.id" :value="v.id" :label="'v' + v.versionNo + (v.remark ? ' - ' + v.remark : '')">
              <div class="wf-ver-opt">
                <span class="wf-ver-opt__no">v{{ v.versionNo }}</span>
                <span v-if="v.remark" class="wf-ver-opt__rm">{{ v.remark }}</span>
                <span class="wf-ver-opt__date">{{ formatDate(v.createdAt) }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item :label="t('workflow.remark')">
          <el-input v-model="publishForm.remark" type="textarea" :rows="2" :placeholder="t('workflow.publishRemarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="publishing" :disabled="publishForm.versionId == null" @click="doPublish">{{ t('workflow.publish') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.wf { padding: 20px 24px; }

/* ── Toolbar ── */
.wf-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 12px;
}

.wf-bar__search { width: 240px; }

/* ── Table ── */
.wf-table {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 10px;
  overflow: hidden;

  table {
    width: 100%;
    border-collapse: collapse;
  }
}

.wf-th {
  padding: 9px 16px;
  font-size: 11px;
  font-weight: 600;
  color: var(--r-text-muted);
  text-align: left;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--r-border);
  background: var(--r-bg-panel);
  white-space: nowrap;
  user-select: none;
}

.wf-row {
  cursor: pointer;
  transition: background 0.12s ease;

  &:nth-child(even) { background: var(--r-bg-panel); }

  &:hover {
    background: var(--r-bg-hover) !important;
    .wf-more { opacity: 1; }
  }

  .wf-td {
    border-bottom: 1px solid var(--r-border-light);
  }
}

.wf-td {
  padding: 10px 16px;
  font-size: 13px;
  vertical-align: middle;
}

.wf-td--actions {
  padding: 8px 12px;
  text-align: center;
}

/* ── Name cell ── */
.wf-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wf-name__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: var(--r-accent-bg);
  color: var(--r-accent);
  flex-shrink: 0;
}

.wf-name__text {
  font-weight: 500;
  color: var(--r-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  .wf-row:hover & { color: var(--r-accent); }
}

/* ── Cells ── */
.wf-desc {
  color: var(--r-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
  max-width: 240px;
}

.wf-cron {
  font-size: 11px;
  font-family: var(--r-font-mono);
  color: var(--r-text-secondary);
  background: var(--r-bg-hover);
  padding: 2px 7px;
  border-radius: 4px;
  letter-spacing: -0.02em;
}

.wf-sched-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-left: 6px;
  vertical-align: middle;

  &--on { background: var(--r-success); box-shadow: 0 0 0 2px var(--r-success-bg); }
  &--off { background: var(--r-text-disabled); }
}

.wf-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 500;
}

.wf-status__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.wf-status--pub {
  color: var(--r-success);
  .wf-status__dot { background: var(--r-success); }
}

.wf-status--draft {
  color: var(--r-text-muted);
  .wf-status__dot { background: var(--r-text-disabled); }
}

.wf-date {
  font-size: 12px;
  color: var(--r-text-muted);
  font-variant-numeric: tabular-nums;
}

/* ── More button ── */
.wf-more {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--r-text-muted);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.12s, background 0.12s, color 0.12s;

  &:hover {
    background: var(--r-bg-active);
    color: var(--r-text-primary);
  }
}

/* ── Empty ── */
.wf-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 20px;
  gap: 10px;

  p {
    margin: 0;
    font-size: 13px;
    color: var(--r-text-muted);
  }
}

/* ── Pagination ── */
.wf-pager {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px;
  border-top: 1px solid var(--r-border-light);
}

/* ── Publish version option ── */
.wf-ver-opt {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.wf-ver-opt__no { font-weight: 600; }
.wf-ver-opt__rm { color: var(--r-text-tertiary); font-size: 13px; }
.wf-ver-opt__date { color: var(--r-text-muted); font-size: 12px; margin-left: auto; }
</style>
