<template>
  <div class="tab-pane">
    <!-- 顶部:搜索 + 来源筛选 + 刷新 -->
    <div class="tab-bar">
      <el-input v-model="search" :placeholder="t('aiAdmin.tools.searchPlaceholder')"
        size="small" clearable style="width: 240px" :prefix-icon="Search" />
      <el-select v-model="sourceFilter" size="small" style="width: 140px" clearable
        :placeholder="t('aiAdmin.tools.sourceAll')">
        <el-option label="NATIVE" value="NATIVE" />
        <el-option label="MCP" value="MCP" />
        <el-option label="SKILL" value="SKILL" />
      </el-select>
      <el-button size="small" :loading="loading" @click="load">
        <el-icon><Refresh /></el-icon>{{ t('common.refresh') }}
      </el-button>
      <span class="bar-hint">{{ t('aiAdmin.tools.hint') }}</span>
    </div>

    <el-table :data="filteredRows" v-loading="loading" size="small" stripe
      @row-click="(row: ToolViewVO) => openDetail(row)" style="cursor: pointer">
      <el-table-column :label="t('aiAdmin.tools.name')" min-width="260">
        <template #default="{ row }">
          <el-tag size="small" :color="sourceColor(row.source)" effect="light"
            style="border: none; color: #fff; margin-right: 8px">{{ row.source }}</el-tag>
          <code class="tool-name">{{ row.name }}</code>
        </template>
      </el-table-column>
      <el-table-column :label="t('aiAdmin.tools.description')" min-width="280">
        <template #default="{ row }">
          <span class="desc">{{ row.description || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('aiAdmin.tools.defaultPerm')" width="220">
        <template #default="{ row }">
          <span class="perm-line">
            <el-tag size="small" type="info">{{ row.defaultPermission.minRole }}</el-tag>
            <el-tag v-if="row.defaultPermission.requireConfirm" size="small" type="warning" effect="plain">{{ t('aiAdmin.tools.needConfirm') }}</el-tag>
            <el-tag v-if="row.defaultPermission.readOnly" size="small" type="success" effect="plain">{{ t('aiAdmin.tools.readOnly') }}</el-tag>
          </span>
        </template>
      </el-table-column>
      <el-table-column :label="t('aiAdmin.tools.config')" width="180">
        <template #default="{ row }">
          <el-tag v-if="row.config" size="small"
            :type="row.config.enabled ? 'warning' : 'danger'">
            {{ row.config.enabled ? configScopeLabel(row.config) : t('aiAdmin.tools.hidden') }}
          </el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="150" align="right">
        <template #default="{ row }">
          <el-button link size="small" @click.stop="openConfigDialog(row)">
            {{ row.config ? t('aiAdmin.tools.editConfig') : t('aiAdmin.tools.createConfig') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :size="520" :title="detailRow?.name || ''">
      <div v-if="detailRow" class="detail">
        <div class="detail__row">
          <span class="detail__label">{{ t('aiAdmin.tools.source') }}</span>
          <el-tag size="small" :color="sourceColor(detailRow.source)" effect="light" style="border: none; color: #fff">
            {{ detailRow.source }}
          </el-tag>
        </div>
        <div class="detail__row">
          <span class="detail__label">{{ t('aiAdmin.tools.description') }}</span>
          <div class="detail__body">{{ detailRow.description || '—' }}</div>
        </div>
        <div class="detail__row">
          <span class="detail__label">{{ t('aiAdmin.tools.defaultPerm') }}</span>
          <div class="detail__body">
            <div class="perm-line">
              <el-tag size="small" type="info">{{ detailRow.defaultPermission.minRole }}</el-tag>
              <el-tag v-if="detailRow.defaultPermission.requireConfirm" size="small" type="warning">{{ t('aiAdmin.tools.needConfirm') }}</el-tag>
              <el-tag v-if="detailRow.defaultPermission.readOnly" size="small" type="success">{{ t('aiAdmin.tools.readOnly') }}</el-tag>
            </div>
          </div>
        </div>
        <div v-if="detailRow.config" class="detail__row">
          <span class="detail__label">{{ t('aiAdmin.tools.config') }}</span>
          <div class="detail__body">
            <div><span class="muted">{{ t('aiAdmin.tools.configWorkspaces') }}</span> {{ configScopeLabel(detailRow.config) }}</div>
            <div class="perm-line" style="margin-top: 6px">
              <el-tag v-if="detailRow.config.minRole" size="small" type="warning">{{ detailRow.config.minRole }}</el-tag>
              <el-tag v-if="detailRow.config.requireConfirm" size="small" type="warning">{{ t('aiAdmin.tools.needConfirm') }}</el-tag>
              <el-tag v-if="detailRow.config.readOnly" size="small" type="success">{{ t('aiAdmin.tools.readOnly') }}</el-tag>
              <el-tag v-if="!detailRow.config.enabled" size="small" type="danger">{{ t('aiAdmin.tools.hidden') }}</el-tag>
            </div>
          </div>
        </div>
        <div v-if="detailRow.inputSchema" class="detail__row">
          <span class="detail__label">Input Schema</span>
          <pre class="detail__schema">{{ JSON.stringify(detailRow.inputSchema, null, 2) }}</pre>
        </div>
      </div>
    </el-drawer>

    <!-- Config 编辑弹窗 -->
    <el-dialog v-model="configEditing" :title="configForm.id ? t('aiAdmin.tools.editConfig') : t('aiAdmin.tools.createConfig')"
      width="560">
      <el-form label-position="top">
        <el-form-item :label="t('aiAdmin.tools.name')" required>
          <el-input :model-value="configForm.toolName" disabled />
        </el-form-item>
        <el-form-item :label="t('aiAdmin.tools.configWorkspaces')" required>
          <el-select v-model="configWorkspaceMode" size="default" style="width: 100%; margin-bottom: 8px">
            <el-option :label="t('aiAdmin.tools.scopeAll')" value="all" />
            <el-option :label="t('aiAdmin.tools.scopePick')" value="pick" />
          </el-select>
          <el-select v-if="configWorkspaceMode === 'pick'" v-model="configWorkspaceIdsList" multiple filterable collapse-tags
            collapse-tags-tooltip style="width: 100%" :placeholder="t('aiAdmin.tools.pickWorkspacesPlaceholder')">
            <el-option v-for="ws in workspaces" :key="ws.id" :label="ws.name" :value="ws.id" />
          </el-select>
          <div class="hint">{{ t('aiAdmin.tools.configWorkspacesHint') }}</div>
        </el-form-item>
        <el-form-item><el-switch v-model="configForm.enabled" :active-text="t('aiAdmin.tools.enableInScope')" /></el-form-item>
        <template v-if="configForm.enabled">
          <el-form-item :label="t('aiAdmin.toolPerm.minRole')">
            <el-select v-model="configForm.minRole" clearable style="width: 100%" :placeholder="t('aiAdmin.tools.useDefault')">
              <el-option label="VIEWER" value="VIEWER" />
              <el-option label="DEVELOPER" value="DEVELOPER" />
              <el-option label="WORKSPACE_OWNER" value="WORKSPACE_OWNER" />
              <el-option label="SUPER_ADMIN" value="SUPER_ADMIN" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('aiAdmin.toolPerm.requireConfirm')">
            <el-select v-model="configForm.requireConfirm" clearable style="width: 100%" :placeholder="t('aiAdmin.tools.useDefault')">
              <el-option :label="t('common.yes')" :value="true" />
              <el-option :label="t('common.no')" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('aiAdmin.toolPerm.readOnly')">
            <el-select v-model="configForm.readOnly" clearable style="width: 100%" :placeholder="t('aiAdmin.tools.useDefault')">
              <el-option :label="t('common.yes')" :value="true" />
              <el-option :label="t('common.no')" :value="false" />
            </el-select>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button v-if="configForm.id" type="danger" @click="removeConfig" style="margin-right: auto">{{ t('common.delete') }}</el-button>
        <el-button @click="configEditing = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveConfig">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listTools, adminToolConfigs, type ToolViewVO, type AiToolConfigVO } from '@/api/ai'
import { listWorkspaces } from '@/api/workspace'

const { t } = useI18n()

const rows = ref<ToolViewVO[]>([])
const loading = ref(false)
const search = ref('')
const sourceFilter = ref<string>()

const workspaces = ref<{ id: number; name: string }[]>([])

const detailVisible = ref(false)
const detailRow = ref<ToolViewVO | null>(null)

const configEditing = ref(false)
const saving = ref(false)
const configForm = reactive<AiToolConfigVO>(emptyConfig())
// 'all' = workspaceIds=null(全工作区);'pick' = 指定 workspace_ids
const configWorkspaceMode = ref<'all' | 'pick'>('all')
const configWorkspaceIdsList = ref<number[]>([])

function emptyConfig(): AiToolConfigVO {
  return { toolName: '', workspaceIds: null, minRole: null, requireConfirm: null, readOnly: null, enabled: true }
}

const filteredRows = computed(() => {
  const q = search.value.trim().toLowerCase()
  return rows.value.filter(r => {
    if (q && !r.name.toLowerCase().includes(q) && !(r.description || '').toLowerCase().includes(q)) return false
    return true
  })
})

async function load() {
  loading.value = true
  try {
    const { data } = await listTools({ source: sourceFilter.value })
    rows.value = data ?? []
  } finally { loading.value = false }
}

async function loadWorkspaces() {
  try {
    const { data } = await listWorkspaces({ pageSize: 200 }) as any
    const list = Array.isArray(data) ? data : (data?.records ?? data?.list ?? [])
    workspaces.value = list.map((w: any) => ({ id: w.id, name: w.name }))
  } catch { /* ignore */ }
}

const SOURCE_COLORS: Record<string, string> = {
  NATIVE: '#3b82f6',
  MCP: '#8b5cf6',
  SKILL: '#f59e0b',
}
function sourceColor(source: string): string {
  return SOURCE_COLORS[source] ?? '#94a3b8'
}

function configScopeLabel(cfg: NonNullable<ToolViewVO['config']>): string {
  if (!cfg.workspaceIds || cfg.workspaceIds.length === 0) {
    return t('aiAdmin.tools.scopeAll')
  }
  const names = cfg.workspaceIds.map(id =>
    workspaces.value.find(w => w.id === id)?.name ?? `#${id}`)
  return names.join(', ')
}

function openDetail(row: ToolViewVO) {
  detailRow.value = row
  detailVisible.value = true
}

function openConfigDialog(row: ToolViewVO) {
  if (row.config) {
    Object.assign(configForm, {
      id: row.config.id,
      toolName: row.name,
      minRole: row.config.minRole,
      requireConfirm: row.config.requireConfirm,
      readOnly: row.config.readOnly,
      enabled: row.config.enabled,
      // workspaceIds 在表单里用两个字段 (mode + list) 分开处理
    })
    if (!row.config.workspaceIds || row.config.workspaceIds.length === 0) {
      configWorkspaceMode.value = 'all'
      configWorkspaceIdsList.value = []
    } else {
      configWorkspaceMode.value = 'pick'
      configWorkspaceIdsList.value = [...row.config.workspaceIds]
    }
  } else {
    Object.assign(configForm, emptyConfig(), { toolName: row.name })
    configWorkspaceMode.value = 'all'
    configWorkspaceIdsList.value = []
  }
  configEditing.value = true
}

async function saveConfig() {
  if (!configForm.toolName) { ElMessage.warning(t('common.required')); return }
  if (configWorkspaceMode.value === 'pick' && configWorkspaceIdsList.value.length === 0) {
    ElMessage.warning(t('aiAdmin.tools.pickWorkspacesRequired'))
    return
  }
  saving.value = true
  try {
    // 把 mode + list 合成 workspaceIds 字符串字段(后端存 JSON 数组字符串,null=全生效)
    const workspaceIdsPayload = configWorkspaceMode.value === 'all'
      ? null
      : JSON.stringify(configWorkspaceIdsList.value)
    const body: AiToolConfigVO = { ...configForm, workspaceIds: workspaceIdsPayload }
    if (configForm.id) await adminToolConfigs.update(configForm.id, body)
    else await adminToolConfigs.create(body)
    configEditing.value = false
    await load()
    ElMessage.success(t('common.success'))
  } catch { ElMessage.error(t('common.failed')) } finally { saving.value = false }
}

async function removeConfig() {
  if (!configForm.id) return
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    await adminToolConfigs.delete(configForm.id)
    configEditing.value = false
    await load()
    ElMessage.success(t('common.success'))
  } catch { /* cancel */ }
}

onMounted(() => Promise.all([load(), loadWorkspaces()]))
</script>

<style scoped lang="scss">
.tool-name {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  background: var(--r-bg-panel);
  padding: 1px var(--r-space-2);
  border-radius: 3px;
  color: var(--r-text-primary);
}
.desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: var(--r-text-secondary);
  font-size: var(--r-font-sm);
  line-height: var(--r-leading-snug);
}
.perm-line {
  display: inline-flex;
  gap: var(--r-space-1);
  flex-wrap: wrap;
  align-items: center;
}

.detail {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-5);
  padding: 0 var(--r-space-1);

  &__row { display: flex; flex-direction: column; gap: var(--r-space-2); }
  &__label {
    font-size: var(--r-font-xs);
    color: var(--r-text-muted);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    font-weight: var(--r-weight-semibold);
  }
  &__body {
    color: var(--r-text-primary);
    font-size: var(--r-font-base);
    line-height: var(--r-leading-normal);
  }
  &__schema {
    margin: 0;
    padding: var(--r-space-3);
    background: var(--r-bg-code);
    border-radius: var(--r-radius-sm);
    font-size: var(--r-font-sm);
    font-family: var(--r-font-mono);
    color: var(--r-text-primary);
    overflow-x: auto;
    line-height: var(--r-leading-snug);
  }
}
</style>
