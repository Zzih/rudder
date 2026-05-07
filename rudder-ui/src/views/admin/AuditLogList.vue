<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listAuditLogs } from '@/api/admin'

const { t } = useI18n()

interface AuditLogRow {
  id: number
  username: string
  module: string
  action: string
  description: string
  requestIp: string
  createdAt: string
}

const logs = ref<AuditLogRow[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterModule = ref('')
const filterAction = ref('')
const filterUsername = ref('')
const filterDateRange = ref<string[]>([])

async function fetchLogs() {
  loading.value = true
  try {
    const res: any = await listAuditLogs({
      module: filterModule.value || undefined,
      action: filterAction.value || undefined,
      username: filterUsername.value.trim() || undefined,
      startTime: filterDateRange.value?.[0] || undefined,
      endTime: filterDateRange.value?.[1] || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    logs.value = res.data ?? []
    total.value = res.total ?? 0
  } finally {
    loading.value = false
  }
}

function handleSearch() { pageNum.value = 1; fetchLogs() }
function handlePageChange(page: number) { pageNum.value = page; fetchLogs() }
function handleReset() {
  filterModule.value = ''
  filterAction.value = ''
  filterUsername.value = ''
  filterDateRange.value = []
  handleSearch()
}

onMounted(fetchLogs)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h3>{{ t('admin.auditLogs') }}</h3>
      <div class="page-actions">
        <el-select v-model="filterModule" :placeholder="t('admin.module')" clearable style="width: 130px">
          <el-option v-for="m in ['WORKSPACE','SCRIPT','WORKFLOW','DATASOURCE','USER']" :key="m" :label="m" :value="m" />
        </el-select>
        <el-select v-model="filterAction" :placeholder="t('admin.action')" clearable style="width: 130px">
          <el-option v-for="a in ['CREATE','UPDATE','DELETE','EXECUTE']" :key="a" :label="a" :value="a" />
        </el-select>
        <el-input v-model="filterUsername" :placeholder="t('admin.username')" clearable style="width: 150px" />
        <el-date-picker v-model="filterDateRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
                        :start-placeholder="t('common.startTime')" :end-placeholder="t('common.endTime')" style="width: 340px" />
        <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
      </div>
    </div>

    <div class="admin-card">
      <el-table :data="logs" v-loading="loading" :empty-text="t('common.noData')">
        <el-table-column prop="username" :label="t('admin.username')" width="120" />
        <el-table-column prop="module" :label="t('admin.module')" width="120" />
        <el-table-column prop="action" :label="t('admin.action')" width="100" />
        <el-table-column prop="description" :label="t('admin.description')" show-overflow-tooltip />
        <el-table-column prop="requestIp" :label="t('admin.requestIp')" width="140" />
        <el-table-column prop="createdAt" :label="t('common.createdAt')" width="180" />
      </el-table>
    </div>

    <el-pagination v-if="total > pageSize" layout="total, prev, pager, next"
                   :total="total" :page-size="pageSize" :current-page="pageNum"
                   @current-change="handlePageChange" class="admin-pagination" />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/admin.scss';
</style>
