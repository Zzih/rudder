import hljs from 'highlight.js/lib/core'
import sql from 'highlight.js/lib/languages/sql'
import javascript from 'highlight.js/lib/languages/javascript'
import python from 'highlight.js/lib/languages/python'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import java from 'highlight.js/lib/languages/java'

const LANGS: Record<string, unknown> = {
  sql,
  javascript,
  js: javascript,
  python,
  bash,
  shell: bash,
  json,
  xml,
  html: xml,
  java,
}

// 多次 import 只注册一次;hljs.registerLanguage 自身已 idempotent,加 flag 省一轮 for 调度
let registered = false
function ensureRegistered() {
  if (registered) return
  for (const [name, lang] of Object.entries(LANGS)) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    hljs.registerLanguage(name, lang as any)
  }
  registered = true
}

export function highlightCode(text: string, lang?: string): { language: string; html: string } {
  ensureRegistered()
  const language = lang && hljs.getLanguage(lang) ? lang : 'plaintext'
  const html = language !== 'plaintext'
      ? hljs.highlight(text, { language }).value
      : escapeHtml(text)
  return { language, html }
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export { hljs }
