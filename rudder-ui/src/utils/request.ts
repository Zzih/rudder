import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import i18n, { getLocale } from '@/locales'
import router from '@/router'

// request.ts 不在 Vue 组件内，取不到 useI18n()。用 i18n 单例的 global 实例翻译。
const t = (key: string) => i18n.global.t(key)

const request = axios.create({
  baseURL: '/api',
  // 60s 默认对常规接口够用;长任务接口(如重索引)在调用处单独 override config.timeout
  timeout: 60000,
})

request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  if (userStore.currentWorkspaceId) {
    config.headers['X-Workspace-Id'] = userStore.currentWorkspaceId
  }
  config.headers['Accept-Language'] = getLocale()
  return config
})

request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { data } = response
    if (data.code !== 200) {
      ElMessage.error(data.message || t('common.requestFailed'))
      if (data.code === 401) {
        useUserStore().logout()
        router.push('/login')
      }
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  (error) => {
    if (error.response?.status === 401) {
      useUserStore().logout()
      const current = router.currentRoute.value.fullPath
      router.push({ path: '/login', query: current && current !== '/login' ? { redirect: current } : {} })
      ElMessage.error(t('common.sessionExpired'))
    } else {
      const msg = error.response?.data?.message || error.message || t('common.networkError')
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  },
)

export default request
