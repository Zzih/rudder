<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getWorkflowDefinition, listWorkflowDefinitionVersions, rollbackWorkflowDefinition, diffWorkflowDefinitionVersions, commitWorkflowDefinitionVersion } from '@/api/workflow'
import { formatDate } from '@/utils/dateFormat'
import { getProject } from '@/api/workspace'
import { cardColor } from '@/utils/colorMeta'

import DagEditor from './DagEditor.vue'
import CronEditor from '@/components/CronEditor.vue'
import WorkflowInstances from './WorkflowInstances.vue'
import DagDiffViewer from '@/components/DagDiffViewer.vue'

interface Version {
  id: number
  versionNo: number
  remark: string
  createdAt: string
  createdBy: number
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const workspaceId = Number(route.params.workspaceId)
const projectCode = route.params.projectCode as string
const workflowDefinitionCode = route.params.workflowDefinitionCode as string

const projectName = ref('')
const projectId = ref(0)
const avatarColor = computed(() => cardColor(projectId.value))
const workflowName = ref('')
const workflowDesc = ref('')
const activeTab = ref('editor')
const dagEditorRef = ref<InstanceType<typeof DagEditor> | null>(null)

// Save dialog
const saveDialogVisible = ref(false)
const saving = ref(false)
const saveForm = reactive({
  name: '',
  description: '',
  cronExpression: '',
  startTime: '',
  endTime: '',
  timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
})

// Global params (Property format)
interface Property {
  prop: string
  direct: 'IN' | 'OUT'
  type: string
  value: string
}

const globalDataTypes = ['VARCHAR', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DATE', 'TIME', 'TIMESTAMP', 'BOOLEAN', 'LIST', 'FILE']
const globalParams = ref<Property[]>([])

function addGlobalParam() {
  globalParams.value.push({ prop: '', direct: 'IN', type: 'VARCHAR', value: '' })
}

function removeGlobalParam(idx: number) {
  globalParams.value.splice(idx, 1)
}

function serializeGlobalParams(): Property[] {
  return globalParams.value.filter(p => p.prop)
}

// Versions
const versions = ref<Version[]>([])
const versionsLoading = ref(false)
const versionsPageNum = ref(1)
const versionsPageSize = ref(10)
const versionsTotal = ref(0)

const workflowInstancesRef = ref<InstanceType<typeof WorkflowInstances> | null>(null)

const timezones = [
  'Asia/Shanghai', 'Asia/Tokyo', 'Asia/Hong_Kong', 'Asia/Singapore', 'Asia/Kolkata',
  'America/New_York', 'America/Chicago', 'America/Los_Angeles', 'America/Sao_Paulo',
  'Europe/London', 'Europe/Paris', 'Europe/Berlin', 'Europe/Moscow',
  'Australia/Sydney', 'Pacific/Auckland', 'UTC',
]

async function fetchWorkflow() {
  const [projRes, wfRes] = await Promise.allSettled([
    getProject(workspaceId, projectCode),
    getWorkflowDefinition(workspaceId, projectCode, workflowDefinitionCode),
  ])
  if (projRes.status === 'fulfilled') {
    const d = projRes.value.data
    projectName.value = d?.name ?? ''
    projectId.value = d?.id ?? 0
  }
  if (wfRes.status === 'fulfilled') {
    const d = wfRes.value.data
    workflowName.value = d?.name ?? ''
    workflowDesc.value = d?.description ?? ''
    saveForm.cronExpression = d?.cronExpression || ''
    saveForm.startTime = d?.startTime || ''
    saveForm.endTime = d?.endTime || ''
    saveForm.timezone = d?.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone
    if (d?.globalParams && Array.isArray(d.globalParams)) {
      globalParams.value = d.globalParams
    }
  }
}

async function fetchVersions() {
  versionsLoading.value = true
  try {
    const res: any = await listWorkflowDefinitionVersions(workspaceId, projectCode, workflowDefinitionCode, { pageNum: versionsPageNum.value, pageSize: versionsPageSize.value })
    versions.value = res.data ?? []
    versionsTotal.value = res.total ?? 0
  } catch { ElMessage.error(t('common.failed')) }
  finally { versionsLoading.value = false }
}

function openSaveDialog() {
  saveForm.name = workflowName.value
  saveForm.description = workflowDesc.value
  saveDialogVisible.value = true
}

async function confirmSave() {
  if (!saveForm.name.trim()) {
    ElMessage.warning(t('common.name') + ' ' + t('common.required'))
    return
  }
  saving.value = true
  try {
    // 一次请求发送所有字段：DAG + metadata + globalParams + schedule
    await dagEditorRef.value?.handleSave({
      name: saveForm.name,
      description: saveForm.description,
      cronExpression: saveForm.cronExpression || undefined,
      startTime: saveForm.startTime || undefined,
      endTime: saveForm.endTime || undefined,
      timezone: saveForm.timezone || undefined,
      globalParams: serializeGlobalParams(),
    })
    saveDialogVisible.value = false
    ElMessage.success(t('common.success'))
    handleBack()
  } catch { ElMessage.error(t('common.failed')) }
  finally { saving.value = false }
}

async function handleRun() {
  await dagEditorRef.value?.handleRun()
  if (activeTab.value === 'history') workflowInstancesRef.value?.fetchInstances()
}

function handleTabChange(tab: string | number) {
  if (tab === 'versions') fetchVersions()
}

function handleBack() {
  router.push(`/workspaces/${workspaceId}/projects/${projectCode}/workflow-definitions`)
}

// Version diff dialog
const diffDialogVisible = ref(false)
const dagDiffData = ref<any>(null)
const diffTitle = ref('')

const diffLoading = ref(false)
const rollbackLoading = ref(false)

async function handleVersionDiff(version: Version) {
  if (versions.value.length < 1) return
  const latest = versions.value[0]
  if (latest.id === version.id) return
  diffLoading.value = true
  try {
    const res: any = await diffWorkflowDefinitionVersions(workspaceId, projectCode, workflowDefinitionCode, version.id, latest.id)
    dagDiffData.value = res.data?.dagDiff ?? null
    diffTitle.value = `v${version.versionNo} → v${latest.versionNo}`
    diffDialogVisible.value = true
  } catch { ElMessage.error(t('common.failed')) }
  finally { diffLoading.value = false }
}

async function handleVersionRollback(version: Version) {
  rollbackLoading.value = true
  try {
    await rollbackWorkflowDefinition(workspaceId, projectCode, workflowDefinitionCode, version.id)
    ElMessage.success(t('ide.rollbackSuccess'))
    fetchWorkflow()
    fetchVersions()
    dagEditorRef.value?.reload()
  } catch { ElMessage.error(t('common.failed')) }
  finally { rollbackLoading.value = false }
}

// Commit version dialog
const commitDialogVisible = ref(false)
const commitMessage = ref('')
const committing = ref(false)

function handleCommit() {
  commitMessage.value = ''
  commitDialogVisible.value = true
}

async function doCommit() {
  if (!commitMessage.value.trim()) return
  committing.value = true
  try {
    // Auto-save DAG before commit
    await dagEditorRef.value?.handleSave({
      name: workflowName.value,
      description: workflowDesc.value,
    })
    await commitWorkflowDefinitionVersion(workspaceId, projectCode, workflowDefinitionCode, { message: commitMessage.value.trim() })
    commitDialogVisible.value = false
    ElMessage.success(t('ide.commitSuccess'))
    activeTab.value = 'versions'
    fetchVersions()
  } catch { ElMessage.error(t('common.failed')) }
  finally { committing.value = false }
}

onMounted(fetchWorkflow)
</script>

<template>
  <div class="wfd">
    <!-- ═══ Top bar ═══ -->
    <div class="wfd-bar">
      <div class="wfd-bar__nav">
        <button class="wfd-back" @click="handleBack">
          <el-icon size="14"><ArrowLeft /></el-icon>
        </button>
        <div class="wfd-bar__avatar" :style="{ background: avatarColor }" @click="handleBack">
          <el-icon size="14"><Folder /></el-icon>
        </div>
        <div class="wfd-bar__crumbs">
          <span class="wfd-bar__project-name" @click="handleBack">{{ projectName }}</span>
          <span class="wfd-bar__sep">/</span>
          <span class="wfd-bar__wf-name">{{ workflowName }}</span>
        </div>
      </div>

      <div class="wfd-bar__main">
        <nav class="wfd-tabs" role="tablist">
          <button
            v-for="tab in [
              { key: 'editor', label: t('workflow.dagEditor') },
              { key: 'history', label: t('workflow.runHistory') },
              { key: 'versions', label: t('workflow.versions') },
            ]"
            :key="tab.key"
            role="tab"
            :aria-selected="activeTab === tab.key"
            class="wfd-tabs__item"
            :class="{ 'is-active': activeTab === tab.key }"
            @click="activeTab = tab.key; handleTabChange(tab.key)"
          >
            {{ tab.label }}
          </button>
        </nav>

        <div class="wfd-bar__actions">
          <el-button class="wfd-btn wfd-btn--run" size="small" @click="handleRun">
            <el-icon><VideoPlay /></el-icon> {{ t('workflow.run') }}
          </el-button>
          <el-button class="wfd-btn" size="small" @click="openSaveDialog">
            <el-icon><FolderChecked /></el-icon> {{ t('ide.save') }}
          </el-button>
          <el-button class="wfd-btn wfd-btn--commit" size="small" @click="handleCommit">
            <el-icon><Promotion /></el-icon> {{ t('ide.commitVersion') }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- ═══ Content ═══ -->
    <div class="wfd-content">
      <DagEditor v-show="activeTab === 'editor'" ref="dagEditorRef" :workflow-definition-code="workflowDefinitionCode" />

      <Transition name="wfd-fade" mode="out-in">
        <div v-if="activeTab === 'versions'" key="versions" class="wfd-panel">
          <div class="wfd-card" v-loading="versionsLoading">
            <div class="wfd-card__header">
              <div class="wfd-card__title-row">
                <span class="wfd-card__title">{{ t('workflow.versions') }}</span>
                <span v-if="versionsTotal" class="wfd-card__badge">{{ versionsTotal }}</span>
              </div>
              <el-button size="small" text type="primary" @click="fetchVersions">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>

            <!-- Timeline -->
            <div v-if="versions.length" class="wfd-tl">
              <div
                v-for="(ver, idx) in versions"
                :key="ver.id"
                class="wfd-tl__item"
                :style="{ '--stagger': `${idx * 50}ms` }"
              >
                <div class="wfd-tl__rail">
                  <div class="wfd-tl__dot" :class="{ 'is-head': idx === 0 }" />
                  <div v-if="idx < versions.length - 1" class="wfd-tl__stem" />
                </div>
                <div class="wfd-tl__body">
                  <div class="wfd-tl__row">
                    <el-tag size="small" :type="idx === 0 ? 'primary' : 'info'" effect="plain" round>
                      v{{ ver.versionNo }}
                    </el-tag>
                    <span class="wfd-tl__date">{{ formatDate(ver.createdAt) }}</span>
                  </div>
                  <p v-if="ver.remark" class="wfd-tl__msg">{{ ver.remark }}</p>
                  <div v-if="idx > 0" class="wfd-tl__ops">
                    <el-button text type="primary" size="small" :loading="diffLoading" @click="handleVersionDiff(ver)">{{ t('ide.compare') }}</el-button>
                    <el-popconfirm :title="t('ide.rollbackConfirm')" @confirm="handleVersionRollback(ver)">
                      <template #reference>
                        <el-button text type="warning" size="small" :loading="rollbackLoading">{{ t('ide.rollback') }}</el-button>
                      </template>
                    </el-popconfirm>
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-else :image-size="60" style="padding: 40px 0" />

            <div v-if="versionsTotal > versionsPageSize" class="wfd-pagination">
              <el-pagination
                background small
                layout="total, prev, pager, next"
                :total="versionsTotal"
                :current-page="versionsPageNum"
                :page-size="versionsPageSize"
                @current-change="(p: number) => { versionsPageNum = p; fetchVersions() }"
              />
            </div>
          </div>
        </div>

        <div v-else-if="activeTab === 'history'" key="history" class="wfd-panel">
          <WorkflowInstances
            ref="workflowInstancesRef"
            :workflow-definition-code="workflowDefinitionCode"
            :show-toolbar="false"
          />
        </div>
      </Transition>
    </div>

    <!-- ═══ Save Dialog ═══ -->
    <el-dialog v-model="saveDialogVisible" :title="t('workflow.saveSettings')" width="680px" destroy-on-close top="6vh">
      <el-form label-position="top" class="save-form">
        <el-form-item :label="t('common.name')" required>
          <el-input v-model="saveForm.name" />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="saveForm.description" type="textarea" :rows="2" />
        </el-form-item>

        <el-collapse class="save-form__collapse">
          <el-collapse-item :title="t('workflow.globalParams') + (globalParams.length ? ` (${globalParams.length})` : '')" name="globalParams">
            <div class="save-form__section">
              <div class="gp-hint">{{ t('workflow.globalParamsHint') }}</div>
              <div v-if="globalParams.length" class="gp-header">
                <span class="gp-col gp-col--name">{{ t('workflow.paramProp') }}</span>
                <span class="gp-col gp-col--type">{{ t('workflow.paramType') }}</span>
                <span class="gp-col gp-col--value">{{ t('workflow.paramValue') }}</span>
                <span class="gp-col gp-col--action"></span>
              </div>
              <div v-for="(param, idx) in globalParams" :key="idx" class="gp-row">
                <el-input v-model="param.prop" size="small" placeholder="prop" class="gp-col gp-col--name" />
                <el-select v-model="param.type" size="small" class="gp-col gp-col--type">
                  <el-option v-for="dt in globalDataTypes" :key="dt" :label="dt" :value="dt" />
                </el-select>
                <el-input v-model="param.value" size="small" :placeholder="t('workflow.globalParamValueHint')" class="gp-col gp-col--value" />
                <el-button class="gp-col gp-col--action" type="danger" circle size="small" @click="removeGlobalParam(idx)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-button size="small" type="primary" plain @click="addGlobalParam" style="margin-top: 6px">
                <el-icon><Plus /></el-icon> {{ t('workflow.addGlobalParam') }}
              </el-button>
            </div>
          </el-collapse-item>
          <el-collapse-item :title="t('workflow.scheduleConfig')" name="schedule">
            <div class="save-form__section">
              <el-form-item :label="t('workflow.cronExpr')" style="margin-bottom: 12px">
                <el-input :model-value="saveForm.cronExpression" readonly>
                  <template #prefix><el-icon><Timer /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-row :gutter="16" style="margin-bottom: 12px">
                <el-col :span="8">
                  <el-form-item :label="t('workflow.startTime')">
                    <el-date-picker v-model="saveForm.startTime" type="datetime" style="width:100%" :placeholder="t('common.optional')" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item :label="t('workflow.endTime')">
                    <el-date-picker v-model="saveForm.endTime" type="datetime" style="width:100%" :placeholder="t('common.optional')" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item :label="t('workflow.timezone')">
                    <el-select v-model="saveForm.timezone" filterable style="width:100%">
                      <el-option v-for="tz in timezones" :key="tz" :label="tz" :value="tz" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <CronEditor v-model="saveForm.cronExpression" />
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <el-button @click="saveDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="confirmSave">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- ═══ Version Diff Dialog ═══ -->
    <el-dialog v-model="diffDialogVisible" :title="diffTitle" width="640px" destroy-on-close top="6vh">
      <DagDiffViewer :dag-diff="dagDiffData" />
    </el-dialog>

    <!-- ═══ Commit Dialog ═══ -->
    <el-dialog v-model="commitDialogVisible" :title="t('ide.commitVersion')" width="420px" destroy-on-close @submit.prevent>
      <el-form @submit.prevent>
        <el-form-item :label="t('ide.commitMessage')" required>
          <el-input v-model="commitMessage" type="textarea" :rows="3" :placeholder="t('ide.commitMessagePlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="commitDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="committing" :disabled="!commitMessage.trim()" @click="doCommit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
/* ╔══════════════════════════════════════════════════════════╗
   ║  WorkflowDetail — Refined Industrial                    ║
   ╚══════════════════════════════════════════════════════════╝ */

.wfd {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--r-bg-panel);
}

/* ═══════════════════════════════════════
   Top Bar
   ═══════════════════════════════════════ */
.wfd-bar {
  display: flex;
  align-items: stretch;
  height: 48px;
  background: var(--r-bg-card);
  border-bottom: 1px solid var(--r-border);
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.04);
  flex-shrink: 0;
  position: relative;
  z-index: 10;
}

/* ── Left: breadcrumb nav ── */
.wfd-bar__nav {
  width: 208px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 12px;
  border-right: 1px solid var(--r-border);
  overflow: hidden;
}

.wfd-back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--r-text-muted);
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease, transform 0.15s ease;

  &:hover {
    background: var(--r-bg-hover);
    color: var(--r-text-primary);
    transform: translateX(-1px);
  }
}

.wfd-bar__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  color: #fff;
  flex-shrink: 0;
  cursor: pointer;
  transition: opacity 0.15s ease, transform 0.15s ease;

  &:hover {
    opacity: 0.85;
    transform: scale(1.06);
  }
}

.wfd-bar__crumbs {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.wfd-bar__sep {
  color: var(--r-text-disabled);
  font-size: 12px;
  flex-shrink: 0;
  user-select: none;
}

.wfd-bar__project-name {
  font-size: 12px;
  color: var(--r-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  transition: color 0.15s;
  flex-shrink: 0;
  max-width: 70px;

  &:hover { color: var(--r-accent); }
}

.wfd-bar__wf-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--r-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ── Right: tabs + actions ── */
.wfd-bar__main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  min-width: 0;
}

/* ═══════════════════════════════════════
   Tabs — underline indicator
   ═══════════════════════════════════════ */
.wfd-tabs {
  display: flex;
  align-items: stretch;
  height: 100%;
  gap: 0;
}

.wfd-tabs__item {
  position: relative;
  display: flex;
  align-items: center;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--r-text-tertiary);
  background: none;
  border: none;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.2s ease;

  &::after {
    content: '';
    position: absolute;
    bottom: -1px;
    left: 16px;
    right: 16px;
    height: 2px;
    background: var(--r-accent);
    border-radius: 2px 2px 0 0;
    transform: scaleX(0);
    transform-origin: center;
    transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  }

  &:hover {
    color: var(--r-text-primary);
  }

  &.is-active {
    color: var(--r-accent);
    font-weight: 600;

    &::after {
      transform: scaleX(1);
    }
  }
}

/* ═══════════════════════════════════════
   Action Buttons
   ═══════════════════════════════════════ */
.wfd-bar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wfd-bar__actions .wfd-btn {
  border-radius: 8px;
  font-weight: 500;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;

  &.wfd-btn--run {
    background: var(--r-success);
    border-color: var(--r-success);
    color: #fff;

    &:hover {
      box-shadow: 0 2px 14px rgb(34 197 94 / 0.35);
      transform: translateY(-1px);
    }
    &:active {
      transform: translateY(0);
    }
  }

  &.wfd-btn--commit {
    background: var(--r-accent-bg);
    border-color: var(--r-accent-border);
    color: var(--r-accent);

    &:hover {
      background: var(--r-accent);
      border-color: var(--r-accent);
      color: #fff;
    }
  }
}

/* ═══════════════════════════════════════
   Content area
   ═══════════════════════════════════════ */
.wfd-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
}

.wfd-panel {
  flex: 1;
  overflow: auto;
  padding: 24px 28px;
}

/* ── panel transition ── */
.wfd-fade-enter-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.wfd-fade-leave-active {
  transition: opacity 0.1s ease;
}
.wfd-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.wfd-fade-leave-to {
  opacity: 0;
}

/* ═══════════════════════════════════════
   Card
   ═══════════════════════════════════════ */
.wfd-card {
  background: var(--r-bg-card);
  border-radius: 12px;
  border: 1px solid var(--r-border);
  box-shadow: var(--r-shadow-sm);
  overflow: hidden;
}

.wfd-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--r-border-light);
}

.wfd-card__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wfd-card__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--r-text-primary);
}

.wfd-card__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--r-accent);
  background: var(--r-accent-bg);
  border-radius: 10px;
}

/* ═══════════════════════════════════════
   Version Timeline
   ═══════════════════════════════════════ */
.wfd-tl {
  padding: 20px 20px 4px;
}

.wfd-tl__item {
  display: flex;
  gap: 16px;
  animation: tl-enter 0.35s ease both;
  animation-delay: var(--stagger, 0ms);
}

@keyframes tl-enter {
  from { opacity: 0; transform: translateX(-8px); }
  to   { opacity: 1; transform: translateX(0); }
}

/* ── rail: dot + stem ── */
.wfd-tl__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 14px;
  flex-shrink: 0;
  padding-top: 6px;
}

.wfd-tl__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--r-border-dark);
  border: 2px solid var(--r-bg-card);
  box-shadow: 0 0 0 2px var(--r-border);
  flex-shrink: 0;
  transition: background 0.2s ease, box-shadow 0.2s ease;

  &.is-head {
    width: 12px;
    height: 12px;
    background: var(--r-accent);
    box-shadow: 0 0 0 3px var(--r-accent-bg), 0 0 10px rgb(59 130 246 / 0.25);
    border-color: var(--r-bg-card);
  }
}

.wfd-tl__stem {
  width: 2px;
  flex: 1;
  min-height: 16px;
  background: var(--r-border);
  margin: 4px 0;
  border-radius: 1px;
}

/* ── body: content beside the rail ── */
.wfd-tl__body {
  flex: 1;
  min-width: 0;
  padding-bottom: 22px;
}

.wfd-tl__row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.wfd-tl__date {
  font-size: 12px;
  color: var(--r-text-muted);
  font-variant-numeric: tabular-nums;
  margin-left: auto;
}

.wfd-tl__msg {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--r-text-secondary);
}

.wfd-tl__ops {
  display: flex;
  gap: 4px;
  margin-top: 8px;
  opacity: 0;
  transform: translateY(2px);
  transition: opacity 0.18s ease, transform 0.18s ease;

  .wfd-tl__item:hover &,
  .wfd-tl__item:focus-within & {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ═══════════════════════════════════════
   Pagination
   ═══════════════════════════════════════ */
.wfd-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 20px;
  border-top: 1px solid var(--r-border-light);
}

/* ═══════════════════════════════════════
   Save dialog
   ═══════════════════════════════════════ */
.save-form {
  :deep(.el-form-item) { margin-bottom: 16px; }
  :deep(.el-form-item__label) { font-size: 13px; color: var(--r-text-secondary); padding-bottom: 4px; }
}

.save-form__collapse {
  border: none;
  margin-top: 4px;

  :deep(.el-collapse-item__header) {
    font-size: 13px;
    font-weight: 600;
    color: var(--r-accent);
    height: 40px;
    background: none;
    border: none;
    border-radius: 8px;
    padding: 0 8px;
    transition: background 0.15s;

    &:hover { background: var(--r-bg-hover); }
  }

  :deep(.el-collapse-item__wrap) { border: none; background: none; }
  :deep(.el-collapse-item__content) { padding-bottom: 0; }
}

.save-form__section {
  background: var(--r-bg-panel);
  border-radius: 10px;
  padding: 16px;
  border: 1px solid var(--r-border);
}

/* ── Global params ── */
.gp-hint {
  font-size: 12px;
  color: var(--r-text-tertiary);
  margin-bottom: 10px;
}

.gp-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;

  .gp-col {
    font-size: 11px;
    font-weight: 600;
    color: var(--r-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }
}

.gp-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.gp-col {
  &--name   { flex: 5; min-width: 0; }
  &--type   { flex: 4; min-width: 0; }
  &--value  { flex: 7; min-width: 0; }
  &--action { flex: 0 0 32px; }
}
</style>
