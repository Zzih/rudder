<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { useWorkspaceStore } from '@/stores/workspace'
import { usePermission } from '@/composables/usePermission'
import { useAboutDialog } from '@/composables/useAboutDialog'
import { setLocale } from '@/locales'
import AboutDialog from '@/components/AboutDialog.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import type { Role } from '@/stores/user'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const workspaceStore = useWorkspaceStore()
const { hasRole } = usePermission()
const { visible: aboutVisible, open: openAbout } = useAboutDialog()

const onWorkspaceList = computed(() => route.path === '/workspaces')
// workspaceId 唯一来源:URL params。admin 也定义为 /workspaces/:id/admin/* 真路由,刷新就会带上
const workspaceId = computed(() => {
  const routeId = Number(route.params.workspaceId)
  return Number.isFinite(routeId) && routeId > 0 ? routeId : null
})
const inWorkspace = computed(() => workspaceId.value !== null)
const workspaceName = computed(() => workspaceStore.currentWorkspace?.name ?? '')
const username = computed(() => userStore.userInfo?.username ?? 'User')

type NavKey = 'ide' | 'projects' | 'jobs' | 'files' | 'approvals' | 'mcp' | 'admin'
interface NavItem { key: NavKey; label: string; icon: string; target: string; requireRole: Role }

const navItems = computed<NavItem[]>(() => {
  const items: NavItem[] = []
  if (inWorkspace.value) {
    const wsBase = `/workspaces/${workspaceId.value}`
    items.push(
      { key: 'ide', label: t('nav.ide'), icon: 'Monitor', target: `${wsBase}/ide`, requireRole: 'VIEWER' },
      { key: 'projects', label: t('nav.projects'), icon: 'Folder', target: `${wsBase}/projects`, requireRole: 'VIEWER' },
      { key: 'jobs', label: t('nav.jobs'), icon: 'Cpu', target: `${wsBase}/jobs`, requireRole: 'DEVELOPER' },
      { key: 'files', label: t('nav.files'), icon: 'FolderOpened', target: `${wsBase}/files`, requireRole: 'VIEWER' },
      { key: 'approvals', label: t('nav.approvals'), icon: 'Stamp', target: `${wsBase}/approvals`, requireRole: 'VIEWER' },
      { key: 'mcp', label: t('nav.mcp'), icon: 'Connection', target: `${wsBase}/mcp`, requireRole: 'VIEWER' },
    )
  }
  // admin tab 一直在:在 workspace 里走 ws-scoped 嵌套 URL,否则走顶层 /admin
  const adminTarget = inWorkspace.value ? `/workspaces/${workspaceId.value}/admin` : '/admin'
  items.push({ key: 'admin', label: t('admin.title'), icon: 'Setting', target: adminTarget, requireRole: 'VIEWER' })
  return items.filter(item => hasRole(item.requireRole))
})

const activeKey = computed(() => {
  const path = route.path
  const match = (seg: string) => path.endsWith(`/${seg}`) || path.includes(`/${seg}/`)
  if (match('admin')) return 'admin'
  if (match('ide')) return 'ide'
  if (match('projects')) return 'projects'
  if (match('jobs')) return 'jobs'
  if (match('files')) return 'files'
  if (match('approvals')) return 'approvals'
  if (match('mcp')) return 'mcp'
  return ''
})

function navigate(item: NavItem) {
  router.push(item.target)
}

function switchLocale(value: string) {
  setLocale(value)
}

function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (cmd === 'workspaces') {
    router.push('/workspaces')
  } else if (cmd === 'about') {
    openAbout()
  }
}

function goHome() {
  router.push('/workspaces')
}
</script>

<template>
  <header class="app-header">
    <div class="header-left">
      <div class="header-logo" @click="goHome">
        <div class="logo-mark">R</div>
        <span class="logo-text">Rudder</span>
      </div>
      <template v-if="inWorkspace && workspaceName">
        <span class="header-divider" />
        <span class="header-ws">{{ workspaceName }}</span>
      </template>
    </div>

    <nav class="header-nav">
      <div
        v-for="item in navItems"
        :key="item.key"
        class="nav-item"
        :class="{ active: activeKey === item.key }"
        @click="navigate(item)"
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
          <span>{{ locale === 'zh' ? '中文' : 'EN' }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="en" :disabled="locale === 'en'">English</el-dropdown-item>
            <el-dropdown-item command="zh" :disabled="locale === 'zh'">中文</el-dropdown-item>
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
            <el-dropdown-item v-if="!onWorkspaceList" command="workspaces">
              <el-icon><Grid /></el-icon>{{ t('header.switchWorkspace') }}
            </el-dropdown-item>
            <el-dropdown-item command="about" :divided="!onWorkspaceList">
              <el-icon><InfoFilled /></el-icon>{{ t('header.about') }}
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon>{{ t('header.logout') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <AboutDialog v-model="aboutVisible" />
  </header>
</template>

<style scoped lang="scss">
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
  &:hover { color: var(--r-text-primary); background: var(--r-bg-hover); }
  &.active { color: var(--r-accent); background: var(--r-accent-bg); font-weight: var(--r-weight-semibold); }
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
  &:hover { background: var(--r-bg-hover); color: var(--r-text-primary); }
}

.header-avatar {
  background: var(--r-logo-bg);
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-semibold);
  color: #fff;
}
</style>
