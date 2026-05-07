<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { colorMetaLight } from '@/utils/colorMeta'
import { listWorkspaces } from '@/api/workspace'
import {
  getChannelDefinitions,
  listNotificationConfigs,
  savePlatformConfig,
  saveWorkspaceConfig,
  deleteWorkspaceConfig,
  testNotification,
  type NotificationConfigRow,
  type ParamDefinition,
} from '@/api/notification'

const { t } = useI18n()

const PLATFORM_VALUE = 0

const loading = ref(false)
const configs = ref<NotificationConfigRow[]>([])
const workspaceMap = ref<Record<number, string>>({})
const testingId = ref<number | null>(null)

// Channel definitions from SPI
interface ProviderDefinition { params: ParamDefinition[]; guide: string }
const channelDefs = ref<Record<string, ProviderDefinition>>({})

// Dialog
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const workspaces = ref<any[]>([])

const EVENT_TYPES = [
  { value: 'APPROVAL' },
  { value: 'NODE_OFFLINE' },
  { value: 'NODE_ONLINE' },
]

const form = reactive({
  id: null as number | null,
  workspaceId: PLATFORM_VALUE as number,
  enabled: true,
  channel: '',
  channelParams: {} as Record<string, string>,
  subscribedEvents: ['APPROVAL', 'NODE_OFFLINE'] as string[],
})

const channelMeta: Record<string, { color: string; bg: string }> = {
  LARK: colorMetaLight('#3b82f6'),
  DINGTALK: colorMetaLight('#0ea5e9'),
  SLACK: colorMetaLight('#8b5cf6'),
}

const eventMeta: Record<string, { color: string; bg: string }> = {
  APPROVAL: colorMetaLight('#f59e0b'),
  NODE_OFFLINE: colorMetaLight('#ef4444'),
  NODE_ONLINE: colorMetaLight('#10b981'),
}

const availableChannels = computed(() => Object.keys(channelDefs.value))
const currentChannelParams = computed(() => channelDefs.value[form.channel]?.params ?? [])

const sortedConfigs = computed(() =>
  [...configs.value].sort((a, b) => {
    if (a.workspaceId == null && b.workspaceId != null) return -1
    if (a.workspaceId != null && b.workspaceId == null) return 1
    return 0
  }),
)

function resetForm() {
  Object.assign(form, {
    id: null, workspaceId: PLATFORM_VALUE, enabled: true,
    channel: availableChannels.value[0] ?? '',
    channelParams: {},
    subscribedEvents: ['APPROVAL', 'NODE_OFFLINE'],
  })
}

function parseEvents(s: string | null): string[] {
  if (!s) return []
  return s.split(',').map(e => e.trim()).filter(Boolean)
}

function joinEvents(arr: string[]): string {
  return arr.join(',')
}

function parseJson(s: string | null): Record<string, string> {
  if (!s) return {}
  try { return JSON.parse(s) } catch { return {} }
}

async function fetchChannelDefs() {
  const res = await getChannelDefinitions()
  channelDefs.value = res.data ?? {}
}

async function fetchData() {
  loading.value = true
  try {
    const [configRes, wsRes] = await Promise.all([
      listNotificationConfigs(),
      listWorkspaces({ pageSize: 999 }),
    ])
    configs.value = configRes.data ?? []
    const wsList = wsRes.data?.records ?? wsRes.data ?? []
    workspaces.value = wsList
    workspaceMap.value = {}
    for (const ws of wsList) {
      workspaceMap.value[ws.id] = ws.name
    }
  } finally {
    loading.value = false
  }
}

function isPlatform(row: NotificationConfigRow): boolean {
  return row.workspaceId == null
}

function getWorkspaceName(wsId: number) {
  return workspaceMap.value[wsId] ?? `#${wsId}`
}

function onChannelChange() {
  form.channelParams = {}
}

function openCreate() {
  resetForm()
  isEdit.value = false
  dialogVisible.value = true
}

function openEdit(row: NotificationConfigRow) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    workspaceId: row.workspaceId ?? PLATFORM_VALUE,
    enabled: row.enabled,
    channel: row.channel,
    channelParams: parseJson(row.channelParams),
    subscribedEvents: parseEvents(row.subscribedEvents),
  })
  isEdit.value = true
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const data = {
      enabled: form.enabled,
      channel: form.channel,
      channelParams: JSON.stringify(form.channelParams),
      subscribedEvents: joinEvents(form.subscribedEvents),
    }
    if (form.workspaceId === PLATFORM_VALUE) {
      await savePlatformConfig(data)
    } else {
      await saveWorkspaceConfig(form.workspaceId, data)
    }
    ElMessage.success(t('common.success'))
    dialogVisible.value = false
    fetchData()
  } catch { /* interceptor */ }
  finally {
    submitting.value = false
  }
}

async function handleDelete(row: NotificationConfigRow) {
  const name = isPlatform(row) ? t('notification.scopePlatform') : getWorkspaceName(row.workspaceId!)
  await ElMessageBox.confirm(
    t('notification.confirmDelete', { name }),
    t('common.confirm'),
    { type: 'warning' },
  )
  try {
    if (isPlatform(row)) {
      await savePlatformConfig({ enabled: false, channel: row.channel, channelParams: row.channelParams })
    } else {
      await deleteWorkspaceConfig(row.workspaceId!)
    }
    ElMessage.success(t('common.success'))
    fetchData()
  } catch { /* cancelled */ }
}

async function handleTest(row: NotificationConfigRow) {
  testingId.value = row.id
  try {
    await testNotification(row.id)
    ElMessage.success(t('notification.testSent'))
  } catch { /* interceptor */ }
  finally {
    testingId.value = null
  }
}

function availableWorkspaceOptions() {
  const used = new Set(configs.value.map(c => c.workspaceId))
  const options: { label: string; value: number }[] = []
  if (!used.has(null as any) || isEdit.value) {
    options.push({ label: t('notification.scopePlatform'), value: PLATFORM_VALUE })
  }
  for (const ws of workspaces.value) {
    if (!used.has(ws.id) || (isEdit.value && form.workspaceId === ws.id)) {
      options.push({ label: ws.name, value: ws.id })
    }
  }
  return options
}

onMounted(() => Promise.all([fetchChannelDefs(), fetchData()]))
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h3>{{ t('notification.title') }}</h3>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('notification.create') }}</el-button>
      </div>
    </div>

    <div class="admin-card">
      <el-table :data="sortedConfigs" v-loading="loading" :empty-text="t('common.noData')">
        <el-table-column :label="t('notification.scope')" min-width="160">
          <template #default="{ row }">
            <span v-if="isPlatform(row)" class="scope-badge scope-platform">
              {{ t('notification.scopePlatform') }}
            </span>
            <span v-else class="scope-badge scope-workspace">
              {{ getWorkspaceName(row.workspaceId) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('notification.channel')" width="120">
          <template #default="{ row }">
            <span class="channel-tag"
                  :style="{ color: channelMeta[row.channel]?.color, background: channelMeta[row.channel]?.bg }">
              {{ row.channel }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('notification.enabled')" width="80" align="center">
          <template #default="{ row }">
            <span class="status-dot" :class="row.enabled ? 'status-on' : 'status-off'" />
          </template>
        </el-table-column>
        <el-table-column :label="t('notification.subscribedEvents')" min-width="200">
          <template #default="{ row }">
            <div class="event-tags">
              <span v-for="ev in parseEvents(row.subscribedEvents)" :key="ev"
                    class="event-pill"
                    :style="{ color: eventMeta[ev]?.color, background: eventMeta[ev]?.bg }">
                {{ t(`notification.event_${ev}`) }}
              </span>
              <span v-if="!row.subscribedEvents" class="text-muted">-</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200">
          <template #default="{ row }">
            <div class="action-group">
              <el-button text size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
              <el-button text size="small" :loading="testingId === row.id" @click="handleTest(row)">{{ t('notification.test') }}</el-button>
              <el-button text size="small" type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Create / Edit Dialog -->
    <el-dialog v-model="dialogVisible"
               :title="isEdit ? t('notification.editTitle') : t('notification.createTitle')"
               width="500" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="120px" @submit.prevent>
        <el-form-item :label="t('notification.scope')" prop="workspaceId"
                      :rules="[{ required: true, message: t('common.required') }]">
          <el-select v-model="form.workspaceId" :disabled="isEdit" filterable style="width: 100%">
            <el-option v-for="opt in availableWorkspaceOptions()" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('notification.channel')" prop="channel"
                      :rules="[{ required: true, message: t('common.required') }]">
          <el-select v-model="form.channel" style="width: 100%" @change="onChannelChange">
            <el-option v-for="ch in availableChannels" :key="ch" :label="ch" :value="ch" />
          </el-select>
        </el-form-item>

        <!-- Dynamic channel params from SPI -->
        <el-form-item v-for="param in currentChannelParams" :key="param.name"
                      :label="param.label"
                      :prop="`channelParams.${param.name}`"
                      :rules="param.required ? [{ required: true, message: t('common.required') }] : []">
          <el-input v-model="form.channelParams[param.name]"
                    :type="param.type === 'password' ? 'password' : 'text'"
                    :show-password="param.type === 'password'"
                    :placeholder="param.placeholder" />
        </el-form-item>

        <el-form-item :label="t('notification.subscribedEvents')">
          <el-select v-model="form.subscribedEvents" multiple collapse-tags collapse-tags-tooltip
                     style="width: 100%">
            <el-option v-for="ev in EVENT_TYPES" :key="ev.value"
                       :label="t(`notification.event_${ev.value}`)" :value="ev.value" />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('notification.enabled')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/admin.scss';

.scope-badge {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
  line-height: 20px;
}

.scope-platform {
  color: var(--r-purple);
  background: var(--r-purple-bg);
  border: 1px solid var(--r-purple-border);
}

.scope-workspace {
  color: var(--r-text-secondary);
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border);
}

.channel-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  letter-spacing: 0.02em;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-on {
  background: var(--r-success);
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.15);
}

.status-off {
  background: var(--r-border-dark);
}

.event-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.event-pill {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 10px;
  line-height: 18px;
  white-space: nowrap;
}

.text-muted {
  color: var(--r-text-muted);
  font-size: 13px;
}

.action-group {
  display: flex;
  gap: 0;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

</style>
