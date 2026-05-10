<script setup lang="ts">
import { ref, watch } from 'vue'
import { listFiles, type StorageEntity } from '@/api/file'
import { ElMessage } from 'element-plus'
import { Folder, Document, ArrowLeft } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  currentPath?: string
  extensions?: string[] // 过滤文件后缀，如 ['.jar']
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'select', path: string): void
}>()

const loading = ref(false)
const currentDir = ref('')
const files = ref<StorageEntity[]>([])
const selectedFile = ref('')

const breadcrumbs = ref<{ name: string; path: string }[]>([])

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      const initPath = props.currentPath
        ? props.currentPath.substring(0, props.currentPath.lastIndexOf('/')) || ''
        : ''
      navigateTo(initPath)
    }
  },
)

async function navigateTo(path: string) {
  loading.value = true
  currentDir.value = path
  selectedFile.value = ''
  updateBreadcrumbs(path)
  try {
    const res = await listFiles(path)
    let items = res.data || []
    items.sort((a, b) => {
      if (a.directory !== b.directory) return a.directory ? -1 : 1
      return a.fileName.localeCompare(b.fileName)
    })
    if (props.extensions?.length) {
      items = items.filter(
        (f) => f.directory || props.extensions!.some((ext) => f.fileName.endsWith(ext)),
      )
    }
    files.value = items
  } catch {
    ElMessage.error(t('fileDialog.loadFailed'))
    files.value = []
  } finally {
    loading.value = false
  }
}

function updateBreadcrumbs(path: string) {
  const parts = path.split('/').filter(Boolean)
  const crumbs = [{ name: t('fileDialog.root'), path: '' }]
  let accumulated = ''
  for (const part of parts) {
    accumulated += '/' + part
    crumbs.push({ name: part, path: accumulated })
  }
  breadcrumbs.value = crumbs
}

function pathOf(item: StorageEntity): string {
  return currentDir.value ? currentDir.value + '/' + item.fileName : item.fileName
}

function onItemClick(item: StorageEntity) {
  if (item.directory) {
    navigateTo(pathOf(item))
  } else {
    selectedFile.value = pathOf(item)
  }
}

function onItemDblClick(item: StorageEntity) {
  if (!item.directory) confirmSelect()
}

function confirmSelect() {
  if (!selectedFile.value) {
    ElMessage.warning(t('fileDialog.selectFirst'))
    return
  }
  emit('select', selectedFile.value)
  emit('update:modelValue', false)
}

function goUp() {
  if (!currentDir.value) return
  const lastSlash = currentDir.value.lastIndexOf('/')
  navigateTo(lastSlash > 0 ? currentDir.value.substring(0, lastSlash) : '')
}

function formatSize(size: number): string {
  if (size === 0) return '—'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    :title="t('fileDialog.title')"
    width="600px"
    destroy-on-close
    align-center
  >
    <!-- 面包屑 + 上级 -->
    <div class="fsd-crumb">
      <el-button
        :icon="ArrowLeft"
        text
        size="small"
        :disabled="!currentDir"
        @click="goUp"
      />
      <el-breadcrumb separator="/" class="fsd-crumb__trail">
        <el-breadcrumb-item
          v-for="crumb in breadcrumbs"
          :key="crumb.path"
          @click="navigateTo(crumb.path)"
          class="fsd-crumb__item"
          :class="{ 'is-current': crumb.path === currentDir }"
        >
          {{ crumb.name }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 文件列表 -->
    <div class="fsd-list" v-loading="loading">
      <div
        v-for="item in files"
        :key="item.fullName"
        class="fsd-row"
        :class="{
          'is-selected': !item.directory && selectedFile === pathOf(item),
          'is-dir': item.directory,
        }"
        @click="onItemClick(item)"
        @dblclick="onItemDblClick(item)"
      >
        <el-icon :size="16" class="fsd-row__icon">
          <Folder v-if="item.directory" />
          <Document v-else />
        </el-icon>
        <span class="fsd-row__name">{{ item.fileName }}</span>
        <span class="fsd-row__size">{{
          item.directory ? '' : formatSize(item.size)
        }}</span>
      </div>
      <div v-if="!loading && files.length === 0" class="fsd-empty">
        {{ t('fileDialog.empty') }}
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{
        t('common.cancel')
      }}</el-button>
      <el-button type="primary" :disabled="!selectedFile" @click="confirmSelect">{{
        t('common.confirm')
      }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.fsd-crumb {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  padding-bottom: var(--r-space-3);
  margin-bottom: var(--r-space-3);
  border-bottom: 1px solid var(--r-border-light);
}

.fsd-crumb__trail {
  flex: 1;
  min-width: 0;
}

.fsd-crumb__item {
  cursor: pointer;
  font-size: var(--r-font-sm);

  :deep(.el-breadcrumb__inner) {
    color: var(--r-text-tertiary);
    transition: color 0.12s ease;
  }

  &:hover :deep(.el-breadcrumb__inner) {
    color: var(--r-accent);
  }

  &.is-current :deep(.el-breadcrumb__inner) {
    color: var(--r-text-primary);
    font-weight: 500;
    cursor: default;
  }
}

.fsd-list {
  height: 360px;
  overflow-y: auto;
  border: 1px solid var(--r-border);
  border-radius: var(--r-radius-md);
  background: var(--r-bg-overlay);
}

.fsd-row {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);
  height: 36px;
  padding: 0 var(--r-space-3);
  cursor: pointer;
  transition: background 0.12s ease;
  user-select: none;

  & + & {
    border-top: 1px solid var(--r-border-light);
  }

  &:hover {
    background: var(--r-bg-hover);
  }

  &.is-selected {
    background: var(--r-accent-bg);

    .fsd-row__name {
      color: var(--r-accent);
      font-weight: 500;
    }
    .fsd-row__icon {
      color: var(--r-accent);
    }
  }
}

.fsd-row__icon {
  color: var(--r-text-muted);
  flex: none;

  .is-dir & {
    color: var(--r-accent);
  }
}

.fsd-row__name {
  flex: 1;
  min-width: 0;
  font-size: var(--r-font-base);
  color: var(--r-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fsd-row__size {
  font-family: var(--r-font-mono);
  font-size: var(--r-font-xs);
  color: var(--r-text-muted);
  min-width: 56px;
  text-align: right;
  font-variant-numeric: tabular-nums;
  flex: none;
}

.fsd-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: var(--r-font-sm);
  color: var(--r-text-muted);
}
</style>
