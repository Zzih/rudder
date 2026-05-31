<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Delete, Edit, ArrowRight, Check, Close } from '@element-plus/icons-vue'
import {
  listBundleStatements,
  addBundleStatement,
  updateBundleStatement,
  deleteBundleStatement,
  listDataPermPluginTypes,
  emptyStatement,
  cloneStatement,
  resourcePathLabel,
  isResourcePathEmpty,
  type DataPermBundle,
  type DataPermAdapter,
  type DataPermScope,
  type StatementDraft,
} from '@/api/data-perm'
import { useDataPermScopes } from '@/composables/useDataPermScopes'
import { usePagination } from '@/composables/usePagination'
import StatementEditor from './StatementEditor.vue'

interface StatementRow extends StatementDraft {
  id: number
}

const props = defineProps<{
  modelValue: boolean
  bundle: DataPermBundle | null
  /** 只读预览(申请人查看包内权限项):隐藏新增 / 编辑 / 删除,仅保留搜索 + 列表。 */
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v),
})

const keyword = ref('')

const { services: allScopes, refresh: refreshScopes } = useDataPermScopes()
const scopes = computed<DataPermScope[]>(() => allScopes.value.filter(s => s.enabled))
const adaptersByPluginType = ref<Record<string, DataPermAdapter>>({})

const buffer = ref<StatementDraft | null>(null)
// null + buffer 存在 = 新增 draft;number = 正在编辑的现有块 id
const bufferId = ref<number | null>(null)
const bufferSaving = ref(false)

const isDraftBuffer = computed(() => buffer.value !== null && bufferId.value === null)
function isEditingStatement(b: StatementRow): boolean {
  return buffer.value !== null && bufferId.value === b.id
}

// 后端分页 + 后端搜索(作用域名 / 分组名 / 库表路径),管理端编辑与申请人预览共用。
const {
  data: statements,
  loading,
  pageNum,
  pageSize,
  total,
  fetch: loadStatements,
  handlePageChange,
  resetAndFetch,
} = usePagination<StatementRow>({
  fetchApi: (params) => {
    const id = props.bundle?.id
    if (!id) return Promise.resolve({ data: [], total: 0 })
    return listBundleStatements(id, { ...params, keyword: keyword.value || undefined } as never)
  },
  extractData: (res: any) => (res?.data ?? []) as StatementRow[],
  defaultPageSize: 10,
})

let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(keyword, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => resetAndFetch(), 300)
})

watch(visible, (v) => {
  if (v) {
    keyword.value = ''
    buffer.value = null
    bufferId.value = null
    loadStatic()
    resetAndFetch()
  }
}, { immediate: true })

async function loadStatic() {
  const [, adaptersRes] = await Promise.all([
    refreshScopes(),
    listDataPermPluginTypes(),
  ]) as any[]
  const list = (adaptersRes?.data as DataPermAdapter[]) ?? []
  adaptersByPluginType.value = Object.fromEntries(list.map(a => [a.pluginType, a]))
}

function scopeOf(scopeCode: number): DataPermScope | undefined {
  return scopes.value.find(s => s.code === scopeCode)
}

function addDraft() {
  buffer.value = emptyStatement()
  bufferId.value = null
  nextTick(() => {
    document.querySelector('[data-statement-row="draft"]')
      ?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  })
}

function startEdit(b: StatementRow) {
  buffer.value = cloneStatement(b)
  bufferId.value = b.id
}

function cancelEdit() {
  buffer.value = null
  bufferId.value = null
}

function updateBuffer(v: StatementDraft) {
  buffer.value = v
}

async function saveBuffer() {
  if (!buffer.value) return
  const bundleId = props.bundle?.id
  if (!bundleId) return
  const b = buffer.value
  if (!b.scopeCode) {
    ElMessage.error(t('dataPerm.apply.serviceRequired'))
    return
  }
  if (!b.groupIds?.length) {
    ElMessage.error(t('dataPerm.apply.groupsRequired'))
    return
  }
  if (!b.resources?.length || b.resources.some(isResourcePathEmpty)) {
    ElMessage.error(t('dataPerm.apply.resourceRequired'))
    return
  }
  bufferSaving.value = true
  try {
    if (bufferId.value === null) {
      await addBundleStatement(bundleId, b)
    } else {
      await updateBundleStatement(bundleId, bufferId.value, b)
    }
    cancelEdit()
    ElMessage.success(t('common.success'))
    await loadStatements()
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    bufferSaving.value = false
  }
}

async function removeStatement(b: StatementRow) {
  const bundleId = props.bundle?.id
  if (!bundleId) return
  try {
    await ElMessageBox.confirm(
        t('dataPermBundle.statementDeleteConfirm'),
        t('common.confirm'), { type: 'warning' })
  } catch { return }
  try {
    await deleteBundleStatement(bundleId, b.id)
    ElMessage.success(t('common.success'))
    await loadStatements()
  } catch {
    ElMessage.error(t('common.failed'))
  }
}
</script>

<template>
  <el-drawer v-model="visible" size="68%" :show-close="false"
    :close-on-click-modal="false" destroy-on-close
    class="rpd">
    <template #header>
      <div class="rpd__head">
        <div class="rpd__head-text">
          <span class="rpd__kicker">PERMISSIONS</span>
          <h3 class="rpd__title">{{ bundle?.name }}</h3>
          <p v-if="bundle?.description" class="rpd__desc">{{ bundle.description }}</p>
        </div>
        <button class="rpd__close" @click="visible = false" :aria-label="t('common.close')">
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </template>

    <div v-loading="loading" class="rpd__body">
      <div class="rpd__toolbar">
        <el-input v-model="keyword"
          :prefix-icon="Search"
          :placeholder="t('dataPermBundle.statementsSearchPlaceholder')"
          clearable class="rpd__search" />
        <el-button v-if="!readonly" type="primary" :icon="Plus"
          :disabled="scopes.length === 0 || buffer !== null"
          @click="addDraft">
          {{ t('dataPermBundle.addStatement') }}
        </el-button>
      </div>

      <div v-if="scopes.length === 0" class="rpd__empty">
        {{ t('dataPermBundle.noRangerServiceHint') }}
      </div>
      <div v-else-if="!statements.length && !loading && !isDraftBuffer" class="rpd__empty">
        {{ keyword ? t('dataPermBundle.statementsNoMatch') : t('dataPermBundle.statementsEmpty') }}
      </div>

      <ul v-else class="rpd__list">
        <!-- 新增 draft -->
        <li v-if="isDraftBuffer" data-statement-row="draft" class="rpd__row is-editing">
          <div class="rpd__editor">
            <div class="rpd__editor-head">
              <el-icon class="rpd__editor-caret"><ArrowRight /></el-icon>
              <span class="rpd__editor-label">{{ t('dataPermBundle.statementNewDraft') }}</span>
              <div class="rpd__editor-actions">
                <el-button size="small" :icon="Close" @click="cancelEdit()">{{ t('common.cancel') }}</el-button>
                <el-button type="primary" size="small" :icon="Check" :loading="bufferSaving"
                  @click="saveBuffer()">{{ t('common.save') }}</el-button>
              </div>
            </div>
            <StatementEditor :model-value="buffer!" :scopes="scopes"
              :adapters-by-plugin-type="adaptersByPluginType"
              @update:model-value="updateBuffer($event)" @remove="cancelEdit()" />
          </div>
        </li>

        <li v-for="b in statements" :key="b.id"
          class="rpd__row" :class="{ 'is-editing': isEditingStatement(b) }">
          <!-- 摘要 -->
          <div v-if="!isEditingStatement(b)" class="rpd__summary" :data-plugin="scopeOf(b.scopeCode)?.pluginType ?? 'UNKNOWN'">
            <aside class="rpd__plugin-bar">
              <span class="rpd__plugin-name">{{ scopeOf(b.scopeCode)?.pluginType ?? '—' }}</span>
            </aside>
            <div class="rpd__main">
              <div class="rpd__main-top">
                <span class="rpd__svc-label">SCOPE</span>
                <span class="rpd__svc-name">{{ b.scopeName ?? `scope#${b.scopeCode}` }}</span>
                <span class="rpd__sep">·</span>
                <span v-for="g in b.groupNames ?? []" :key="g" class="rpd__group-chip">{{ g }}</span>
              </div>
              <div class="rpd__resources">
                <code v-for="(r, ri) in b.resources" :key="ri" class="rpd__res">{{ resourcePathLabel(r) }}</code>
              </div>
            </div>
            <div v-if="!readonly" class="rpd__row-actions">
              <el-button text size="small" :icon="Edit" type="primary"
                :disabled="buffer !== null" @click="startEdit(b)" />
              <el-button text size="small" :icon="Delete" type="danger"
                :disabled="buffer !== null" @click="removeStatement(b)" />
            </div>
          </div>

          <!-- 编辑现有块 -->
          <div v-else class="rpd__editor">
            <div class="rpd__editor-head">
              <el-icon class="rpd__editor-caret"><ArrowRight /></el-icon>
              <span class="rpd__editor-label">{{ t('dataPermBundle.statementEditing') }}</span>
              <div class="rpd__editor-actions">
                <el-button size="small" :icon="Close" @click="cancelEdit()">{{ t('common.cancel') }}</el-button>
                <el-button type="primary" size="small" :icon="Check" :loading="bufferSaving"
                  @click="saveBuffer()">{{ t('common.save') }}</el-button>
              </div>
            </div>
            <StatementEditor :model-value="buffer!" :scopes="scopes"
              :adapters-by-plugin-type="adaptersByPluginType"
              @update:model-value="updateBuffer($event)" @remove="cancelEdit()" />
          </div>
        </li>
      </ul>

      <div v-if="total > pageSize" class="rpd__pager">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="pageNum"
          background
          @current-change="handlePageChange" />
      </div>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.rpd :deep(.el-drawer__header) {
  margin: 0;
  padding: var(--r-space-4) var(--r-space-6) var(--r-space-3);
  border-bottom: 1px solid var(--r-border-light);
}
.rpd :deep(.el-drawer__body) { padding: 0; display: flex; flex-direction: column; min-height: 0; }

.rpd__head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--r-space-4); width: 100%; }
.rpd__head-text { min-width: 0; }
.rpd__kicker {
  display: block; font-family: var(--r-font-mono); font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold); letter-spacing: 0.16em; text-transform: uppercase;
  color: var(--r-text-muted); margin-bottom: 2px;
}
.rpd__title { margin: 0; font-size: var(--r-font-lg); font-weight: var(--r-weight-bold); color: var(--r-text-primary); }
.rpd__desc { margin: 4px 0 0; font-size: var(--r-font-sm); color: var(--r-text-tertiary); max-width: 720px; }
.rpd__close {
  all: unset; flex-shrink: 0; width: 28px; height: 28px; display: inline-flex; align-items: center;
  justify-content: center; border-radius: var(--r-radius-sm); color: var(--r-text-muted); cursor: pointer;
  &:hover { color: var(--r-text-primary); background: var(--r-bg-hover); }
}

.rpd__body { flex: 1; display: flex; flex-direction: column; min-height: 0; padding: var(--r-space-4) var(--r-space-6); overflow-y: auto; }
.rpd__toolbar { display: flex; align-items: center; gap: var(--r-space-2); margin-bottom: var(--r-space-4); }
.rpd__search { flex: 1; max-width: 360px; }

.rpd__empty {
  margin: var(--r-space-5) 0; padding: var(--r-space-5) var(--r-space-3); background: var(--r-bg-panel);
  border: 1px dashed var(--r-border-light); border-radius: var(--r-radius-md); color: var(--r-text-muted);
  font-size: var(--r-font-sm); text-align: center;
}

.rpd__list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.rpd__pager { display: flex; justify-content: center; margin-top: var(--r-space-4); }
.rpd__row {
  background: var(--r-bg-card); border: 1px solid var(--r-border-light); border-radius: var(--r-radius-md);
  transition: border-color 0.15s, box-shadow 0.15s;
  &:hover:not(.is-editing) { border-color: var(--r-border); }
  &.is-editing { border-color: var(--r-accent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--r-accent) 8%, transparent); }
}

.rpd__summary { display: grid; grid-template-columns: 88px 1fr auto; align-items: stretch; min-height: 60px; }
.rpd__plugin-bar {
  display: flex; align-items: center; justify-content: center; border-right: 1px solid var(--r-border-light);
  background: var(--r-bg-panel); color: var(--r-text-secondary);
}
.rpd__plugin-name {
  font-family: var(--r-font-mono); font-size: var(--r-font-xs); font-weight: var(--r-weight-bold);
  letter-spacing: 0.10em; text-transform: uppercase;
}
.rpd__main { display: flex; flex-direction: column; justify-content: center; gap: 6px; padding: 10px var(--r-space-3); min-width: 0; }
.rpd__main-top { display: flex; align-items: center; flex-wrap: wrap; row-gap: 4px; column-gap: var(--r-space-2); }
.rpd__svc-label {
  font-family: var(--r-font-mono); font-size: var(--r-font-xs); font-weight: var(--r-weight-semibold);
  letter-spacing: 0.14em; text-transform: uppercase; color: var(--r-text-muted);
}
.rpd__svc-name { font-family: var(--r-font-mono); font-size: var(--r-font-sm); font-weight: var(--r-weight-semibold); color: var(--r-text-primary); }
.rpd__sep { color: var(--r-text-disabled); }
.rpd__group-chip {
  font-size: var(--r-font-xs); color: var(--r-accent); background: var(--r-accent-bg);
  border: 1px solid var(--r-accent-border); border-radius: var(--r-radius-sm); padding: 1px 8px;
}
.rpd__resources { display: flex; flex-wrap: wrap; gap: 4px; }
.rpd__res {
  font-family: var(--r-font-mono); font-size: var(--r-font-xs); color: var(--r-text-secondary);
  background: var(--r-bg-panel); border: 1px solid var(--r-border-light); border-radius: var(--r-radius-sm); padding: 1px 8px;
}
.rpd__row-actions {
  display: inline-flex; align-items: center; gap: 2px; padding-right: var(--r-space-2);
  flex-shrink: 0; border-left: 1px solid var(--r-border-light); padding-left: var(--r-space-1);
}

.rpd__editor { background: var(--r-bg-panel); }
.rpd__editor-head { display: flex; align-items: center; gap: var(--r-space-2); padding: 8px 12px; border-bottom: 1px dashed var(--r-border-light); }
.rpd__editor-caret { color: var(--r-accent); font-size: 12px; transform: rotate(90deg); }
.rpd__editor-label {
  flex: 1; font-family: var(--r-font-mono); font-size: var(--r-font-xs); font-weight: var(--r-weight-semibold);
  letter-spacing: 0.08em; text-transform: uppercase; color: var(--r-text-muted);
}
.rpd__editor-actions { display: inline-flex; gap: var(--r-space-2); }
.rpd__editor :deep(.perm-card) { border: none; border-radius: 0; }
</style>
