<script setup lang="ts">
import { computed, reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getDataPermConfig,
  saveDataPermConfig,
  testDataPermConnection,
  triggerDataPermReconcile,
  getDataPermGuide,
  DEFAULT_DATA_PERM_CONFIG,
  type DataPermConfig,
  type PluginType,
  type DataPermScope,
  PLUGIN_TYPES,
} from '@/api/data-perm'
import { listDatasources } from '@/api/datasource'
import { useTaskTypesStore } from '@/stores/taskTypes'
import MarkdownGuideAside from '@/components/MarkdownGuideAside.vue'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const config = ref<DataPermConfig>({ ...DEFAULT_DATA_PERM_CONFIG, scopes: [] })
const passwordTouched = ref(false)
const datasources = ref<Array<{ id: number; name: string; datasourceType: string }>>([])
const taskTypesStore = useTaskTypesStore()

// dirty 检测:JSON 快照对比。passwordTouched 单独跟踪密码框是否被改过
const originalSnapshot = ref<string>('')
const isDirty = computed(() => JSON.stringify(config.value) !== originalSnapshot.value || passwordTouched.value)

const svcDialog = reactive({
  visible: false,
  editingIndex: -1, // -1 = new
  draft: {
    code: undefined as number | undefined,
    name: '',
    pluginType: 'HADOOP_SQL' as PluginType,
    metadataDatasourceId: 0,
    managedTaskTypes: [] as string[],
    rangerServiceName: '',
    description: '',
    enabled: true,
  },
})

function datasourceName(id: number): string {
  return datasources.value.find(d => d.id === id)?.name ?? `#${id}`
}

function openAddService() {
  svcDialog.editingIndex = -1
  svcDialog.draft = {
    code: undefined,
    name: '',
    pluginType: 'HADOOP_SQL',
    metadataDatasourceId: datasources.value[0]?.id ?? 0,
    managedTaskTypes: [],
    rangerServiceName: '',
    description: '',
    enabled: true,
  }
  svcDialog.visible = true
}

function openEditService(idx: number) {
  const s = config.value.scopes[idx]
  svcDialog.editingIndex = idx
  svcDialog.draft = {
    code: s.code,
    name: s.name,
    pluginType: s.pluginType,
    metadataDatasourceId: s.metadataDatasourceId,
    managedTaskTypes: s.managedTaskTypes ? [...s.managedTaskTypes] : [],
    rangerServiceName: s.rangerServiceName ?? '',
    description: s.description ?? '',
    enabled: s.enabled,
  }
  svcDialog.visible = true
}

function commitServiceDialog() {
  const d = svcDialog.draft
  if (!d.name?.trim()) {
    ElMessage.error(t('dataPermConfig.svcNameRequired'))
    return
  }
  if (!d.metadataDatasourceId) {
    ElMessage.error(t('dataPermConfig.svcMetadataRequired'))
    return
  }
  // 同名查重(同一份 list 内)
  const dupIdx = config.value.scopes.findIndex(
    (s, i) => s.name === d.name.trim() && i !== svcDialog.editingIndex)
  if (dupIdx >= 0) {
    ElMessage.error(t('dataPermConfig.svcNameDuplicate'))
    return
  }
  if (config.value.rangerModeEnabled && !d.rangerServiceName?.trim()) {
    ElMessage.error(t('dataPermConfig.rangerServiceNameRequired'))
    return
  }
  // managedTaskTypes 跨 Scope 唯一性前端兜底:命中其他 scope 已绑定的 TaskType → 阻断,与后端 normalizeScopes 校验对齐。
  const conflictType = d.managedTaskTypes.find(tt =>
    config.value.scopes.some((s, i) => i !== svcDialog.editingIndex
        && (s.managedTaskTypes ?? []).includes(tt)))
  if (conflictType) {
    const label = taskTypesStore.list.find(t => t.value === conflictType)?.label ?? conflictType
    ElMessage.error(t('dataPermConfig.managedTaskTypeConflict', { type: label }))
    return
  }
  const next: DataPermScope = {
    code: d.code,
    name: d.name.trim(),
    pluginType: d.pluginType,
    metadataDatasourceId: d.metadataDatasourceId,
    managedTaskTypes: d.managedTaskTypes.length ? [...d.managedTaskTypes] : undefined,
    rangerServiceName: d.rangerServiceName?.trim() || undefined,
    description: d.description?.trim() || undefined,
    enabled: d.enabled,
  }
  if (svcDialog.editingIndex === -1) {
    config.value.scopes.push(next)
  } else {
    config.value.scopes.splice(svcDialog.editingIndex, 1, next)
  }
  svcDialog.visible = false
}

async function removeService(idx: number) {
  try {
    await ElMessageBox.confirm(
      t('dataPermConfig.svcDeleteConfirm', { name: config.value.scopes[idx].name }),
      t('common.confirm'), { type: 'warning' })
  } catch { return }
  config.value.scopes.splice(idx, 1)
}

const hasConnection = computed(() =>
  !!config.value.rangerAdminUrl?.trim()
  && !!config.value.rangerAdminUsername?.trim()
  && (config.value.passwordConfigured || !!config.value.rangerAdminPassword?.trim()),
)

async function load() {
  loading.value = true
  try {
    const [cfgRes, dsRes] = await Promise.all([
      getDataPermConfig(),
      listDatasources(),
      taskTypesStore.ensureLoaded(),
    ]) as any[]
    config.value = {
      ...DEFAULT_DATA_PERM_CONFIG,
      ...(cfgRes?.data ?? {}),
      rangerAdminPassword: '',
      scopes: (cfgRes?.data?.scopes as DataPermScope[]) ?? [],
    }
    datasources.value = (dsRes?.data as any[]) ?? []
    passwordTouched.value = false
    originalSnapshot.value = JSON.stringify(config.value)
  } catch {
    config.value = { ...DEFAULT_DATA_PERM_CONFIG, scopes: [] }
    originalSnapshot.value = JSON.stringify(config.value)
  } finally {
    loading.value = false
  }
}

async function fetchGuide(): Promise<string> {
  const res = (await getDataPermGuide()) as any
  return (res?.data?.body as string) ?? ''
}

async function save() {
  if (config.value.enabled
      && !config.value.rangerModeEnabled
      && !config.value.localModeEnabled) {
    ElMessage.error(t('dataPermConfig.atLeastOneModeRequired'))
    return
  }
  if (config.value.enabled && config.value.rangerModeEnabled
      && !config.value.rangerAdminUrl) {
    ElMessage.error(t('dataPermConfig.urlRequired'))
    return
  }
  if (config.value.enabled && config.value.localModeEnabled
      && !config.value.scopes.some(s => s.enabled && (s.managedTaskTypes?.length ?? 0) > 0)) {
    ElMessage.error(t('dataPermConfig.localModeNoManagedTaskType'))
    return
  }
  saving.value = true
  try {
    await saveDataPermConfig(config.value)
    ElMessage.success(t('common.success'))
    await load()
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    saving.value = false
  }
}

async function testConn() {
  if (!config.value.rangerAdminUrl) {
    ElMessage.error(t('dataPermConfig.urlRequired'))
    return
  }
  testing.value = true
  try {
    await testDataPermConnection(config.value)
    ElMessage.success(t('dataPermConfig.testOk'))
  } catch {
    ElMessage.error(t('dataPermConfig.testFailed'))
  } finally {
    testing.value = false
  }
}

async function triggerNow() {
  try {
    await ElMessageBox.confirm(t('dataPermConfig.triggerConfirm'), t('common.confirm'), {
      type: 'info',
    })
  } catch {
    return
  }
  try {
    await triggerDataPermReconcile()
    ElMessage.success(t('dataPermConfig.triggered'))
  } catch {
    ElMessage.error(t('common.failed'))
  }
}

function onBeforeUnload(e: BeforeUnloadEvent) {
  if (!isDirty.value) return
  e.preventDefault()
  e.returnValue = ''
}

onBeforeRouteLeave(async () => {
  if (!isDirty.value) return true
  try {
    await ElMessageBox.confirm(
      t('dataPermConfig.dirtyLeaveConfirm'),
      t('dataPermConfig.dirtyLeaveTitle'),
      { type: 'warning', confirmButtonText: t('common.discard'), cancelButtonText: t('common.cancel') },
    )
    return true
  } catch {
    return false
  }
})

onMounted(() => {
  load()
  window.addEventListener('beforeunload', onBeforeUnload)
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))
</script>

<template>
  <div class="page-container dp-cfg" v-loading="loading">
    <!-- Hero header: title + master switch + status pills -->
    <header class="dp-cfg__hero">
      <div class="dp-cfg__hero-text">
        <span class="dp-cfg__hero-kicker">Platform · Data Permission</span>
        <h2 class="dp-cfg__hero-title">{{ t('dataPermConfig.title') }}</h2>
        <p class="dp-cfg__hero-lead">{{ t('dataPermConfig.enableDesc') }}</p>
      </div>
      <div class="dp-cfg__hero-side">
        <label class="dp-cfg__master">
          <el-switch v-model="config.enabled" size="large" />
          <span class="dp-cfg__master-text">{{ t('dataPermConfig.enable') }}</span>
        </label>
        <div class="dp-cfg__pills">
          <span class="dp-pill" :class="config.enabled ? 'is-on' : 'is-off'">
            <span class="dp-pill__dot" />
            {{ config.enabled ? t('dataPermConfig.statusOn') : t('dataPermConfig.statusOff') }}
          </span>
          <span class="dp-pill" :class="hasConnection ? 'is-on' : 'is-off'">
            <span class="dp-pill__dot" />
            {{ hasConnection ? t('dataPermConfig.statusConnConfigured') : t('dataPermConfig.statusConnEmpty') }}
          </span>
        </div>
      </div>
    </header>

    <div class="dp-cfg__panel" :class="{ 'is-dimmed': !config.enabled }">
      <div class="dp-cfg__form-col">

        <!-- 00 · Modes -->
        <section class="dp-cfg__section">
          <header class="dp-cfg__sect-head">
            <span class="dp-cfg__sect-num">00</span>
            <div>
              <span class="dp-cfg__sect-title">{{ t('dataPermConfig.sectionModes') }}</span>
              <span class="dp-cfg__sect-desc">{{ t('dataPermConfig.sectionModesDesc') }}</span>
            </div>
          </header>
          <div class="dp-cfg__mode-grid">
            <label class="dp-cfg__mode-card" :class="{ 'is-on': config.rangerModeEnabled }">
              <el-switch v-model="config.rangerModeEnabled" />
              <div class="dp-cfg__mode-text">
                <div class="dp-cfg__mode-title">{{ t('dataPermConfig.modeRanger') }}</div>
                <div class="dp-cfg__mode-desc">{{ t('dataPermConfig.modeRangerDesc') }}</div>
              </div>
            </label>
            <label class="dp-cfg__mode-card" :class="{ 'is-on': config.localModeEnabled }">
              <el-switch v-model="config.localModeEnabled" />
              <div class="dp-cfg__mode-text">
                <div class="dp-cfg__mode-title">{{ t('dataPermConfig.modeLocal') }}</div>
                <div class="dp-cfg__mode-desc">{{ t('dataPermConfig.modeLocalDesc') }}</div>
              </div>
            </label>
          </div>
        </section>

        <!-- 01 · Connection (仅 Ranger mode) -->
        <section v-if="config.rangerModeEnabled" class="dp-cfg__section">
          <header class="dp-cfg__sect-head">
            <span class="dp-cfg__sect-num">01</span>
            <div>
              <span class="dp-cfg__sect-title">{{ t('dataPermConfig.sectionConnection') }}</span>
              <span class="dp-cfg__sect-desc">{{ t('dataPermConfig.sectionConnectionDesc') }}</span>
            </div>
          </header>
          <el-form label-position="top" class="dp-cfg__form">
            <el-form-item :label="t('dataPermConfig.rangerAdminUrl')">
              <el-input v-model="config.rangerAdminUrl"
                placeholder="http://ranger-admin:6080" autocomplete="off" />
            </el-form-item>
            <div class="dp-cfg__grid-2">
              <el-form-item :label="t('dataPermConfig.rangerAdminUsername')">
                <el-input v-model="config.rangerAdminUsername" autocomplete="off" />
              </el-form-item>
              <el-form-item>
                <template #label>
                  <span class="dp-cfg__label-row">
                    {{ t('dataPermConfig.rangerAdminPassword') }}
                    <span v-if="config.passwordConfigured && !passwordTouched"
                      class="dp-cfg__chip">{{ t('dataPermConfig.passwordSet') }}</span>
                  </span>
                </template>
                <el-input v-model="config.rangerAdminPassword"
                  type="password" show-password autocomplete="new-password"
                  :placeholder="config.passwordConfigured && !passwordTouched
                    ? t('dataPermConfig.passwordKeep') : ''"
                  @input="passwordTouched = true" />
              </el-form-item>
            </div>
          </el-form>
        </section>

        <!-- 02 · 数据权限域 (通用,两个 mode 都用) -->
        <section class="dp-cfg__section">
          <header class="dp-cfg__sect-head dp-cfg__sect-head--row">
            <div class="dp-cfg__sect-head-main">
              <span class="dp-cfg__sect-num">02</span>
              <div>
                <span class="dp-cfg__sect-title">{{ t('dataPermConfig.sectionServices') }}</span>
                <span class="dp-cfg__sect-desc">{{ t('dataPermConfig.sectionServicesDesc') }}</span>
              </div>
            </div>
            <el-button size="small" :icon="Plus"
              :disabled="datasources.length === 0"
              @click="openAddService">
              {{ t('dataPermConfig.svcAdd') }}
            </el-button>
          </header>

          <div v-if="!config.scopes.length" class="dp-cfg__svc-empty">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <rect x="3" y="4" width="18" height="16" rx="2"/>
              <path d="M3 9h18M8 4v16"/>
            </svg>
            <span>{{ t('dataPermConfig.svcEmpty') }}</span>
          </div>

          <ul v-else class="dp-cfg__svc-list">
            <li v-for="(svc, idx) in config.scopes" :key="svc.code ?? idx"
              class="dp-cfg__svc-row" :class="{ 'is-disabled': !svc.enabled }">
              <div class="dp-cfg__svc-ident">
                <code class="dp-cfg__svc-name">{{ svc.name }}</code>
                <span v-if="svc.description" class="dp-cfg__svc-desc">{{ svc.description }}</span>
              </div>
              <span class="dp-cfg__svc-plugin">{{ svc.pluginType }}</span>
              <span class="dp-cfg__svc-meta">
                <span class="dp-cfg__svc-meta-kicker">via</span>
                <span>{{ datasourceName(svc.metadataDatasourceId) }}</span>
              </span>
              <el-switch v-model="config.scopes[idx].enabled" size="small" />
              <div class="dp-cfg__svc-actions">
                <el-button text size="small" type="primary" :icon="Edit"
                  @click="openEditService(idx)" />
                <el-button text size="small" type="danger" :icon="Delete"
                  @click="removeService(idx)" />
              </div>
            </li>
          </ul>
        </section>

        <!-- 03 · 同步策略 (reconciler 周期通用,Ranger 专属字段子级 v-if) -->
        <section class="dp-cfg__section">
          <header class="dp-cfg__sect-head">
            <span class="dp-cfg__sect-num">03</span>
            <div>
              <span class="dp-cfg__sect-title">{{ t('dataPermConfig.sectionSync') }}</span>
              <span class="dp-cfg__sect-desc">{{ t('dataPermConfig.sectionSyncDesc') }}</span>
            </div>
          </header>
          <el-form label-position="top" class="dp-cfg__form">
            <div class="dp-cfg__grid-3">
              <el-form-item :label="t('dataPermConfig.reconcileInterval')">
                <el-input-number v-model="config.reconcileIntervalSeconds"
                  :min="60" :step="60" controls-position="right" style="width: 100%" />
                <div class="dp-cfg__hint">{{ t('dataPermConfig.seconds') }}</div>
              </el-form-item>
              <el-form-item v-if="config.rangerModeEnabled" :label="t('dataPermConfig.writeConcurrency')">
                <el-input-number v-model="config.rangerWriteConcurrency"
                  :min="1" :max="32" controls-position="right" style="width: 100%" />
                <div class="dp-cfg__hint">{{ t('dataPermConfig.writeConcurrencyHint') }}</div>
              </el-form-item>
              <el-form-item v-if="config.rangerModeEnabled" :label="t('dataPermConfig.failureThreshold')">
                <el-input-number v-model="config.reconcileFailureAlertThreshold"
                  :min="1" :max="20" controls-position="right" style="width: 100%" />
                <div class="dp-cfg__hint">{{ t('dataPermConfig.failureThresholdHint') }}</div>
              </el-form-item>
            </div>
          </el-form>
        </section>

        <!-- 04 · Advanced (仅 Ranger mode) -->
        <section v-if="config.rangerModeEnabled" class="dp-cfg__section dp-cfg__section--last">
          <header class="dp-cfg__sect-head">
            <span class="dp-cfg__sect-num">04</span>
            <div>
              <span class="dp-cfg__sect-title">{{ t('dataPermConfig.sectionAdvanced') }}</span>
              <span class="dp-cfg__sect-desc">{{ t('dataPermConfig.sectionAdvancedDesc') }}</span>
            </div>
          </header>
          <el-form label-position="top" class="dp-cfg__form">
            <div class="dp-cfg__grid-2">
              <el-form-item :label="t('dataPermConfig.timeoutMs')">
                <el-input-number v-model="config.rangerAdminTimeoutMs"
                  :min="1000" :step="1000" controls-position="right" style="width: 100%" />
                <div class="dp-cfg__hint">{{ t('dataPermConfig.timeoutHint') }}</div>
              </el-form-item>
            </div>
            <el-form-item>
              <div class="dp-cfg__inline-toggle">
                <el-switch v-model="config.ensureRangerUser" />
                <div class="dp-cfg__inline-text">
                  <div class="dp-cfg__inline-label">{{ t('dataPermConfig.ensureUser') }}</div>
                  <div class="dp-cfg__inline-desc">{{ t('dataPermConfig.ensureUserDesc') }}</div>
                </div>
              </div>
            </el-form-item>
          </el-form>
        </section>
      </div>

      <MarkdownGuideAside
        class="dp-cfg__guide"
        :loader="fetchGuide"
        :kicker="t('common.setupGuide')"
        :empty-text="t('common.emptyTip')"
      />
    </div>

    <!-- Floating action bar -->
    <div class="dp-cfg__actions" :class="{ 'is-dirty': isDirty }">
      <span v-if="isDirty" class="dp-cfg__dirty">
        <span class="dp-cfg__dirty-dot" />
        {{ t('dataPermConfig.dirtyHint') }}
      </span>
      <el-button text @click="triggerNow">
        <el-icon class="dp-cfg__action-icon"><Refresh /></el-icon>
        {{ t('dataPermConfig.triggerNow') }}
      </el-button>
      <template v-if="config.rangerModeEnabled">
        <span class="dp-cfg__actions-sep" />
        <el-button :loading="testing" @click="testConn">
          <el-icon class="dp-cfg__action-icon"><Connection /></el-icon>
          {{ t('dataPermConfig.testConn') }}
        </el-button>
      </template>
      <el-button type="primary" :loading="saving" @click="save">
        {{ t('common.save') }}
      </el-button>
    </div>

    <!-- Service 编辑 Dialog -->
    <el-dialog v-model="svcDialog.visible"
      :title="svcDialog.editingIndex === -1
        ? t('dataPermConfig.svcAdd') : t('dataPermConfig.svcEdit')"
      width="540" :close-on-click-modal="false" destroy-on-close>
      <el-form label-position="top">
        <el-form-item :label="t('dataPermConfig.svcDialogName')" required>
          <el-input v-model="svcDialog.draft.name"
            :placeholder="t('dataPermConfig.svcDialogNamePlaceholder')" maxlength="128" />
          <div class="dp-cfg__hint">{{ t('dataPermConfig.svcDialogNameHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('dataPermConfig.svcDialogPluginType')" required>
          <el-select v-model="svcDialog.draft.pluginType" style="width: 100%">
            <el-option v-for="p in PLUGIN_TYPES" :key="p" :label="p" :value="p" />
          </el-select>
          <div class="dp-cfg__hint">{{ t('dataPermConfig.svcDialogPluginTypeHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('dataPermConfig.svcDialogMetadata')" required>
          <el-select v-model="svcDialog.draft.metadataDatasourceId" filterable style="width: 100%">
            <el-option v-for="d in datasources" :key="d.id"
              :label="`${d.name} (${d.datasourceType})`" :value="d.id" />
          </el-select>
          <div class="dp-cfg__hint">{{ t('dataPermConfig.svcDialogMetadataHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('dataPermConfig.svcDialogManagedTaskTypes')">
          <el-select v-model="svcDialog.draft.managedTaskTypes" multiple filterable
            collapse-tags collapse-tags-tooltip style="width: 100%">
            <el-option v-for="tt in taskTypesStore.list" :key="tt.value"
              :label="`${tt.label} (${tt.value})`" :value="tt.value" />
          </el-select>
          <div class="dp-cfg__hint">{{ t('dataPermConfig.svcDialogManagedTaskTypesHint') }}</div>
        </el-form-item>
        <el-form-item v-if="config.rangerModeEnabled"
          :label="t('dataPermConfig.svcDialogRangerServiceName')" required>
          <el-input v-model="svcDialog.draft.rangerServiceName"
            :placeholder="t('dataPermConfig.svcDialogRangerServiceNamePlaceholder')" />
          <div class="dp-cfg__hint">{{ t('dataPermConfig.svcDialogRangerServiceNameHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('dataPermConfig.svcDialogDescription')">
          <el-input v-model="svcDialog.draft.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item>
          <div class="dp-cfg__inline-toggle">
            <el-switch v-model="svcDialog.draft.enabled" />
            <div class="dp-cfg__inline-text">
              <div class="dp-cfg__inline-label">{{ t('dataPermConfig.svcDialogEnabled') }}</div>
              <div class="dp-cfg__inline-desc">{{ t('dataPermConfig.svcDialogEnabledHint') }}</div>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="svcDialog.visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="commitServiceDialog">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">

.dp-cfg { padding-bottom: 96px; }

/* ============ HERO ============ */
.dp-cfg__hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--r-space-5);
  align-items: flex-end;
  padding: var(--r-space-4) 0 var(--r-space-5);
  margin-bottom: var(--r-space-5);
  border-bottom: 1px solid var(--r-border);
}
.dp-cfg__hero-text { min-width: 0; }
.dp-cfg__hero-kicker {
  display: block;
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  text-transform: uppercase;
  letter-spacing: 0.18em;
  color: var(--r-text-muted);
  margin-bottom: var(--r-space-2);
}
.dp-cfg__hero-title {
  margin: 0;
  font-size: 28px;
  font-weight: var(--r-weight-bold);
  color: var(--r-text-primary);
  letter-spacing: -0.025em;
  line-height: 1.15;
}
.dp-cfg__hero-lead {
  margin: var(--r-space-2) 0 0;
  max-width: 60ch;
  color: var(--r-text-tertiary);
  font-size: var(--r-font-sm);
  line-height: var(--r-leading-snug);
}
.dp-cfg__hero-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--r-space-3);
}
.dp-cfg__master {
  display: inline-flex;
  align-items: center;
  gap: var(--r-space-2);
  cursor: pointer;
}
.dp-cfg__master-text {
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-medium);
  color: var(--r-text-primary);
}
.dp-cfg__pills {
  display: inline-flex;
  gap: var(--r-space-2);
  flex-wrap: wrap;
  justify-content: flex-end;
}

/* ============ Neutral monospace pill(共用 design language) ============ */
.dp-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px var(--r-space-2);
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-sm);
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--r-text-secondary);
}
.dp-pill__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--r-text-muted);
}
.dp-pill.is-on .dp-pill__dot {
  background: var(--r-success);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-success) 22%, transparent);
}

/* ============ PANEL (两列网格 + sticky guide) ============ */
.dp-cfg__panel {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(520px, 1fr);
  gap: var(--r-space-5);
  align-items: start;
  transition: opacity 0.2s ease;
}
.dp-cfg__panel.is-dimmed .dp-cfg__form-col { opacity: 0.55; }

.dp-cfg__form-col {
  min-width: 0;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-lg);
  overflow: hidden;
}

.dp-cfg__guide {
  position: sticky;
  top: 24px;
  max-height: calc(100vh - 48px);
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-lg);
  overflow: hidden;
}

@media (max-width: 1280px) {
  .dp-cfg__panel { grid-template-columns: 1fr; }
  .dp-cfg__guide { position: static; max-height: none; }
}

/* ============ SECTION (editorial 编号式分割) ============ */
.dp-cfg__section {
  padding: var(--r-space-5) var(--r-space-6);
  border-bottom: 1px solid var(--r-border-light);
}
.dp-cfg__section--last { border-bottom: none; }

.dp-cfg__sect-head {
  display: flex;
  align-items: flex-start;
  gap: var(--r-space-3);
  margin-bottom: var(--r-space-4);
}
.dp-cfg__sect-head--row {
  justify-content: space-between;
  align-items: center;
}
.dp-cfg__sect-head-main {
  display: flex;
  align-items: flex-start;
  gap: var(--r-space-3);
  min-width: 0;
}
.dp-cfg__sect-num {
  flex: 0 0 auto;
  font-family: var(--r-font-mono);
  font-size: var(--r-font-md);
  font-weight: var(--r-weight-bold);
  color: var(--r-text-muted);
  letter-spacing: -0.02em;
  min-width: 22px;
  padding-top: 2px;
  font-variant-numeric: tabular-nums;
}
.dp-cfg__sect-title {
  display: block;
  font-size: var(--r-font-md);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  letter-spacing: -0.005em;
}
.dp-cfg__sect-desc {
  display: block;
  margin-top: 2px;
  color: var(--r-text-tertiary);
  font-size: var(--r-font-sm);
  line-height: var(--r-leading-snug);
}

/* ============ FORM grids ============ */
.dp-cfg__form { max-width: 880px; }
.dp-cfg__grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 var(--r-space-4);
}
.dp-cfg__grid-3 {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 var(--r-space-4);
}
@media (max-width: 720px) {
  .dp-cfg__grid-2,
  .dp-cfg__grid-3 { grid-template-columns: 1fr; }
}

.dp-cfg__label-row {
  display: inline-flex;
  align-items: center;
  gap: var(--r-space-2);
}
.dp-cfg__chip {
  display: inline-flex;
  align-items: center;
  height: 16px;
  padding: 0 6px;
  background: var(--r-success-bg);
  border: 1px solid var(--r-success-border);
  border-radius: var(--r-radius-sm);
  color: var(--r-success);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.dp-cfg__hint {
  margin-top: 4px;
  color: var(--r-text-muted);
  font-size: var(--r-font-xs);
  line-height: var(--r-leading-snug);
}

.dp-cfg__inline-toggle {
  display: flex;
  gap: var(--r-space-3);
  align-items: flex-start;
}
.dp-cfg__inline-label {
  font-size: var(--r-font-md);
  font-weight: var(--r-weight-medium);
  color: var(--r-text-primary);
}
.dp-cfg__inline-desc {
  margin-top: 2px;
  color: var(--r-text-tertiary);
  font-size: var(--r-font-sm);
  line-height: var(--r-leading-snug);
}

/* ============ Mode toggle cards ============ */
.dp-cfg__mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--r-space-3);
}
.dp-cfg__mode-card {
  display: flex;
  align-items: flex-start;
  gap: var(--r-space-3);
  padding: var(--r-space-3) var(--r-space-4);
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;

  &.is-on {
    border-color: var(--r-accent-border);
    background: var(--r-accent-bg);
  }
}
.dp-cfg__mode-text { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.dp-cfg__mode-title {
  font-size: var(--r-font-base);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
}
.dp-cfg__mode-desc {
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);
  line-height: var(--r-leading-snug);
}

/* ============ Ranger Services 列表(行卡片布局,非 el-table) ============ */
.dp-cfg__svc-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--r-space-2);
  padding: var(--r-space-5) var(--r-space-3);
  background: var(--r-bg-panel);
  border: 1px dashed var(--r-border-light);
  border-radius: var(--r-radius-md);
  color: var(--r-text-muted);
  font-size: var(--r-font-sm);

  svg { color: var(--r-text-disabled); }
}
.dp-cfg__svc-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--r-space-2);
}
.dp-cfg__svc-row {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) auto minmax(0, 1.2fr) auto auto;
  gap: var(--r-space-4);
  align-items: center;
  padding: var(--r-space-3) var(--r-space-4);
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  transition: border-color 0.15s, background 0.15s;

  &:hover {
    border-color: var(--r-border);
    background: var(--r-bg-card);
  }
  &.is-disabled { opacity: 0.55; }
}
.dp-cfg__svc-ident {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.dp-cfg__svc-name {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
}
.dp-cfg__svc-desc {
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dp-cfg__svc-plugin {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--r-text-secondary);
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-sm);
  padding: 3px var(--r-space-2);
  font-variant-numeric: tabular-nums;
}
.dp-cfg__svc-meta {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  font-size: var(--r-font-sm);
  color: var(--r-text-secondary);

  > :last-child {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
.dp-cfg__svc-meta-kicker {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--r-text-muted);
}
.dp-cfg__svc-actions {
  display: inline-flex;
  gap: 2px;
}
.visually-hidden {
  position: absolute;
  width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden;
  clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0;
}

/* ============ Floating action bar ============ */
.dp-cfg__actions {
  position: fixed;
  bottom: 20px;
  right: 28px;
  z-index: 20;
  display: inline-flex;
  align-items: center;
  gap: var(--r-space-2);
  padding: 6px var(--r-space-3) 6px var(--r-space-3);
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 999px;
  box-shadow:
    0 1px 2px color-mix(in srgb, var(--r-text-primary) 6%, transparent),
    0 12px 32px -8px color-mix(in srgb, var(--r-text-primary) 18%, transparent);
  transition: border-color 0.2s, box-shadow 0.2s;

  &.is-dirty {
    border-color: var(--r-warning);
    box-shadow:
      0 0 0 3px color-mix(in srgb, var(--r-warning) 14%, transparent),
      0 12px 32px -8px color-mix(in srgb, var(--r-warning) 28%, transparent);
  }
}
.dp-cfg__dirty {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 var(--r-space-2) 0 4px;
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  color: var(--r-warning);
  font-variant-numeric: tabular-nums;
}
.dp-cfg__dirty-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--r-warning);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-warning) 22%, transparent);
  animation: dp-cfg-pulse 1.6s ease-in-out infinite;
}
@keyframes dp-cfg-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}
.dp-cfg__actions-sep {
  width: 1px;
  align-self: stretch;
  background: var(--r-border-light);
  margin: 4px 2px;
}
.dp-cfg__action-icon {
  margin-right: 4px;
  font-size: 14px;
}
</style>
