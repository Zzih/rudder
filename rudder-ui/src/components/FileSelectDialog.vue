<script setup lang="ts">
import { ref, watch } from 'vue'
import { listFiles, type StorageEntity } from '@/api/file'
import { ElMessage } from 'element-plus'
import { FolderOpened, Document, ArrowLeft } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: boolean
  currentPath?: string
  extensions?: string[]   // 过滤文件后缀，如 ['.jar']
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'select', path: string): void
}>()

const loading = ref(false)
const currentDir = ref('')
const files = ref<StorageEntity[]>([])
const selectedFile = ref('')

// 面包屑路径段
const breadcrumbs = ref<{ name: string; path: string }[]>([])

watch(() => props.modelValue, (visible) => {
  if (visible) {
    const initPath = props.currentPath
      ? props.currentPath.substring(0, props.currentPath.lastIndexOf('/')) || ''
      : ''
    navigateTo(initPath)
  }
})

async function navigateTo(path: string) {
  loading.value = true
  currentDir.value = path
  selectedFile.value = ''
  updateBreadcrumbs(path)
  try {
    const res = await listFiles(path)
    let items = res.data || []
    // 按目录优先、名称排序
    items.sort((a, b) => {
      if (a.directory !== b.directory) return a.directory ? -1 : 1
      return a.fileName.localeCompare(b.fileName)
    })
    // 过滤文件后缀
    if (props.extensions?.length) {
      items = items.filter(f => f.directory || props.extensions!.some(ext => f.fileName.endsWith(ext)))
    }
    files.value = items
  } catch {
    ElMessage.error('加载文件列表失败')
    files.value = []
  } finally {
    loading.value = false
  }
}

function updateBreadcrumbs(path: string) {
  const parts = path.split('/').filter(Boolean)
  const crumbs = [{ name: '根目录', path: '' }]
  let accumulated = ''
  for (const part of parts) {
    accumulated += '/' + part
    crumbs.push({ name: part, path: accumulated })
  }
  breadcrumbs.value = crumbs
}

function onItemClick(item: StorageEntity) {
  if (item.directory) {
    const path = currentDir.value ? currentDir.value + '/' + item.fileName : item.fileName
    navigateTo(path)
  } else {
    selectedFile.value = currentDir.value ? currentDir.value + '/' + item.fileName : item.fileName
  }
}

function onItemDblClick(item: StorageEntity) {
  if (!item.directory) {
    confirmSelect()
  }
}

function confirmSelect() {
  if (!selectedFile.value) {
    ElMessage.warning('请选择一个文件')
    return
  }
  emit('select', selectedFile.value)
  emit('update:modelValue', false)
}

function goUp() {
  const lastSlash = currentDir.value.lastIndexOf('/')
  navigateTo(lastSlash > 0 ? currentDir.value.substring(0, lastSlash) : '')
}

function formatSize(size: number): string {
  if (size === 0) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    title="选择文件"
    width="640px"
    destroy-on-close
  >
    <!-- 面包屑导航 -->
    <div class="file-breadcrumb">
      <el-button :icon="ArrowLeft" text size="small" @click="goUp" :disabled="!currentDir" />
      <el-breadcrumb separator="/">
        <el-breadcrumb-item
          v-for="crumb in breadcrumbs"
          :key="crumb.path"
          @click="navigateTo(crumb.path)"
          class="crumb-item"
        >
          {{ crumb.name }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 文件列表 -->
    <div class="file-list" v-loading="loading">
      <div
        v-for="item in files"
        :key="item.fullName"
        class="file-item"
        :class="{ selected: !item.directory && selectedFile === (currentDir ? currentDir + '/' + item.fileName : item.fileName) }"
        @click="onItemClick(item)"
        @dblclick="onItemDblClick(item)"
      >
        <el-icon :size="18" class="file-icon">
          <FolderOpened v-if="item.directory" />
          <Document v-else />
        </el-icon>
        <span class="file-name">{{ item.fileName }}</span>
        <span class="file-size">{{ formatSize(item.size) }}</span>
      </div>
      <div v-if="!loading && files.length === 0" class="empty-tip">
        暂无文件
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="confirmSelect" :disabled="!selectedFile">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.file-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--r-border);
}

.crumb-item {
  cursor: pointer;
  &:hover {
    color: var(--el-color-primary);
  }
}

.file-list {
  height: 360px;
  overflow-y: auto;
  border: 1px solid var(--r-border);
  border-radius: 4px;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--r-bg-hover);

  &:hover {
    background: var(--r-bg-panel);
  }

  &.selected {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }
}

.file-icon {
  margin-right: 8px;
  color: var(--r-text-muted);

  .selected & {
    color: var(--el-color-primary);
  }
}

.file-name {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  font-size: 12px;
  color: var(--r-text-muted);
  margin-left: 12px;
}

.empty-tip {
  text-align: center;
  padding: 40px;
  color: var(--r-text-muted);
  font-size: 13px;
}
</style>
