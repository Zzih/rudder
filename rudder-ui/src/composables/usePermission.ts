import { computed } from 'vue'
import { useUserStore, type Role } from '@/stores/user'

export const ROLE_LEVEL: Record<Role, number> = {
  VIEWER: 0,
  DEVELOPER: 1,
  WORKSPACE_OWNER: 2,
  SUPER_ADMIN: 3,
}

export function usePermission() {
  const userStore = useUserStore()

  const roleLevel = computed(() => {
    const role = userStore.userInfo?.role
    return role ? ROLE_LEVEL[role] : 0
  })

  function hasRole(minRole: Role): boolean {
    return roleLevel.value >= ROLE_LEVEL[minRole]
  }

  const isSuperAdmin = computed(() => hasRole('SUPER_ADMIN'))
  const isAdmin = computed(() => hasRole('WORKSPACE_OWNER'))
  const canEdit = computed(() => hasRole('DEVELOPER'))

  return { hasRole, isSuperAdmin, isAdmin, canEdit }
}
