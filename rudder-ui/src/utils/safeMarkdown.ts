import { marked, type Marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * 渲染 Markdown 并 sanitize，防止服务端返回内容里的 XSS。
 *
 * - 剥离所有 on* 事件属性、<script>、<iframe> 等危险标签
 * - 允许代码块自定义按钮（以 data-action 驱动事件委托）
 * - 默认使用 {@link marked} 模块单例；传入自定义 renderer 时可传入自建 Marked 实例
 */
export function renderSafeMarkdown(md: string, instance: Marked | typeof marked = marked): string {
  if (!md) return ''
  const raw = instance.parse(md) as string
  return DOMPurify.sanitize(raw, {
    ADD_ATTR: ['data-action', 'data-code', 'target'],
    FORBID_TAGS: ['style', 'iframe', 'script', 'object', 'embed', 'form'],
    FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur', 'onchange', 'oninput'],
  })
}
