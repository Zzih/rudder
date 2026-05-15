<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getProject } from '@/api/workspace'
import { useUserStore } from '@/stores/user'
import { useWorkflowContext } from '@/composables/useWorkflowContext'
import { cardColor } from '@/utils/colorMeta'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { workflowName } = useWorkflowContext()

const workspaceId = computed(() => Number(route.params.workspaceId))
const projectCode = computed(() => route.params.projectCode as string)
const workflowDefinitionCode = computed(() => route.params.workflowDefinitionCode as string | undefined)
const inWorkflow = computed(() => !!workflowDefinitionCode.value)

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

// 工作流模式下 back 回到该项目的工作流列表;否则回工作空间项目列表。
function goBack() {
  if (inWorkflow.value) {
    router.push(`/workspaces/${workspaceId.value}/projects/${projectCode.value}/workflow-definitions`)
  } else {
    router.push(`/workspaces/${workspaceId.value}/projects`)
  }
}

function goProjectHome() {
  router.push(`/workspaces/${workspaceId.value}/projects/${projectCode.value}/workflow-definitions`)
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
      <!-- Project 模式 header -->
      <div v-if="!inWorkflow" class="pl-header">
        <button class="pl-back" @click="goBack" :aria-label="t('common.back')">
          <el-icon :size="16"><ArrowLeft /></el-icon>
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

      <!-- Workflow 模式 header -->
      <div v-else class="pl-header">
        <button class="pl-back" @click="goBack" :aria-label="t('common.back')">
          <el-icon :size="16"><ArrowLeft /></el-icon>
        </button>
        <div class="pl-project">
          <div class="pl-project__avatar pl-project__avatar--workflow">
            <el-icon size="14"><Share /></el-icon>
          </div>
          <div class="pl-project__text">
            <span class="pl-project__crumb-row">
              <span class="pl-project__crumb" @click="goProjectHome">{{ projectName }}</span>
              <span class="pl-project__sep">/</span>
            </span>
            <span class="pl-project__name">{{ workflowName || '—' }}</span>
          </div>
        </div>
      </div>

      <!-- Project 模式 nav -->
      <nav v-if="!inWorkflow" class="sidebar-nav pl-nav">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="['sidebar-item', { 'is-active': activeTab === tab.key }]"
          @click="switchTab(tab.key)"
        >
          <el-icon :size="16"><component :is="tab.icon" /></el-icon>
          <span>{{ t(tabLabels[tab.key]) }}</span>
        </button>
      </nav>

      <!-- Workflow 模式 nav:DagEditor 通过 Teleport 把 task palette 挂进来 -->
      <div v-else id="wfd-palette-target" class="pl-palette-host" />
    </aside>

    <main class="pl-body">
      <router-view />
    </main>
  </div>
</template>

<style scoped lang="scss">
.pl { display: flex; height: 100%; }

.pl-side {
  width: 232px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--r-bg-card);
  border-right: 1px solid var(--r-border);
  overflow-y: auto;
}

.pl-header {
  display: flex;
  align-items: center;
  gap: var(--r-space-3);
  height: 52px;
  padding: 0 var(--r-space-3);
  border-bottom: 1px solid var(--r-border-light);
  flex-shrink: 0;
}

.pl-back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--r-radius-md);
  background: transparent;
  color: var(--r-text-muted);
  cursor: pointer;
  flex-shrink: 0;
  transition: background-color 0.15s ease, color 0.15s ease;
  &:hover {
    background: var(--r-bg-hover);
    color: var(--r-text-primary);
  }
  &:focus-visible {
    outline: 2px solid var(--r-accent);
    outline-offset: -2px;
  }
}

.pl-project {
  display: flex;
  align-items: center;
  gap: var(--r-space-2);
  min-width: 0;
}

.pl-project__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--r-radius-md);
  color: #fff;
  flex-shrink: 0;

  &--workflow {
    background: var(--r-accent);
  }
}

.pl-project__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.pl-project__label {
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-bold);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--r-text-muted);
  line-height: 1.2;
}

.pl-project__crumb-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  min-width: 0;
  font-size: var(--r-font-xs);
  line-height: 1.2;
}

.pl-project__crumb {
  color: var(--r-text-muted);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.15s ease;
  &:hover { color: var(--r-accent); }
}

.pl-project__sep {
  flex-shrink: 0;
  color: var(--r-text-disabled);
}

.pl-project__name {
  font-size: var(--r-font-base);
  font-weight: var(--r-weight-semibold);
  color: var(--r-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

.pl-nav {
  padding: var(--r-space-4) var(--r-space-3);
  flex: 1;
}

.pl-palette-host {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;

  // Teleport 进来的 .dag-palette 原本设计成独立左栏,这里改为填满 host
  :deep(.dag-palette) {
    width: auto;
    flex: 1;
    background: transparent;
    border-right: none;
  }
}

.pl-body { flex: 1; overflow: auto; background: var(--r-bg-panel); min-width: 0; }
</style>
