<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getWorkspace } from '@/api/workspace'
import { listDatasources } from '@/api/datasource'
import { useUserStore } from '@/stores/user'
import { useWorkspaceStore } from '@/stores/workspace'
import { useDatasourceStore } from '@/stores/datasource'
import { useAiChatStore } from '@/stores/aiChat'
import { usePermission } from '@/composables/usePermission'
import { setLocale, getLocale } from '@/locales'
import AboutDialog from '@/components/AboutDialog.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const workspaceStore = useWorkspaceStore()
const datasourceStore = useDatasourceStore()
const aiChatStore = useAiChatStore()
const { hasRole, isSuperAdmin } = usePermission()
const currentLocale = ref(getLocale())
const aboutVisible = ref(false)

function switchLocale(locale: string) {
  setLocale(locale)
  currentLocale.value = locale
}

const workspaceId = computed(() => Number(route.params.workspaceId))

const activeMenu = computed(() => {
  const path = route.path
  if (path.includes('/ide')) return 'ide'
  if (path.includes('/projects')) return 'projects'
  if (path.includes('/jobs')) return 'jobs'
  if (path.includes('/files')) return 'files'
  if (path.includes('/approvals')) return 'approvals'
  if (path.includes('/mcp')) return 'mcp'
  if (path.includes('/admin')) return 'admin'
  return 'ide'
})

const workspaceName = computed(() => workspaceStore.currentWorkspace?.name ?? 'Workspace')
const username = computed(() => userStore.userInfo?.username ?? 'User')

// admin 顶部 tab 给 VIEWER 也开:进去后 AdminLayout 侧边栏只会渲染 ServiceMonitor 这一项 (其它系统级配置仍受 router meta 拦截)
const navItems = computed(() => {
  const all = [
    { key: 'ide', label: t('nav.ide'), icon: 'Monitor', requireRole: 'VIEWER' as const },
    { key: 'projects', label: t('nav.projects'), icon: 'Folder', requireRole: 'VIEWER' as const },
    { key: 'jobs', label: t('nav.jobs'), icon: 'Cpu', requireRole: 'DEVELOPER' as const },
    { key: 'files', label: t('nav.files'), icon: 'FolderOpened', requireRole: 'VIEWER' as const },
    { key: 'approvals', label: t('nav.approvals'), icon: 'Stamp', requireRole: 'VIEWER' as const },
    { key: 'mcp', label: t('nav.mcp'), icon: 'Connection', requireRole: 'VIEWER' as const },
    { key: 'admin', label: t('admin.title'), icon: 'Setting', requireRole: 'VIEWER' as const },
  ]
  return all.filter(item => hasRole(item.requireRole))
})

function handleMenuSelect(index: string) {
  router.push(`/workspaces/${workspaceId.value}/${index}`)
}

function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (cmd === 'workspaces') {
    router.push('/workspaces')
  } else if (cmd === 'about') {
    aboutVisible.value = true
  }
}

async function init() {
  const wsId = workspaceId.value
  userStore.setWorkspace(wsId)

  datasourceStore.setDatasources([])
  aiChatStore.reset()

  try {
    const { data } = await getWorkspace(wsId)
    workspaceStore.setCurrent(data)
  } catch {
    ElMessage.error('Failed to load workspace')
  }

  // SUPER_ADMIN 看全量,其他角色按 workspace 权限过滤
  try {
    const params = isSuperAdmin.value ? {} : { workspaceId: wsId }
    const { data } = await listDatasources(params)
    datasourceStore.setDatasources(data ?? [])
  } catch (e) {
    console.warn('Failed to load datasources', e)
  }
}

onMounted(init)
watch(workspaceId, init)
</script>

<template>
  <div class="app-layout">
    <!-- Header -->
    <header class="app-header">
      <div class="header-left">
        <div class="header-logo" @click="handleCommand('workspaces')">
          <div class="logo-mark">R</div>
          <span class="logo-text">Rudder</span>
        </div>
        <span class="header-divider" />
        <span class="header-ws">{{ workspaceName }}</span>
      </div>

      <nav class="header-nav">
        <div
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: activeMenu === item.key }"
          @click="handleMenuSelect(item.key)"
        >
          <el-icon size="16"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </div>
      </nav>

      <div class="header-right">
        <ThemeToggle />
        <el-dropdown trigger="click" @command="switchLocale">
          <div class="header-action">
            <el-icon size="16"><Eleme /></el-icon>
            <span>{{ currentLocale === 'zh' ? '中文' : 'EN' }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="en" :disabled="currentLocale === 'en'">English</el-dropdown-item>
              <el-dropdown-item command="zh" :disabled="currentLocale === 'zh'">中文</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span class="header-sep" />
        <el-dropdown trigger="click" @command="handleCommand">
          <div class="header-action">
            <el-avatar :size="24" class="header-avatar">
              {{ username.charAt(0).toUpperCase() }}
            </el-avatar>
            <span>{{ username }}</span>
            <el-icon size="12" style="color: var(--r-text-disabled)"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="workspaces">
                <el-icon><Grid /></el-icon>{{ t('header.switchWorkspace') }}
              </el-dropdown-item>
              <el-dropdown-item command="about" divided>
                <el-icon><InfoFilled /></el-icon>{{ t('header.about') }}
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>{{ t('header.logout') }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- Content -->
    <main class="app-content" :class="{ 'app-content--ide': activeMenu === 'ide' }">
      <router-view />
    </main>

    <!-- About Dialog -->
    <AboutDialog v-model="aboutVisible" />
  </div>
</template>

<style scoped lang="scss">
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  height: 52px;
  padding: 0 var(--r-space-5);
  background: var(--r-bg-header);
  border-bottom: 1px solid var(--r-border);
  flex-shrink: 0;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);
  margin-right: var(--r-space-6);
}

.header-logo {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  cursor: pointer;
}

.logo-mark {
  width: 26px;
  height: 26px;
  background: var(--r-logo-bg);
  border-radius: var(--r-radius-md);
  font-size: var(--r-font-base);
  font-weight: var(--r-weight-bold);
  color: var(--r-logo-text);
  text-align: center;
  line-height: 26px;
}

.logo-text {
  font-size: var(--r-font-md);
  font-weight: var(--r-weight-bold);
  color: var(--r-text-primary);
  letter-spacing: -0.02em;
}

.header-divider {
  display: inline-block;
  width: 1px;
  height: 18px;
  background: var(--r-border);
}

.header-ws {
  font-size: var(--r-font-base);
  color: var(--r-text-tertiary);
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-nav {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: var(--r-font-base);
  color: var(--r-text-tertiary);
  border-radius: var(--r-radius-md);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;

  &:hover {
    color: var(--r-text-primary);
    background: var(--r-bg-hover);
  }

  &.active {
    color: var(--r-accent);
    background: var(--r-accent-bg);
    font-weight: var(--r-weight-semibold);
  }
}

.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
}

.header-sep {
  width: 1px;
  height: 14px;
  background: var(--r-border);
  margin: 0 6px;
}

.header-action {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 5px 8px;
  border-radius: var(--r-radius-md);
  font-size: var(--r-font-sm);
  color: var(--r-text-tertiary);
  transition: background-color 0.15s ease, color 0.15s ease;

  &:hover {
    background: var(--r-bg-hover);
    color: var(--r-text-primary);
  }
}

.header-avatar {
  background: var(--r-logo-bg);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  color: #fff;
}

.app-content {
  flex: 1;
  overflow: auto;
  background: var(--r-bg-panel);

  &--ide {
    overflow: hidden;
  }
}
</style>
