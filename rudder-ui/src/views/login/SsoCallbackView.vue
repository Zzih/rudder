<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getMe } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

onMounted(async () => {
  const token = route.query.token as string | undefined
  const error = route.query.error as string | undefined

  if (error) {
    ElMessage.error(t('login.ssoFailed', { code: error }))
    router.replace('/login')
    return
  }

  if (!token) {
    router.replace('/login')
    return
  }

  userStore.setToken(token)
  try {
    const { data } = await getMe()
    userStore.setUserInfo({
      userId: data.userId,
      username: data.username,
      role: data.role || 'VIEWER',
    })
    router.replace('/workspaces')
  } catch {
    // /me 失败说明 token 无效;清掉重登
    userStore.logout()
    router.replace('/login')
  }
})
</script>

<template>
  <div class="sso-callback">
    <div class="hint">{{ t('login.ssoProcessing') }}</div>
  </div>
</template>

<style scoped>
.sso-callback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--r-bg-page);
}

.hint {
  font-size: 14px;
  color: var(--r-text-muted);
}
</style>
