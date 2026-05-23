import { colorMeta } from '@/utils/colorMeta'

export const STAGE_LABEL_KEY: Record<string, string> = {
  PROJECT_OWNER: 'approval.stepProject',
  WORKSPACE_OWNER: 'approval.stepWorkspace',
  SUPER_ADMIN: 'approval.stepSuperAdmin',
}

export const TYPE_LABEL_KEY: Record<string, string> = {
  PROJECT_PUBLISH: 'approval.projectPublish',
  WORKFLOW_PUBLISH: 'approval.workflowPublish',
  DATA_PERM_APPLY: 'approval.dataPermApply',
  MCP_TOKEN: 'approval.mcpToken',
}

export const TYPE_CLASS_BY_RESOURCE: Record<string, string> = {
  PROJECT_PUBLISH: 'ap-type-tag--project',
  WORKFLOW_PUBLISH: 'ap-type-tag--workflow',
  DATA_PERM_APPLY: 'ap-type-tag--dataperm',
  MCP_TOKEN: 'ap-type-tag--mcp',
}

export type ApprovalStageState = 'done' | 'active' | 'rejected' | 'waiting'

const STATUS_META: Record<string, { color: string; bg: string; border: string; label: string }> = {
  PENDING:  { ...colorMeta('#f59e0b'), label: 'approval.pending' },
  APPROVED: { ...colorMeta('#10b981'), label: 'approval.approved' },
  REJECTED: { ...colorMeta('#ef4444'), label: 'approval.rejected' },
}

export function getStatusMeta(status: string) {
  return STATUS_META[status] ?? { ...colorMeta('#64748b'), label: status }
}

interface StageStateInput {
  stageChain?: string[]
  currentStage: string
  status: string
}

export function getStageState(row: StageStateInput, idx: number): ApprovalStageState {
  const chain = row.stageChain ?? []
  const cur = chain.indexOf(row.currentStage)
  if (idx < cur) return 'done'
  if (idx > cur) return row.status === 'APPROVED' ? 'done' : 'waiting'
  if (row.status === 'PENDING') return 'active'
  if (row.status === 'REJECTED') return 'rejected'
  return 'done'
}
