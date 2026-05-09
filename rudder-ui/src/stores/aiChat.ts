import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listSessions, createSession, updateSession, deleteSession as apiDeleteSession,
  getSessionMessages, postTurn, cancelStream, approveTool as apiApproveTool,
  type AiSessionVO, type AiMessageVO, type TurnEvent,
} from '@/api/ai'

export type SessionMode = 'CHAT' | 'AGENT'

/** 前端渲染用的消息(一行 = 一条 t_r_ai_message)。 */
export interface UiMessage {
  id: number | string            // 初始 = 临时 tmp-xxx,meta 事件到达后替换为 DB id
  /** 乐观插入时生成的稳定 key,id 被 meta 替换后 token/done 事件仍能用它定位消息。 */
  _tempKey?: string
  role: 'user' | 'assistant' | 'tool_call' | 'tool_result' | 'system'
  status: 'PENDING' | 'STREAMING' | 'DONE' | 'CANCELLED' | 'FAILED' | null
  content: string
  errorMessage?: string | null
  toolCallId?: string | null
  toolName?: string | null
  toolInput?: string | null
  toolOutput?: string | null
  toolSuccess?: boolean | null
  requiresConfirm?: boolean
  approvalState?: 'pending' | 'approved' | 'rejected' | null
  model?: string | null
  promptTokens?: number | null
  completionTokens?: number | null
  costCents?: number | null

  /** 模型 reasoning/thinking 流,独立于 content,前端折叠展示。不持久化。 */
  thinking?: string
  /** thinking 开始/结束时间戳,用于展示"Thought for Ns"摘要。 */
  thinkingStartedAt?: number
  thinkingEndedAt?: number
}

/**
 * AI Chat store(setup style)。state + actions 按三个 domain 组织,边界用 ====== 注释清晰可见:
 *
 * <ul>
 *   <li>SESSIONS —— 会话列表 / activeSessionId / pendingMode</li>
 *   <li>MESSAGES —— 当前会话消息数组 / 占位行轮换</li>
 *   <li>STREAMING —— 当前流的状态机(streaming / abortFetch / activeStreamId)+ sendTurn / onEvent / cancel / approve</li>
 * </ul>
 *
 * <p>内部 race 隐患:旧 turn 的 onEvent / onFinally 可能在切换 session 后跑;
 * sendTurn 用 sessionId 守卫 + abortFn 引用比较防覆盖,详见 STREAMING section。
 */
export const useAiChatStore = defineStore('aiChat', () => {
  // ==================== SESSIONS ====================

  const sessions = ref<AiSessionVO[]>([])
  const activeSessionId = ref<number | null>(null)
  const pendingMode = ref<SessionMode>('CHAT')
  const sessionsLoading = ref(false)
  const initialized = ref(false)

  async function init() {
    if (initialized.value) return
    await loadSessions()
    initialized.value = true
  }

  function reset() {
    sessions.value = []
    activeSessionId.value = null
    messages.value = []
    streaming.value = false
    streamingSessionId.value = null
    activeStreamId.value = null
    abortFetch.value = null
    initialized.value = false
  }

  async function loadSessions() {
    sessionsLoading.value = true
    try {
      const { data } = await listSessions({ pageNum: 1, pageSize: 50 })
      sessions.value = data?.records ?? []
      if (sessions.value.length > 0 && !activeSessionId.value) {
        await switchSession(sessions.value[0].id)
      }
    } catch { /* ignore */ } finally {
      sessionsLoading.value = false
    }
  }

  async function switchSession(sessionId: number) {
    if (activeSessionId.value === sessionId) return
    activeSessionId.value = sessionId
    messages.value = []
    const session = sessions.value.find(s => s.id === sessionId)
    if (session) pendingMode.value = session.mode
    try {
      const { data } = await getSessionMessages(sessionId)
      messages.value = (data ?? []).map(fromApi)
    } catch { /* ignore */ }
  }

  function startNewSession() {
    activeSessionId.value = null
    messages.value = []
  }

  async function deleteSession(sessionId: number) {
    try {
      await apiDeleteSession(sessionId)
      sessions.value = sessions.value.filter(s => s.id !== sessionId)
      if (activeSessionId.value === sessionId) {
        activeSessionId.value = null
        messages.value = []
        if (sessions.value.length > 0) await switchSession(sessions.value[0].id)
      }
    } catch { /* ignore */ }
  }

  // forceModeIfNew 只覆盖新建会话的 mode;已有会话沿用其原 mode,IDE 按钮触发的 turn 也跟着当前对话走。
  async function ensureSession(firstMessage: string, forceModeIfNew?: SessionMode): Promise<number> {
    if (activeSessionId.value) return activeSessionId.value
    const { data } = await createSession({
      title: firstMessage.slice(0, 40),
      mode: forceModeIfNew ?? pendingMode.value,
    })
    sessions.value.unshift(data)
    activeSessionId.value = data.id
    return data.id
  }

  async function renameSession(id: number, title: string) {
    await updateSession(id, { title })
    const s = sessions.value.find(x => x.id === id)
    if (s) s.title = title
  }

  // ==================== MESSAGES ====================

  const messages = ref<UiMessage[]>([])
  /**
   * 当前 turn 正在累积 text/thinking 的 assistant 行的 _tempKey。
   * 每次 tool_result 到达后会"轮换"成一个新的 placeholder,使得 tool 卡片按时序穿插在文本之间。
   */
  const currentAssistantKey = ref<string | null>(null)

  /**
   * turn 结束时统一收尾:
   *  - 所有 STREAMING 状态的 assistant 行改成最终状态
   *  - 把"完全空白"的轮换 placeholder 删掉(模型有时在 tool 之间没说话)
   */
  function finalizeAssistantRows(finalStatus: 'DONE' | 'CANCELLED' | 'FAILED', errorMessage?: string) {
    messages.value = messages.value.filter(m => {
      const empty = m.role === 'assistant'
        && (m.status === 'STREAMING' || m.status === 'PENDING')
        && !m.content && !m.thinking
        && typeof m.id === 'string'   // 只删纯 UI 的 temp 行(有 DB id 的保留)
      return !empty
    })
    for (const m of messages.value) {
      if (m.role === 'assistant' && (m.status === 'STREAMING' || m.status === 'PENDING')) {
        m.status = finalStatus
        if (finalStatus === 'FAILED' && errorMessage) m.errorMessage = errorMessage
      }
    }
    currentAssistantKey.value = null
  }

  // ==================== STREAMING ====================

  const streaming = ref(false)
  // 流属于哪个 session;UI 据此区分"取消我自己的流"和"切回别的会话点取消会误杀"
  const streamingSessionId = ref<number | null>(null)
  const activeStreamId = ref<string | null>(null)
  const abortFetch = ref<null | (() => void)>(null)

  /** 发起一轮对话。前端只管渲染;DB 由后端权威写入。 */
  async function sendTurn(text: string, ctx?: {
    datasourceId?: number | null
    scriptCode?: number | null
    selection?: string | null
    pinnedTables?: string[]
    taskType?: string | null
    forceModeIfNew?: SessionMode
  }) {
    const sessionId = await ensureSession(text, ctx?.forceModeIfNew)
    if (streaming.value) return

    streaming.value = true
    streamingSessionId.value = sessionId
    // 立即乐观插 user + assistant,等 meta 事件回来用真实 id 替换 id 字段;
    // _tempKey 保留作稳定引用,供后续 token/done 事件定位消息。
    const tempUserKey = 'tmp-user-' + Date.now()
    const tempAssistantKey = 'tmp-assistant-' + Date.now()
    messages.value.push({
      id: tempUserKey, _tempKey: tempUserKey, role: 'user', status: 'DONE', content: text,
    })
    messages.value.push({
      id: tempAssistantKey, _tempKey: tempAssistantKey, role: 'assistant', status: 'PENDING', content: '',
    })
    currentAssistantKey.value = tempAssistantKey

    // sessionId 守卫:旧流事件不再写新 session 状态;
    // abortFn 引用比较:只有自己仍是当前流才复位全局状态机,避免覆盖已切换的新流
    const turnSessionId = sessionId
    const myAbort = postTurn(sessionId, {
      message: text,
      datasourceId: ctx?.datasourceId ?? null,
      scriptCode: ctx?.scriptCode ?? null,
      selection: ctx?.selection ?? null,
      pinnedTables: ctx?.pinnedTables ?? [],
      taskType: ctx?.taskType ?? null,
    }, (ev) => {
      if (turnSessionId !== activeSessionId.value) return
      onEvent(ev, tempUserKey, tempAssistantKey)
    }, () => {
      if (abortFetch.value !== myAbort) return
      streaming.value = false
      streamingSessionId.value = null
      activeStreamId.value = null
      abortFetch.value = null
      currentAssistantKey.value = null
    })
    abortFetch.value = myAbort
  }

  function onEvent(ev: TurnEvent, tempUserKey: string, initialAssistantKey: string) {
    // meta 事件把 id 从 tempKey 替换为真实 DB id,之后 findIndex(m => m.id === tempKey)
    // 就永远 -1;用 _tempKey 旁路字段做稳定引用,id 可以尽早换成真 id 以启用 feedback 按钮。
    const findByTempKey = (tempKey: string) => messages.value.findIndex(m => m._tempKey === tempKey)
    const userIdx = findByTempKey(tempUserKey)
    const initialAssistantIdx = findByTempKey(initialAssistantKey)
    // text/thinking 每次写入当前"活动" assistant 行 —— tool_result 后会轮换成新的。
    const currentIdx = currentAssistantKey.value ? findByTempKey(currentAssistantKey.value) : -1
    switch (ev.type) {
      case 'meta': {
        activeStreamId.value = ev.data.streamId
        if (userIdx >= 0 && ev.data.userMessageId != null) {
          messages.value[userIdx].id = ev.data.userMessageId
        }
        // DB 只有一行 assistant 消息,id 只落在最初那条 placeholder 上(后续的轮换 placeholder
        // 是纯前端 UI,保留 tempKey 不发 feedback 按钮)。
        if (initialAssistantIdx >= 0 && ev.data.assistantMessageId != null) {
          messages.value[initialAssistantIdx].id = ev.data.assistantMessageId
        }
        break
      }
      case 'token': {
        if (currentIdx >= 0) {
          const m = messages.value[currentIdx]
          m.status = 'STREAMING'
          m.content = (m.content || '') + ev.data
          // token 一到就意味着正文开始,thinking 阶段结束
          if (m.thinking && !m.thinkingEndedAt) m.thinkingEndedAt = Date.now()
        }
        break
      }
      case 'thinking': {
        if (currentIdx >= 0) {
          const m = messages.value[currentIdx]
          m.status = 'STREAMING'
          if (!m.thinkingStartedAt) m.thinkingStartedAt = Date.now()
          m.thinking = (m.thinking || '') + ev.data
        }
        break
      }
      case 'tool_call': {
        // 当前 assistant 若还是 PENDING,切到 STREAMING —— 让"有动静"可见
        if (currentIdx >= 0 && messages.value[currentIdx].status === 'PENDING') {
          messages.value[currentIdx].status = 'STREAMING'
        }
        messages.value.push({
          id: ev.data.messageId,
          _tempKey: `tool-call-${ev.data.toolCallId}`,
          role: 'tool_call',
          status: null,
          content: '',
          toolCallId: ev.data.toolCallId,
          toolName: ev.data.name,
          toolInput: JSON.stringify(ev.data.input ?? {}),
          requiresConfirm: !!ev.data.requiresConfirm,
          approvalState: ev.data.requiresConfirm ? 'pending' : null,
        })
        break
      }
      case 'tool_result': {
        messages.value.push({
          id: ev.data.messageId,
          _tempKey: `tool-result-${ev.data.toolCallId}`,
          role: 'tool_result',
          status: null,
          content: '',
          toolCallId: ev.data.toolCallId,
          toolOutput: ev.data.output,
          toolSuccess: ev.data.success,
          errorMessage: ev.data.errorMessage,
        })
        // tool_result 到达说明审批已处理完,把对应的 pending tool_call 置为 approved/rejected
        const call = messages.value.find(m => m.role === 'tool_call' && m.toolCallId === ev.data.toolCallId)
        if (call && call.approvalState === 'pending') {
          call.approvalState = ev.data.success ? 'approved' : 'rejected'
        }
        // 下一轮 text/thinking 进新 placeholder;tool 卡片自然夹在两段文字之间,不堆底部。
        const newKey = `tmp-assistant-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`
        messages.value.push({
          id: newKey, _tempKey: newKey, role: 'assistant', status: 'STREAMING', content: '',
        })
        currentAssistantKey.value = newKey
        break
      }
      case 'usage': {
        // usage 落在最初那条(有 DB id 的)
        if (initialAssistantIdx >= 0) {
          const m = messages.value[initialAssistantIdx]
          m.model = ev.data.model
          m.promptTokens = ev.data.promptTokens
          m.completionTokens = ev.data.completionTokens
          m.costCents = ev.data.costCents
        }
        break
      }
      case 'done': {
        finalizeAssistantRows('DONE')
        break
      }
      case 'cancelled': {
        finalizeAssistantRows('CANCELLED')
        break
      }
      case 'error': {
        finalizeAssistantRows('FAILED', ev.data.message)
        break
      }
    }
  }

  async function cancelCurrent() {
    if (!activeStreamId.value) {
      // Fallback:切断 fetch(服务端取消不到也会自然断开)
      abortFetch.value?.()
      return
    }
    try {
      await cancelStream(activeStreamId.value)
    } catch { /* ignore */ }
  }

  /** 用户对写类工具做出 Apply / Reject 决策。 */
  async function approveTool(toolCallId: string, approved: boolean) {
    if (!activeStreamId.value) return
    const call = messages.value.find(m => m.role === 'tool_call' && m.toolCallId === toolCallId)
    if (!call || call.approvalState !== 'pending') return
    // 乐观更新:避免后端慢回导致按钮可重复点击
    call.approvalState = approved ? 'approved' : 'rejected'
    try {
      await apiApproveTool(activeStreamId.value, toolCallId, approved)
    } catch {
      // 失败回滚,让用户能重试
      call.approvalState = 'pending'
    }
  }

  return {
    // sessions
    sessions, activeSessionId, pendingMode, sessionsLoading, initialized,
    init, reset, loadSessions, switchSession, startNewSession, deleteSession, ensureSession, renameSession,
    // messages
    messages, currentAssistantKey,
    // streaming
    streaming, streamingSessionId, activeStreamId, abortFetch,
    sendTurn, cancelCurrent, approveTool,
  }
})

function fromApi(m: AiMessageVO): UiMessage {
  return {
    id: m.id,
    role: m.role,
    status: m.status,
    content: m.content ?? '',
    errorMessage: m.errorMessage,
    toolCallId: m.toolCallId,
    toolName: m.toolName,
    toolInput: m.toolInput,
    toolOutput: m.toolOutput,
    toolSuccess: m.toolSuccess,
    model: m.model,
    promptTokens: m.promptTokens,
    completionTokens: m.completionTokens,
    costCents: m.costCents,
  }
}
