/** Parse a hex color (#rgb or #rrggbb) to [r, g, b]. Returns [0,0,0] for invalid input. */
function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace('#', '')
  let r: number, g: number, b: number
  if (h.length === 3) {
    [r, g, b] = [parseInt(h[0] + h[0], 16), parseInt(h[1] + h[1], 16), parseInt(h[2] + h[2], 16)]
  } else {
    [r, g, b] = [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)]
  }
  if (isNaN(r)) r = 0
  if (isNaN(g)) g = 0
  if (isNaN(b)) b = 0
  return [r, g, b]
}

/** Generate { color, bg, border } from a single hex value. bg = 10% opacity, border = 20% opacity. */
export function colorMeta(hex: string): { color: string; bg: string; border: string } {
  const [r, g, b] = hexToRgb(hex)
  return {
    color: hex,
    bg: `rgba(${r}, ${g}, ${b}, 0.1)`,
    border: `rgba(${r}, ${g}, ${b}, 0.2)`,
  }
}

/** Generate { color, bg } from a single hex value (no border). */
export function colorMetaLight(hex: string): { color: string; bg: string } {
  const { color, bg } = colorMeta(hex)
  return { color, bg }
}

/**
 * Theme-aware variant. In dark mode the source hex is blended toward white to
 * raise lightness (keeps category hue recognizable while meeting AA contrast on
 * dark chip backgrounds), and chip bg opacity is doubled so the chip is visible.
 */
export function colorMetaAdaptive(hex: string, isDark: boolean): { color: string; bg: string } {
  const [r, g, b] = hexToRgb(hex)
  if (!isDark) {
    return { color: hex, bg: `rgba(${r}, ${g}, ${b}, 0.1)` }
  }
  const lr = Math.round(r + (255 - r) * 0.45)
  const lg = Math.round(g + (255 - g) * 0.45)
  const lb = Math.round(b + (255 - b) * 0.45)
  return {
    color: `rgb(${lr}, ${lg}, ${lb})`,
    bg: `rgba(${lr}, ${lg}, ${lb}, 0.18)`,
  }
}

/** Deterministic card color by entity ID. */
const CARD_PALETTE = ['#3b82f6', '#10b981', '#8b5cf6', '#f59e0b', '#ec4899', '#0ea5e9', '#f97316']
export function cardColor(id: number): string {
  return CARD_PALETTE[id % CARD_PALETTE.length]
}

/**
 * Provider 调色盘。比 CARD_PALETTE 更丰富,按 provider name 稳定 hash,
 * 让同名 provider 永远同色(无需手配)。新增 SPI 前端不用改。
 */
const PROVIDER_PALETTE = [
  '#3b82f6', // blue
  '#10b981', // emerald
  '#8b5cf6', // violet
  '#f59e0b', // amber
  '#ec4899', // pink
  '#0ea5e9', // sky
  '#f97316', // orange
  '#d97706', // dark amber
  '#6366f1', // indigo
  '#14b8a6', // teal
  '#ef4444', // red
  '#a855f7', // purple
  '#84cc16', // lime
]

/** FNV-1a 32-bit hash,小而稳定。 */
function hashName(name: string): number {
  let h = 0x811c9dc5
  for (let i = 0; i < name.length; i++) {
    h ^= name.charCodeAt(i)
    h = Math.imul(h, 0x01000193) >>> 0
  }
  return h
}

/** 按 provider name 稳定取色。LOCAL / DB 这种"默认 / 通用"类特殊处理成中性灰。 */
export function providerColor(name: string | null | undefined): string {
  if (!name) return '#6b7280'
  const upper = name.toUpperCase()
  if (upper === 'LOCAL' || upper === 'DB') return '#6b7280' // 中性灰:表示"自带 / 默认"
  return PROVIDER_PALETTE[hashName(upper) % PROVIDER_PALETTE.length]
}
