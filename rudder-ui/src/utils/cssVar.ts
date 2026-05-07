/** Read a live CSS custom property value from :root */
export function cv(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}
