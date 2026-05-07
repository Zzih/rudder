import request from '@/utils/request'

export type QuickLinkCategory = 'QUICK_ENTRY' | 'DOC_LINK'
export type QuickLinkTarget = '_blank' | '_self'

export interface QuickLink {
  id: number
  category: QuickLinkCategory
  name: string
  description?: string
  icon?: string
  url: string
  target: QuickLinkTarget
  sortOrder: number
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

export interface QuickLinkPayload {
  category: QuickLinkCategory
  name: string
  description?: string
  icon?: string
  url: string
  target?: QuickLinkTarget
  sortOrder?: number
  enabled?: boolean
}

export function listQuickLinks(params?: { category?: QuickLinkCategory; onlyEnabled?: boolean }) {
  return request.get('/quick-links', { params })
}

export function createQuickLink(data: QuickLinkPayload) {
  return request.post('/quick-links', data)
}

export function updateQuickLink(id: number, data: QuickLinkPayload) {
  return request.put(`/quick-links/${id}`, data)
}

export function deleteQuickLink(id: number) {
  return request.delete(`/quick-links/${id}`)
}

export function updateQuickLinkSort(idsInOrder: number[]) {
  return request.put('/quick-links/sort', idsInOrder)
}
