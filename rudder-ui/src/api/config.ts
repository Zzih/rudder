import request from '@/utils/request'

export interface TaskTypeDef {
  value: string
  label: string
  ext: string
  category: string
  needsDatasource: boolean
  datasourceType: string | null
  executionModes: string[]
}

export function getTaskTypes() {
  return request.get<TaskTypeDef[]>('/config/task-types')
}

export interface LanguageSyntax {
  language: string
  keywords: string[]
  functions: string[]
  snippets: { label: string; insertText: string; detail: string }[]
}

export function getAllSyntax() {
  return request.get<Record<string, LanguageSyntax>>('/config/syntax')
}

export interface RuntimeTypeDef {
  value: string
  label: string
}

export function getRuntimeTypes() {
  return request.get<RuntimeTypeDef[]>('/config/runtime-types')
}

export function generateCodes(count = 1) {
  return request.get<string[]>('/codes/generate', { params: { count } })
}
