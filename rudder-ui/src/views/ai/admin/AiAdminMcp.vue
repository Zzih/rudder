<template>
  <div class="tab-pane">
    <div class="tab-bar">
      <el-button type="primary" size="small" @click="openEdit()"><el-icon><Plus /></el-icon>{{ t('common.create') }}</el-button>
      <el-button size="small" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>{{ t('common.refresh') }}</el-button>
      <el-button size="small" @click="refreshHealth"><el-icon><CircleCheck /></el-icon>{{ t('aiAdmin.mcpRefreshHealth') }}</el-button>
      <span class="bar-hint">{{ t('aiAdmin.mcpHint') }}</span>
    </div>
    <el-table :data="rows" v-loading="loading" size="small" stripe>
      <el-table-column prop="name" :label="t('aiAdmin.col.name')" width="180" />
      <el-table-column prop="transport" :label="t('aiAdmin.col.transport')" width="120" />
      <el-table-column prop="command" :label="t('aiAdmin.col.command')" />
      <el-table-column prop="url" :label="t('aiAdmin.col.url')" />
      <el-table-column :label="t('aiAdmin.col.health')" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="row.healthStatus === 'UP' ? 'success' : row.healthStatus === 'DOWN' ? 'danger' : 'info'">
            {{ row.healthStatus || 'UNKNOWN' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.status')" width="80">
        <template #default="{ row }"><el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? 'ON' : 'OFF' }}</el-tag></template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="140">
        <template #default="{ row }">
          <el-button link size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button link size="small" type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pager" small background layout="total, prev, pager, next"
      :total="total" :page-size="pageSize" :current-page="pageNum" @current-change="(n: number) => { pageNum = n; load() }" />

    <el-dialog v-model="editing" :title="form.id ? t('common.edit') : t('common.create')" width="520">
      <el-form label-position="top">
        <el-form-item :label="t('aiAdmin.col.name')" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="t('aiAdmin.col.transport')" required>
          <el-radio-group v-model="form.transport"><el-radio value="STDIO">STDIO</el-radio><el-radio value="HTTP_SSE">HTTP_SSE</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.transport === 'STDIO'" :label="t('aiAdmin.col.command')" required>
          <el-input v-model="form.command" :placeholder="'npx -y @modelcontextprotocol/server-filesystem /data'" />
        </el-form-item>
        <el-form-item v-else :label="t('aiAdmin.col.url')" required><el-input v-model="form.url" /></el-form-item>
        <el-form-item :label="t('aiAdmin.col.env')">
          <el-input v-model="form.env" type="textarea" :rows="3" :placeholder="'{&quot;KEY&quot;:&quot;value&quot;}'" />
        </el-form-item>
        <el-form-item :label="t('aiAdmin.col.credentials')">
          <el-input v-model="form.credentials" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="t('aiAdmin.col.toolAllowlist')">
          <el-input v-model="form.toolAllowlist" :placeholder='`["list_repos","search_code"]  留空=暴露全部`' />
          <div class="hint">{{ t('aiAdmin.col.toolAllowlistHint') }}</div>
        </el-form-item>
        <el-form-item><el-switch v-model="form.enabled" :active-text="t('aiAdmin.col.enabled')" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editing = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh, CircleCheck } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminMcp, type AiMcpServerVO } from '@/api/ai'

const { t } = useI18n()
const rows = ref<AiMcpServerVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const editing = ref(false)
const saving = ref(false)
const form = reactive<AiMcpServerVO>(empty())

function empty(): AiMcpServerVO {
  return { name: '', transport: 'STDIO', command: '', url: '', env: '', credentials: '', toolAllowlist: '', healthStatus: 'UNKNOWN', enabled: true }
}

async function load() {
  loading.value = true
  try {
    const { data } = await adminMcp.list({ pageNum: pageNum.value, pageSize: pageSize.value })
    rows.value = data?.records ?? []
    total.value = data?.total ?? 0
  } finally { loading.value = false }
}

function openEdit(row?: AiMcpServerVO) {
  Object.assign(form, row ? { ...row } : empty())
  editing.value = true
}

async function save() {
  if (!form.name || !form.transport) { ElMessage.warning(t('common.required')); return }
  saving.value = true
  // MySQL JSON 列不接受空串,可选字段空值发 null
  const payload: AiMcpServerVO = {
    ...form,
    env: form.env?.trim() || null,
    credentials: form.credentials?.trim() || null,
    toolAllowlist: form.toolAllowlist?.trim() || null,
  }
  try {
    if (payload.id) await adminMcp.update(payload.id, payload)
    else await adminMcp.create(payload)
    editing.value = false
    await load()
    ElMessage.success(t('common.success'))
  } catch { ElMessage.error(t('common.failed')) } finally { saving.value = false }
}

async function remove(row: AiMcpServerVO) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    await adminMcp.remove(row.id); await load()
  } catch { /* cancel */ }
}

async function refreshHealth() {
  try { await adminMcp.refreshHealth(); await load(); ElMessage.success(t('common.success')) }
  catch { ElMessage.error(t('common.failed')) }
}

onMounted(load)
</script>

