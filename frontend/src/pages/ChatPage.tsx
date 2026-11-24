import { useState, useEffect, useRef } from 'react'
import {
  getChatSessions,
  getChatSessionDetail,
  sendChatMessage,
  submitFeedbackApi,
  getSuggestions,
} from '../services/api'
import { useAuth } from '../context/AuthContext'
import type { ChatSessionSummary, Source, Suggestion } from '../types'
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

interface LocalMessage {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  citations: Source[]
  feedback?: 'HELPFUL' | 'NOT_HELPFUL' | null
}

export default function ChatPage() {
  const { isAuthenticated } = useAuth()

  // Chat State
  const [currentSessionId, setCurrentSessionId] = useState<string | undefined>(undefined)
  const [messages, setMessages] = useState<LocalMessage[]>([])
  const [inputValue, setInputValue] = useState('')
  const [isGenerating, setIsGenerating] = useState(false)
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

    // Add user message optimistically
    const userMsgId = 'temp-' + Date.now()
    const userMsg: LocalMessage = {
      id: userMsgId,
      role: 'USER',
      content: question,
      citations: [],
    }

    setMessages((prev) => [...prev, userMsg])
    setIsGenerating(true)

    try {
      const response = await sendChatMessage(question, currentSessionId)

      // Update session ID if newly created
      if (!currentSessionId && response.sessionId) {
        setCurrentSessionId(response.sessionId)
        if (isAuthenticated) {
          loadSessions()
        }
      }

      // Add assistant response
      const assistantMsg: LocalMessage = {
        id: response.assistantMessageId,
        role: 'ASSISTANT',
        content: response.answer,
        citations: response.sources || [],
      }

      setMessages((prev) => [...prev, assistantMsg])

      // Auto-preview sources if citations exist
      if (response.sources && response.sources.length > 0) {
        setActiveSources(response.sources)
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'Dịch vụ AI hiện không khả dụng. Vui lòng thử lại sau.')
    } finally {
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

  const openSourceDrawer = (sources: Source[], highlightLocator?: string) => {
    setActiveSources(sources)
    setActiveCitationHighlight(highlightLocator || null)
    setSourceDrawerOpen(true)
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
              {messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`message-row message-row--${msg.role.toLowerCase()}`}
                >
                  <div className="message-avatar">
                    {msg.role === 'USER' ? <IconUser /> : <IconBot />}
                  </div>

                  <div className="message-content-wrapper">
                    <div className="message-bubble">
                      {msg.role === 'USER' ? (
                        <div className="message-text">{msg.content}</div>
                      ) : (
                        <FormattedAnswer
                          content={msg.content}
                          citations={msg.citations}
                          onSourceClick={(locator) => openSourceDrawer(msg.citations, locator)}
                        />
                      )}

                      {/* Citation Pills on Assistant Message */}
                      {msg.role === 'ASSISTANT' && msg.citations && msg.citations.length > 0 && (
                        <div className="citation-pills-row">
                          <span className="citation-label">Căn cứ:</span>
                          {msg.citations.map((src, i) => {
                            const label = src.dieu ? `Điều ${src.dieu}` : src.title
                            return (
                              <button
                                key={i}
                                type="button"
                                className="citation-pill"
                                title={`Nhấp để xem trích đoạn: ${src.title}`}
                                onClick={() => openSourceDrawer(msg.citations, label)}
                              >
                                <IconSource />
                                <span>{label}</span>
                              </button>
                            )
                          })}
                        </div>
                      )}
                    </div>

                    {/* Actions on Assistant Message */}
                    {msg.role === 'ASSISTANT' && (
                      <div className="message-actions">
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
                          </button>
                        </div>

                        {msg.citations && msg.citations.length > 0 && (
                          <button
                            type="button"
                            className="button button--ghost button--small view-sources-btn"
                            onClick={() => openSourceDrawer(msg.citations)}
                          >
                            <IconSource />
                            <span>Xem {msg.citations.length} nguồn văn bản</span>
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              ))}

              {isGenerating && (
                <div className="message-row message-row--assistant">
                  <div className="message-avatar">
                    <IconBot />
                  </div>
                  <div className="message-bubble message-bubble--generating">
                    <div className="typing-indicator">
                      <span />
                      <span />
                      <span />
                    </div>
                    <span className="generating-text">
                      IUH-RegRAG đang tra cứu quy chế...
                    </span>
                  </div>
                </div>
              )}

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
