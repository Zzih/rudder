<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Plus, Top, Bottom } from '@element-plus/icons-vue'
import {
  listQuickLinks, createQuickLink, updateQuickLink, deleteQuickLink, updateQuickLinkSort,
  type QuickLink, type QuickLinkCategory, type QuickLinkPayload, type QuickLinkTarget,
} from '@/api/quickLink'
import { useDeleteConfirm } from '@/composables/useDeleteConfirm'

const { t } = useI18n()
const { confirmDelete } = useDeleteConfirm()

const ICON_PREFIX = 'data:image/svg+xml;base64,'
const BASE64_MARK = 'base64,'
const ICON_MAX_BYTES = 64 * 1024

const items = ref<QuickLink[]>([])
const loading = ref(false)
const filterCategory = ref<QuickLinkCategory | ''>('')

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const form = ref<QuickLinkPayload>(emptyForm())

function emptyForm(): QuickLinkPayload {
  return {
    category: 'QUICK_ENTRY',
    name: '',
    description: '',
    icon: '',
    url: '',
    target: '_blank',
    sortOrder: 0,
    enabled: true,
  }
}

const filteredItems = computed(() =>
  filterCategory.value ? items.value.filter(i => i.category === filterCategory.value) : items.value,
)

async function fetchList() {
  loading.value = true
  try {
    const res: any = await listQuickLinks()
    items.value = res.data ?? []
  } catch { /* interceptor */ } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = emptyForm()
  dialogVisible.value = true
}

function openEdit(row: QuickLink) {
  editingId.value = row.id
  form.value = {
    category: row.category,
    name: row.name,
    description: row.description ?? '',
    icon: row.icon ?? '',
    url: row.url,
    target: row.target,
    sortOrder: row.sortOrder,
    enabled: row.enabled,
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.value.name?.trim() || !form.value.url?.trim()) {
    ElMessage.warning(t('quickLink.nameUrlRequired'))
    return
  }
  submitting.value = true
  try {
    if (editingId.value == null) {
      await createQuickLink(form.value)
    } else {
      await updateQuickLink(editingId.value, form.value)
    }
    ElMessage.success(t('common.success'))
    dialogVisible.value = false
    await fetchList()
  } catch { /* interceptor */ } finally {
    submitting.value = false
  }
}

function handleDelete(row: QuickLink) {
  confirmDelete(
    t('quickLink.deleteConfirm', { name: row.name }),
    () => deleteQuickLink(row.id),
    () => fetchList(),
  )
}

async function handleToggleEnabled(row: QuickLink) {
  try {
    await updateQuickLink(row.id, {
      category: row.category,
      name: row.name,
      description: row.description,
      icon: row.icon,
      url: row.url,
      target: row.target,
      sortOrder: row.sortOrder,
      enabled: !row.enabled,
    })
    ElMessage.success(t('common.success'))
    await fetchList()
  } catch { /* interceptor */ }
}

async function handleMove(row: QuickLink, direction: -1 | 1) {
  const sameCat = items.value.filter(i => i.category === row.category)
  const idx = sameCat.findIndex(i => i.id === row.id)
  const target = idx + direction
  if (target < 0 || target >= sameCat.length) return
  const reordered = [...sameCat]
  ;[reordered[idx], reordered[target]] = [reordered[target], reordered[idx]]
  try {
    await updateQuickLinkSort(reordered.map(i => i.id))
    await fetchList()
  } catch { /* interceptor */ }
}

function triggerIconPick() {
  fileInputRef.value?.click()
}

function handleIconPick(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!/\.svg$/i.test(file.name) && file.type !== 'image/svg+xml') {
    ElMessage.error(t('quickLink.svgOnly'))
    return
  }
  if (file.size > ICON_MAX_BYTES) {
    ElMessage.error(t('quickLink.svgTooLarge', { kb: Math.floor(ICON_MAX_BYTES / 1024) }))
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    const result = String(reader.result || '')
    if (result.startsWith(ICON_PREFIX)) {
      form.value.icon = result
      return
    }
    // FileReader 在某些浏览器对 .svg 给出 application/octet-stream;base64 — 强制 coerce 为 image/svg+xml
    const idx = result.indexOf(BASE64_MARK)
    if (idx < 0) {
      ElMessage.error(t('quickLink.svgInvalid'))
      return
    }
    form.value.icon = ICON_PREFIX + result.substring(idx + BASE64_MARK.length)
  }
  reader.onerror = () => ElMessage.error(t('quickLink.svgInvalid'))
  reader.readAsDataURL(file)
}

function clearIcon() {
  form.value.icon = ''
}

const categoryOptions = computed<{ value: QuickLinkCategory; label: string }[]>(() => [
  { value: 'QUICK_ENTRY', label: t('quickLink.categoryQuickEntry') },
  { value: 'DOC_LINK', label: t('quickLink.categoryDocLink') },
])

const targetOptions = computed<{ value: QuickLinkTarget; label: string }[]>(() => [
  { value: '_blank', label: t('quickLink.targetBlank') },
  { value: '_self', label: t('quickLink.targetSelf') },
])

function categoryLabel(c: QuickLinkCategory) {
  return c === 'QUICK_ENTRY' ? t('quickLink.categoryQuickEntry') : t('quickLink.categoryDocLink')
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h3>{{ t('admin.quickLinks') }}</h3>
      <div class="page-actions">
        <el-select v-model="filterCategory" :placeholder="t('quickLink.allCategories')" clearable style="width: 160px">
          <el-option v-for="opt in categoryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('quickLink.create') }}</el-button>
      </div>
    </div>

    <div class="admin-card">
      <el-table :data="filteredItems" v-loading="loading" row-key="id">
        <el-table-column :label="t('quickLink.icon')" width="72" align="center">
          <template #default="{ row }">
            <div class="ql-icon-cell">
              <img v-if="row.icon" :src="row.icon" alt="" />
              <span v-else class="ql-icon-cell__empty">—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('quickLink.category')" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.category === 'QUICK_ENTRY' ? 'primary' : 'info'">
              {{ categoryLabel(row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" :label="t('quickLink.name')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="description" :label="t('quickLink.description')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="url" :label="t('quickLink.url')" min-width="220" show-overflow-tooltip />
        <el-table-column :label="t('quickLink.target')" width="100" align="center">
          <template #default="{ row }">
            <span class="ql-mono">{{ row.target }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('quickLink.enabled')" width="90" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="handleToggleEnabled(row)" />
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="240" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" :icon="Top" @click="handleMove(row, -1)" />
            <el-button text size="small" :icon="Bottom" @click="handleMove(row, 1)" />
            <el-button text size="small" type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button text size="small" type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId == null ? t('quickLink.create') : t('quickLink.edit')"
      width="540"
      destroy-on-close
      @submit.prevent
    >
      <el-form :model="form" label-width="92px">
        <el-form-item :label="t('quickLink.category')" required>
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="opt in categoryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('quickLink.name')" required>
          <el-input v-model="form.name" :placeholder="t('quickLink.namePlaceholder')" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('quickLink.description')">
          <el-input v-model="form.description" :placeholder="t('quickLink.descriptionPlaceholder')" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('quickLink.url')" required>
          <el-input v-model="form.url" placeholder="https://..." maxlength="512" />
        </el-form-item>
        <el-form-item :label="t('quickLink.target')">
          <el-radio-group v-model="form.target">
            <el-radio v-for="opt in targetOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('quickLink.icon')">
          <div class="ql-icon-picker">
            <div class="ql-icon-picker__preview">
              <img v-if="form.icon" :src="form.icon" alt="" />
              <span v-else>{{ t('quickLink.noIcon') }}</span>
            </div>
            <div class="ql-icon-picker__body">
              <div class="ql-icon-picker__actions">
                <el-button size="small" @click="triggerIconPick">{{ t('quickLink.uploadSvg') }}</el-button>
                <el-button v-if="form.icon" size="small" text type="danger" @click="clearIcon">{{ t('common.clear') }}</el-button>
              </div>
              <div class="ql-icon-picker__hint">{{ t('quickLink.svgHint') }}</div>
              <input ref="fileInputRef" type="file" accept=".svg,image/svg+xml" hidden @change="handleIconPick" />
            </div>
          </div>
        </el-form-item>
        <el-form-item :label="t('quickLink.enabled')">
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

.ql-icon-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--r-radius-md);
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  img {
    width: 20px;
    height: 20px;
    object-fit: contain;
  }
  &__empty {
    color: var(--r-text-disabled);
    font-size: var(--r-font-xs);
  }
}

.ql-mono {
  font-family: var(--r-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: var(--r-font-sm);
  color: var(--r-text-muted);
}

.ql-icon-picker {
  display: flex;
  gap: var(--r-space-3);
  align-items: stretch;
  width: 100%;

  &__preview {
    width: 72px;
    height: 72px;
    border: 1px dashed var(--r-border);
    border-radius: var(--r-radius-md);
    background: var(--r-bg-panel);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    img { width: 44px; height: 44px; object-fit: contain; }
    span {
      font-size: var(--r-font-xs);
      color: var(--r-text-disabled);
    }
  }

  &__body {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: var(--r-space-2);
    flex: 1;
    min-width: 0;
  }

  &__actions { display: flex; gap: var(--r-space-2); align-items: center; }
  &__hint { font-size: var(--r-font-xs); color: var(--r-text-muted); }
}
</style>
