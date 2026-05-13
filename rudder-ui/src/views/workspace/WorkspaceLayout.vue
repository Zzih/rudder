<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getWorkspace } from '@/api/workspace'
import { listDatasources } from '@/api/datasource'
import { useUserStore } from '@/stores/user'
import { useWorkspaceStore } from '@/stores/workspace'
import { useDatasourceStore } from '@/stores/datasource'
import { useAiChatStore } from '@/stores/aiChat'
import AppHeader from '@/components/AppHeader.vue'

const route = useRoute()
const userStore = useUserStore()
const workspaceStore = useWorkspaceStore()
const datasourceStore = useDatasourceStore()
const aiChatStore = useAiChatStore()

const workspaceId = computed(() => Number(route.params.workspaceId))

const isIde = computed(() => route.path.includes('/ide'))

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

  // 工作空间上下文里所有角色都只看该工作空间已 grant 的数据源 (SUPER_ADMIN 也尊重隔离)
  try {
    const { data } = await listDatasources({ workspaceId: wsId })
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
    <AppHeader />
    <main class="app-content" :class="{ 'app-content--ide': isIde }">
      <router-view />
    </main>
  </div>
</template>

<style scoped lang="scss">
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
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
