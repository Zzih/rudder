<template>
  <div class="ai-chat">
    <!-- Header -->
    <div class="ai-chat__header">
      <span class="ai-chat__title">
        <el-icon :size="13" class="ai-chat__title-icon"><MagicStick /></el-icon>
        <span>{{ t('ide.aiAssistant') }}</span>
      </span>
      <div class="ai-chat__header-actions">
        <el-tooltip
          :content="hasActiveSession ? t('ide.agentModeLocked') : t('ide.agentModeSwitchHint')"
          placement="bottom"
          :show-after="200"
        >
          <span class="agent-toggle">
            <el-switch
              v-model="agentModeOn"
              :active-text="t('ide.isAgentMode')"
              :disabled="hasActiveSession"
              size="small"
            />
          </span>
        </el-tooltip>
        <button class="header-btn" @click="ideState.aiPanelVisible = false"><el-icon :size="14"><ArrowRight /></el-icon></button>
      </div>
    </div>

    <!-- Messages -->
    <div ref="messagesEl" class="ai-chat__body">
      <div v-if="!visibleMessages.length && !store.streaming" class="ai-chat__empty">
        <div class="empty-icon"><el-icon :size="20"><MagicStick /></el-icon></div>
        <p>{{ agentModeOn ? t('ide.agentHint') : t('ide.aiHint') }}</p>
      </div>

      <template v-for="item in displayItems" :key="item.kind === 'msg' ? (item.msg._tempKey || item.msg.id) : item.key">
        <!-- User -->
        <div v-if="item.kind === 'msg' && item.msg.role === 'user'" class="msg msg--user">
          <div class="msg__avatar"><el-icon :size="14"><User /></el-icon></div>
          <div class="msg__body">
            <div class="msg__content msg__content--user">{{ item.msg.content }}</div>
          </div>
        </div>

        <!-- Assistant(轮换出的空 placeholder 在 token 到来前不渲染,避免空灰框) -->
        <div v-else-if="item.kind === 'msg' && item.msg.role === 'assistant'
               && (item.msg.content || item.msg.thinking || item.msg.status !== 'STREAMING')"
             class="msg msg--ai">
          <div class="msg__avatar"><el-icon :size="14"><MagicStick /></el-icon></div>
          <div class="msg__body">
            <details v-if="item.msg.thinking" class="msg__thinking-block" :open="!item.msg.content">
              <summary>
                <el-icon :size="11"><MagicStick /></el-icon>
                <span>{{ thinkingSummary(item.msg) }}</span>
              </summary>
              <div class="msg__thinking-text">{{ item.msg.thinking }}</div>
            </details>
            <div v-if="item.msg.content" class="msg__content" v-html="renderMarkdown(item.msg.content)" />
            <div v-if="item.msg.status === 'PENDING'" class="msg__thinking">
              <span class="thinking-dots"><span /><span /><span /></span>{{ t('ide.aiThinking') }}
            </div>
            <div v-if="item.msg.status === 'CANCELLED'" class="msg__cancelled">
              <el-icon :size="11"><CircleClose /></el-icon><span>{{ t('ide.aiCancelled') }}</span>
            </div>
            <div v-if="item.msg.status === 'FAILED'" class="msg__cancelled msg__failed">
              <el-icon :size="11"><CircleClose /></el-icon><span>{{ item.msg.errorMessage || t('common.failed') }}</span>
            </div>
            <div v-if="item.msg.promptTokens != null || item.msg.completionTokens != null" class="msg__usage">
              {{ item.msg.promptTokens ?? 0 }} in / {{ item.msg.completionTokens ?? 0 }} out
              <span v-if="item.msg.costCents != null"> · ¥{{ (item.msg.costCents / 100).toFixed(3) }}</span>
            </div>
            <div v-if="item.msg.status === 'DONE' && typeof item.msg.id === 'number'" class="msg__feedback">
              <button
                class="msg__feedback-btn"
                :class="{ 'msg__feedback-btn--up': feedbackGiven[item.msg.id] === 'THUMBS_UP' }"
                @click="handleFeedback(item.msg.id as number, 'THUMBS_UP')"
              >{{ t('ide.feedbackUp') }}</button>
              <span class="msg__feedback-sep" aria-hidden="true">·</span>
              <button
                class="msg__feedback-btn"
                :class="{ 'msg__feedback-btn--down': feedbackGiven[item.msg.id] === 'THUMBS_DOWN' }"
                @click="handleFeedback(item.msg.id as number, 'THUMBS_DOWN')"
              >{{ t('ide.feedbackDown') }}</button>
            </div>
          </div>
        </div>

        <!-- Tool group:单个 call = 传统卡片 / artifact 卡片;多个连续同名 = 合并卡片 -->
        <template v-else-if="item.kind === 'tool_group'">
          <!-- Artifact (create_script 等) - 只可能单个(ARTIFACT_TOOLS 不参与合并) -->
          <div v-if="item.calls.length === 1 && isArtifactTool(item.calls[0].toolName)"
               class="msg msg--tool">
            <div class="msg__avatar msg__avatar--tool"><el-icon :size="13"><Document /></el-icon></div>
            <div class="msg__body">
              <div class="artifact-card"
                   :class="{ 'artifact-card--err': toolResultStatus(item.calls[0]) === 'err' }">
                <div class="artifact-card__head">
                  <el-icon :size="14"><Document /></el-icon>
                  <span class="artifact-card__title">{{ artifactTitle(item.calls[0]) }}</span>
                  <span class="artifact-card__status" :class="`artifact-card__status--${toolResultStatus(item.calls[0])}`">
                    {{ toolResultStatus(item.calls[0]) === 'ok' ? t('ide.artifactCreated')
                       : toolResultStatus(item.calls[0]) === 'err' ? t('common.failed')
                       : '…' }}
                  </span>
                </div>
                <div v-if="artifactCode(item.calls[0])" class="artifact-card__meta">
                  <span class="artifact-card__label">code</span>
                  <code>{{ artifactCode(item.calls[0]) }}</code>
                  <button class="artifact-card__action" @click="copyText(artifactCode(item.calls[0])!, t('ide.codeCopied'))">
                    {{ t('ide.copy') }}
                  </button>
                </div>
                <div v-if="toolResultStatus(item.calls[0]) === 'err'" class="artifact-card__error">
                  {{ getToolResult(item.calls[0].toolCallId)?.errorMessage
                     || getToolResult(item.calls[0].toolCallId)?.toolOutput }}
                </div>
              </div>
            </div>
          </div>
          <!-- 单个普通 tool call -->
          <div v-else-if="item.calls.length === 1" class="msg msg--tool">
            <div class="msg__avatar msg__avatar--tool"><el-icon :size="13"><Tools /></el-icon></div>
            <div class="msg__body">
              <div class="tool-card"
                   :class="{
                     'tool-card--pending': item.calls[0].approvalState === 'pending',
                     'tool-card--err': isToolFailed(item.calls[0]),
                   }">
                <div class="tool-card__head">
                  <span class="tool-card__name">{{ formatToolName(item.calls[0].toolName) }}</span>
                  <ToolStatusBadge :message-id="item.calls[0].toolCallId ?? ''" :messages="store.messages" />
                </div>
                <details v-if="item.calls[0].toolInput" class="tool-card__details"
                         :open="item.calls[0].approvalState === 'pending' || isToolFailed(item.calls[0])">
                  <summary>
                    {{ item.calls[0].approvalState === 'pending' ? t('ide.toolPreview')
                       : isToolFailed(item.calls[0]) ? t('ide.toolFailedDetail') : 'input' }}
                  </summary>
                  <pre>{{ prettyJson(item.calls[0].toolInput) }}</pre>
                  <pre v-if="isToolFailed(item.calls[0])" class="tool-card__err-text">{{ getToolResult(item.calls[0].toolCallId)?.errorMessage
                       || getToolResult(item.calls[0].toolCallId)?.toolOutput }}</pre>
                </details>
                <div v-if="item.calls[0].approvalState === 'pending'" class="tool-card__actions">
                  <span class="tool-card__warn">{{ t('ide.toolConfirmHint') }}</span>
                  <el-button size="small" type="primary" @click="handleApprove(item.calls[0].toolCallId!, true)">
                    {{ t('ide.apply') }}
                  </el-button>
                  <el-button size="small" @click="handleApprove(item.calls[0].toolCallId!, false)">
                    {{ t('ide.reject') }}
                  </el-button>
                </div>
                <div v-else-if="item.calls[0].approvalState === 'rejected'" class="tool-card__rejected">
                  {{ t('ide.toolRejected') }}
                </div>
              </div>
            </div>
          </div>
          <!-- 连续同名 tool(≥2):合并成一条紧凑卡片 -->
          <div v-else class="msg msg--tool">
            <div class="msg__avatar msg__avatar--tool"><el-icon :size="13"><Tools /></el-icon></div>
            <div class="msg__body">
              <details class="tool-group"
                       :open="item.calls.some(c => isToolFailed(c))">
                <summary class="tool-group__head">
                  <span class="tool-group__name">{{ formatToolName(item.name) }}</span>
                  <span class="tool-group__count">×{{ item.calls.length }}</span>
                  <span class="tool-group__dots">
                    <span v-for="c in item.calls" :key="c.toolCallId ?? c.id"
                          class="tool-group__dot" :class="`tool-group__dot--${toolResultStatus(c)}`" />
                  </span>
                </summary>
                <div class="tool-group__list">
                  <div v-for="c in item.calls" :key="c.toolCallId ?? c.id" class="tool-group__item">
                    <span class="tool-group__item-badge" :class="`tool-group__dot--${toolResultStatus(c)}`">
                      {{ toolResultStatus(c) === 'ok' ? '✓' : toolResultStatus(c) === 'err' ? '✗' : '…' }}
                    </span>
                    <code class="tool-group__item-args">{{ prettyJson(c.toolInput).slice(0, 120) }}</code>
                    <div v-if="isToolFailed(c)" class="tool-group__item-err">
                      {{ getToolResult(c.toolCallId)?.errorMessage || getToolResult(c.toolCallId)?.toolOutput }}
                    </div>
                  </div>
                </div>
              </details>
            </div>
          </div>
        </template>
      </template>
    </div>

    <!-- Session bar -->
    <div class="ai-chat__sessions">
      <el-tooltip :content="t('ide.newChat')" placement="top" :show-after="400">
        <button class="sessions__new-btn" @click="handleNewChat">
          <el-icon :size="13"><Plus /></el-icon>
        </button>
      </el-tooltip>
      <el-popover v-if="store.sessions.length" placement="top-start" :width="240" trigger="click">
        <template #reference>
          <button class="sessions__all-btn" :title="t('ide.history')">
            <el-icon :size="13"><ChatDotRound /></el-icon>
          </button>
        </template>
        <div class="sessions-pop">
          <div class="sessions-pop__title">{{ t('ide.history') }}</div>
          <div
            v-for="s in store.sessions" :key="s.id"
            class="sessions-pop__item" :class="{ active: s.id === store.activeSessionId }"
            @click="store.switchSession(s.id)"
          >
            <span class="sessions-pop__label">{{ s.title }}</span>
            <button class="sessions-pop__del" @click.stop="store.deleteSession(s.id)">
              <el-icon :size="11"><Close /></el-icon>
            </button>
          </div>
        </div>
      </el-popover>
      <div v-if="store.sessions.length" class="sessions__tabs">
        <button
          v-for="s in recentSessions" :key="s.id"
          class="sessions__tab" :class="{ active: s.id === store.activeSessionId }"
          :title="s.title"
          @click="store.switchSession(s.id)"
        >{{ s.title }}</button>
      </div>
    </div>

    <!-- Input -->
    <div class="ai-chat__input">
      <div class="input-wrap">
        <el-input
          ref="inputEl"
          v-model="inputText"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 6 }"
          :placeholder="agentModeOn ? t('ide.agentPlaceholder') : t('ide.aiPlaceholder')"
          resize="none"
          @keydown.enter.exact="handleEnter"
          @compositionstart="imeComposing = true"
          @compositionend="imeComposing = false"
        />
      </div>
      <button v-if="isStreamingMine" class="send-btn send-btn--stop" @click="handleCancel">
        <el-icon :size="16"><VideoPause /></el-icon>
      </button>
      <button v-else class="send-btn" :class="{ disabled: !canSend }" @click="handleSend">
        <el-icon :size="16"><Promotion /></el-icon>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, inject, computed, nextTick, onMounted, onUnmounted, watch, h, defineComponent } from 'vue'
import { IDE_STATE_KEY, buildTurnContext } from './ideState'
import { useI18n } from 'vue-i18n'
import { ChatDotRound, Promotion, Plus, Close, VideoPause, ArrowRight, MagicStick, User, CircleClose, Tools, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAiChatStore, type UiMessage } from '@/stores/aiChat'
import { feedback as feedbackApi, type FeedbackSignal } from '@/api/ai'
import { Marked } from 'marked'
import { renderSafeMarkdown } from '@/utils/safeMarkdown'
import hljs from 'highlight.js/lib/core'
import sql from 'highlight.js/lib/languages/sql'
import javascript from 'highlight.js/lib/languages/javascript'
import python from 'highlight.js/lib/languages/python'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import java from 'highlight.js/lib/languages/java'

hljs.registerLanguage('sql', sql)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('java', java)

const marked = new Marked({
  gfm: true,
  breaks: false,
  renderer: {
    code({ text, lang }) {
      const language = lang && hljs.getLanguage(lang) ? lang : 'plaintext'
      const highlighted = language !== 'plaintext'
        ? hljs.highlight(text, { language }).value
        : escapeHtml(text)
      return `<div class="code-block">
        <div class="code-block__header">
          <span class="code-block__lang">${language}</span>
          <button class="code-block__copy" data-action="copy">Copy</button>
          <button class="code-block__apply" data-action="apply">Apply</button>
        </div>
        <pre><code class="hljs">${highlighted}</code></pre>
      </div>`
    },
    codespan({ text }) {
      return `<code class="inline-code">${escapeHtml(text)}</code>`
    },
  },
})

function escapeHtml(str: string) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function renderMarkdown(text: string): string {
  if (!text) return ''
  const sanitized = renderSafeMarkdown(text, marked)
  return sanitized.replace(/<table>/g, '<div class="table-wrap"><table>').replace(/<\/table>/g, '</table></div>')
}

const { t } = useI18n()
const ideState = inject(IDE_STATE_KEY)!
const store = useAiChatStore()

// 写类工具成功后刷新 IDE。用户在 tab 里有未保存改动时不要覆盖 —— 只刷文件树。
// Why: 用户审批 Apply 时已经同意了修改,但同名 tab 里可能还有尚未提交的本地编辑,
// 静默覆盖会丢工作成果。
const WRITE_TOOLS = new Set(['update_script', 'create_script', 'rename_script', 'move_script', 'delete_script'])
let lastAppliedResultId: string | number | null | undefined = null
watch(() => store.messages.length, () => {
  for (let i = store.messages.length - 1; i >= 0; i--) {
    const m = store.messages[i]
    if (m.role !== 'tool_result' || m.id === lastAppliedResultId) break
    const call = store.messages.find(x => x.role === 'tool_call' && x.toolCallId === m.toolCallId)
    if (!call || !WRITE_TOOLS.has(call.toolName || '') || !m.toolSuccess) continue
    lastAppliedResultId = m.id
    ideState.fileTreeRefreshKey++
    const activeTab = ideState.tabs?.find((t: any) => t.id === ideState.activeTabId)
    if (activeTab && !activeTab.modified) {
      ideState.editorRefreshKey++
    } else if (activeTab?.modified) {
      ElMessage.warning(t('ide.toolAppliedKeepLocal'))
    }
    break
  }
})

// Agent mode 仅用于新会话。已有 session 后 mode 锁定(tooltip 告诉用户),
// 技术约束:CHAT 历史无 tool_call,中途切到 AGENT 会让 LLM 拿到工具但上下文错位。
const hasActiveSession = computed(() =>
  store.activeSessionId != null && store.sessions.some(x => x.id === store.activeSessionId),
)
const agentModeOn = computed<boolean>({
  get() {
    const s = store.sessions.find(x => x.id === store.activeSessionId)
    return s ? s.mode === 'AGENT' : store.pendingMode === 'AGENT'
  },
  set(v: boolean) {
    if (hasActiveSession.value) return
    store.pendingMode = v ? 'AGENT' : 'CHAT'
  },
})

const recentSessions = computed(() => store.sessions.slice(0, 3))
const inputText = ref('')
const inputEl = ref()
const messagesEl = ref<HTMLElement>()

// 隐藏 tool_result:tool_call 卡片内部合并展示
const visibleMessages = computed(() => store.messages.filter(m => m.role !== 'tool_result'))

// ---------- 渲染层分组:连续同名 tool_call 合并成 tool_group ----------
type DisplayItem =
  | { kind: 'msg'; msg: UiMessage }
  | { kind: 'tool_group'; key: string; name: string; calls: UiMessage[] }

const displayItems = computed<DisplayItem[]>(() => {
  const out: DisplayItem[] = []
  for (const m of visibleMessages.value) {
    if (m.role === 'tool_call') {
      const last = out[out.length - 1]
      if (last && last.kind === 'tool_group' && last.name === (m.toolName ?? '')
          // 待审批的不合并(UI 上要单独显示 Apply/Reject),artifact 类也不合并(各自要显示卡片)
          && m.approvalState !== 'pending' && !isArtifactTool(m.toolName)) {
        last.calls.push(m)
        continue
      }
      out.push({
        kind: 'tool_group',
        key: m.toolCallId ?? String(m.id),
        name: m.toolName ?? 'tool',
        calls: [m],
      })
    } else {
      out.push({ kind: 'msg', msg: m })
    }
  }
  return out
})

// ---------- 助手:查 tool_call 对应的 tool_result / 判失败 / 判 artifact 类型 ----------
const ARTIFACT_TOOLS = new Set(['create_script', 'update_script'])
function isArtifactTool(name: string | null | undefined): boolean {
  return !!name && ARTIFACT_TOOLS.has(name)
}
function getToolResult(callId: string | null | undefined): UiMessage | undefined {
  if (!callId) return undefined
  return store.messages.find(m => m.role === 'tool_result' && m.toolCallId === callId)
}
function isToolFailed(call: UiMessage): boolean {
  const r = getToolResult(call.toolCallId)
  return !!r && r.toolSuccess === false
}
function toolResultStatus(call: UiMessage): 'running' | 'ok' | 'err' {
  const r = getToolResult(call.toolCallId)
  if (!r) return 'running'
  return r.toolSuccess ? 'ok' : 'err'
}

/** 从 create_script 的 toolInput 里取脚本名(UI 卡片标题)。 */
function artifactTitle(call: UiMessage): string {
  try {
    const args = JSON.parse(call.toolInput ?? '{}')
    return args?.name ?? call.toolName ?? 'artifact'
  } catch {
    return call.toolName ?? 'artifact'
  }
}

/** 从 create_script 的 toolOutput 里取脚本 code(如果后端返回了)。 */
function artifactCode(call: UiMessage): string | null {
  const r = getToolResult(call.toolCallId)
  if (!r?.toolOutput) return null
  try {
    const payload = JSON.parse(r.toolOutput)
    const code = payload?.code ?? payload?.scriptCode
    return code != null ? String(code) : null
  } catch {
    // 有的工具直接返回字符串,尝试 regex 提取数字 id
    const match = /"?code"?\s*[:=]\s*(\d+)/.exec(r.toolOutput)
    return match ? match[1] : null
  }
}

function copyText(text: string, okMsg?: string) {
  navigator.clipboard.writeText(text).then(() => ElMessage.success(okMsg ?? text))
}

// 流是否属于当前会话:跨 session 切换 / 新建会话时,旧流仍在跑但不属于当前 session
const isStreamingMine = computed(() => store.streaming && store.streamingSessionId === store.activeSessionId)
const canSend = computed(() => inputText.value.trim().length > 0 && !store.streaming)

// ---------- tool_call 右侧状态徽章组件 ----------
const ToolStatusBadge = defineComponent({
  props: { messageId: { type: String, required: true }, messages: { type: Array, required: true } },
  setup(props) {
    return () => {
      const msgs = props.messages as UiMessage[]
      const result = msgs.find(m => m.role === 'tool_result' && m.toolCallId === props.messageId)
      if (!result) {
        return h('span', { class: 'tool-badge tool-badge--running' }, '…')
      }
      return h('span', {
        class: ['tool-badge', result.toolSuccess ? 'tool-badge--ok' : 'tool-badge--err'],
      }, result.toolSuccess ? '✓' : '✗')
    }
  },
})

function formatToolName(tool: string | null | undefined): string {
  return tool || 'tool'
}

function prettyJson(raw: string | null | undefined): string {
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

/**
 * 折叠态 thinking 块的 summary 文案:
 *  - 正文还没开始(thinking 中) → "思考中..."
 *  - 正文开始后 → "已思考 Xs"
 */
function thinkingSummary(msg: { thinking?: string; thinkingStartedAt?: number; thinkingEndedAt?: number }): string {
  if (!msg.thinking) return ''
  if (msg.thinkingStartedAt && msg.thinkingEndedAt) {
    const secs = Math.max(1, Math.round((msg.thinkingEndedAt - msg.thinkingStartedAt) / 1000))
    return t('ide.thoughtFor', { secs })
  }
  return t('ide.thinkingOngoing')
}

// ==================== Code block interactions ====================

function handleCodeBlockClick(ev: MouseEvent) {
  const target = ev.target as HTMLElement | null
  if (!target) return
  const btn = target.closest<HTMLElement>('[data-action]')
  if (!btn) return
  const action = btn.dataset.action
  const block = btn.closest('.code-block')
  const code = block?.querySelector('code')?.textContent ?? ''
  if (!code) return
  if (action === 'copy') {
    navigator.clipboard.writeText(code).then(() => {
      const prev = btn.textContent
      btn.textContent = '✓'
      setTimeout(() => { btn.textContent = prev ?? 'Copy' }, 1500)
    })
  } else if (action === 'apply') {
    const tab = ideState.tabs?.find((t: any) => t.id === ideState.activeTabId)
    if (!tab) {
      ElMessage.warning(t('ide.applyNoTab'))
      return
    }
    tab.sql = code
    tab.modified = true
    // Monaco 只监听 editorRefreshKey,不监听 tab.sql,这里手动触发一次同步
    ideState.editorRefreshKey++
    ElMessage.success(t('ide.codeApplied'))
  }
}

onMounted(async () => {
  messagesEl.value?.addEventListener('click', handleCodeBlockClick)
  await store.init()
  scrollToBottom()
})

const unmounted = ref(false)
onUnmounted(() => {
  unmounted.value = true
  messagesEl.value?.removeEventListener('click', handleCodeBlockClick)
})

// 滚动触发:消息条数变 或 最后一条流式消息长度变。
// 不 join 所有 content —— 长会话下那是每 token 拼一次 MB 级字符串。
watch(() => store.messages.length, () => scrollToBottom())
watch(() => {
  const last = visibleMessages.value[visibleMessages.value.length - 1]
  if (!last) return 0
  return (last.content?.length ?? 0) + (last.thinking?.length ?? 0)
}, () => scrollToBottom())

let scrollRaf: number | null = null
function scrollToBottom() {
  if (scrollRaf != null) return
  scrollRaf = requestAnimationFrame(async () => {
    scrollRaf = null
    await nextTick()
    const el = messagesEl.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// ==================== Actions ====================

function handleNewChat() {
  store.startNewSession()
  inputText.value = ''
  nextTick(() => inputEl.value?.focus?.())
}

function handleCancel() {
  store.cancelCurrent()
}

function handleApprove(toolCallId: string, approved: boolean) {
  store.approveTool(toolCallId, approved)
}

const feedbackGiven = reactive<Record<number, FeedbackSignal>>({})
async function handleFeedback(messageId: number, signal: FeedbackSignal) {
  if (feedbackGiven[messageId] === signal) return
  try {
    await feedbackApi.submit({ messageId, signal })
    feedbackGiven[messageId] = signal
    ElMessage.success(t('ide.feedbackThanks'))
  } catch { ElMessage.error(t('common.failed')) }
}

const imeComposing = ref(false)
function handleEnter(ev: Event) {
  const ke = ev as KeyboardEvent
  if (imeComposing.value || ke.isComposing || ke.keyCode === 229) return
  ev.preventDefault()
  handleSend()
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text) return
  if (store.streaming) {
    ElMessage.warning(t('ide.aiAnotherSessionStreaming'))
    return
  }
  inputText.value = ''
  const activeTab = ideState.tabs?.find(t => t.id === ideState.activeTabId)
  try {
    await store.sendTurn(text, buildTurnContext(activeTab, ideState))
    if (unmounted.value) return
    // selection 用完即清,避免下一轮重复注入
    ideState.aiSelectionText = ''
  } catch (e: any) {
    if (unmounted.value) return
    // 不直出 e?.message,可能含内部异常细节;详细信息进 console 上报
    console.error('AI sendTurn failed', e)
    ElMessage.error(t('common.failed'))
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/ide.scss' as *;

.ai-chat {
  height: 100%; display: flex; flex-direction: column; background: $ide-bg;
}

.ai-chat__header {
  @extend %section-header;
  justify-content: space-between;
  padding: 0 10px;
}
.ai-chat__title {
  @extend %section-title;
  display: inline-flex; align-items: center; gap: 7px;
}
.ai-chat__title-icon { color: $ide-spark; flex-shrink: 0; }
.ai-chat__header-actions { display: flex; align-items: center; gap: 4px; }
.agent-toggle { display: inline-flex; align-items: center; }

.header-btn {
  width: 24px; height: 24px; border: none; background: transparent;
  border-radius: 4px; cursor: pointer; color: var(--r-text-muted);
  display: flex; align-items: center; justify-content: center; transition: all 0.15s;
  &:hover { background: var(--r-bg-hover); color: var(--r-text-secondary); }
}

.ai-chat__body {
  flex: 1; overflow-y: auto; padding: 16px 12px;
  display: flex; flex-direction: column; gap: 16px;
}

.ai-chat__empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  flex: 1; color: var(--r-text-disabled); font-size: 12px; line-height: 1.6; text-align: center;
  p { max-width: 220px; margin: 12px 0 0; }
}
.empty-icon {
  width: 44px; height: 44px; border-radius: 14px;
  background: $ide-spark-soft;
  border: 1px solid $ide-spark-border;
  display: flex; align-items: center; justify-content: center;
  color: $ide-spark;
  box-shadow: 0 0 0 6px rgba(232, 167, 91, 0.06);
  animation: empty-icon-in 420ms cubic-bezier(0.2, 0.9, 0.3, 1) both;
}
@keyframes empty-icon-in {
  from { opacity: 0; transform: scale(0.8); }
}

.msg { display: flex; gap: 10px; align-items: flex-start; }
.msg__avatar {
  width: 26px; height: 26px; border-radius: 8px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.msg--user .msg__avatar {
  background: var(--r-bg-hover);
  color: var(--r-text-secondary);
  border: 1px solid var(--r-border);
}
.msg--ai .msg__avatar {
  background: $ide-spark-soft;
  color: $ide-spark;
  border: 1px solid $ide-spark-border;
}
.msg__avatar--tool {
  background: var(--r-bg-panel);
  color: var(--r-text-muted);
  border: 1px solid $ide-border;
}
.msg__body { flex: 1; min-width: 0; }

.msg__content {
  font-size: 13px; line-height: 1.7; color: var(--r-text-secondary); word-break: break-word;
  :deep(h1) { font-size: 18px; font-weight: 700; color: var(--r-text-primary); margin: 16px 0 8px; line-height: 1.4; &:first-child { margin-top: 0; } }
  :deep(h2) { font-size: 16px; font-weight: 700; color: var(--r-text-primary); margin: 14px 0 6px; line-height: 1.4; &:first-child { margin-top: 0; } }
  :deep(h3) { font-size: 14px; font-weight: 600; color: var(--r-text-primary); margin: 12px 0 4px; line-height: 1.4; &:first-child { margin-top: 0; } }
  :deep(h4), :deep(h5), :deep(h6) { font-size: 13px; font-weight: 600; color: var(--r-text-primary); margin: 10px 0 4px; line-height: 1.4; &:first-child { margin-top: 0; } }
  :deep(p) { margin: 0 0 8px; &:last-child { margin-bottom: 0; } }
  :deep(ul), :deep(ol) { margin: 4px 0 8px; padding-left: 20px; }
  :deep(ol) { list-style-type: decimal; }
  :deep(ul) { list-style-type: disc; }
  :deep(li) { margin: 2px 0; }
  :deep(.table-wrap) {
    margin: 8px 0; overflow-x: auto; border-radius: 4px; border: 1px solid #{$ide-border};
  }
  :deep(table) { border-collapse: collapse; font-size: 12px; width: 100%; min-width: max-content; margin: 8px 0; }
  :deep(.table-wrap table) { margin: 0; }
  :deep(th), :deep(td) { border: 1px solid #{$ide-border}; padding: 6px 10px; text-align: left; white-space: nowrap; }
  :deep(td) { white-space: normal; }
  :deep(th) { background: var(--r-bg-panel); font-weight: 600; color: var(--r-text-primary); }
  :deep(blockquote) {
    border-left: 3px solid var(--r-border-dark); margin: 8px 0; padding: 4px 12px; color: var(--r-text-muted);
    background: var(--r-bg-panel); border-radius: 0 4px 4px 0;
  }
  :deep(strong) { font-weight: 600; color: var(--r-text-primary); }
  :deep(em) { font-style: italic; }
  :deep(a) { color: var(--r-accent); text-decoration: none; &:hover { text-decoration: underline; } }
  :deep(hr) { border: none; border-top: 1px solid var(--r-border-light); margin: 12px 0; }
  :deep(.inline-code),
  :deep(code:not(.hljs)) {
    background: var(--r-bg-hover); padding: 1px 5px; border-radius: 3px;
    font-family: var(--r-font-mono); font-size: 11px; color: var(--r-danger);
  }
  :deep(.code-block) {
    margin: 8px 0; border-radius: 6px; overflow: hidden;
    border: 1px solid #{$ide-border}; background: var(--r-bg-code);
  }
  :deep(.code-block__header) {
    display: flex; align-items: center; gap: 8px;
    padding: 4px 10px; background: var(--r-bg-hover); font-size: 11px; color: var(--r-text-muted);
  }
  :deep(.code-block__lang) { flex: 1; text-transform: uppercase; font-weight: 500; }
  :deep(.code-block__copy), :deep(.code-block__apply) {
    border: none; background: transparent; cursor: pointer; font-size: 11px;
    color: var(--r-text-muted); padding: 2px 6px; border-radius: 3px;
    &:hover { background: var(--r-border); color: var(--r-text-secondary); }
  }
  :deep(.code-block__apply) { color: var(--r-accent); &:hover { background: var(--r-accent-bg); } }
  :deep(.code-block pre) { margin: 0; padding: 10px 12px; overflow-x: auto; }
  :deep(.code-block code) {
    font-family: var(--r-font-mono); font-size: 12px; line-height: 1.5;
  }
}
.msg__content--user {
  background: var(--r-accent-bg); padding: 8px 12px; border-radius: 8px; color: var(--r-text-primary);
}

.msg__thinking-block {
  margin: 0 0 6px;
  border-left: 2px solid var(--r-border);
  padding-left: 10px;
  summary {
    list-style: none;
    cursor: pointer;
    display: inline-flex; align-items: center; gap: 6px;
    font-size: var(--r-font-xs); color: var(--r-text-muted);
    padding: 2px 0;
    user-select: none;
    &::-webkit-details-marker { display: none; }
    &::before {
      content: '▸'; font-size: 9px; color: var(--r-text-disabled);
      transition: transform 0.15s;
    }
    &:hover { color: var(--r-text-primary); }
  }
  &[open] summary::before { transform: rotate(90deg); }
}
.msg__thinking-text {
  margin-top: 6px;
  font-size: var(--r-font-xs);
  line-height: 1.6;
  color: var(--r-text-muted);
  white-space: pre-wrap;
  word-break: break-word;
}

.msg__thinking {
  display: flex; align-items: center; gap: 8px;
  font-size: 12px; color: var(--r-text-muted); padding: 8px 0;
}
.thinking-dots {
  display: inline-flex; gap: 3px;
  span {
    width: 5px; height: 5px; border-radius: 50%; background: var(--r-text-disabled);
    animation: dot-bounce 1.4s infinite ease-in-out both;
    &:nth-child(1) { animation-delay: 0s; }
    &:nth-child(2) { animation-delay: 0.16s; }
    &:nth-child(3) { animation-delay: 0.32s; }
  }
}
@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.msg__cancelled {
  display: inline-flex; align-items: center; gap: 4px;
  margin-top: 6px; padding: 2px 8px; font-size: 11px;
  color: var(--r-text-muted); background: var(--r-bg-hover);
  border: 1px solid $ide-border; border-radius: 999px; line-height: 1.4;
}
.msg__failed {
  color: var(--r-danger); background: var(--r-danger-bg); border-color: var(--r-danger-border);
}
.msg__usage {
  margin-top: 6px; font-size: 11px; color: var(--r-text-disabled);
  font-family: var(--r-font-mono);
}
.msg__feedback {
  display: inline-flex; align-items: baseline;
  gap: 8px;
  margin-top: 8px;
  // 默认淡出,鼠标悬停消息体时浮现 —— 阅读时不抢戏
  opacity: 0;
  transition: opacity 200ms ease;
  .msg:hover & { opacity: 1; }
}
.msg__feedback-btn {
  font: inherit;
  font-size: var(--r-font-xs);
  font-weight: var(--r-weight-medium);
  letter-spacing: 0.04em;
  cursor: pointer;
  background: transparent;
  border: 0;
  padding: 0;
  color: var(--r-text-muted);
  // 用 background-image 画一根 1px 下划线 hairline,从无到有过渡更克制(对比 text-decoration 不够细)
  background-image: linear-gradient(currentColor, currentColor);
  background-size: 0% 1px;
  background-position: 0 100%;
  background-repeat: no-repeat;
  transition: color 160ms ease, background-size 220ms cubic-bezier(0.2, 0.9, 0.3, 1);

  &:hover {
    color: var(--r-text-secondary);
    background-size: 100% 1px;
  }
  &--up {
    color: $ide-spark;
    background-size: 100% 1px;
  }
  &--down {
    color: var(--r-danger);
    background-size: 100% 1px;
  }
  &--up:hover { color: $ide-spark; }
  &--down:hover { color: var(--r-danger); }
}
.msg__feedback-sep {
  font-size: var(--r-font-xs);
  color: var(--r-text-disabled);
  user-select: none;
}

// ==================== Tool card ====================
.tool-card {
  border: 1px solid $ide-border; border-radius: 6px;
  background: var(--r-bg-panel); padding: 6px 10px;
  font-size: 12px;
  &--pending {
    border-color: var(--r-warning, #e6a23c);
    background: color-mix(in srgb, var(--r-warning, #e6a23c) 6%, transparent);
  }
  &--err {
    border-color: var(--r-danger, #f56c6c);
    background: color-mix(in srgb, var(--r-danger, #f56c6c) 4%, transparent);
  }
}
.tool-card__err-text {
  margin-top: 4px !important;
  color: var(--r-danger) !important;
  background: var(--r-danger-bg) !important;
}
.tool-card__actions {
  display: flex; align-items: center; gap: 6px;
  margin-top: 8px; padding-top: 6px; border-top: 1px dashed $ide-border;
}
.tool-card__warn { flex: 1; font-size: 11px; color: var(--r-warning, #e6a23c); }
.tool-card__rejected {
  margin-top: 6px; padding-top: 6px; border-top: 1px dashed $ide-border;
  font-size: 11px; color: var(--r-text-muted); font-style: italic;
}
.tool-card__head {
  display: flex; align-items: center; gap: 6px;
}
.tool-card__name {
  flex: 1; font-family: var(--r-font-mono); font-size: 12px; color: var(--r-text-secondary);
}
.tool-card__details { margin-top: 4px; font-size: 11px;
  summary { cursor: pointer; color: var(--r-text-muted); }
  pre {
    margin: 4px 0 0; padding: 6px 8px; background: var(--r-bg-code); border-radius: 4px;
    font-family: var(--r-font-mono); font-size: 11px; color: var(--r-text-secondary);
    white-space: pre-wrap; word-break: break-all; max-height: 160px; overflow-y: auto;
  }
}
.tool-badge {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 18px; height: 18px; padding: 0 6px; border-radius: 999px;
  font-size: 11px; font-weight: 600;
  &--running { background: var(--r-bg-hover); color: var(--r-text-muted); }
  &--ok { background: var(--r-success-bg); color: var(--r-success); }
  &--err { background: var(--r-danger-bg); color: var(--r-danger); }
}

// ==================== Tool group (连续同名 tool 合并) ====================
.tool-group {
  border: 1px solid $ide-border; border-radius: 6px;
  background: var(--r-bg-panel);
  font-size: 12px;
  summary {
    list-style: none;
    cursor: pointer;
    display: flex; align-items: center; gap: 8px;
    padding: 6px 10px;
    user-select: none;
    &::-webkit-details-marker { display: none; }
    &:hover { background: var(--r-bg-hover); }
  }
  &[open] summary { border-bottom: 1px dashed $ide-border; }
}
.tool-group__name {
  flex: 1; font-family: var(--r-font-mono); color: var(--r-text-secondary);
}
.tool-group__count {
  font-size: 11px; color: var(--r-text-muted);
  background: var(--r-bg-hover); padding: 1px 6px; border-radius: 999px;
}
.tool-group__dots {
  display: inline-flex; gap: 2px;
}
.tool-group__dot {
  display: inline-block; width: 7px; height: 7px; border-radius: 50%;
  &--running { background: var(--r-text-disabled); }
  &--ok { background: var(--r-success); }
  &--err { background: var(--r-danger); }
}
.tool-group__list {
  padding: 6px 10px;
  display: flex; flex-direction: column; gap: 4px;
}
.tool-group__item {
  display: flex; align-items: flex-start; gap: 8px;
  padding: 3px 0;
  font-size: 11px;
}
.tool-group__item-badge {
  display: inline-flex; align-items: center; justify-content: center;
  width: 16px; height: 16px; border-radius: 50%;
  font-size: 10px; font-weight: 600; flex-shrink: 0;
  &.tool-group__dot--ok { background: var(--r-success-bg); color: var(--r-success); }
  &.tool-group__dot--err { background: var(--r-danger-bg); color: var(--r-danger); }
  &.tool-group__dot--running { background: var(--r-bg-hover); color: var(--r-text-muted); }
}
.tool-group__item-args {
  flex: 1; font-family: var(--r-font-mono); color: var(--r-text-muted);
  white-space: pre-wrap; word-break: break-all;
}
.tool-group__item-err {
  width: 100%; padding-left: 24px; margin-top: 2px;
  color: var(--r-danger); font-size: 11px;
}

// ==================== Artifact card (create_script 等) ====================
.artifact-card {
  border: 1px solid var(--r-accent); border-radius: 6px;
  background: color-mix(in srgb, var(--r-accent) 5%, var(--r-bg-panel));
  padding: 8px 12px;
  &--err {
    border-color: var(--r-danger);
    background: color-mix(in srgb, var(--r-danger) 4%, var(--r-bg-panel));
  }
}
.artifact-card__head {
  display: flex; align-items: center; gap: 8px;
}
.artifact-card__title {
  flex: 1; font-weight: 600; color: var(--r-text-primary); font-size: 12px;
}
.artifact-card__status {
  font-size: 11px; padding: 1px 8px; border-radius: 999px;
  &--ok { background: var(--r-success-bg); color: var(--r-success); }
  &--err { background: var(--r-danger-bg); color: var(--r-danger); }
  &--running { background: var(--r-bg-hover); color: var(--r-text-muted); }
}
.artifact-card__meta {
  display: flex; align-items: center; gap: 8px;
  margin-top: 6px;
  font-size: 11px; color: var(--r-text-muted);
  code {
    font-family: var(--r-font-mono); background: var(--r-bg-code);
    padding: 1px 6px; border-radius: 3px; color: var(--r-text-secondary);
  }
}
.artifact-card__label { color: var(--r-text-disabled); }
.artifact-card__action {
  background: transparent; border: 1px solid $ide-border; border-radius: 3px;
  padding: 1px 8px; font-size: 11px; color: var(--r-text-secondary);
  cursor: pointer;
  &:hover { background: var(--r-bg-hover); border-color: var(--r-accent); color: var(--r-text-primary); }
}
.artifact-card__error {
  margin-top: 6px; padding: 6px 8px; border-radius: 4px;
  background: var(--r-danger-bg); color: var(--r-danger);
  font-size: 11px; font-family: var(--r-font-mono);
  white-space: pre-wrap; word-break: break-word;
}

// ==================== Sessions ====================
.ai-chat__sessions {
  flex-shrink: 0; border-top: 1px solid var(--r-border-light); background: var(--r-bg-panel);
  padding: 4px 8px; display: flex; align-items: center; gap: 4px;
}
.sessions__new-btn, .sessions__all-btn {
  flex-shrink: 0; width: 24px; height: 24px; border: 1px solid #{$ide-border};
  border-radius: 6px; background: var(--r-bg-card); cursor: pointer; color: var(--r-text-muted);
  display: flex; align-items: center; justify-content: center;
  &:hover { color: var(--r-accent); border-color: var(--r-accent); }
}
.sessions__tabs {
  display: flex; gap: 4px; flex: 1; min-width: 0; overflow: hidden;
}
.sessions__tab {
  flex-shrink: 0; max-width: 100px; padding: 3px 10px;
  border: 1px solid #{$ide-border}; border-radius: 12px; background: var(--r-bg-card);
  font-size: 11px; color: var(--r-text-muted); cursor: pointer;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; transition: all 0.15s;
  &:hover { color: var(--r-text-secondary); border-color: var(--r-border-dark); }
  &.active { color: var(--r-accent); border-color: var(--r-accent); background: var(--r-accent-bg); font-weight: 500; }
}
.sessions-pop { max-height: 260px; overflow-y: auto; }
.sessions-pop__title { font-size: 12px; font-weight: 600; color: var(--r-text-secondary); padding: 0 4px 8px; border-bottom: 1px solid var(--r-border-light); margin-bottom: 4px; }
.sessions-pop__item {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 8px; cursor: pointer; border-radius: 4px; font-size: 12px; color: var(--r-text-secondary);
  &:hover { background: var(--r-bg-hover); }
  &.active { color: var(--r-accent); font-weight: 500; }
}
.sessions-pop__label { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sessions-pop__del {
  flex-shrink: 0; opacity: 0; border: none; background: transparent;
  width: 18px; height: 18px; border-radius: 3px; cursor: pointer; color: var(--r-text-disabled);
  display: flex; align-items: center; justify-content: center;
  .sessions-pop__item:hover & { opacity: 1; }
  &:hover { color: var(--r-danger); }
}

// ==================== Input ====================
.ai-chat__input {
  display: flex; gap: 6px; padding: 8px 10px;
  border-top: 1px solid #{$ide-border}; flex-shrink: 0; align-items: flex-end;
}
.input-wrap {
  flex: 1; min-width: 0;
  :deep(.el-textarea__inner) {
    font-size: 13px; line-height: 1.5; padding: 6px 10px;
    border-radius: 8px; box-shadow: 0 0 0 1px var(--r-border);
    &:focus { box-shadow: 0 0 0 1px var(--r-accent); }
  }
}
.send-btn {
  width: 32px; height: 32px; border: none; border-radius: 8px;
  background: var(--r-accent); color: #fff; cursor: pointer;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
  transition: all 0.15s;
  &:hover { background: var(--r-accent-hover); }
  &.disabled { background: var(--r-bg-hover); color: var(--r-text-disabled); cursor: not-allowed; }
  &--stop { background: var(--r-danger); &:hover { opacity: 0.85; } }
}
</style>
