/**
 * Common date formatting utilities shared across views.
 */

export function formatDate(d: string | null | undefined): string {
  if (!d) return '-'
  return new Date(d).toLocaleString()
}

export function formatDateShort(d: string | null | undefined): string {
  if (!d) return ''
  return new Date(d).toLocaleDateString()
}

export function relativeTime(d: string | null | undefined, justNowLabel = 'just now'): string {
  if (!d) return ''
  const mins = Math.floor((Date.now() - new Date(d).getTime()) / 60000)
  if (mins < 1) return justNowLabel
  if (mins < 60) return `${mins}m`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h`
  return `${Math.floor(hours / 24)}d`
}
