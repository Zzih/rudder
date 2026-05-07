/**
 * Script.content 结构化工具——纯函数,无全局可变状态。
 *
 * 所有任务类型的 Script.content 都是 JSON 字符串,跟对应的 TaskParams 结构一致。
 * datasourceId / executionMode 等参数都存在 content JSON 内部。
 *
 * 内容格式由 TaskType.category 决定:
 *   SQL              → {"sql":"...", "dataSourceId":1, ...}
 *   SCRIPT           → {"content":"..."}
 *   DATA_INTEGRATION → {"content":"...", "deployMode":"cluster"}
 *   JAR              → {"mainClass":"...", "jarPath":"...", ...}  参数化表单
 *   API              → {"url":"...", "method":"GET", ...}         参数化表单(HTTP / Webhook)
 *   CONTROL          → 无脚本内容
 *
 * <p>调用方从 {@code useTaskTypesStore} 拿 category 传入。store 由 router 守卫保证已就绪。
 */

/** 从 Script.content JSON 中提取编辑器展示内容(纯文本)。 */
export function extractEditorContent(content: string | null | undefined, category: string): string {
  if (!content) return ''
  try {
    const parsed = JSON.parse(content)
    if (category === 'SQL') return parsed.sql ?? ''
    if (category === 'SCRIPT' || category === 'DATA_INTEGRATION') return parsed.content ?? ''
    return content
  } catch {
    return content
  }
}

/** 将编辑器文本包装为 Script.content JSON。extra 传 dataSourceId / executionMode / preStatements 等。 */
export function wrapEditorContent(editorText: string, category: string, extra?: Record<string, any>): string {
  if (category === 'SQL') {
    return JSON.stringify({ sql: editorText, ...extra })
  }
  if (category === 'SCRIPT') {
    return JSON.stringify({ content: editorText })
  }
  if (category === 'DATA_INTEGRATION') {
    return JSON.stringify({ content: editorText, deployMode: extra?.deployMode ?? 'cluster' })
  }
  return editorText
}

/**
 * 将 Script.content 转换为可读 diff 文本。
 * SQL/SCRIPT/DATA_INTEGRATION → 提取纯文本; JAR 等 → 格式化 JSON。
 */
export function extractDiffContent(content: string | null | undefined, category: string): string {
  if (!content) return ''
  try {
    const parsed = JSON.parse(content)
    if (category === 'SQL') return parsed.sql ?? ''
    if (category === 'SCRIPT' || category === 'DATA_INTEGRATION') return parsed.content ?? ''
    return JSON.stringify(parsed, null, 2)
  } catch {
    return content
  }
}

/** 从 Script.content JSON 中提取指定字段;不依赖 category。 */
export function extractContentField(content: string | null | undefined, field: string, defaultValue: any = null): any {
  if (!content) return defaultValue
  try {
    const parsed = JSON.parse(content)
    return parsed[field] ?? defaultValue
  } catch {
    return defaultValue
  }
}
