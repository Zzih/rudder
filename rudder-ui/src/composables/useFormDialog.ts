import { ref } from 'vue'
import type { FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

/**
 * Composable for form dialog (create / edit) pattern.
 * Manages visibility, form ref, validation, submission, and reset.
 */
export function useFormDialog<T extends Record<string, unknown>>(defaults: T) {
  const { t } = useI18n()
  const visible = ref(false)
  const formRef = ref<FormInstance>()
  const form = ref<T>({ ...defaults }) as import('vue').Ref<T>

  function open(initial?: Partial<T>) {
    form.value = { ...defaults, ...initial }
    visible.value = true
  }

  function close() {
    visible.value = false
    formRef.value?.resetFields()
  }

  /**
   * Validate and submit. Returns true if successful.
   * @param submitFn - API call with form data
   * @param onSuccess - optional callback after success (e.g. refresh list)
   */
  async function submit(submitFn: (data: T) => Promise<unknown>, onSuccess?: () => void): Promise<boolean> {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return false
    try {
      await submitFn(form.value)
      ElMessage.success(t('common.success'))
      close()
      onSuccess?.()
      return true
    } catch {
      return false
    }
  }

  return { visible, formRef, form, open, close, submit }
}
