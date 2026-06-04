import { createFeatureFlag } from '@/composables/useFeatureFlag'
import { getDataPermEnabled } from '@/api/data-perm'

const flag = createFeatureFlag(getDataPermEnabled)

/** 平台级 data-perm enable 状态(模块级单例,跨调用方共享)。 */
export function useDataPermEnabled() {
  return flag
}
