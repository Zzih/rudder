// Auto-load all task icon SVGs as URL strings
const iconModules = import.meta.glob('@/assets/task-icons/*.svg', { eager: true, query: '?url', import: 'default' })

const iconMap: Record<string, string> = {}
for (const [path, url] of Object.entries(iconModules)) {
  const name = path.split('/').pop()?.replace('.svg', '') ?? ''
  iconMap[name] = url as string
}

export function getTaskIconUrl(taskType: string): string {
  return iconMap[taskType] ?? ''
}

export { iconMap }
