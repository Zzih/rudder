import { useI18n } from 'vue-i18n'
import type { DataPermRolePermissionItem, UserGrantView } from '@/api/data-perm'

/** 在卡片视图(MyDataPerm / GrantManage)之间共享的 grant 渲染辅助。 */
export function useGrantView() {
  const { t } = useI18n()

  function fmtTime(s?: string): string {
    if (!s) return t('dataPerm.permanent')
    return new Date(s).toLocaleString()
  }

  function resourcePath(item: DataPermRolePermissionItem): string {
    const parts: string[] = []
    if (item.catalogName) parts.push(item.catalogName)
    if (item.databaseName) parts.push(item.databaseName)
    if (item.tableName) parts.push(item.tableName)
    if (item.columnName) parts.push(item.columnName)
    return parts.join('.') || '*'
  }

  /** 判断 grant 在 referenceMs(默认 Date.now())时是否仍有效。 */
  function isActiveAt(g: UserGrantView, referenceMs: number = Date.now()): boolean {
    if (g.endReason) return false
    if (!g.expirationTime) return true
    return new Date(g.expirationTime).getTime() > referenceMs
  }

  return { fmtTime, resourcePath, isActiveAt }
}
