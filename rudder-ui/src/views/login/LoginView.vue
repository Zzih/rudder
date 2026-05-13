<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Connection, Key } from '@element-plus/icons-vue'
import {
  login as localLogin,
  loginBySource,
  listPublicSources,
  ssoStartUrl,
  type PublicAuthSource,
} from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { setLocale, getLocale } from '@/locales'
import AboutDialog from '@/components/AboutDialog.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const aboutVisible = ref(false)
const form = reactive({ username: '', password: '' })
const currentLocale = ref(getLocale())
const sources = ref<PublicAuthSource[]>([])
/** 当前选中的凭证类登录方式 source.id;null 表示用本地账号(/auth/login),否则走 /auth/sources/{id}/login。 */
const activeCredentialSourceId = ref<number | null>(null)

const rules: FormRules = {
  username: [{ required: true, message: () => t('login.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: () => t('login.passwordRequired'), trigger: 'blur' }],
}

/** 凭证类(LDAP)source 列表,前端用按钮切换为表单输入。 */
const credentialSources = computed(() => sources.value.filter(s => s.type === 'LDAP'))
/** SSO 跳转类(OIDC)source 列表,渲染为按钮。 */
const ssoSources = computed(() => sources.value.filter(s => s.type === 'OIDC'))
/** 当前显示的凭证表单标题 / 提交目标。 */
const activeSourceName = computed(() => {
  if (activeCredentialSourceId.value == null) return ''
  return credentialSources.value.find(s => s.id === activeCredentialSourceId.value)?.name ?? ''
})

/** 选了 LDAP source 时隐藏本地账号 / 其它 source 按钮,只显示选中 source 的表单。 */
const isCredentialMode = computed(() => activeCredentialSourceId.value != null)
const showAltButtons = computed(
  () => !isCredentialMode.value
    && (credentialSources.value.length > 0 || ssoSources.value.length > 0),
)

onMounted(async () => {
  try {
    const { data } = await listPublicSources()
    sources.value = (data || []).slice().sort((a, b) => b.priority - a.priority)
  } catch {
    sources.value = []
  }
})

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const { data } =
      activeCredentialSourceId.value == null
        ? await localLogin({ username: form.username, password: form.password })
        : await loginBySource(activeCredentialSourceId.value, {
            username: form.username,
            password: form.password,
          })
    userStore.setToken(data.token)
    userStore.setUserInfo({
      userId: data.userId,
      username: data.username,
      // 进 workspace 后 WorkspaceLayout 会用 /auth/me 拉工作空间维度的角色覆盖,这里只是登录页兜底。
      role: data.role || 'VIEWER',
    })
    router.push('/workspaces')
  } catch {
    /* interceptor handles */
  } finally {
    loading.value = false
  }
}

function startSso(source: PublicAuthSource) {
  window.location.href = ssoStartUrl(source.id)
}

function selectCredentialSource(source: PublicAuthSource | null) {
  activeCredentialSourceId.value = source?.id ?? null
  form.username = ''
  form.password = ''
}

function switchLocale(locale: string) {
  setLocale(locale)
  currentLocale.value = locale
}

</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <!-- Top actions -->
      <div class="login-actions">
        <ThemeToggle />
        <el-dropdown trigger="click" @command="switchLocale">
          <span class="locale-btn">
            <el-icon size="14"><Connection /></el-icon>
            {{ currentLocale === 'zh' ? '中文' : 'EN' }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="en" :disabled="currentLocale === 'en'">English</el-dropdown-item>
              <el-dropdown-item command="zh" :disabled="currentLocale === 'zh'">中文</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div class="login-logo">
        <div class="logo-icon">R</div>
        <h1>{{ t('login.title') }}</h1>
        <p>{{ t('login.subtitle') }}</p>
      </div>

      <!-- 表单区:本地账号 或 选中的凭证 source(LDAP) -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="handleSubmit"
      >
        <div v-if="isCredentialMode" class="active-source-hint">
          <el-icon><Key /></el-icon>
          <span>{{ t('login.loginAs', { name: activeSourceName }) }}</span>
          <el-button link size="small" @click="selectCredentialSource(null)">×</el-button>
        </div>

        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            :placeholder="t('login.usernamePlaceholder')"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t('login.passwordPlaceholder')"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="login-btn" @click="handleSubmit">
            {{ t('login.signIn') }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 其它登录方式按钮 -->
      <div v-if="showAltButtons" class="alt-sources">
        <div class="divider">
          <span>{{ t('login.orUseSso') }}</span>
        </div>
        <div class="source-buttons">
          <el-button
            v-for="src in credentialSources"
            :key="`ldap-${src.id}`"
            class="source-btn"
            @click="selectCredentialSource(src)"
          >
            {{ src.name }}
          </el-button>
          <el-button
            v-for="src in ssoSources"
            :key="`oidc-${src.id}`"
            class="source-btn"
            @click="startSso(src)"
          >
            {{ src.name }}
          </el-button>
        </div>
      </div>
    </div>
    <div class="login-footer" @click="aboutVisible = true">{{ t('footer.copyright') }}</div>
    <AboutDialog v-model="aboutVisible" />
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--r-bg-page);
}

.login-card {
  position: relative;
  width: 400px;
  padding: 40px 36px 28px;
  background: var(--r-bg-card);
  border-radius: 6px;
  border: 1px solid var(--r-border);
  box-shadow: var(--r-shadow-md);
}

.login-actions {
  position: absolute;
  top: 14px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.locale-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--r-text-muted);
  cursor: pointer;
  padding: 5px 8px;
  border-radius: 5px;
  transition: all 0.12s;
}

.locale-btn:hover {
  color: var(--r-accent);
  background: var(--r-bg-hover);
}

.login-logo {
  text-align: center;
  margin-bottom: 32px;
}

.logo-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 12px;
  background: var(--r-logo-bg);
  border-radius: 10px;
  font-size: 24px;
  font-weight: 700;
  color: var(--r-logo-text);
  line-height: 48px;
}

.login-logo h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--r-text-primary);
}

.login-logo p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--r-text-muted);
}

.login-btn {
  width: 100%;
  border-radius: 4px;
}

.active-source-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 16px;
  background: var(--r-bg-hover);
  border-radius: 4px;
  font-size: 13px;
  color: var(--r-text-primary);
}

.active-source-hint .el-button {
  margin-left: auto;
}

.alt-sources {
  margin-top: 8px;
}

.divider {
  display: flex;
  align-items: center;
  margin: 16px 0 12px;
  font-size: 12px;
  color: var(--r-text-muted);
  gap: 12px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--r-border);
}

.source-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.source-btn {
  width: 100%;
}

.login-footer {
  position: absolute;
  bottom: 24px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 12px;
  color: var(--r-text-disabled);
  cursor: pointer;
}
</style>
