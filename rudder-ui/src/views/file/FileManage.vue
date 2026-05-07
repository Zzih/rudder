<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { colorMetaAdaptive } from '@/utils/colorMeta'
import { useThemeStore } from '@/stores/theme'
import {
  listFiles,
  uploadFile,
  deleteFile,
  createDirectory,
  renameFile,
  onlineCreateFile,
  readFileContent,
  updateFileContent,
  getEditableSuffixes,
  type StorageEntity,
} from '@/api/file'
import { useUserStore } from '@/stores/user'
import { relativeTime as relativeTimeUtil } from '@/utils/dateFormat'

const { t } = useI18n()

const loading = ref(false)
const files = ref<StorageEntity[]>([])
const currentPath = ref('')
const editableSuffixes = ref<Set<string>>(new Set())
const MAX_EDITABLE_SIZE = 1024 * 1024
const viewMode = ref<'grid' | 'list'>('list')
const searchText = ref('')
const appeared = ref(false)
const dragOver = ref(false)

function isEditable(row: StorageEntity): boolean {
  if (row.directory) return false
  const ext = (row.extension || '').toLowerCase()
  return editableSuffixes.value.has(ext) && row.size < MAX_EDITABLE_SIZE
}

// breadcrumb
const breadcrumbs = computed(() => {
  const parts = currentPath.value.split('/').filter(Boolean)
  const items = [{ label: t('file.rootDir'), path: '' }]
  let acc = ''
  for (const p of parts) {
    acc = acc ? acc + '/' + p : p
    items.push({ label: p, path: acc })
  }
  return items
})

// filtered files
const filteredFiles = computed(() => {
  const q = searchText.value.trim().toLowerCase()
  if (!q) return files.value
  return files.value.filter(f => f.fileName.toLowerCase().includes(q))
})

const themeStore = useThemeStore()
const TYPE_HEX: Record<string, string> = {
  sql: '#e67e22', py: '#3572A5', java: '#b07219', js: '#f1e05a', ts: '#3178c6',
  json: '#64748b', xml: '#e44d26', yml: '#cb171e', yaml: '#cb171e',
  sh: '#4EAA25', bat: '#C1F12E', md: '#083fa1', csv: '#217346',
  html: '#e34c26', css: '#663399', scss: '#c6538c', vue: '#41b883',
  log: '#94a3b8', txt: '#94a3b8',
  properties: '#64748b', conf: '#64748b', cfg: '#64748b', ini: '#64748b',
  hql: '#e67e22', jar: '#b07219', zip: '#64748b', gz: '#64748b', tar: '#64748b',
}
const FALLBACK_HEX = '#94a3b8'

const typeStyles = computed(() => {
  const dark = themeStore.isDark
  const toCss = (hex: string) => {
    const { color, bg } = colorMetaAdaptive(hex, dark)
    return { color, background: bg }
  }
  const out: Record<string, { color: string; background: string }> = {}
  for (const ext in TYPE_HEX) out[ext] = toCss(TYPE_HEX[ext])
  out.__fallback__ = toCss(FALLBACK_HEX)
  return out
})

function getTypeStyle(ext: string) {
  return typeStyles.value[ext?.toLowerCase()] ?? typeStyles.value.__fallback__
}

// fetch
async function fetchFiles() {
  loading.value = true
  appeared.value = false
  try {
    const res = await listFiles(currentPath.value)
    files.value = ((res as unknown as { data: StorageEntity[] }).data ?? [])
      .sort((a, b) => {
        if (a.directory !== b.directory) return a.directory ? -1 : 1
        return a.fileName.localeCompare(b.fileName)
      })
    appeared.value = true
  } catch { /* interceptor */ } finally {
    loading.value = false
  }
}

async function fetchEditableSuffixes() {
  try {
    const res = await getEditableSuffixes()
    const data = (res as unknown as { data: string[] }).data ?? []
    editableSuffixes.value = new Set(data.map((s: string) => s.toLowerCase()))
  } catch {
    editableSuffixes.value = new Set([
      'txt', 'log', 'sh', 'bat', 'conf', 'cfg', 'py', 'java', 'sql',
      'xml', 'hql', 'properties', 'json', 'yml', 'yaml', 'ini', 'js',
      'ts', 'md', 'csv', 'html', 'css', 'scss', 'vue', 'jsx', 'tsx',
    ])
  }
}

function navigateTo(path: string) {
  currentPath.value = path
  searchText.value = ''
  fetchFiles()
}

function openItem(row: StorageEntity) {
  if (row.directory) {
    navigateTo(row.fullName)
  } else if (isEditable(row)) {
    openEditor(row)
  }
}

// upload
async function handleUpload(options: { file: File }) {
  try {
    await uploadFile(options.file, currentPath.value)
    ElMessage.success(t('file.uploadSuccess'))
    fetchFiles()
  } catch {
    ElMessage.error(t('file.uploadFailed'))
  }
}

// drag and drop
function onDragOver(e: DragEvent) {
  e.preventDefault()
  dragOver.value = true
}
function onDragLeave() {
  dragOver.value = false
}
async function onDrop(e: DragEvent) {
  e.preventDefault()
  dragOver.value = false
  const droppedFiles = e.dataTransfer?.files
  if (!droppedFiles || droppedFiles.length === 0) return
  for (const file of Array.from(droppedFiles)) {
    try {
      await uploadFile(file, currentPath.value)
    } catch { /* skip failed */ }
  }
  ElMessage.success(t('file.uploadSuccess'))
  fetchFiles()
}

// folder upload
const folderInputRef = ref<HTMLInputElement | null>(null)
const folderUploading = ref(false)
const folderUploadProgress = ref({ total: 0, done: 0, failed: 0 })

function triggerFolderUpload() {
  folderInputRef.value?.click()
}

async function handleFolderSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const fileList = input.files
  if (!fileList || fileList.length === 0) return

  const fileArr = Array.from(fileList)
  folderUploading.value = true
  folderUploadProgress.value = { total: fileArr.length, done: 0, failed: 0 }

  for (const file of fileArr) {
    const relativePath = (file as any).webkitRelativePath as string
    if (!relativePath) continue
    const lastSlash = relativePath.lastIndexOf('/')
    const dirPart = lastSlash > 0 ? relativePath.substring(0, lastSlash) : ''
    const targetDir = currentPath.value
      ? currentPath.value + '/' + dirPart
      : dirPart
    try {
      await uploadFile(file, targetDir, file.name)
      folderUploadProgress.value.done++
    } catch {
      folderUploadProgress.value.failed++
    }
  }

  folderUploading.value = false
  input.value = ''
  const { done, failed } = folderUploadProgress.value
  if (failed === 0) {
    ElMessage.success(t('file.folderUploadSuccess', { count: done }))
  } else {
    ElMessage.warning(t('file.folderUploadPartial', { done, failed }))
  }
  fetchFiles()
}

// delete
async function handleDelete(row: StorageEntity) {
  try {
    await ElMessageBox.confirm(
      t('file.deleteConfirm', { name: row.fileName }),
      t('common.confirm'),
      { type: 'warning' },
    )
    await deleteFile(row.fullName)
    ElMessage.success(t('file.deleteSuccess'))
    fetchFiles()
  } catch { /* cancelled */ }
}

// new folder
const folderDialogVisible = ref(false)
const newFolderName = ref('')

function showNewFolder() {
  newFolderName.value = ''
  folderDialogVisible.value = true
}

async function confirmNewFolder() {
  const name = newFolderName.value.trim()
  if (!name) return
  const path = currentPath.value ? currentPath.value + '/' + name : name
  try {
    await createDirectory(path)
    ElMessage.success(t('file.folderCreated'))
    folderDialogVisible.value = false
    fetchFiles()
  } catch { /* error */ }
}

// new file
const fileDialogVisible = ref(false)
const newFileName = ref('')

function showNewFile() {
  newFileName.value = ''
  fileDialogVisible.value = true
}

async function confirmNewFile() {
  const name = newFileName.value.trim()
  if (!name) return
  try {
    await onlineCreateFile(name, currentPath.value, '')
    ElMessage.success(t('file.fileCreated'))
    fileDialogVisible.value = false
    fetchFiles()
  } catch { /* error */ }
}

// rename
const renameDialogVisible = ref(false)
const renameTarget = ref<StorageEntity | null>(null)
const renameNewName = ref('')

function showRename(row: StorageEntity) {
  renameTarget.value = row
  renameNewName.value = row.fileName
  renameDialogVisible.value = true
}

async function confirmRename() {
  if (!renameTarget.value) return
  const name = renameNewName.value.trim()
  if (!name || name === renameTarget.value.fileName) return
  const oldPath = renameTarget.value.fullName
  const parentPath = renameTarget.value.parentPath === '/' ? '' : renameTarget.value.parentPath
  const newPath = parentPath ? parentPath + '/' + name : name
  try {
    await renameFile(oldPath, newPath)
    ElMessage.success(t('file.renameSuccess'))
    renameDialogVisible.value = false
    fetchFiles()
  } catch { /* error */ }
}

// editor
const editorVisible = ref(false)
const editorPath = ref('')
const editorFileName = ref('')
const editorContent = ref('')
const editorLoading = ref(false)
const editorReadonly = ref(false)

async function openEditor(row: StorageEntity) {
  editorPath.value = row.fullName
  editorFileName.value = row.fileName
  editorReadonly.value = !isEditable(row)
  editorLoading.value = true
  editorVisible.value = true
  try {
    const res = await readFileContent(row.fullName)
    editorContent.value = (res as unknown as { data: string }).data ?? ''
  } catch {
    editorContent.value = ''
  } finally {
    editorLoading.value = false
  }
}

async function saveEditor() {
  try {
    await updateFileContent(editorPath.value, editorContent.value)
    ElMessage.success(t('file.contentSaved'))
  } catch { /* error */ }
}

// download:走 fetch + blob,token 放 Authorization 头,不暴露在 URL(防止落进浏览器历史 / 中间代理日志)
async function handleDownload(row: StorageEntity) {
  const userStore = useUserStore()
  try {
    const resp = await fetch(`/api/files/download?path=${encodeURIComponent(row.fullName)}`, {
      headers: { 'Authorization': `Bearer ${userStore.token || ''}` },
    })
    if (!resp.ok) {
      ElMessage.error(t('common.failed'))
      return
    }
    const blob = await resp.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = row.fileName || 'download'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(blobUrl)
  } catch {
    ElMessage.error(t('common.failed'))
  }
}

// format
function formatSize(bytes: number): string {
  if (bytes === 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

function relativeTime(d: string) {
  return relativeTimeUtil(d, t('project.justNow'))
}

const isEmpty = computed(() => !loading.value && filteredFiles.value.length === 0)

onMounted(() => {
  fetchEditableSuffixes()
  fetchFiles()
})
</script>

<template>
  <div
    class="fm-page"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
  >
    <!-- Drag overlay -->
    <Transition name="fm-fade">
      <div v-if="dragOver" class="fm-drop-overlay">
        <div class="fm-drop-zone">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
            <rect x="4" y="10" width="40" height="30" rx="4" stroke="var(--r-accent)" stroke-width="2" stroke-dasharray="4 3" fill="var(--r-accent-bg)"/>
            <path d="M24 20v12M18 26l6-6 6 6" stroke="var(--r-accent)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span class="fm-drop-zone__text">{{ t('file.dragOrClick') }}</span>
        </div>
      </div>
    </Transition>

    <!-- Toolbar -->
    <div class="fm-toolbar">
      <div class="fm-toolbar__left">
        <h2 class="fm-toolbar__title">{{ t('file.title') }}</h2>
        <span v-if="files.length" class="fm-toolbar__count">{{ files.length }}</span>
      </div>
      <div class="fm-toolbar__right">
        <div class="fm-search">
          <svg class="fm-search__icon" width="14" height="14" viewBox="0 0 16 16" fill="none">
            <circle cx="7" cy="7" r="5.5" stroke="currentColor" stroke-width="1.5"/>
            <path d="M11 11l3.5 3.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          <input
            v-model="searchText"
            class="fm-search__input"
            :placeholder="t('file.searchPlaceholder')"
            type="text"
          >
        </div>
        <div class="fm-view-toggle">
          <button
            :class="['fm-view-btn', { 'fm-view-btn--active': viewMode === 'list' }]"
            @click="viewMode = 'list'"
            :title="t('file.listView')"
          >
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M2 4h12M2 8h12M2 12h12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </button>
          <button
            :class="['fm-view-btn', { 'fm-view-btn--active': viewMode === 'grid' }]"
            @click="viewMode = 'grid'"
            :title="t('file.gridView')"
          >
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <rect x="1.5" y="1.5" width="5" height="5" rx="1" stroke="currentColor" stroke-width="1.5"/>
              <rect x="9.5" y="1.5" width="5" height="5" rx="1" stroke="currentColor" stroke-width="1.5"/>
              <rect x="1.5" y="9.5" width="5" height="5" rx="1" stroke="currentColor" stroke-width="1.5"/>
              <rect x="9.5" y="9.5" width="5" height="5" rx="1" stroke="currentColor" stroke-width="1.5"/>
            </svg>
          </button>
        </div>
        <div class="fm-toolbar__sep" />
        <button class="fm-btn fm-btn--ghost" @click="showNewFolder">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <path d="M2 4.5A1.5 1.5 0 013.5 3H6l1.5 2H12.5A1.5 1.5 0 0114 6.5V12a1.5 1.5 0 01-1.5 1.5h-9A1.5 1.5 0 012 12V4.5z" stroke="currentColor" stroke-width="1.3" fill="none"/>
            <path d="M8 8v3M6.5 9.5H9.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
          </svg>
          {{ t('file.newFolder') }}
        </button>
        <button class="fm-btn fm-btn--ghost" @click="showNewFile">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <path d="M4 1.5h5l4 4V13.5a1 1 0 01-1 1H4a1 1 0 01-1-1v-12a1 1 0 011-1z" stroke="currentColor" stroke-width="1.3" fill="none"/>
            <path d="M9 1.5v4h4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M8 8.5v3M6.5 10H9.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
          </svg>
          {{ t('file.newFile') }}
        </button>
        <button class="fm-btn fm-btn--ghost" @click="triggerFolderUpload">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <path d="M2 4.5A1.5 1.5 0 013.5 3H6l1.5 2H12.5A1.5 1.5 0 0114 6.5V12a1.5 1.5 0 01-1.5 1.5h-9A1.5 1.5 0 012 12V4.5z" stroke="currentColor" stroke-width="1.3" fill="none"/>
            <path d="M8 8v3M6 9.5l2-2 2 2" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          {{ t('file.uploadFolder') }}
        </button>
        <input
          ref="folderInputRef"
          type="file"
          webkitdirectory
          multiple
          style="display: none"
          @change="handleFolderSelected"
        >
        <el-upload :show-file-list="false" :http-request="handleUpload as any" multiple>
          <button class="fm-btn fm-btn--primary">
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
              <path d="M8 3v8M5 5.5l3-3 3 3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 11v2a1.5 1.5 0 001.5 1.5h9A1.5 1.5 0 0014 13v-2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            {{ t('file.uploadFile') }}
          </button>
        </el-upload>
      </div>
    </div>

    <!-- Breadcrumb -->
    <div class="fm-breadcrumb">
      <button
        v-for="(bc, idx) in breadcrumbs"
        :key="idx"
        :class="['fm-breadcrumb__item', { 'fm-breadcrumb__item--active': idx === breadcrumbs.length - 1 }]"
        @click="idx < breadcrumbs.length - 1 && navigateTo(bc.path)"
      >
        <svg v-if="idx === 0" width="14" height="14" viewBox="0 0 16 16" fill="none" class="fm-breadcrumb__home">
          <path d="M2 4.5A1.5 1.5 0 013.5 3H6l1.5 2H12.5A1.5 1.5 0 0114 6.5V12a1.5 1.5 0 01-1.5 1.5h-9A1.5 1.5 0 012 12V4.5z" stroke="currentColor" stroke-width="1.3" fill="none"/>
        </svg>
        <span>{{ bc.label }}</span>
        <svg v-if="idx < breadcrumbs.length - 1" width="12" height="12" viewBox="0 0 16 16" fill="none" class="fm-breadcrumb__sep">
          <path d="M6 4l4 4-4 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>

    <!-- Loading skeleton -->
    <div v-if="loading && files.length === 0" :class="viewMode === 'grid' ? 'fm-grid' : 'fm-list-wrap'">
      <div v-for="i in 8" :key="i" :class="viewMode === 'grid' ? 'fm-grid-skel' : 'fm-list-skel'">
        <div class="fm-skel__icon" />
        <div class="fm-skel__text" />
        <div v-if="viewMode === 'list'" class="fm-skel__meta" />
      </div>
    </div>

    <!-- Grid View -->
    <div v-else-if="viewMode === 'grid' && filteredFiles.length" class="fm-grid">
      <div
        v-for="(row, idx) in filteredFiles"
        :key="row.fullName"
        :class="['fm-grid-item', { 'fm-grid-item--appeared': appeared }]"
        :style="{ '--delay': idx * 30 + 'ms' }"
        @dblclick="openItem(row)"
      >
        <!-- Folder icon -->
        <div v-if="row.directory" class="fm-grid-item__icon fm-grid-item__icon--folder" @click="openItem(row)">
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
            <path d="M3 8a3 3 0 013-3h6l3 3h11a3 3 0 013 3v13a3 3 0 01-3 3H6a3 3 0 01-3-3V8z" fill="#fbbf24" stroke="#f59e0b" stroke-width="1"/>
          </svg>
        </div>
        <!-- File icon -->
        <div v-else class="fm-grid-item__icon" @click="openItem(row)">
          <div
            class="fm-grid-item__ext-badge"
            :style="getTypeStyle(row.extension)"
          >
            {{ (row.extension || '?').toUpperCase().slice(0, 4) }}
          </div>
        </div>
        <div class="fm-grid-item__name" :title="row.fileName" @click="openItem(row)">{{ row.fileName }}</div>
        <div class="fm-grid-item__meta">
          {{ row.directory ? t('file.directory') : formatSize(row.size) }}
        </div>
        <!-- Hover actions -->
        <div class="fm-grid-item__actions">
          <button v-if="isEditable(row)" class="fm-mini-btn" :title="t('file.editContent')" @click.stop="openEditor(row)">
            <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
              <path d="M11.5 1.5l3 3-9 9H2.5v-3l9-9z" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
          <button v-if="!row.directory" class="fm-mini-btn" :title="t('file.download')" @click.stop="handleDownload(row)">
            <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
              <path d="M8 2v8M5 7.5l3 3 3-3" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M2 11v2a1.5 1.5 0 001.5 1.5h9A1.5 1.5 0 0014 13v-2" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
            </svg>
          </button>
          <button class="fm-mini-btn" :title="t('file.rename')" @click.stop="showRename(row)">
            <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
              <path d="M2 13h12" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
              <path d="M7 3l6 8" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
              <path d="M3.5 3H6l-2 8H1.5L3.5 3z" stroke="currentColor" stroke-width="1.2" fill="none"/>
            </svg>
          </button>
          <button class="fm-mini-btn fm-mini-btn--danger" :title="t('file.delete')" @click.stop="handleDelete(row)">
            <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
              <path d="M3 4h10M6 4V2.5h4V4M4.5 4l.5 9.5h6L11.5 4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- List View -->
    <div v-else-if="viewMode === 'list' && filteredFiles.length" class="fm-list-wrap">
      <div class="fm-list-header">
        <span class="fm-list-header__name">{{ t('file.fileName') }}</span>
        <span class="fm-list-header__size">{{ t('file.fileSize') }}</span>
        <span class="fm-list-header__type">{{ t('common.type') }}</span>
        <span class="fm-list-header__time">{{ t('file.updateTime') }}</span>
        <span class="fm-list-header__actions">{{ t('common.actions') }}</span>
      </div>
      <div
        v-for="(row, idx) in filteredFiles"
        :key="row.fullName"
        :class="['fm-list-row', { 'fm-list-row--appeared': appeared }]"
        :style="{ '--delay': idx * 20 + 'ms' }"
        @dblclick="openItem(row)"
      >
        <div class="fm-list-row__name">
          <div :class="['fm-list-row__icon', row.directory ? 'fm-list-row__icon--folder' : '']" @click="openItem(row)">
            <svg v-if="row.directory" width="18" height="18" viewBox="0 0 18 18" fill="none">
              <path d="M2 4.5A1.5 1.5 0 013.5 3h3l1.5 1.5h5A1.5 1.5 0 0114.5 6v6.5a1.5 1.5 0 01-1.5 1.5H3.5A1.5 1.5 0 012 12.5v-8z" fill="#fbbf24" stroke="#f59e0b" stroke-width="0.8"/>
            </svg>
            <span
              v-else
              class="fm-list-row__ext"
              :style="getTypeStyle(row.extension)"
            >{{ (row.extension || '?').toUpperCase().slice(0, 3) }}</span>
          </div>
          <span class="fm-list-row__filename" @click="openItem(row)">{{ row.fileName }}</span>
        </div>
        <span class="fm-list-row__size">{{ row.directory ? '-' : formatSize(row.size) }}</span>
        <span class="fm-list-row__type">
          <span v-if="row.directory" class="fm-type-tag fm-type-tag--dir">{{ t('file.directory') }}</span>
          <span v-else class="fm-type-tag" :style="getTypeStyle(row.extension)">
            {{ (row.extension || '-').toUpperCase() }}
          </span>
        </span>
        <span class="fm-list-row__time" :title="row.updateTime || ''">
          {{ row.updateTime ? relativeTime(row.updateTime) : '-' }}
        </span>
        <div class="fm-list-row__actions">
          <button v-if="isEditable(row)" class="fm-action-link fm-action-link--edit" @click.stop="openEditor(row)">
            {{ t('file.editContent') }}
          </button>
          <button v-if="!row.directory" class="fm-action-link" @click.stop="handleDownload(row)">
            {{ t('file.download') }}
          </button>
          <button class="fm-action-link" @click.stop="showRename(row)">
            {{ t('file.rename') }}
          </button>
          <button class="fm-action-link fm-action-link--danger" @click.stop="handleDelete(row)">
            {{ t('file.delete') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Empty state -->
    <div v-if="isEmpty" class="fm-empty">
      <div class="fm-empty__visual">
        <svg width="80" height="80" viewBox="0 0 80 80" fill="none">
          <rect x="10" y="18" width="60" height="44" rx="6" stroke="var(--r-border-dark)" stroke-width="1.5" fill="var(--r-bg-panel)"/>
          <path d="M10 28h60" stroke="var(--r-border)" stroke-width="1.5"/>
          <path d="M10 18l8-8h24l8 8" stroke="var(--r-border-dark)" stroke-width="1.5" fill="var(--r-bg-hover)" stroke-linejoin="round"/>
          <path d="M36 40v10M31 45l5-5 5 5" stroke="var(--r-text-muted)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <p class="fm-empty__text">{{ searchText ? t('common.noData') : t('file.noFiles') }}</p>
      <p class="fm-empty__hint">{{ t('file.emptyHint') }}</p>
    </div>

    <!-- New Folder Dialog -->
    <el-dialog v-model="folderDialogVisible" :title="t('file.newFolder')" width="420px" class="fm-dialog">
      <el-input
        v-model="newFolderName"
        :placeholder="t('file.folderNamePlaceholder')"
        @keyup.enter="confirmNewFolder"
      />
      <template #footer>
        <el-button @click="folderDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="confirmNewFolder">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- New File Dialog -->
    <el-dialog v-model="fileDialogVisible" :title="t('file.newFile')" width="420px" class="fm-dialog">
      <el-input
        v-model="newFileName"
        :placeholder="t('file.fileNamePlaceholder')"
        @keyup.enter="confirmNewFile"
      />
      <template #footer>
        <el-button @click="fileDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="confirmNewFile">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Rename Dialog -->
    <el-dialog v-model="renameDialogVisible" :title="t('file.rename')" width="420px" class="fm-dialog">
      <el-input
        v-model="renameNewName"
        :placeholder="t('file.renameTo')"
        @keyup.enter="confirmRename"
      />
      <template #footer>
        <el-button @click="renameDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="confirmRename">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Folder Upload Progress Dialog -->
    <el-dialog
      v-model="folderUploading"
      :title="t('file.uploadFolder')"
      width="420px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      class="fm-dialog"
    >
      <div style="text-align: center; padding: 16px 0">
        <el-progress
          :percentage="folderUploadProgress.total > 0
            ? Math.round((folderUploadProgress.done + folderUploadProgress.failed) / folderUploadProgress.total * 100)
            : 0"
          :stroke-width="20"
          :text-inside="true"
        />
        <div style="margin-top: 12px; color: var(--r-text-secondary)">
          {{ t('file.folderUploadProgress', {
            done: folderUploadProgress.done + folderUploadProgress.failed,
            total: folderUploadProgress.total
          }) }}
        </div>
      </div>
    </el-dialog>

    <!-- Editor Dialog -->
    <el-dialog
      v-model="editorVisible"
      :title="editorFileName"
      width="80%"
      top="5vh"
      :close-on-click-modal="false"
      class="fm-dialog fm-dialog--editor"
    >
      <div v-loading="editorLoading" class="fm-editor">
        <el-input
          v-model="editorContent"
          type="textarea"
          :rows="24"
          :readonly="editorReadonly"
          class="fm-editor__textarea"
        />
      </div>
      <template #footer>
        <el-button @click="editorVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-if="!editorReadonly" type="primary" @click="saveEditor">
          {{ t('file.saveContent') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
/* ── Page ── */
.fm-page {
  padding: 24px 28px;
  min-height: 100%;
  position: relative;
}

/* ── Drop overlay ── */
.fm-drop-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: var(--r-accent-bg);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.fm-drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 60px 80px;
  border: 2px dashed var(--r-accent);
  border-radius: 20px;
  background: var(--r-bg-overlay);
  box-shadow: var(--r-shadow-lg);

  &__text {
    font-size: 14px;
    font-weight: 600;
    color: var(--r-accent);
  }
}

.fm-fade-enter-active,
.fm-fade-leave-active {
  transition: opacity 0.2s ease;
}
.fm-fade-enter-from,
.fm-fade-leave-to {
  opacity: 0;
}

/* ── Toolbar ── */
.fm-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.fm-toolbar__left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.fm-toolbar__title {
  font-size: 18px;
  font-weight: 700;
  color: var(--r-text-primary);
  margin: 0;
  letter-spacing: -0.025em;
}

.fm-toolbar__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 11px;
  background: var(--r-bg-hover);
  color: var(--r-text-muted);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.fm-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.fm-toolbar__sep {
  width: 1px;
  height: 24px;
  background: var(--r-border);
  margin: 0 4px;
}

/* ── Search ── */
.fm-search {
  position: relative;
  display: flex;
  align-items: center;
}

.fm-search__icon {
  position: absolute;
  left: 10px;
  color: var(--r-text-muted);
  pointer-events: none;
}

.fm-search__input {
  width: 180px;
  height: 34px;
  padding: 0 12px 0 32px;
  border: 1px solid var(--r-border);
  border-radius: 10px;
  background: var(--r-bg-panel);
  font-size: 13px;
  color: var(--r-text-secondary);
  outline: none;
  transition: all 0.18s ease;

  &::placeholder {
    color: var(--r-text-muted);
  }

  &:focus {
    border-color: var(--r-accent-border);
    background: var(--r-bg-card);
    box-shadow: 0 0 0 3px var(--r-accent-bg);
    width: 220px;
  }
}

/* ── View toggle ── */
.fm-view-toggle {
  display: flex;
  background: var(--r-bg-hover);
  border-radius: 8px;
  padding: 2px;
  gap: 1px;
}

.fm-view-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 7px;
  color: var(--r-text-muted);
  background: transparent;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover { color: var(--r-text-muted); }

  &--active {
    color: var(--r-text-primary);
    background: var(--r-bg-card);
    box-shadow: var(--r-shadow-sm);
  }
}

/* ── Buttons ── */
.fm-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  border: none;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;
  line-height: 1;

  &--ghost {
    color: var(--r-text-secondary);
    background: var(--r-bg-card);
    border: 1px solid var(--r-border);

    &:hover {
      border-color: var(--r-border-dark);
      background: var(--r-bg-panel);
      color: var(--r-text-primary);
    }
  }

  &--primary {
    color: #fff;
    background: linear-gradient(135deg, var(--r-accent), var(--r-accent-hover));
    box-shadow: var(--r-shadow-sm);

    &:hover {
      background: var(--r-accent-hover);
      box-shadow: var(--r-shadow-md);
      transform: translateY(-0.5px);
    }

    &:active { transform: translateY(0); }
  }
}

/* ── Breadcrumb ── */
.fm-breadcrumb {
  display: flex;
  align-items: center;
  gap: 0;
  margin-bottom: 20px;
  padding: 10px 16px;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: 10px;
  overflow-x: auto;
}

.fm-breadcrumb__item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: none;
  border-radius: 6px;
  background: none;
  font-size: 13px;
  font-weight: 500;
  color: var(--r-accent);
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;

  &:hover { background: var(--r-accent-bg); }

  &--active {
    color: var(--r-text-primary);
    font-weight: 600;
    cursor: default;

    &:hover { background: none; }
  }
}

.fm-breadcrumb__home {
  color: var(--r-text-muted);
}

.fm-breadcrumb__sep {
  color: var(--r-border-dark);
  flex-shrink: 0;
  margin: 0 2px;
}

/* ── Grid View ── */
.fm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.fm-grid-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 12px 14px;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: 12px;
  cursor: default;
  transition: all 0.2s ease;
  opacity: 0;
  transform: translateY(6px);

  &--appeared {
    animation: fm-fadeIn 0.25s ease forwards;
    animation-delay: var(--delay);
  }

  &:hover {
    border-color: var(--r-border);
    box-shadow: var(--r-shadow-md);

    .fm-grid-item__actions {
      opacity: 1;
      transform: translateY(0);
    }
  }
}

.fm-grid-item__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  cursor: pointer;

  &--folder {
    cursor: pointer;
  }
}

.fm-grid-item__ext-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.03em;
}

.fm-grid-item__name {
  font-size: 12px;
  font-weight: 500;
  color: var(--r-text-primary);
  text-align: center;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  line-height: 1.3;
}

.fm-grid-item__meta {
  font-size: 11px;
  color: var(--r-text-muted);
}

.fm-grid-item__actions {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  gap: 3px;
  opacity: 0;
  transform: translateY(-4px);
  transition: all 0.18s ease;
}

.fm-mini-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 6px;
  background: var(--r-bg-hover);
  color: var(--r-text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
  backdrop-filter: blur(4px);

  &:hover {
    background: var(--r-border);
    color: var(--r-text-secondary);
  }

  &--danger:hover {
    background: var(--r-danger-bg);
    color: var(--r-danger);
  }
}

@keyframes fm-fadeIn {
  to { opacity: 1; transform: translateY(0); }
}

/* ── List View ── */
.fm-list-wrap {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 12px;
  overflow: hidden;
}

.fm-list-header {
  display: grid;
  grid-template-columns: 1fr 100px 90px 100px 200px;
  align-items: center;
  padding: 10px 20px;
  background: var(--r-bg-panel);
  border-bottom: 1px solid var(--r-border-light);
  font-size: 11px;
  font-weight: 600;
  color: var(--r-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.fm-list-header__size,
.fm-list-header__type,
.fm-list-header__time {
  text-align: center;
}

.fm-list-header__actions {
  text-align: right;
}

.fm-list-row {
  display: grid;
  grid-template-columns: 1fr 100px 90px 100px 200px;
  align-items: center;
  padding: 10px 20px;
  border-bottom: 1px solid var(--r-bg-panel);
  transition: background 0.12s ease;
  opacity: 0;
  transform: translateX(-4px);

  &--appeared {
    animation: fm-slideIn 0.2s ease forwards;
    animation-delay: var(--delay);
  }

  &:hover {
    background: var(--r-bg-panel);

    .fm-list-row__actions {
      opacity: 1;
    }
  }

  &:last-child {
    border-bottom: none;
  }
}

@keyframes fm-slideIn {
  to { opacity: 1; transform: translateX(0); }
}

.fm-list-row__name {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.fm-list-row__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  cursor: pointer;

  &--folder {
    cursor: pointer;
  }
}

.fm-list-row__ext {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 7px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.fm-list-row__filename {
  font-size: 13px;
  font-weight: 500;
  color: var(--r-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.12s ease;

  &:hover {
    color: var(--r-accent);
  }
}

.fm-list-row__size {
  font-size: 12px;
  color: var(--r-text-muted);
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.fm-list-row__type {
  text-align: center;
}

.fm-type-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.03em;

  &--dir {
    color: var(--r-warning);
    background: var(--r-warning-bg);
  }
}

.fm-list-row__time {
  font-size: 12px;
  color: var(--r-text-muted);
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.fm-list-row__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.fm-action-link {
  padding: 4px 10px;
  border: none;
  border-radius: 6px;
  background: none;
  font-size: 12px;
  font-weight: 500;
  color: var(--r-text-muted);
  cursor: pointer;
  transition: all 0.12s ease;
  white-space: nowrap;

  &:hover {
    color: var(--r-accent);
    background: var(--r-accent-bg);
  }

  &--edit {
    color: var(--r-accent);
  }

  &--danger:hover {
    color: var(--r-danger);
    background: var(--r-danger-bg);
  }
}

/* ── Skeleton ── */
.fm-grid-skel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 12px 14px;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border-light);
  border-radius: 12px;
}

.fm-list-skel {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid var(--r-bg-panel);
}

.fm-skel__icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(90deg, var(--r-bg-hover) 25%, var(--r-border) 50%, var(--r-bg-hover) 75%);
  background-size: 200% 100%;
  animation: fm-shimmer 1.5s infinite;
}

.fm-skel__text {
  width: 80px;
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--r-bg-hover) 25%, var(--r-border) 50%, var(--r-bg-hover) 75%);
  background-size: 200% 100%;
  animation: fm-shimmer 1.5s infinite;
}

.fm-skel__meta {
  flex: 1;
  height: 10px;
  border-radius: 5px;
  background: linear-gradient(90deg, var(--r-bg-hover) 25%, var(--r-border) 50%, var(--r-bg-hover) 75%);
  background-size: 200% 100%;
  animation: fm-shimmer 1.5s infinite;
}

.fm-grid-skel .fm-skel__icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
}

@keyframes fm-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ── Empty state ── */
.fm-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px 60px;
}

.fm-empty__visual {
  margin-bottom: 20px;
  opacity: 0.7;
}

.fm-empty__text {
  font-size: 14px;
  font-weight: 600;
  color: var(--r-text-muted);
  margin: 0 0 6px 0;
}

.fm-empty__hint {
  font-size: 13px;
  color: var(--r-text-muted);
  margin: 0;
}

/* ── Editor ── */
.fm-editor {
  min-height: 400px;
}

.fm-editor__textarea :deep(.el-textarea__inner) {
  font-family: var(--r-font-mono);
  font-size: 13px;
  line-height: 1.6;
  border-radius: 8px;
  background: var(--r-bg-panel);
  border-color: var(--r-border);
  padding: 16px;

  &:focus {
    background: var(--r-bg-card);
    border-color: var(--r-accent-border);
    box-shadow: 0 0 0 3px var(--r-accent-bg);
  }
}
</style>
