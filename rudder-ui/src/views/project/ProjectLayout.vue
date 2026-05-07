<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getProject } from '@/api/workspace'
import { useUserStore } from '@/stores/user'
import { cardColor } from '@/utils/colorMeta'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const workspaceId = computed(() => Number(route.params.workspaceId))
const projectCode = computed(() => route.params.projectCode as string)
const projectName = ref('Project')
const projectId = ref(0)
const avatarColor = computed(() => cardColor(projectId.value))

const tabs = [
  { key: 'workflow-definitions', icon: 'Share' },
  { key: 'instances', icon: 'DataAnalysis' },
  { key: 'parameters', icon: 'SetUp' },
  { key: 'publish-records', icon: 'Clock' },
] as const

const tabLabels: Record<string, string> = {
  'workflow-definitions': 'project.workflowDefinitions',
  'instances': 'project.instances',
  'parameters': 'project.parameters',
  'publish-records': 'project.publishRecords',
}

const activeTab = computed(() => {
  const path = route.path
  if (path.endsWith('/parameters')) return 'parameters'
  if (path.endsWith('/instances')) return 'instances'
  if (path.endsWith('/publish-records')) return 'publish-records'
  return 'workflow-definitions'
})

function switchTab(tab: string) {
  router.push(`/workspaces/${workspaceId.value}/projects/${projectCode.value}/${tab}`)
}

function goBack() {
  router.push(`/workspaces/${workspaceId.value}/projects`)
}

async function loadProject() {
  try {
    userStore.setProject(Number(projectCode.value))
    const { data } = await getProject(workspaceId.value, projectCode.value)
    projectName.value = data?.name ?? 'Project'
    projectId.value = data?.id ?? 0
  } catch {
    ElMessage.error('Failed to load project')
  }
}

onMounted(loadProject)
watch(projectCode, () => { loadProject() })
</script>

<template>
  <div class="pl">
    <aside class="pl-side">
      <div class="pl-header">
        <button class="pl-back" @click="goBack">
          <el-icon size="14"><ArrowLeft /></el-icon>
        </button>
        <div class="pl-project">
          <div class="pl-project__avatar" :style="{ background: avatarColor }">
            <el-icon size="14"><Folder /></el-icon>
          </div>
          <div class="pl-project__text">
            <span class="pl-project__label">{{ t('nav.projects') }}</span>
            <span class="pl-project__name">{{ projectName }}</span>
          </div>
        </div>
      </div>

      <nav class="pl-nav">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="['pl-item', { 'pl-item--active': activeTab === tab.key }]"
          @click="switchTab(tab.key)"
        >
          <el-icon size="15"><component :is="tab.icon" /></el-icon>
          <span>{{ t(tabLabels[tab.key]) }}</span>
        </button>
      </nav>
    </aside>

    <main class="pl-body">
      <router-view />
    </main>
  </div>
</template>

<style scoped lang="scss">
.pl { display: flex; height: 100%; }

.pl-side {
  width: 208px; flex-shrink: 0; display: flex; flex-direction: column;
  background: var(--r-bg-card); border-right: 1px solid var(--r-border);
}

.pl-header {
  display: flex; align-items: center; gap: 10px; padding: 12px;
  border-bottom: 1px solid var(--r-border-light);
}

.pl-back {
  display: flex; align-items: center; justify-content: center;
  width: 26px; height: 26px; border: none; border-radius: 6px;
  background: transparent; color: var(--r-text-muted); cursor: pointer; flex-shrink: 0;
  transition: background 0.12s, color 0.12s;
  &:hover { background: var(--r-bg-hover); color: var(--r-text-primary); }
}

.pl-project { display: flex; align-items: center; gap: 9px; min-width: 0; }

.pl-project__avatar {
  display: flex; align-items: center; justify-content: center;
  width: 26px; height: 26px; border-radius: 6px; color: #fff; flex-shrink: 0;
}

.pl-project__text { display: flex; flex-direction: column; gap: 1px; min-width: 0; }

.pl-project__label {
  font-size: 11px; font-weight: 500; color: var(--r-text-muted);
  text-transform: uppercase; letter-spacing: 0.05em; line-height: 1.2;
}

.pl-project__name {
  font-size: 13px; font-weight: 600; color: var(--r-text-primary);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; line-height: 1.3;
}

.pl-nav { display: flex; flex-direction: column; gap: 1px; padding: 10px 8px; flex: 1; }

.pl-item {
  display: flex; align-items: center; gap: 6px; padding: 7px 10px;
  border: none; border-radius: 6px; background: transparent;
  font-size: 13px; color: var(--r-text-tertiary); cursor: pointer;
  transition: all 0.12s; white-space: nowrap; text-align: left;
  &:hover { background: var(--r-bg-hover); color: var(--r-text-primary); }
  &--active { background: var(--r-accent-bg); color: var(--r-accent); font-weight: 600; }
}

.pl-body { flex: 1; overflow: auto; background: var(--r-bg-panel); min-width: 0; }
</style>
