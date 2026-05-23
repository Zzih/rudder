<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { Plus, CollectionTag, Lock, WarningFilled } from '@element-plus/icons-vue'
import {
  listDataPermRoles,
  listDataPermPluginTypes,
  submitDataPermApplication,
  type DataPermRole,
  type DataPermAdapter,
  type DataPermRolePermissionItem,
  type DataPermScope,
  DANGEROUS_ACCESS_RE,
} from '@/api/data-perm'
import { useDataPermScopes } from '@/composables/useDataPermScopes'
import PermissionItemEditor from './components/PermissionItemEditor.vue'

const { t } = useI18n()
const router = useRouter()

const props = defineProps<{
  modelValue: boolean
  workspaceId: number
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'submitted': []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v),
})

const loading = ref(false)
const submitting = ref(false)

const roles = ref<DataPermRole[]>([])
const { services: allScopes, refresh: refreshScopes } = useDataPermScopes()
const scopes = computed<DataPermScope[]>(() => allScopes.value.filter(s => s.enabled))
const adaptersByPluginType = ref<Record<string, DataPermAdapter>>({})

const selectedRoleIds = ref<number[]>([])
const directItems = ref<DataPermRolePermissionItem[]>([])
const expireMode = ref<'7d' | '30d' | '90d' | '180d' | '1y' | 'never'>('90d')
const reason = ref<string>('')

const expireOptions = [
  { value: '7d',    label: '7d' },
  { value: '30d',   label: '30d' },
  { value: '90d',   label: '90d' },
  { value: '180d',  label: '180d' },
  { value: '1y',    label: '1y' },
  { value: 'never', label: '∞' },
] as const

watch(visible, (v) => {
  if (v) { reset(); load() }
})

function reset() {
  selectedRoleIds.value = []
  directItems.value = []
  expireMode.value = '90d'
  reason.value = ''
}

async function load() {
  loading.value = true
  try {
    const [rolesRes, , adaptersRes] = await Promise.all([
      listDataPermRoles(),
      refreshScopes(),
      listDataPermPluginTypes(),
    ]) as any[]
    roles.value = (rolesRes?.data as DataPermRole[]) ?? []
    const list = (adaptersRes?.data as DataPermAdapter[]) ?? []
    adaptersByPluginType.value = Object.fromEntries(list.map(a => [a.pluginType, a]))
  } finally {
    loading.value = false
  }
}

function addDirectItem() {
  directItems.value.push({ scopeCode: 0, accesses: [] })
}
function removeDirectItem(idx: number) {
  directItems.value.splice(idx, 1)
}

function computeExpireAt(): string | undefined {
  if (expireMode.value === 'never') return undefined
  const days = expireMode.value === '7d' ? 7
    : expireMode.value === '30d' ? 30
    : expireMode.value === '90d' ? 90
    : expireMode.value === '180d' ? 180
    : 365
  // 后端 LocalDateTime 序列化为 "yyyy-MM-dd HH:mm:ss" 无时区,使用本地时间避免 toISOString 的 UTC 偏移
  const d = new Date(Date.now() + days * 86_400_000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
      + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const hasWriteAccess = computed(() =>
  directItems.value.some(it =>
    it.accesses?.some(a => DANGEROUS_ACCESS_RE.test(a))))

async function submit() {
  if (!selectedRoleIds.value.length && !directItems.value.length) {
    ElMessage.error(t('dataPerm.apply.atLeastOneRequired'))
    return
  }
  if (!reason.value.trim()) {
    ElMessage.error(t('dataPerm.apply.reasonRequired'))
    return
  }
  for (const it of directItems.value) {
    if (!it.scopeCode) {
      ElMessage.error(t('dataPerm.apply.serviceRequired'))
      return
    }
    if (!it.accesses.length) {
      ElMessage.error(t('dataPerm.apply.accessesRequired'))
      return
    }
  }
  submitting.value = true
  try {
    await submitDataPermApplication({
      roleIds: selectedRoleIds.value,
      directItems: directItems.value,
      expireAt: computeExpireAt(),
      reason: reason.value.trim(),
    })
    visible.value = false
    emit('submitted')
    ElMessageBox.confirm(t('dataPerm.apply.submitted'), t('common.success'), {
      confirmButtonText: t('dataPerm.apply.goToApprovalCenter'),
      cancelButtonText: t('common.close'),
      type: 'success',
    }).then(() => {
      router.push({ name: 'ApprovalList' })
    }).catch(() => { /* user dismissed */ })
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="680px"
    :close-on-click-modal="false"
    class="dp-apply-dialog"
    @submit.prevent
  >
    <template #header>
      <div class="dlg-header">
        <div class="dlg-header__icon"><el-icon><Lock /></el-icon></div>
        <div class="dlg-header__title">
          <h3>{{ t('dataPerm.apply.title') }}</h3>
          <p>{{ t('dataPerm.apply.flowHint') }}</p>
        </div>
      </div>
    </template>

    <el-form v-loading="loading" class="dlg-form" label-position="top" @submit.prevent>
      <el-form-item>
        <template #label>
          <div class="form-row">
            <span class="form-label">{{ t('dataPerm.apply.roleSection') }}</span>
            <span v-if="selectedRoleIds.length" class="form-row__pill">
              {{ selectedRoleIds.length }} {{ t('dataPerm.apply.selected') }}
            </span>
          </div>
        </template>
        <div class="form-hint">{{ t('dataPerm.apply.roleHint') }}</div>

        <div v-if="!roles.length" class="placeholder-card">
          <el-icon><CollectionTag /></el-icon>{{ t('dataPerm.apply.noRoles') }}
        </div>
        <el-checkbox-group v-else v-model="selectedRoleIds" class="cap-list">
          <el-checkbox v-for="r in roles" :key="r.id" :value="r.id" class="cap-item">
            <div class="cap-item__body">
              <code class="cap-item__id">{{ r.name }}</code>
              <span v-if="r.description" class="cap-item__desc">{{ r.description }}</span>
            </div>
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <el-form-item>
        <template #label>
          <div class="form-row">
            <span class="form-label">{{ t('dataPerm.apply.directSection') }}</span>
            <el-tooltip
              :content="t('dataPermRole.noRangerServiceHint')"
              placement="top"
              :disabled="scopes.length > 0"
            >
              <span>
                <button type="button" class="form-row__add"
                  :disabled="scopes.length === 0"
                  @click="addDirectItem">
                  <el-icon><Plus /></el-icon>{{ t('dataPerm.apply.addDirect') }}
                </button>
              </span>
            </el-tooltip>
          </div>
        </template>
        <div class="form-hint">{{ t('dataPerm.apply.directHint') }}</div>

        <div v-if="scopes.length === 0" class="placeholder-card">
          <el-icon><Lock /></el-icon>{{ t('dataPermRole.noRangerServiceHint') }}
        </div>
        <div v-else-if="!directItems.length" class="placeholder-card">
          <el-icon><Lock /></el-icon>{{ t('dataPerm.apply.directEmpty') }}
        </div>
        <div v-else class="direct-list">
          <PermissionItemEditor
            v-for="(_, idx) in directItems" :key="idx"
            v-model="directItems[idx]"
            :scopes="scopes"
            :adapters-by-plugin-type="adaptersByPluginType"
            :workspace-id="workspaceId"
            @remove="removeDirectItem(idx)"
          />
        </div>

        <div v-if="hasWriteAccess" class="warn-banner">
          <el-icon class="warn-banner__icon"><WarningFilled /></el-icon>
          <span>{{ t('dataPerm.apply.writeWarning') }}</span>
        </div>
      </el-form-item>

      <el-form-item>
        <template #label>
          <span class="form-label">{{ t('dataPerm.apply.expireAt') }}</span>
        </template>
        <div class="seg-group">
          <button
            v-for="opt in expireOptions" :key="opt.value"
            type="button"
            class="seg"
            :class="{ 'is-active': expireMode === opt.value }"
            @click="expireMode = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>
        <div class="form-hint">
          {{ expireMode === 'never'
              ? t('dataPerm.apply.expireNeverHint')
              : t('dataPerm.apply.expireDateHint', { date: computeExpireAt()?.slice(0, 10) }) }}
        </div>
      </el-form-item>

      <el-form-item required>
        <template #label>
          <span class="form-label">{{ t('dataPerm.apply.reason') }}</span>
        </template>
        <el-input v-model="reason" type="textarea" :rows="3" resize="none"
          maxlength="512" show-word-limit
          :placeholder="t('dataPerm.apply.reasonPlaceholder')" />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dlg-footer">
        <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ t('dataPerm.apply.submit') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.dlg-header {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);

  &__icon {
    width: 36px; height: 36px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-radius-lg);
    background: var(--r-accent-bg);
    color: var(--r-accent);
    border: 1px solid var(--r-accent-border);
    .el-icon { font-size: 18px; }
  }
  &__title {
    h3 {
      margin: 0;
      font-size: var(--r-font-lg);
      font-weight: var(--r-weight-bold);
      color: var(--r-text-primary);
      letter-spacing: -0.02em;
      line-height: 1.2;
    }
    p {
      margin: 3px 0 0;
      font-size: var(--r-font-xs);
      color: var(--r-text-muted);
      font-family: var(--r-font-mono);
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }
  }
}

.dlg-form { padding-top: var(--r-space-3); }
.dlg-form :deep(.el-form-item) { margin-bottom: var(--r-space-4); }
.dlg-form :deep(.el-form-item__label) {
  padding-bottom: 6px;
  line-height: 1.4;
  width: 100%;
}
.dlg-form :deep(.el-form-item__content) {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 0;
  line-height: 1.4;
}

.form-row {
  display: flex;
  align-items: center;
  width: 100%;
  gap: var(--r-space-2);

  &__pill {
    margin-left: auto;
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    background: var(--r-accent-bg);
    border: 1px solid var(--r-accent-border);
    border-radius: 999px;
    color: var(--r-accent);
    font-family: var(--r-font-mono);
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }
  &__add {
    all: unset;
    margin-left: auto;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 4px 10px;
    border-radius: var(--r-radius-sm);
    background: var(--r-bg-panel);
    border: 1px solid var(--r-border-light);
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-secondary);
    transition: all 0.15s;

    &:hover:not(:disabled) {
      color: var(--r-accent);
      background: var(--r-accent-bg);
      border-color: var(--r-accent-border);
    }
    &:disabled {
      cursor: not-allowed;
      opacity: 0.5;
    }
    .el-icon { font-size: 12px; }
  }
}

.form-label {
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  letter-spacing: -0.005em;
}
.form-hint {
  margin: 4px 0 8px;
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  line-height: var(--r-leading-snug);
}

.placeholder-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  background: var(--r-bg-panel);
  border: 1px dashed var(--r-border);
  border-radius: var(--r-radius-md);
  color: var(--r-text-muted);
  font-size: var(--r-font-sm);

  .el-icon { color: var(--r-text-disabled); font-size: 16px; }
}

.cap-list { display: flex; flex-direction: column; gap: 6px; width: 100%; }
.cap-item {
  display: flex !important;
  align-items: flex-start !important;
  width: 100%;
  height: auto !important;
  padding: 10px 12px;
  margin: 0 !important;
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  background: var(--r-bg-card);
  transition: background 0.15s, border-color 0.15s;

  &:hover { border-color: var(--r-border); background: var(--r-bg-panel); }
  &.is-checked { background: var(--r-accent-bg); border-color: var(--r-accent-border); }

  :deep(.el-checkbox__input) { align-self: flex-start; margin-top: 2px; }
  :deep(.el-checkbox__label) {
    flex: 1; min-width: 0; padding-left: 10px;
    line-height: var(--r-leading-snug);
  }

  &__body { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
  &__id {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-sm);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
    background: none; padding: 0;
  }
  &__desc {
    font-size: var(--r-font-xs);
    color: var(--r-text-tertiary);
    line-height: var(--r-leading-snug);
    white-space: normal;
  }
}

.direct-list { display: flex; flex-direction: column; gap: 8px; }

.warn-banner {
  display: flex;
  align-items: flex-start;
  gap: var(--r-space-2);
  margin-top: 8px;
  padding: 10px 12px;
  background: var(--r-warning-bg);
  border: 1px solid var(--r-warning-border);
  border-radius: var(--r-radius-md);
  font-size: var(--r-font-xs);
  color: var(--r-text-secondary);
  line-height: var(--r-leading-snug);

  &__icon { color: var(--r-warning); font-size: 16px; flex-shrink: 0; margin-top: 1px; }
}

.seg-group {
  display: inline-flex;
  align-items: stretch;
  padding: 3px;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  gap: 2px;
  width: fit-content;
}
.seg {
  all: unset;
  cursor: pointer;
  padding: 6px 14px;
  font-family: var(--r-font-mono);
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-tertiary);
  border-radius: 5px;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;

  &:hover:not(.is-active) { color: var(--r-text-primary); }
  &.is-active {
    background: var(--r-bg-card);
    color: var(--r-accent);
    box-shadow: 0 1px 2px rgb(0 0 0 / 0.06), 0 0 0 1px var(--r-border);
  }
}

.dlg-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--r-space-2);
}

:deep(.dp-apply-dialog .el-dialog__header) {
  padding: 20px 24px 0;
  margin-right: 0;
  border-bottom: none;
}
:deep(.dp-apply-dialog .el-dialog__body) {
  padding: 8px 24px 4px;
}
:deep(.dp-apply-dialog .el-dialog__footer) {
  padding: 16px 24px 20px;
  border-top: 1px solid var(--r-border-light);
}
</style>
