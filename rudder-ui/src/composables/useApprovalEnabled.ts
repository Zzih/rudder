import { createFeatureFlag } from '@/composables/useFeatureFlag'
import { getApprovalEnabled } from '@/api/approval'

const flag = createFeatureFlag(getApprovalEnabled)

/** 平台级 approval enable 状态(模块级单例,跨调用方共享)。 */
export function useApprovalEnabled() {
  return flag
}
