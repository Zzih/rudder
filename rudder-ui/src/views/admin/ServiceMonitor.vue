<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listServices } from '@/api/admin'

const { t } = useI18n()

interface ServiceNode {
  id: number
  type: string
  host: string
  port: number
  status: string
  heartbeat: string
  startTime: string
  taskCount: number
}

const services = ref<ServiceNode[]>([])
const loading = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

async function fetchServices() {
  loading.value = true
  try {
    const { data } = await listServices()
    services.value = data ?? []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchServices()
  timer = setInterval(fetchServices, 10000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h3>{{ t('admin.services') }}</h3>
    </div>
    <div class="admin-card">
      <el-table :data="services" v-loading="loading" :empty-text="t('common.noData')">
        <el-table-column prop="type" :label="t('admin.serviceType')" width="120" />
        <el-table-column prop="host" :label="t('admin.host')" />
        <el-table-column prop="port" :label="t('admin.port')" width="100" />
        <el-table-column :label="t('admin.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'" size="small" round>
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="heartbeat" :label="t('admin.heartbeat')" width="180" />
        <el-table-column prop="startTime" :label="t('admin.startTime')" width="180" />
        <el-table-column prop="taskCount" :label="t('admin.taskCount')" width="100" />
      </el-table>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/admin.scss';
</style>
