<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { listWorkspaces, createWorkspace } from '@/api/workspace'
import { listQuickLinks, type QuickLink } from '@/api/quickLink'
import { getOverviewStats, type OverviewStats } from '@/api/overview'
import { useUserStore } from '@/stores/user'
import { cardColor } from '@/utils/colorMeta'
import AppHeader from '@/components/AppHeader.vue'
import { useAboutDialog } from '@/composables/useAboutDialog'

interface Workspace {
  id: number
  name: string
  description: string
  createdAt: string
}

const { t, locale } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const { open: openAbout } = useAboutDialog()

const workspaces = ref<Workspace[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const creating = ref(false)
const searchText = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const createFormRef = ref<FormInstance>()
const createForm = ref({ name: '', description: '' })
const quickEntries = ref<QuickLink[]>([])
const docLinks = ref<QuickLink[]>([])
const stats = ref<OverviewStats>({ workspaceCount: 0, workflowCount: 0, scriptCount: 0 })

const username = computed(() => userStore.userInfo?.username ?? 'User')
const isSuperAdmin = computed(() => userStore.userInfo?.role === 'SUPER_ADMIN')

const nameRef = ref<HTMLElement | null>(null)

async function fitNameSize() {
  await nextTick()
  const el = nameRef.value
  if (!el) return
  let size = 14
  el.style.fontSize = size + 'px'
  while (el.scrollWidth > el.clientWidth && size > 9) {
    size -= 0.5
    el.style.fontSize = size + 'px'
  }
}

watch(username, fitNameSize, { immediate: true })

async function fetchWorkspaces() {
  loading.value = true
  try {
    const res: any = await listWorkspaces({
      searchVal: searchText.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    workspaces.value = res.data ?? []
    total.value = res.total ?? 0
  } catch {
    ElMessage.error('Failed to load workspaces')
  } finally {
    loading.value = false
  }
}

function handleSearch() { pageNum.value = 1; fetchWorkspaces() }

function handlePageChange(page: number) { pageNum.value = page; fetchWorkspaces() }
function handleSizeChange(size: number) { pageSize.value = size; pageNum.value = 1; fetchWorkspaces() }

function openWorkspace(ws: Workspace) {
  router.push(`/workspaces/${ws.id}/ide`)
}

function openCreateDialog() {
  createForm.value = { name: '', description: '' }
  dialogVisible.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    await createWorkspace(createForm.value)
    ElMessage.success(t('common.success'))
    dialogVisible.value = false
    await fetchWorkspaces()
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    creating.value = false
  }
}

async function handleDelete(ws: Workspace) {
  try {
    await ElMessageBox.confirm(
      t('workspace.deleteConfirm', { name: ws.name }),
      t('common.confirm'),
      { type: 'warning' },
    )
    ElMessage.success(t('common.success'))
    await fetchWorkspaces()
  } catch { /* cancelled */ }
}

function formatDate(d: string) {
  if (!d) return ''
  return new Date(d).toLocaleDateString(locale.value === 'zh' ? 'zh-CN' : 'en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
  })
}

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 12) return locale.value === 'zh' ? '上午好' : 'Good morning'
  if (h < 18) return locale.value === 'zh' ? '下午好' : 'Good afternoon'
  return locale.value === 'zh' ? '晚上好' : 'Good evening'
})

async function fetchQuickLinks() {
  try {
    const res: any = await listQuickLinks({ onlyEnabled: true })
    const all: QuickLink[] = res.data ?? []
    quickEntries.value = all.filter(q => q.category === 'QUICK_ENTRY')
    docLinks.value = all.filter(q => q.category === 'DOC_LINK')
  } catch { /* 拦截器已 toast */ }
}

async function fetchStats() {
  try {
    const res: any = await getOverviewStats()
    if (res.data) stats.value = res.data
  } catch { /* 拦截器已 toast */ }
}

onMounted(() => {
  fetchWorkspaces()
  fetchQuickLinks()
  fetchStats()
})
</script>

<template>
  <div class="page">
    <AppHeader />

    <!-- Body -->
    <div class="body">
      <div class="main">
        <!-- Title row -->
        <div class="main__head">
          <div class="main__head-left">
            <div class="main__title-row">
              <h1 class="main__title">{{ t('workspace.title') }}</h1>
              <span class="main__count" v-if="total">{{ total }}</span>
            </div>
            <p class="main__subtitle">{{ t('workspace.subtitle') }}</p>
          </div>
          <div class="main__head-right">
            <el-input
              v-model="searchText"
              :placeholder="t('common.search')"
              clearable
              :prefix-icon="Search"
              style="width: 200px"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
            <el-button v-if="isSuperAdmin" type="primary" @click="openCreateDialog">
              <el-icon><Plus /></el-icon>{{ t('workspace.newWorkspace') }}
            </el-button>
          </div>
        </div>

        <!-- Grid -->
        <div v-loading="loading" class="grid">
          <div
            v-for="(ws, idx) in workspaces"
            :key="ws.id"
            class="card"
            :style="{ '--accent': cardColor(ws.id), '--i': idx }"
            @click="openWorkspace(ws)"
          >
            <div class="card__inner">
              <div class="card__row1">
                <div class="card__avatar">
                  <span>{{ ws.name.charAt(0).toUpperCase() }}</span>
                </div>
                <el-dropdown v-if="isSuperAdmin" trigger="click" @click.stop>
                  <div class="card__menu" @click.stop>
                    <el-icon size="14"><MoreFilled /></el-icon>
                  </div>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click.stop="handleDelete(ws)">
                        <el-icon><Delete /></el-icon>{{ t('common.delete') }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
              <div class="card__name">{{ ws.name }}</div>
              <div class="card__desc">{{ ws.description || t('workspace.noDescription') }}</div>
              <div class="card__footer">
                <span class="card__date" v-if="ws.createdAt">
                  <el-icon size="12"><Calendar /></el-icon>
                  {{ formatDate(ws.createdAt) }}
                </span>
                <span class="card__enter">
                  <el-icon size="12"><Right /></el-icon>
                </span>
              </div>
            </div>
          </div>

          <!-- New card -->
          <div v-if="isSuperAdmin" class="card card--new" :style="{ '--i': workspaces.length }" @click="openCreateDialog">
            <div class="card--new__body">
              <div class="card--new__icon"><el-icon size="20"><Plus /></el-icon></div>
              <span class="card--new__label">{{ t('workspace.newWorkspace') }}</span>
            </div>
          </div>
        </div>

        <div v-if="!loading && workspaces.length === 0" class="empty">
          <el-empty :description="t('common.noData')" />
        </div>

        <div v-if="total > pageSize" class="pagination-wrap">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="total"
            :current-page="pageNum"
            :page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </div>

      <!-- Sidebar -->
      <aside class="sidebar">
        <!-- Profile -->
        <div class="sb-card sb-profile">
          <div class="profile__greeting">{{ greeting }},</div>
          <div class="profile__row">
            <el-avatar :size="40" class="user-avatar">{{ username.charAt(0).toUpperCase() }}</el-avatar>
            <div class="profile__info">
              <div class="profile__name" ref="nameRef">{{ username }}</div>
              <el-tag v-if="isSuperAdmin" size="small" type="danger" effect="light" round>{{ t('sidebar.superAdmin') }}</el-tag>
              <el-tag v-else size="small" effect="light" round>{{ t('sidebar.member') }}</el-tag>
            </div>
          </div>
        </div>

        <!-- Overview -->
        <div class="sb-card">
          <div class="sb-title">{{ t('sidebar.overview') }}</div>
          <div class="stats">
            <div class="stat">
              <el-icon class="stat__icon" size="18" style="color: var(--r-accent)"><Folder /></el-icon>
              <div class="stat__value">{{ stats.workspaceCount }}</div>
              <div class="stat__label">{{ t('sidebar.workspaces') }}</div>
            </div>
            <div class="stat">
              <el-icon class="stat__icon" size="18" style="color: var(--r-purple)"><Share /></el-icon>
              <div class="stat__value">{{ stats.workflowCount }}</div>
              <div class="stat__label">{{ t('sidebar.workflowDefinitions') }}</div>
            </div>
            <div class="stat">
              <el-icon class="stat__icon" size="18" style="color: var(--r-success)"><Document /></el-icon>
              <div class="stat__value">{{ stats.scriptCount }}</div>
              <div class="stat__label">{{ t('sidebar.scripts') }}</div>
            </div>
          </div>
        </div>

        <!-- Quick links -->
        <div class="sb-card" v-if="quickEntries.length">
          <div class="sb-title">{{ t('sidebar.quickLinks') }}</div>
          <div class="link-list">
            <a
              v-for="item in quickEntries"
              :key="item.id"
              class="link-item"
              :href="item.url"
              :target="item.target"
              :rel="item.target === '_blank' ? 'noopener noreferrer' : undefined"
            >
              <div class="link-item__icon">
                <img v-if="item.icon" :src="item.icon" :alt="item.name" class="link-item__svg" />
              </div>
              <div class="link-item__text">
                <div>{{ item.name }}</div>
                <div class="link-item__sub" v-if="item.description">{{ item.description }}</div>
              </div>
            </a>
          </div>
        </div>

        <!-- Docs -->
        <div class="sb-card" v-if="docLinks.length">
          <div class="sb-title">{{ t('sidebar.docs') }}</div>
          <div class="link-list">
            <a
              v-for="item in docLinks"
              :key="item.id"
              class="link-item"
              :href="item.url"
              :target="item.target"
              :rel="item.target === '_blank' ? 'noopener noreferrer' : undefined"
            >
              <div class="link-item__icon">
                <img v-if="item.icon" :src="item.icon" :alt="item.name" class="link-item__svg" />
              </div>
              <div class="link-item__text">
                <div>{{ item.name }}</div>
                <div class="link-item__sub" v-if="item.description">{{ item.description }}</div>
              </div>
            </a>
          </div>
        </div>
      </aside>
    </div>

    <!-- Footer -->
    <footer class="page-footer" @click="openAbout">{{ t('footer.copyright') }}</footer>

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="t('workspace.createTitle')" width="460px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" label-position="top" @submit.prevent="handleCreate">
        <el-form-item
          :label="t('common.name')"
          prop="name"
          :rules="[{ required: true, message: t('workspace.nameRequired'), trigger: 'blur' }]"
        >
          <el-input v-model="createForm.name" :placeholder="t('workspace.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="createForm.description" type="textarea" :rows="3" :placeholder="t('workspace.descPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--r-bg-panel);
}

.user-avatar { background: var(--r-logo-bg); font-size: 11px; font-weight: 600; color: #fff; flex-shrink: 0; }

/* ===== Body ===== */
.body {
  display: flex;
  gap: 24px;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
  padding: 28px 32px 48px;
  align-items: flex-start;
}

.main { flex: 1; min-width: 0; }

.main__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 22px;
}
.main__head-left { display: flex; flex-direction: column; gap: 4px; }
.main__head-right { display: flex; align-items: center; gap: 8px; margin-top: 2px; }

.main__title-row { display: flex; align-items: center; gap: 10px; }
.main__title { margin: 0; font-size: 18px; font-weight: 700; color: var(--r-text-primary); letter-spacing: -0.02em; }
.main__subtitle { margin: 0; font-size: 13px; color: var(--r-text-muted); font-weight: 400; }
.main__count {
  font-size: 11px; color: var(--r-text-tertiary); background: var(--r-border);
  padding: 2px 8px; border-radius: 4px; font-weight: 600;
}

/* ===== Grid ===== */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
  gap: 14px;
}

/* ===== Card ===== */
.card {
  position: relative;
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
  animation: card-in 0.4s cubic-bezier(0.23, 1, 0.32, 1) backwards;
  animation-delay: calc(var(--i, 0) * 0.05s);

  &:not(.card--new)::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 4px;
    background: var(--accent, var(--r-border));
    transition: width 0.2s ease;
  }

  &:hover {
    border-color: var(--r-border-dark);
    box-shadow: var(--r-shadow-md);
    transform: translateY(-3px);

    &:not(.card--new)::before { width: 6px; }
    .card__menu { opacity: 1; }
    .card__enter { opacity: 1; }
  }
}

.card__inner { padding: 16px 18px 14px 20px; }

.card__row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.card__avatar {
  width: 36px; height: 36px; border-radius: 8px;
  font-size: 14px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  background: color-mix(in srgb, var(--accent) 8%, transparent);
  span { color: var(--accent); }
}

.card__menu {
  opacity: 0; padding: 3px 5px; border-radius: 4px; color: var(--r-text-muted);
  transition: opacity 0.12s, background 0.12s;
  &:hover { background: var(--r-bg-hover); color: var(--r-text-secondary); }
}

.card__name {
  font-size: 14px; font-weight: 600; color: var(--r-text-primary);
  margin-bottom: 4px; letter-spacing: -0.01em;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.card__desc {
  font-size: 12px; color: var(--r-text-muted); line-height: 1.5;
  height: 36px; overflow: hidden;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}

.card__footer {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 14px; padding-top: 10px; border-top: 1px solid var(--r-border-light);
}

.card__date {
  display: flex; align-items: center; gap: 4px;
  font-size: 11px; color: var(--r-text-disabled);
}

.card__enter {
  opacity: 0;
  color: var(--r-accent);
  transition: opacity 0.15s;
}

/* New workspace card */
.card--new {
  border-style: dashed; border-color: var(--r-border-dark);
  display: flex; align-items: center; justify-content: center;
  min-height: 170px;

  &:hover {
    border-color: var(--r-accent);
    background: var(--r-accent-bg);
    transform: translateY(-3px);
    box-shadow: var(--r-shadow-md);
    .card--new__icon { background: var(--r-accent); color: var(--r-text-inverse); }
    .card--new__label { color: var(--r-accent); }
  }
}
.card--new__body { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.card--new__icon {
  width: 40px; height: 40px; border-radius: 10px;
  background: var(--r-bg-hover); color: var(--r-text-muted);
  display: flex; align-items: center; justify-content: center;
  transition: background 0.2s ease, color 0.2s ease;
}
.card--new__label { font-size: 12px; color: var(--r-text-muted); font-weight: 500; transition: color 0.15s; }

.empty { margin-top: 60px; }
.pagination-wrap { padding: 24px 0 0; display: flex; justify-content: center; }

/* ===== Sidebar ===== */
.sidebar {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sb-card {
  background: var(--r-bg-card);
  border: 1px solid var(--r-border);
  border-radius: 8px;
  padding: 18px;
  animation: card-in 0.4s ease backwards;

  &:nth-child(1) { animation-delay: 0.1s; }
  &:nth-child(2) { animation-delay: 0.16s; }
  &:nth-child(3) { animation-delay: 0.22s; }
  &:nth-child(4) { animation-delay: 0.28s; }
}

.sb-title {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--r-text-muted);
  margin-bottom: 12px;
}

/* Profile */
.sb-profile { padding: 18px; }
.profile__greeting { font-size: 12px; color: var(--r-text-muted); margin-bottom: 10px; }
.profile__row { display: flex; align-items: center; gap: 10px; }
.profile__info { display: flex; flex-direction: column; gap: 3px; min-width: 0; flex: 1; }
.profile__name {
  font-size: 14px; font-weight: 700; color: var(--r-text-primary); letter-spacing: -0.01em;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* Stats */
.stats { display: flex; gap: 8px; }
.stat {
  flex: 1; text-align: center;
  padding: 10px 0 8px;
  background: var(--r-bg-panel);
  border: 1px solid var(--r-border-light);
  border-radius: 6px;
}
.stat__icon { margin-bottom: 2px; }
.stat__value { font-size: 16px; font-weight: 700; color: var(--r-text-primary); margin-top: 1px; }
.stat__label { font-size: 11px; color: var(--r-text-muted); margin-top: 1px; font-weight: 500; }

/* Links */
.link-list { display: flex; flex-direction: column; gap: 1px; }
.link-item {
  display: flex; align-items: center; gap: 9px;
  padding: 7px 8px; border-radius: 6px;
  font-size: 12px; color: var(--r-text-secondary);
  cursor: pointer; text-decoration: none;
  transition: background 0.12s;
  &:hover { background: var(--r-bg-hover); }
}
.link-item__icon {
  width: 32px; height: 32px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  background: var(--r-accent-bg);
}
.link-item__svg { width: 20px; height: 20px; object-fit: contain; flex-shrink: 0; }
.link-item__text { flex: 1; min-width: 0; }
.link-item__sub { font-size: 11px; color: var(--r-text-disabled); margin-top: 1px; }

/* ===== Footer ===== */
.page-footer {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  text-align: center;
  padding: 10px 0;
  font-size: 11px;
  color: var(--r-text-disabled);
  cursor: pointer;
  transition: color 0.12s;
  &:hover { color: var(--r-text-muted); }
}
</style>
