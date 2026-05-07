import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'

/**
 * Composable for delete confirmation pattern.
 * Shows confirm dialog, calls delete API, shows success message, then triggers refresh.
 */
export function useDeleteConfirm() {
  const { t } = useI18n()

  /**
   * @param message - Confirmation message to display (already translated)
   * @param deleteFn - API call to perform the deletion
   * @param onSuccess - Callback after successful deletion (e.g. refresh list)
   */
  async function confirmDelete(message: string, deleteFn: () => Promise<unknown>, onSuccess?: () => void) {
    try {
      await ElMessageBox.confirm(message, t('common.confirm'), { type: 'warning' })
    } catch {
      return // user clicked Cancel
    }
    try {
      await deleteFn()
      ElMessage.success(t('common.success'))
      onSuccess?.()
    } catch { /* API interceptor handles */ }
  }

  return { confirmDelete }
}
