import { useState, useEffect, useRef } from 'react'
import {
  getChatSessions,
  getChatSessionDetail,
  sendChatMessageStream,
  sendCompareChatStream,
  voteModelComparison,
  submitFeedbackApi,
  getSuggestions,
} from '../services/api'
import { useAuth } from '../context/AuthContext'
import type { ChatSessionSummary, CompareWinner, Source, Suggestion } from '../types'
import SourceDrawer from '../components/SourceDrawer'
import FormattedAnswer from '../components/FormattedAnswer'
import {
  IconBot,
  IconPlus,
  IconSend,
  IconSource,
  IconThumbDown,
  IconThumbUp,
  IconUser,
  IconMenu,
  IconClose,
} from '../components/icons/Icons'

interface CompareSlotState {
  content: string
  citations: Source[]
  error?: string | null
  model?: string | null
}

interface CompareState {
  comparisonId?: string
  slotA: CompareSlotState
  slotB: CompareSlotState
  winner?: CompareWinner | null
  isVoting?: boolean
}

interface LocalMessage {
  id: string
  role: 'USER' | 'ASSISTANT' | 'COMPARE'
  content: string
  citations: Source[]
  feedback?: 'HELPFUL' | 'NOT_HELPFUL' | null
  isStreaming?: boolean
  compare?: CompareState
}

export default function ChatPage() {
  const { isAuthenticated } = useAuth()

  // Chat State
  const [currentSessionId, setCurrentSessionId] = useState<string | undefined>(undefined)
  const [messages, setMessages] = useState<LocalMessage[]>([])
  const [inputValue, setInputValue] = useState('')
  const [isGenerating, setIsGenerating] = useState(false)
  const [compareMode, setCompareMode] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  // Sidebar & Sessions
  const [sessions, setSessions] = useState<ChatSessionSummary[]>([])
  const [sidebarOpen, setSidebarOpen] = useState(false)

  // Source Drawer
  const [sourceDrawerOpen, setSourceDrawerOpen] = useState(false)
  const [activeSources, setActiveSources] = useState<Source[]>([])
  const [activeCitationHighlight, setActiveCitationHighlight] = useState<string | null>(null)

  // Suggestions
  const [suggestions, setSuggestions] = useState<Suggestion[]>([])

  const messagesEndRef = useRef<HTMLDivElement>(null)
  const streamAbortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    return () => {
      streamAbortRef.current?.abort()
    }
  }, [])

  // Auto-scroll to bottom of messages
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages, isGenerating])

  // Load suggestions
  useEffect(() => {
    getSuggestions()
      .then((data) => setSuggestions(data))
      .catch(() => {})
  }, [])

  // Load sessions if authenticated
  useEffect(() => {
    if (isAuthenticated) {
      loadSessions()
    } else {
      setSessions([])
    }
  }, [isAuthenticated])

  const loadSessions = async () => {
    try {
      const res = await getChatSessions(0, 30)
      setSessions(res.content || [])
    } catch {
      // Ignore if session fetch fails
    }
  }

  const handleSelectSession = async (sessionId: string) => {
    streamAbortRef.current?.abort()
    try {
      setIsGenerating(true)
      setErrorMessage(null)
      const detail = await getChatSessionDetail(sessionId)
      setCurrentSessionId(detail.id)
      setMessages(
        detail.messages.map((m) => ({
          id: m.id,
          role: m.role,
          content: m.content,
          citations: m.citations || [],
        }))
      )
      setSidebarOpen(false)
    } catch (err: any) {
      setErrorMessage(err.message || 'Không thể tải lịch sử phiên chat.')
    } finally {
      setIsGenerating(false)
    }
  }

  const handleNewChat = () => {
    streamAbortRef.current?.abort()
    setIsGenerating(false)
    setCurrentSessionId(undefined)
    setMessages([])
    setErrorMessage(null)
    setSourceDrawerOpen(false)
    setSidebarOpen(false)
  }

  const handleSendMessage = async (textToSend?: string) => {
    const question = (textToSend || inputValue).trim()
    if (!question || isGenerating) return

    setInputValue('')
    setErrorMessage(null)

    const now = Date.now()
    const userMsg: LocalMessage = {
      id: 'temp-user-' + now,
      role: 'USER',
      content: question,
      citations: [],
    }

    if (compareMode) {
      const compareTempId = 'temp-compare-' + now
      const comparePlaceholder: LocalMessage = {
        id: compareTempId,
        role: 'COMPARE',
        content: '',
        citations: [],
        isStreaming: true,
        compare: {
          slotA: { content: '', citations: [] },
          slotB: { content: '', citations: [] },
        },
      }

      setMessages((prev) => [...prev, userMsg, comparePlaceholder])
      setIsGenerating(true)

      const controller = new AbortController()
      streamAbortRef.current = controller
      let receivedAny = false

      try {
        await sendCompareChatStream(
          question,
          currentSessionId,
          (event) => {
            if (event.type === 'token') {
              receivedAny = true
              setMessages((prev) =>
                prev.map((m) => {
                  if (m.id !== compareTempId || !m.compare) return m
                  const key = event.slot === 'A' ? 'slotA' : 'slotB'
                  return {
                    ...m,
                    compare: {
                      ...m.compare,
                      [key]: {
                        ...m.compare[key],
                        content: m.compare[key].content + event.content,
                      },
                    },
                  }
                })
              )
            } else if (event.type === 'slot_error') {
              receivedAny = true
              setMessages((prev) =>
                prev.map((m) => {
                  if (m.id !== compareTempId || !m.compare) return m
                  const key = event.slot === 'A' ? 'slotA' : 'slotB'
                  return {
                    ...m,
                    compare: {
                      ...m.compare,
                      [key]: {
                        ...m.compare[key],
                        error: event.message,
                        content: m.compare[key].content || event.message,
                      },
                    },
                  }
                })
              )
            } else if (event.type === 'compare_done') {
              if (!currentSessionId && event.sessionId) {
                setCurrentSessionId(event.sessionId)
                if (isAuthenticated) {
                  loadSessions()
                }
              }

              setMessages((prev) =>
                prev.map((m) =>
                  m.id === compareTempId
                    ? {
                        ...m,
                        id: event.comparisonId,
                        isStreaming: false,
                        compare: {
                          comparisonId: event.comparisonId,
                          slotA: {
                            content: event.answerA,
                            citations: event.sourcesA || [],
                          },
                          slotB: {
                            content: event.answerB,
                            citations: event.sourcesB || [],
                          },
                          winner: null,
                        },
                      }
                    : m
                )
              )
            }
          },
          controller.signal
        )
      } catch (err: any) {
        if (err?.name === 'AbortError') {
          return
        }
        if (!receivedAny) {
          setMessages((prev) => prev.filter((m) => m.id !== compareTempId))
        } else {
          setMessages((prev) =>
            prev.map((m) => (m.id === compareTempId ? { ...m, isStreaming: false } : m))
          )
        }
        setErrorMessage(err.message || 'Dịch vụ AI hiện không khả dụng. Vui lòng thử lại sau.')
      } finally {
        if (streamAbortRef.current === controller) {
          streamAbortRef.current = null
        }
        setIsGenerating(false)
      }
      return
    }

    const assistantTempId = 'temp-assistant-' + now
    const assistantPlaceholder: LocalMessage = {
      id: assistantTempId,
      role: 'ASSISTANT',
      content: '',
      citations: [],
      isStreaming: true,
    }

    setMessages((prev) => [...prev, userMsg, assistantPlaceholder])
    setIsGenerating(true)

    const controller = new AbortController()
    streamAbortRef.current = controller
    let receivedToken = false

    try {
      await sendChatMessageStream(
        question,
        currentSessionId,
        (event) => {
          if (event.type === 'token') {
            receivedToken = true
            setMessages((prev) =>
              prev.map((m) =>
                m.id === assistantTempId
                  ? { ...m, content: m.content + event.content }
                  : m
              )
            )
          } else if (event.type === 'done') {
            if (!currentSessionId && event.sessionId) {
              setCurrentSessionId(event.sessionId)
              if (isAuthenticated) {
                loadSessions()
              }
            }

            setMessages((prev) =>
              prev.map((m) =>
                m.id === assistantTempId
                  ? {
                      ...m,
                      id: event.assistantMessageId,
                      content: event.answer,
                      citations: event.sources || [],
                      isStreaming: false,
                    }
                  : m
              )
            )

            if (event.sources && event.sources.length > 0) {
              setActiveSources(event.sources)
            }
          }
        },
        controller.signal
      )
    } catch (err: any) {
      if (err?.name === 'AbortError') {
        return
      }
      if (!receivedToken) {
        setMessages((prev) => prev.filter((m) => m.id !== assistantTempId))
      } else {
        setMessages((prev) =>
          prev.map((m) => (m.id === assistantTempId ? { ...m, isStreaming: false } : m))
        )
      }
      setErrorMessage(err.message || 'Dịch vụ AI hiện không khả dụng. Vui lòng thử lại sau.')
    } finally {
      if (streamAbortRef.current === controller) {
        streamAbortRef.current = null
      }
      setIsGenerating(false)
    }
  }

  const handleFeedback = async (messageId: string, rating: 'HELPFUL' | 'NOT_HELPFUL') => {
    if (!isAuthenticated) {
      setErrorMessage('Vui lòng đăng nhập để gửi phản hồi đánh giá câu trả lời.')
      return
    }

    try {
      await submitFeedbackApi(messageId, rating)
      setMessages((prev) =>
        prev.map((m) => (m.id === messageId ? { ...m, feedback: rating } : m))
      )
    } catch (err: any) {
      setErrorMessage(err.message || 'Không thể lưu phản hồi.')
    }
  }

  const handleVoteComparison = async (comparisonId: string, winner: CompareWinner) => {
    if (!isAuthenticated) {
      setErrorMessage('Vui lòng đăng nhập để bình chọn mô hình.')
      return
    }

    setMessages((prev) =>
      prev.map((m) =>
        m.compare?.comparisonId === comparisonId
          ? { ...m, compare: { ...m.compare, isVoting: true } }
          : m
      )
    )

    try {
      const res = await voteModelComparison(comparisonId, winner)
      setMessages((prev) =>
        prev.map((m) =>
          m.compare?.comparisonId === comparisonId
            ? {
                ...m,
                compare: {
                  ...m.compare,
                  winner: res.winner,
                  isVoting: false,
                  slotA: { ...m.compare.slotA, model: res.modelA },
                  slotB: { ...m.compare.slotB, model: res.modelB },
                },
              }
            : m
        )
      )
    } catch (err: any) {
      setMessages((prev) =>
        prev.map((m) =>
          m.compare?.comparisonId === comparisonId
            ? { ...m, compare: { ...m.compare, isVoting: false } }
            : m
        )
      )
      setErrorMessage(err.message || 'Không thể lưu đánh giá mô hình.')
    }
  }

  const openSourceDrawer = (sources: Source[], highlightLocator?: string) => {
    setActiveSources(sources)
    setActiveCitationHighlight(highlightLocator || null)
    setSourceDrawerOpen(true)
  }

  const renderAssistantBody = (
    content: string,
    citations: Source[],
    isStreaming?: boolean,
    badge?: React.ReactNode,
    footerExtra?: React.ReactNode
  ) => {
    const seen = new Set<string>()
    const uniqueCitations = citations.filter((src) => {
      const label = src.dieu ? `Điều ${src.dieu}` : src.title
      if (seen.has(label)) return false
      seen.add(label)
      return true
    })

    return (
      <div className="message-row message-row--assistant">
        <div className="message-avatar">
          <IconBot />
        </div>

        <div className="message-content-wrapper">
          {badge}
          <div className="message-bubble">
            {isStreaming && !content ? (
              <div className="message-bubble--generating">
                <div className="typing-indicator">
                  <span />
                  <span />
                  <span />
                </div>
                <span className="generating-text">IUH-RegRAG đang tra cứu quy chế...</span>
              </div>
            ) : (
              <>
                <FormattedAnswer
                  content={content}
                  citations={citations}
                  onSourceClick={(locator) => openSourceDrawer(citations, locator)}
                />
                {isStreaming && <span className="streaming-cursor" aria-hidden="true" />}
              </>
            )}

            {!isStreaming && uniqueCitations.length > 0 && (
              <div className="citation-pills-row">
                <span className="citation-label">Căn cứ:</span>
                {uniqueCitations.map((src, i) => {
                  const label = src.dieu ? `Điều ${src.dieu}` : src.title
                  return (
                    <button
                      key={i}
                      type="button"
                      className="citation-pill"
                      title={`Nhấp để xem trích đoạn: ${src.title}`}
                      onClick={() => openSourceDrawer(citations, label)}
                    >
                      <IconSource />
                      <span>{label}</span>
                    </button>
                  )
                })}
              </div>
            )}
          </div>

          {!isStreaming && (citations.length > 0 || footerExtra) && (
            <div className="message-actions">
              {citations.length > 0 && (
                <button
                  type="button"
                  className="action-pill-btn action-pill-btn--sources"
                  onClick={() => openSourceDrawer(citations)}
                >
                  <IconSource />
                  <span>Xem nguồn trích dẫn ({citations.length})</span>
                </button>
              )}
              {footerExtra}
            </div>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="chat-layout">
      {/* ── Mobile Sidebar Toggle ── */}
      <button
        type="button"
        className="mobile-sidebar-toggle"
        onClick={() => setSidebarOpen(!sidebarOpen)}
        aria-label="Mở lịch sử chat"
      >
        {sidebarOpen ? <IconClose /> : <IconMenu />}
        <span>Lịch sử</span>
      </button>

      {/* ── Sidebar: Chat History ── */}
      <aside className={`chat-sidebar ${sidebarOpen ? 'chat-sidebar--open' : ''}`}>
        <div className="sidebar-top">
          <button
            type="button"
            className="button button--primary button--full"
            onClick={handleNewChat}
          >
            <IconPlus />
            <span>Đoạn chat mới</span>
          </button>
        </div>

        <div className="sidebar-sessions">
          <div className="sidebar-section-title">Lịch sử hội thoại</div>
          {!isAuthenticated ? (
            <div className="sidebar-hint">
              <p>Đăng nhập để lưu và xem lại các phiên hỏi đáp trước đây.</p>
            </div>
          ) : sessions.length === 0 ? (
            <div className="sidebar-hint">
              <p>Chưa có phiên chat nào.</p>
            </div>
          ) : (
            <ul className="session-list">
              {sessions.map((s) => (
                <li key={s.id}>
                  <button
                    type="button"
                    className={`session-item ${
                      s.id === currentSessionId ? 'session-item--active' : ''
                    }`}
                    onClick={() => handleSelectSession(s.id)}
                  >
                    <span className="session-title">{s.title || 'Phiên hỏi đáp'}</span>
                    <small className="session-date">
                      {new Date(s.updatedAt).toLocaleDateString('vi-VN')}
                    </small>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </aside>

      {/* ── Main Chat Conversation Area ── */}
      <div className="chat-main">
        <div className="chat-mode-bar">
          <div className="chat-mode-tabs" role="tablist" aria-label="Chế độ hỏi đáp">
            <button
              type="button"
              role="tab"
              aria-selected={!compareMode}
              className={`mode-tab ${!compareMode ? 'mode-tab--active' : ''}`}
              onClick={() => setCompareMode(false)}
              disabled={isGenerating}
            >
              Hỏi đáp chuẩn
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={compareMode}
              className={`mode-tab ${compareMode ? 'mode-tab--active' : ''}`}
              onClick={() => setCompareMode(true)}
              disabled={isGenerating}
            >
              So sánh 2 Model (Blind A/B)
            </button>
          </div>
          <span className="chat-mode-hint">
            {compareMode
              ? '2 mô hình chạy song song ẩn danh để bạn chấm điểm khách quan'
              : 'Trả lời trực tiếp với trích dẫn Điều/Khoản'}
          </span>
        </div>

        {errorMessage && (
          <div className="chat-error-banner" role="alert">
            <span>{errorMessage}</span>
            <button
              type="button"
              className="icon-button"
              onClick={() => setErrorMessage(null)}
              aria-label="Đóng cảnh báo"
            >
              <IconClose />
            </button>
          </div>
        )}

        <div className="chat-scroll-area">
          {messages.length === 0 ? (
            /* ── Welcome & Suggestion View ── */
            <div className="chat-welcome">
              <div className="welcome-avatar">
                <IconBot />
              </div>
              <h1>Xin chào! Bạn cần tra cứu quy chế nào của IUH hôm nay?</h1>
              <p className="welcome-desc">
                Hệ thống trả lời thông minh trích xuất từ quy chế đào tạo tín chỉ, điều kiện
                tốt nghiệp, đăng ký học phần và các văn bản học vụ chính thức của Trường Đại
                học Công nghiệp TP.HCM.
              </p>

              {suggestions.length > 0 && (
                <div className="suggestions-container">
                  <div className="suggestions-header">
                    <span>💡 Câu hỏi gợi ý theo chủ đề:</span>
                  </div>
                  <div className="suggestions-grid">
                    {suggestions.map((s) => (
                      <button
                        key={s.id}
                        type="button"
                        className="suggestion-chip"
                        onClick={() => handleSendMessage(s.question)}
                      >
                        <span className="chip-topic">{s.topic}</span>
                        <span className="chip-question">{s.question}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : (
            /* ── Message Thread ── */
            <div className="messages-thread">
              {messages.map((msg) => {
                if (msg.role === 'COMPARE' && msg.compare) {
                  const cmp = msg.compare
                  const slots: Array<{ key: 'A' | 'B'; label: string; data: CompareSlotState }> = [
                    { key: 'A', label: 'Phương án A', data: cmp.slotA },
                    { key: 'B', label: 'Phương án B', data: cmp.slotB },
                  ]
                  return (
                    <div key={msg.id} className="compare-row">
                      <div className="compare-grid">
                        {slots.map(({ key, label, data }) => {
                          const isWinner = cmp.winner === key
                          return (
                            <div
                              key={key}
                              className={`compare-pane ${isWinner ? 'compare-pane--winner' : ''}`}
                            >
                              {renderAssistantBody(
                                data.content,
                                data.citations,
                                msg.isStreaming,
                                <div className="compare-slot-label-row">
                                  <span className="compare-slot-title">{label}</span>
                                  {data.model ? (
                                    <span className="compare-model-badge">{data.model}</span>
                                  ) : (
                                    <span className="compare-blind-badge">Ẩn danh</span>
                                  )}
                                </div>
                              )}
                            </div>
                          )
                        })}
                      </div>

                      {!msg.isStreaming && cmp.comparisonId && (
                        <div className="compare-vote-bar">
                          {cmp.winner ? (
                            <div className="compare-vote-result">
                              <span>
                                Đã ghi nhận bình chọn:{' '}
                                <strong>
                                  {cmp.winner === 'A'
                                    ? `Phương án A (${cmp.slotA.model})`
                                    : cmp.winner === 'B'
                                    ? `Phương án B (${cmp.slotB.model})`
                                    : cmp.winner === 'TIE'
                                    ? 'Hai bên tương đương'
                                    : 'Cả hai chưa tốt'}
                                </strong>
                              </span>
                            </div>
                          ) : (
                            <>
                              <span className="compare-vote-label">
                                Phương án nào trả lời chính xác và dễ hiểu hơn?
                              </span>
                              <div className="compare-vote-buttons">
                                <button
                                  type="button"
                                  className="vote-btn"
                                  disabled={cmp.isVoting}
                                  onClick={() => handleVoteComparison(cmp.comparisonId!, 'A')}
                                >
                                  Phương án A tốt hơn
                                </button>
                                <button
                                  type="button"
                                  className="vote-btn"
                                  disabled={cmp.isVoting}
                                  onClick={() => handleVoteComparison(cmp.comparisonId!, 'B')}
                                >
                                  Phương án B tốt hơn
                                </button>
                                <button
                                  type="button"
                                  className="vote-btn vote-btn--neutral"
                                  disabled={cmp.isVoting}
                                  onClick={() => handleVoteComparison(cmp.comparisonId!, 'TIE')}
                                >
                                  Tương đương
                                </button>
                                <button
                                  type="button"
                                  className="vote-btn vote-btn--bad"
                                  disabled={cmp.isVoting}
                                  onClick={() => handleVoteComparison(cmp.comparisonId!, 'BOTH_BAD')}
                                >
                                  Cả hai chưa tốt
                                </button>
                              </div>
                            </>
                          )}
                        </div>
                      )}
                    </div>
                  )
                }

                if (msg.role === 'ASSISTANT') {
                  return (
                    <div key={msg.id}>
                      {renderAssistantBody(
                        msg.content,
                        msg.citations,
                        msg.isStreaming,
                        undefined,
                        <div className="feedback-buttons">
                          <button
                            type="button"
                            className={`action-btn ${
                              msg.feedback === 'HELPFUL' ? 'action-btn--active-like' : ''
                            }`}
                            title="Câu trả lời hữu ích"
                            onClick={() => handleFeedback(msg.id, 'HELPFUL')}
                          >
                            <IconThumbUp filled={msg.feedback === 'HELPFUL'} />
                            <span>Hữu ích</span>
                          </button>
                          <button
                            type="button"
                            className={`action-btn ${
                              msg.feedback === 'NOT_HELPFUL' ? 'action-btn--active-dislike' : ''
                            }`}
                            title="Chưa chính xác hoặc thiếu căn cứ"
                            onClick={() => handleFeedback(msg.id, 'NOT_HELPFUL')}
                          >
                            <IconThumbDown filled={msg.feedback === 'NOT_HELPFUL'} />
                            <span>Chưa tốt</span>
                          </button>
                        </div>
                      )}
                    </div>
                  )
                }

                return (
                <div
                  key={msg.id}
                  className={`message-row message-row--${msg.role.toLowerCase()}`}
                >
                  <div className="message-avatar">
                    <IconUser />
                  </div>

                  <div className="message-content-wrapper">
                    <div className="message-bubble">
                      <div className="message-text">{msg.content}</div>
                    </div>
                  </div>
                </div>
                )
              })}

              <div ref={messagesEndRef} />
            </div>
          )}
        </div>

        {/* ── Floating Input Box ── */}
        <div className="chat-input-wrapper">
          <form
            className="chat-input-form"
            onSubmit={(e) => {
              e.preventDefault()
              handleSendMessage()
            }}
          >
            <input
              type="text"
              className="chat-input"
              placeholder="Đặt câu hỏi về quy chế học vụ, tín chỉ, tốt nghiệp..."
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              disabled={isGenerating}
            />
            <button
              type="submit"
              className="chat-send-btn"
              disabled={!inputValue.trim() || isGenerating}
              aria-label="Gửi câu hỏi"
            >
              <IconSend />
            </button>
          </form>
          <div className="chat-input-footnote">
            IUH-RegRAG có thể mắc lỗi. Vui lòng đối chiếu các văn bản quy chế chính thức của
            Trường ĐH Công nghiệp TP.HCM.
          </div>
        </div>
      </div>

      {/* ── Citations Drawer Panel (Right on desktop, Bottom sheet on mobile) ── */}
      <SourceDrawer
        isOpen={sourceDrawerOpen}
        onClose={() => setSourceDrawerOpen(false)}
        sources={activeSources}
        activeCitation={activeCitationHighlight}
      />
    </div>
  )
}
