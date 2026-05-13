<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  listAuthSources,
  getAuthSource,
  createAuthSource,
  updateAuthSource,
  deleteAuthSource,
  toggleAuthSource,
  testAuthSource,
  type AuthSourceSummary,
  type AuthSourceDetail,
  type AuthSourceType,
  type HealthStatus,
  type OidcConfig,
  type LdapConfig,
} from '@/api/auth-source'

const { t } = useI18n()

const loading = ref(false)
const rows = ref<AuthSourceSummary[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)

const form = reactive({
  name: '',
  type: 'OIDC' as Exclude<AuthSourceType, 'PASSWORD'>,
  enabled: true,
  priority: 0,
  oidc: emptyOidc(),
  ldap: emptyLdap(),
})

function emptyOidc(): OidcConfig {
  return {
    clientId: '',
    clientSecret: '',
    issuer: '',
    scopes: 'openid profile email',
    frontendRedirectUrl: '',
  }
}

function emptyLdap(): LdapConfig {
  return {
    url: 'ldap://localhost:389',
    trustAllCerts: false,
    baseDn: '',
    bindDn: '',
    bindPassword: '',
    userSearchFilter: '(&(objectClass=user)(sAMAccountName={0}))',
    usernameAttribute: 'sAMAccountName',
    emailAttribute: 'mail',
    displayNameAttribute: 'displayName',
  }
}

function resetForm() {
  form.name = ''
  form.type = 'OIDC'
  form.enabled = true
  form.priority = 0
  form.oidc = emptyOidc()
  form.ldap = emptyLdap()
  editingId.value = null
}

async function fetchList() {
  loading.value = true
  try {
    const { data } = await listAuthSources()
    rows.value = data ?? []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

async function openEdit(row: AuthSourceSummary) {
  if (row.isSystem) return
  resetForm()
  dialogMode.value = 'edit'
  editingId.value = row.id
  try {
    const { data } = await getAuthSource(row.id)
    fillFormFromDetail(data)
    dialogVisible.value = true
  } catch {
    /* interceptor */
  }
}

function fillFormFromDetail(detail: AuthSourceDetail) {
  form.name = detail.name
  form.type = detail.type
  form.enabled = detail.enabled
  form.priority = detail.priority
  if (!detail.config) return
  if (detail.type === 'OIDC') {
    Object.assign(form.oidc, emptyOidc(), detail.config as OidcConfig)
    // 后端返回的敏感字段是 mask;清空让用户重填,提交时也不会带 mask 占位符
    form.oidc.clientSecret = ''
  } else if (detail.type === 'LDAP') {
    Object.assign(form.ldap, emptyLdap(), detail.config as LdapConfig)
    form.ldap.bindPassword = ''
  }
}

function currentConfig(): OidcConfig | LdapConfig {
  return form.type === 'OIDC' ? form.oidc : form.ldap
}

async function handleSubmit() {
  if (!form.name.trim()) {
    ElMessage.warning(t('admin.authSource.nameRequired'))
    return
  }
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createAuthSource({
        name: form.name.trim(),
        type: form.type,
        config: currentConfig(),
        enabled: form.enabled,
        priority: form.priority,
      })
      ElMessage.success(t('common.success'))
    } else if (editingId.value != null) {
      await updateAuthSource(editingId.value, {
        type: form.type,
        name: form.name.trim(),
        config: currentConfig(),
        enabled: form.enabled,
        priority: form.priority,
      })
      ElMessage.success(t('common.success'))
    }
    dialogVisible.value = false
    await fetchList()
  } catch {
    /* interceptor */
  } finally {
    submitting.value = false
  }
}

async function handleToggle(row: AuthSourceSummary, enabled: boolean) {
  try {
    await toggleAuthSource(row.id, enabled)
    row.enabled = enabled
    ElMessage.success(t('common.success'))
  } catch {
    /* interceptor */
  }
}

async function handleTest(row: AuthSourceSummary) {
  try {
    const { data } = await testAuthSource(row.id)
    showHealth(row, data)
  } catch {
    /* interceptor */
  }
}

function showHealth(row: AuthSourceSummary, status: HealthStatus) {
  const stateMap: Record<string, 'success' | 'warning' | 'error' | 'info'> = {
    HEALTHY: 'success',
    DEGRADED: 'warning',
    UNHEALTHY: 'error',
    UNKNOWN: 'info',
  }
  const type = stateMap[status.state] || 'info'
  ElMessage({
    type,
    message: `${row.name}: ${status.state}${status.message ? ' — ' + status.message : ''}`,
    duration: 5000,
  })
}

async function handleDelete(row: AuthSourceSummary) {
  if (row.isSystem) return
  await ElMessageBox.confirm(
    t('admin.authSource.confirmDelete', { name: row.name }),
    t('common.warning'),
    { type: 'warning' },
  ).catch(() => 'cancel')
  try {
    await deleteAuthSource(row.id)
    ElMessage.success(t('common.success'))
    await fetchList()
  } catch {
    /* interceptor */
  }
}

const TYPE_TAG: Record<AuthSourceType, 'success' | 'warning'> = {
  OIDC: 'success',
  LDAP: 'warning',
}

const dialogTitle = computed(() =>
  dialogMode.value === 'create' ? t('admin.authSource.create') : t('admin.authSource.edit'),
)

const frontendRedirectHint = computed(() => `${window.location.origin}/sso/callback`)

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h3>{{ t('admin.authSources') }}</h3>
      <div class="page-actions">
        <el-button :icon="Refresh" @click="fetchList">{{ t('common.refresh') }}</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('admin.authSource.create') }}</el-button>
      </div>
    </div>

    <div class="admin-card">
      <el-table :data="rows" v-loading="loading" row-key="id">
        <el-table-column prop="name" :label="t('admin.authSource.name')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('admin.authSource.type')" width="120">
          <template #default="{ row }">
            <el-tag :type="TYPE_TAG[row.type as AuthSourceType]" size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.authSource.enabled')" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              :disabled="row.isSystem"
              @change="(v: string | number | boolean) => handleToggle(row, Boolean(v))"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('admin.authSource.system')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isSystem" type="info" size="small">{{ t('admin.authSource.systemTag') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" :label="t('admin.authSource.priority')" width="100" align="center" />
        <el-table-column prop="updatedAt" :label="t('common.updatedAt')" width="180" />
        <el-table-column :label="t('common.actions')" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.isSystem" text size="small" type="primary" @click="openEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button v-if="!row.isSystem" text size="small" @click="handleTest(row)">
              {{ t('admin.authSource.test') }}
            </el-button>
            <el-button v-if="!row.isSystem" text size="small" type="danger" @click="handleDelete(row)">
              {{ t('common.delete') }}
            </el-button>
            <span v-else class="readonly-hint">
              {{ t('admin.authSource.systemReadonly') }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" :close-on-click-modal="false">
      <el-form label-width="140px" label-position="right" size="default">
        <el-form-item :label="t('admin.authSource.name')" required>
          <el-input v-model="form.name" :placeholder="t('admin.authSource.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('admin.authSource.type')">
          <el-radio-group v-model="form.type" :disabled="dialogMode === 'edit'">
            <el-radio value="OIDC">OIDC</el-radio>
            <el-radio value="LDAP">LDAP</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('admin.authSource.priority')">
          <el-input-number v-model="form.priority" :min="0" :max="999" />
        </el-form-item>
        <el-form-item :label="t('admin.authSource.enabled')">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <el-divider>{{ form.type }} {{ t('admin.authSource.config') }}</el-divider>

        <template v-if="form.type === 'OIDC'">
          <el-form-item label="Client ID" required>
            <el-input v-model="form.oidc.clientId" />
          </el-form-item>
          <el-form-item label="Client Secret" required>
            <el-input
              v-model="form.oidc.clientSecret"
              type="password"
              show-password
              :placeholder="dialogMode === 'edit' ? t('admin.authSource.secretPlaceholder') : ''"
            />
          </el-form-item>
          <el-form-item label="Issuer" required>
            <el-input v-model="form.oidc.issuer" placeholder="https://idp.example.com" />
          </el-form-item>
          <el-form-item label="Scopes">
            <el-input v-model="form.oidc.scopes" />
          </el-form-item>
          <el-form-item label="Frontend Redirect URL" required>
            <el-input v-model="form.oidc.frontendRedirectUrl" :placeholder="frontendRedirectHint" />
          </el-form-item>
        </template>

        <template v-else-if="form.type === 'LDAP'">
          <el-form-item label="URL" required>
            <el-input v-model="form.ldap.url" placeholder="ldap://ad.company.com:389" />
          </el-form-item>
          <el-form-item label="Trust All Certs">
            <el-switch v-model="form.ldap.trustAllCerts" />
          </el-form-item>
          <el-form-item label="Base DN" required>
            <el-input v-model="form.ldap.baseDn" placeholder="dc=company,dc=com" />
          </el-form-item>
          <el-form-item label="Bind DN">
            <el-input v-model="form.ldap.bindDn" />
          </el-form-item>
          <el-form-item label="Bind Password">
            <el-input
              v-model="form.ldap.bindPassword"
              type="password"
              show-password
              :placeholder="dialogMode === 'edit' ? t('admin.authSource.secretPlaceholder') : ''"
            />
          </el-form-item>
          <el-form-item label="User Search Filter">
            <el-input v-model="form.ldap.userSearchFilter" />
          </el-form-item>
          <el-form-item label="Username Attr">
            <el-input v-model="form.ldap.usernameAttribute" />
          </el-form-item>
          <el-form-item label="Email Attr">
            <el-input v-model="form.ldap.emailAttribute" />
          </el-form-item>
          <el-form-item label="Display Name Attr">
            <el-input v-model="form.ldap.displayNameAttribute" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/admin.scss';

.readonly-hint {
  color: var(--r-text-muted);
  font-size: var(--r-font-sm);
}
</style>
