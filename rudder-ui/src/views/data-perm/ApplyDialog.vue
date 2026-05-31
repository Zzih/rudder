<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { Plus, CollectionTag, Lock, Edit, Delete, Check, Close, View } from '@element-plus/icons-vue'
import {
  listDataPermBundles,
  listDataPermPluginTypes,
  listAccessGroups,
  submitDataPermApplication,
  emptyStatement,
  cloneStatement,
  resourcePathLabel,
  isResourcePathEmpty,
  type DataPermBundle,
  type DataPermAdapter,
  type DataPermScope,
  type DataPermScopeAccessGroup,
  type StatementDraft,
} from '@/api/data-perm'
import { useDataPermScopes } from '@/composables/useDataPermScopes'
import StatementEditor from './components/StatementEditor.vue'
import BundleStatementsDrawer from './components/BundleStatementsDrawer.vue'

/** 一个 Direct 块卡片:draft=已保存态(折叠摘要展示),buffer 非空即处于编辑态(工作副本)。 */
interface DirectRow {
  uid: number
  draft: StatementDraft
  buffer: StatementDraft | null
  committed: boolean
}

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

const roles = ref<DataPermBundle[]>([])
const { services: allScopes, refresh: refreshScopes } = useDataPermScopes()
const scopes = computed<DataPermScope[]>(() => allScopes.value.filter(s => s.enabled))
const adaptersByPluginType = ref<Record<string, DataPermAdapter>>({})

const selectedBundleIds = ref<number[]>([])

// 权限包「查看权限项」:只读抽屉,与编辑权限项同一套摘要卡视觉。
const previewBundle = ref<DataPermBundle | null>(null)
const previewVisible = ref(false)
function openPreview(r: DataPermBundle) {
  previewBundle.value = r
  previewVisible.value = true
}

const directRows = ref<DirectRow[]>([])
let directUidSeq = 1
const groupNamesByScope = ref<Record<number, Record<number, string>>>({})
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
  selectedBundleIds.value = []
  directRows.value = []
  previewVisible.value = false
  expireMode.value = '90d'
  reason.value = ''
}

async function load() {
  loading.value = true
  try {
    const [rolesRes, , adaptersRes] = await Promise.all([
      listDataPermBundles(),
      refreshScopes(),
      listDataPermPluginTypes(),
    ]) as any[]
    roles.value = (rolesRes?.data as DataPermBundle[]) ?? []
    const list = (adaptersRes?.data as DataPermAdapter[]) ?? []
    adaptersByPluginType.value = Object.fromEntries(list.map(a => [a.pluginType, a]))
  } finally {
    loading.value = false
  }
}

function addDirectItem() {
  const draft = emptyStatement()
  directRows.value.push({
    uid: directUidSeq++, draft, buffer: cloneStatement(draft), committed: false,
  })
}
function editDirectRow(row: DirectRow) {
  row.buffer = cloneStatement(row.draft)
}
function cancelDirectRow(row: DirectRow) {
  // 新建未保存 → 取消即移除;已保存 → 丢弃改动回到折叠摘要。
  if (!row.committed) {
    directRows.value = directRows.value.filter(r => r.uid !== row.uid)
  } else {
    row.buffer = null
  }
}
function removeDirectRow(uid: number) {
  directRows.value = directRows.value.filter(r => r.uid !== uid)
}
function saveDirectRow(row: DirectRow): boolean {
  if (!row.buffer || !validateDirect(row.buffer)) return false
  row.draft = row.buffer
  row.buffer = null
  row.committed = true
  void ensureGroupNames(row.draft.scopeCode)
  return true
}
function validateDirect(d: StatementDraft): boolean {
  if (!d.scopeCode) {
    ElMessage.error(t('dataPerm.apply.serviceRequired'))
    return false
  }
  if (!d.groupIds.length) {
    ElMessage.error(t('dataPerm.apply.groupsRequired'))
    return false
  }
  if (!d.resources.length || d.resources.some(isResourcePathEmpty)) {
    ElMessage.error(t('dataPerm.apply.resourceRequired'))
    return false
  }
  return true
}

function scopeOf(code: number): DataPermScope | undefined {
  return scopes.value.find(s => s.code === code)
}
function scopeNameOf(d: StatementDraft): string {
  return scopeOf(d.scopeCode)?.name ?? d.scopeName ?? `scope#${d.scopeCode}`
}
async function ensureGroupNames(scopeCode: number) {
  if (!scopeCode || groupNamesByScope.value[scopeCode]) return
  try {
    const res: any = await listAccessGroups(scopeCode)
    const map: Record<number, string> = {}
    for (const g of ((res?.data ?? []) as DataPermScopeAccessGroup[])) map[g.id!] = g.name
    groupNamesByScope.value = { ...groupNamesByScope.value, [scopeCode]: map }
  } catch { /* 名称解析失败不阻断,摘要回退 #id */ }
}
function groupNamesOf(d: StatementDraft): string[] {
  const map = groupNamesByScope.value[d.scopeCode] ?? {}
  return (d.groupIds ?? []).map(id => map[id] ?? `#${id}`)
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

async function submit() {
  if (!selectedBundleIds.value.length && !directRows.value.length) {
    ElMessage.error(t('dataPerm.apply.atLeastOneRequired'))
    return
  }
  if (!reason.value.trim()) {
    ElMessage.error(t('dataPerm.apply.reasonRequired'))
    return
  }
  // 仍在编辑的块先就地保存(校验 + 折叠);任一不合法则中止,保持展开。
  for (const row of directRows.value) {
    if (row.buffer && !saveDirectRow(row)) return
  }
  for (const row of directRows.value) {
    if (!validateDirect(row.draft)) { editDirectRow(row); return }
  }
  submitting.value = true
  try {
    await submitDataPermApplication({
      bundleIds: selectedBundleIds.value,
      directGrants: directRows.value.map(r => r.draft),
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
    width="540px"
    :close-on-click-modal="false"
    destroy-on-close
    class="dp-apply-dialog"
  >
    <template #header>
      <div class="ap-head">
        <div class="ap-head__icon"><el-icon><Lock /></el-icon></div>
        <div class="ap-head__text">
          <h3>{{ t('dataPerm.apply.title') }}</h3>
          <p>{{ t('dataPerm.apply.flowHint') }}</p>
        </div>
      </div>
    </template>

    <div v-loading="loading" class="ap-body">
      <!-- 权限包:随包内容自动同步,适合长期角色 -->
      <section class="ap-sec">
        <div class="ap-sec__head">
          <span class="ap-sec__mark"><el-icon><CollectionTag /></el-icon></span>
          <span class="ap-sec__title">{{ t('dataPerm.apply.roleSection') }}</span>
          <span v-if="selectedBundleIds.length" class="ap-pill">
            {{ selectedBundleIds.length }} {{ t('dataPerm.apply.selected') }}
          </span>
        </div>
        <p class="ap-sec__hint">{{ t('dataPerm.apply.roleHint') }}</p>

        <div v-if="!roles.length" class="ap-empty">
          <el-icon><CollectionTag /></el-icon>{{ t('dataPerm.apply.noRoles') }}
        </div>
        <el-checkbox-group v-else v-model="selectedBundleIds" class="ap-roles">
          <div v-for="r in roles" :key="r.id"
            class="ap-role" :class="{ 'is-checked': selectedBundleIds.includes(r.id!) }">
            <el-checkbox :value="r.id" class="ap-role__check">
              <div class="ap-role__body">
                <code class="ap-role__name">{{ r.name }}</code>
                <span v-if="r.description" class="ap-role__desc">{{ r.description }}</span>
              </div>
            </el-checkbox>
            <button type="button" class="ap-role__peek"
              :title="t('dataPerm.apply.viewPerms')" @click.stop="openPreview(r)">
              <el-icon><View /></el-icon>
            </button>
          </div>
        </el-checkbox-group>
      </section>

      <!-- Direct 项:一次性授权,不随他人改动变化 -->
      <section class="ap-sec">
        <div class="ap-sec__head">
          <span class="ap-sec__mark"><el-icon><Lock /></el-icon></span>
          <span class="ap-sec__title">{{ t('dataPerm.apply.directSection') }}</span>
          <el-tooltip
            :content="t('dataPermBundle.noRangerServiceHint')"
            placement="top"
            :disabled="scopes.length > 0"
          >
            <span class="ap-sec__action">
              <button type="button" class="ap-add"
                :disabled="scopes.length === 0"
                @click="addDirectItem">
                <el-icon><Plus /></el-icon>{{ t('dataPerm.apply.addDirect') }}
              </button>
            </span>
          </el-tooltip>
        </div>
        <p class="ap-sec__hint">{{ t('dataPerm.apply.directHint') }}</p>

        <div v-if="scopes.length === 0" class="ap-empty">
          <el-icon><Lock /></el-icon>{{ t('dataPermBundle.noRangerServiceHint') }}
        </div>
        <div v-else-if="!directRows.length" class="ap-empty">
          <el-icon><Lock /></el-icon>{{ t('dataPerm.apply.directEmpty') }}
        </div>
        <div v-else class="ap-direct">
          <div v-for="row in directRows" :key="row.uid"
            class="ap-block" :class="{ 'is-editing': row.buffer }">
            <!-- 编辑态 -->
            <template v-if="row.buffer">
              <div class="ap-block__head">
                <span class="ap-block__caption">{{
                  row.committed ? t('dataPerm.apply.directEditing') : t('dataPerm.apply.directNewDraft')
                }}</span>
                <div class="ap-block__actions">
                  <el-button size="small" :icon="Close" @click="cancelDirectRow(row)">
                    {{ t('common.cancel') }}
                  </el-button>
                  <el-button type="primary" size="small" :icon="Check" @click="saveDirectRow(row)">
                    {{ t('common.save') }}
                  </el-button>
                </div>
              </div>
              <StatementEditor
                :model-value="row.buffer!"
                :scopes="scopes"
                :adapters-by-plugin-type="adaptersByPluginType"
                :workspace-id="workspaceId"
                @update:model-value="row.buffer = $event"
                @remove="cancelDirectRow(row)"
              />
            </template>

            <!-- 折叠摘要 -->
            <div v-else class="ap-block__summary">
              <aside class="ap-block__plugin">
                {{ scopeOf(row.draft.scopeCode)?.pluginType ?? '—' }}
              </aside>
              <div class="ap-block__main">
                <div class="ap-block__top">
                  <span class="ap-block__scope-label">SCOPE</span>
                  <span class="ap-block__scope">{{ scopeNameOf(row.draft) }}</span>
                  <span class="ap-block__sep">·</span>
                  <span v-for="g in groupNamesOf(row.draft)" :key="g" class="ap-block__group">{{ g }}</span>
                </div>
                <div class="ap-block__res">
                  <code v-for="(r, ri) in row.draft.resources" :key="ri">{{ resourcePathLabel(r) }}</code>
                </div>
              </div>
              <div class="ap-block__row-actions">
                <el-button text size="small" :icon="Edit" type="primary" @click="editDirectRow(row)" />
                <el-button text size="small" :icon="Delete" type="danger" @click="removeDirectRow(row.uid)" />
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 申请设置:有效期 + 理由 -->
      <section class="ap-sec ap-sec--set">
        <div class="ap-field">
          <label class="ap-field__label">{{ t('dataPerm.apply.expireAt') }}</label>
          <div class="ap-seg">
            <button
              v-for="opt in expireOptions" :key="opt.value"
              type="button"
              class="ap-seg__btn"
              :class="{ 'is-active': expireMode === opt.value }"
              @click="expireMode = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
          <p class="ap-sec__hint ap-sec__hint--tight">
            {{ expireMode === 'never'
                ? t('dataPerm.apply.expireNeverHint')
                : t('dataPerm.apply.expireDateHint', { date: computeExpireAt()?.slice(0, 10) }) }}
          </p>
        </div>

        <div class="ap-field">
          <label class="ap-field__label">
            {{ t('dataPerm.apply.reason') }}<span class="ap-req">*</span>
          </label>
          <el-input v-model="reason" type="textarea" :rows="3" resize="none"
            maxlength="512" show-word-limit
            :placeholder="t('dataPerm.apply.reasonPlaceholder')" />
        </div>
      </section>
    </div>

    <template #footer>
      <div class="ap-foot">
        <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ t('dataPerm.apply.submit') }}
        </el-button>
      </div>
    </template>
  </el-dialog>

  <BundleStatementsDrawer v-model="previewVisible" :bundle="previewBundle" readonly />
</template>

<style scoped lang="scss">
/* ---------- header ---------- */
.ap-head {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);

  &__icon {
    width: 38px; height: 38px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-radius-lg);
    background: var(--r-accent-bg);
    color: var(--r-accent);
    border: 1px solid var(--r-accent-border);
    .el-icon { font-size: 19px; }
  }
  &__text {
    min-width: 0;
    h3 {
      margin: 0;
      font-size: var(--r-font-lg);
      font-weight: var(--r-weight-bold);
      color: var(--r-text-primary);
      letter-spacing: -0.02em;
      line-height: 1.2;
    }
    p {
      margin: 4px 0 0;
      font-size: var(--r-font-xs);
      color: var(--r-text-muted);
      line-height: var(--r-leading-snug);
    }
  }
}

/* ---------- body / sections ---------- */
.ap-body {
  display: flex;
  flex-direction: column;
}

.ap-sec {
  padding: var(--r-space-4) 0;
  border-top: 1px solid var(--r-border-light);
  animation: ap-rise 0.34s cubic-bezier(0.22, 1, 0.36, 1) both;

  &:first-child { padding-top: var(--r-space-2); border-top: none; }
  &:last-child { padding-bottom: var(--r-space-1); }

  &:nth-child(1) { animation-delay: 0.02s; }
  &:nth-child(2) { animation-delay: 0.07s; }
  &:nth-child(3) { animation-delay: 0.12s; }

  &__head {
    display: flex;
    align-items: center;
    gap: var(--r-space-2);
  }
  &__mark {
    width: 24px; height: 24px;
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-radius-md);
    background: var(--r-bg-panel);
    border: 1px solid var(--r-border-light);
    color: var(--r-text-tertiary);
    .el-icon { font-size: 13px; }
  }
  &__title {
    font-size: var(--r-font-md);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
    letter-spacing: -0.01em;
  }
  &__action { margin-left: auto; display: inline-flex; }
  &__hint {
    margin: 8px 0 12px;
    font-size: var(--r-font-xs);
    color: var(--r-text-muted);
    line-height: var(--r-leading-snug);

    &--tight { margin: 8px 0 0; }
  }
}

.ap-pill {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  background: var(--r-accent-bg);
  border: 1px solid var(--r-accent-border);
  border-radius: 999px;
  color: var(--r-accent);
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  letter-spacing: 0.04em;
}

.ap-add {
  all: unset;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: var(--r-radius-sm);
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-secondary);
  transition: color 0.15s, background 0.15s, border-color 0.15s;

  &:hover:not(:disabled) {
    color: var(--r-accent);
    background: var(--r-accent-bg);
    border-color: var(--r-accent-border);
  }
  &:disabled { cursor: not-allowed; opacity: 0.5; }
  .el-icon { font-size: 12px; }
}

/* ---------- empty placeholder ---------- */
.ap-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 13px;
  background: var(--r-bg-panel);
  border: 1px dashed var(--r-border);
  border-radius: var(--r-radius-md);
  color: var(--r-text-muted);
  font-size: var(--r-font-xs);

  .el-icon { color: var(--r-text-disabled); font-size: 14px; }
}

/* ---------- role bundle cards (checkbox + 查看权限项 peek) ---------- */
.ap-roles { display: flex; flex-direction: column; gap: 6px; width: 100%; }
.ap-role {
  display: flex;
  align-items: stretch;
  width: 100%;
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  background: var(--r-bg-card);
  overflow: hidden;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;

  &:hover { border-color: var(--r-border); }
  &.is-checked {
    background: var(--r-accent-bg);
    border-color: var(--r-accent-border);
    box-shadow: inset 2px 0 0 var(--r-accent);
  }

  &__check {
    flex: 1;
    min-width: 0;
    align-items: flex-start !important;
    height: auto !important;
    margin: 0 !important;
    padding: 11px 13px;

    :deep(.el-checkbox__input) { align-self: flex-start; margin-top: 2px; }
    :deep(.el-checkbox__label) {
      flex: 1; min-width: 0; padding-left: 10px;
      line-height: var(--r-leading-snug);
      white-space: normal;
    }
  }
  &__body { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
  &__name {
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
  &__peek {
    all: unset;
    flex-shrink: 0;
    align-self: center;
    margin: 0 8px;
    width: 28px;
    height: 28px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-radius-sm);
    cursor: pointer;
    color: var(--r-text-muted);
    transition: color 0.15s, background 0.15s;

    &:hover { color: var(--r-accent); background: var(--r-accent-bg); }
    .el-icon { font-size: 15px; }
  }
}

/* ---------- direct blocks (bordered card · edit ↔ collapsed summary) ---------- */
.ap-direct { display: flex; flex-direction: column; gap: 8px; }
.ap-block {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;

  &:hover:not(.is-editing) { border-color: var(--r-border); }
  &.is-editing {
    border-color: var(--r-accent-border);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-accent) 8%, transparent);
  }

  &__head {
    display: flex;
    align-items: center;
    gap: var(--r-space-2);
    padding: 8px 10px 8px 12px;
    border-bottom: 1px dashed var(--r-border-light);
  }
  &__caption {
    flex: 1;
    font-family: var(--r-font-mono);
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-semibold);
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--r-text-muted);
  }
  &__actions { display: inline-flex; gap: var(--r-space-2); }

  /* 编辑态:内嵌的 perm-card 去掉自身边框,边界交给本卡片 */
  &__head + :deep(.perm-card) { border: none; border-radius: 0; }

  &__summary {
    display: grid;
    grid-template-columns: 84px 1fr auto;
    align-items: stretch;
    min-height: 58px;
  }
  &__plugin {
    display: flex;
    align-items: center;
    justify-content: center;
    border-right: 1px solid var(--r-border-light);
    background: var(--r-bg-panel);
    color: var(--r-text-secondary);
    font-family: var(--r-font-mono);
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-bold);
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }
  &__main {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 6px;
    padding: 10px var(--r-space-3);
    min-width: 0;
  }
  &__top {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    row-gap: 4px;
    column-gap: var(--r-space-2);
  }
  &__scope-label {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-xs);
    font-weight: var(--r-weight-semibold);
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: var(--r-text-muted);
  }
  &__scope {
    font-family: var(--r-font-mono);
    font-size: var(--r-font-sm);
    font-weight: var(--r-weight-semibold);
    color: var(--r-text-primary);
  }
  &__sep { color: var(--r-text-disabled); }
  &__group {
    font-size: var(--r-font-xs);
    color: var(--r-accent);
    background: var(--r-accent-bg);
    border: 1px solid var(--r-accent-border);
    border-radius: var(--r-radius-sm);
    padding: 1px 8px;
  }
  &__res {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;

    code {
      font-family: var(--r-font-mono);
      font-size: var(--r-font-xs);
      color: var(--r-text-secondary);
      background: var(--r-bg-panel);
      border: 1px solid var(--r-border-light);
      border-radius: var(--r-radius-sm);
      padding: 1px 8px;
    }
  }
  &__row-actions {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    flex-shrink: 0;
    padding: 0 var(--r-space-2);
    border-left: 1px solid var(--r-border-light);
  }
}

/* ---------- settings (expire + reason) ---------- */
.ap-sec--set {
  display: flex;
  flex-direction: column;
  gap: var(--r-space-4);
}
.ap-field { display: flex; flex-direction: column; }
.ap-field__label {
  margin-bottom: 8px;
  font-size: var(--r-font-sm);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  letter-spacing: -0.005em;
}
.ap-req { color: var(--r-danger); margin-left: 3px; }

.ap-seg {
  display: inline-flex;
  align-items: stretch;
  padding: 3px;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: var(--r-radius-md);
  gap: 2px;
  width: fit-content;

  &__btn {
    all: unset;
    cursor: pointer;
    padding: 6px 15px;
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
}

/* ---------- footer ---------- */
.ap-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--r-space-2);
}

@keyframes ap-rise {
  from { opacity: 0; transform: translateY(7px); }
  to   { opacity: 1; transform: none; }
}
@media (prefers-reduced-motion: reduce) {
  .ap-sec { animation: none; }
}

/* ---------- dialog chrome ---------- */
:deep(.dp-apply-dialog .el-dialog__header) {
  padding: 22px 24px 18px;
  margin-right: 0;
  border-bottom: 1px solid var(--r-border-light);
}
:deep(.dp-apply-dialog .el-dialog__body) {
  padding: 4px 24px;
}
:deep(.dp-apply-dialog .el-dialog__footer) {
  padding: 16px 24px 20px;
  border-top: 1px solid var(--r-border-light);
}
</style>
