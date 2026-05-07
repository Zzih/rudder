import request from '@/utils/request'

export interface OverviewStats {
  workspaceCount: number
  workflowCount: number
  scriptCount: number
}

export function getOverviewStats() {
  return request.get('/overview/stats')
}
