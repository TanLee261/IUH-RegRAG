import type {
  ActiveVectorDocument,
  AuthResponse,
  ChatResponse,
  ChatSessionDetail,
  ChatSessionSummary,
  DocumentResponse,
  FeedbackRating,
  FeedbackResponse,
  IngestStatus,
  Suggestion,
  SystemHealth,
} from '../types'

const TOKEN_KEY = 'iuh_regrag_access_token'
const REFRESH_KEY = 'iuh_regrag_refresh_token'

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setStoredTokens(access: string, refresh: string): void {
  localStorage.setItem(TOKEN_KEY, access)
  localStorage.setItem(REFRESH_KEY, refresh)
}

export function clearStoredTokens(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {})
  const token = getStoredToken()

  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let response = await fetch(endpoint, {
    ...options,
    headers,
  })

  // Tự động refresh token nếu gặp 401 Unauthorized và có Refresh Token
  if (response.status === 401 && !endpoint.startsWith('/api/auth/')) {
    const refreshToken = localStorage.getItem(REFRESH_KEY)
    if (refreshToken) {
      try {
        const refreshRes = await fetch('/api/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        })
        if (refreshRes.ok) {
          const authData = await refreshRes.json()
          setStoredTokens(authData.accessToken, authData.refreshToken)
          headers.set('Authorization', `Bearer ${authData.accessToken}`)
          response = await fetch(endpoint, {
            ...options,
            headers,
          })
        } else {
          clearStoredTokens()
          window.dispatchEvent(new Event('auth:expired'))
        }
      } catch {
        clearStoredTokens()
        window.dispatchEvent(new Event('auth:expired'))
      }
    } else {
      clearStoredTokens()
      window.dispatchEvent(new Event('auth:expired'))
    }
  }

  if (!response.ok) {
    let errorDetail = `Lỗi yêu cầu: ${response.status} ${response.statusText}`
    try {
      const errorJson = await response.json()
      if (errorJson && errorJson.message) {
        errorDetail = errorJson.message
      }
    } catch {
      // Body not JSON
    }
    throw new Error(errorDetail)
  }

  if (response.status === 204) {
    return {} as T
  }

  return response.json()
}

// ── Auth APIs ──────────────────────────────────────────────────────────

export async function loginApi(email: string, password: string): Promise<AuthResponse> {
  const res = await request<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  setStoredTokens(res.accessToken, res.refreshToken)
  return res
}

export async function registerApi(email: string, password: string): Promise<AuthResponse> {
  const res = await request<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  setStoredTokens(res.accessToken, res.refreshToken)
  return res
}

export async function logoutApi(): Promise<void> {
  const refreshToken = localStorage.getItem(REFRESH_KEY) || ''
  try {
    if (refreshToken) {
      await request('/api/auth/logout', {
        method: 'POST',
        body: JSON.stringify({ refreshToken }),
      })
    }
  } finally {
    clearStoredTokens()
  }
}

// ── Chat APIs ──────────────────────────────────────────────────────────

export async function sendChatMessage(
  question: string,
  sessionId?: string
): Promise<ChatResponse> {
  return request<ChatResponse>('/api/chat', {
    method: 'POST',
    body: JSON.stringify({ question, sessionId }),
  })
}

export async function getChatSessions(
  page = 0,
  size = 20
): Promise<{ content: ChatSessionSummary[]; totalPages: number; totalElements: number }> {
  return request(`/api/chat/sessions?page=${page}&size=${size}`)
}

export async function getChatSessionDetail(
  sessionId: string,
  page = 0,
  size = 50
): Promise<ChatSessionDetail> {
  return request<ChatSessionDetail>(`/api/chat/sessions/${sessionId}?page=${page}&size=${size}`)
}

// ── Feedback APIs ──────────────────────────────────────────────────────

export async function submitFeedbackApi(
  messageId: string,
  rating: FeedbackRating,
  comment?: string
): Promise<FeedbackResponse> {
  return request<FeedbackResponse>('/api/feedback', {
    method: 'POST',
    body: JSON.stringify({ messageId, rating, comment }),
  })
}

export async function getFeedbackByMessage(messageId: string): Promise<FeedbackResponse | null> {
  try {
    return await request<FeedbackResponse>(`/api/feedback/messages/${messageId}`)
  } catch {
    return null
  }
}

// ── Suggestions APIs ───────────────────────────────────────────────────

export async function getSuggestions(): Promise<Suggestion[]> {
  return request<Suggestion[]>('/api/suggestions')
}

export async function getAllSuggestionsAdmin(): Promise<Suggestion[]> {
  return request<Suggestion[]>('/api/suggestions/all')
}

export async function createSuggestionAdmin(
  topic: string,
  question: string,
  displayOrder = 0,
  active = true
): Promise<Suggestion> {
  return request<Suggestion>('/api/suggestions', {
    method: 'POST',
    body: JSON.stringify({ topic, question, displayOrder, active }),
  })
}

export async function deleteSuggestionAdmin(id: string): Promise<void> {
  return request<void>(`/api/suggestions/${id}`, { method: 'DELETE' })
}

// ── System APIs ────────────────────────────────────────────────────────

export async function getSystemHealth(): Promise<SystemHealth> {
  return request<SystemHealth>('/api/system/health')
}

export async function getActiveVectorDocuments(): Promise<ActiveVectorDocument[]> {
  return request<ActiveVectorDocument[]>('/api/system/documents')
}

// ── Documents (Admin) APIs ─────────────────────────────────────────────

export async function getDocumentsAdmin(
  status?: IngestStatus,
  page = 0,
  size = 20
): Promise<{ content: DocumentResponse[]; totalPages: number; totalElements: number }> {
  const query = status ? `status=${status}&page=${page}&size=${size}` : `page=${page}&size=${size}`
  return request(`/api/documents?${query}`)
}

export async function uploadDocumentAdmin(formData: FormData): Promise<DocumentResponse> {
  return request<DocumentResponse>('/api/documents', {
    method: 'POST',
    body: formData,
  })
}

export async function deleteDocumentAdmin(id: string): Promise<void> {
  return request<void>(`/api/documents/${id}`, {
    method: 'DELETE',
  })
}

export async function updateDocumentStatusAdmin(
  id: string,
  status: IngestStatus,
  knowledgeDocumentId?: string,
  ingestError?: string
): Promise<DocumentResponse> {
  return request<DocumentResponse>(`/api/documents/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status, knowledgeDocumentId, ingestError }),
  })
}
