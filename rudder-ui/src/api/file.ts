import request from '@/utils/request'

export interface StorageEntity {
  fullName: string
  fileName: string
  parentPath: string
  directory: boolean
  size: number
  createTime: string
  updateTime: string
  extension: string
}

export function listFiles(path = '') {
  return request.get<StorageEntity[]>('/files', { params: { path } })
}

export function uploadFile(file: File, currentDir = '', fileName?: string) {
  const formData = new FormData()
  // Third arg of FormData.append() overrides the filename sent to server,
  // preventing browsers from sending full relative paths (webkitdirectory).
  formData.append('file', file, fileName ?? file.name)
  formData.append('currentDir', currentDir)
  return request.post<string>('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function downloadFile(path: string) {
  return request.get('/files/download', {
    params: { path },
    responseType: 'blob',
  })
}

export function deleteFile(path: string) {
  return request.delete<boolean>('/files', { params: { path } })
}

export function createDirectory(path: string) {
  return request.post('/files/directory', { path })
}

export function renameFile(oldPath: string, newPath: string) {
  return request.put('/files/rename', { oldPath, newPath })
}

export function onlineCreateFile(fileName: string, currentDir: string, content = '') {
  return request.post<string>('/files/online-create', { fileName, currentDir, content })
}

export function readFileContent(path: string, skipLines = 0, limit = 5000) {
  return request.get<string>('/files/content', { params: { path, skipLines, limit } })
}

export function updateFileContent(path: string, content: string) {
  return request.put('/files/content', { path, content })
}

export function getEditableSuffixes() {
  return request.get<string[]>('/files/editable-suffixes')
}
