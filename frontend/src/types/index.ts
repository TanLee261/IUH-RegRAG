export type UserRole = 'STUDENT' | 'ADMIN'

export interface User {
  id: string
  email: string
  role: UserRole
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
}

export interface Source {
  chunk_id?: string
  document_id?: string
  title: string
  dieu?: string | null
  khoan?: string | null
  source: string
  snippet: string
  score: number
}

export interface ChatResponse {
  sessionId: string
  userMessageId: string
  assistantMessageId: string
  answer: string
  status: string
  sources: Source[]
}

export type ChatStreamEvent =
  | { type: 'token'; content: string }
  | ({ type: 'done' } & ChatResponse)
  | { type: 'error'; message: string }

export interface ChatSessionSummary {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface MessageDto {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  citations: Source[]
  createdAt: string
}

export interface ChatSessionDetail {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  messages: MessageDto[]
  page: number
  totalPages: number
  totalElements: number
}

export type FeedbackRating = 'HELPFUL' | 'NOT_HELPFUL'

export interface FeedbackResponse {
  id: string
  messageId: string
  rating: FeedbackRating
  comment?: string | null
  createdAt: string
  updatedAt: string
}

export interface Suggestion {
  id: string
  topic: string
  question: string
  displayOrder: number
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export type IngestStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'NEEDS_REVIEW' | 'FAILED'

export interface DocumentResponse {
  id: string
  title: string
  originalFilename?: string | null
  sourceUrl?: string | null
  storageKey?: string | null
  version?: string | null
  contentSha256?: string | null
  knowledgeDocumentId?: string | null
  ingestStatus: IngestStatus
  ingestError?: string | null
  uploadedById?: string | null
  createdAt: string
  updatedAt: string
}

export interface ActiveVectorDocument {
  id: string
  title: string
  source: string
  documentGroup: string
  soQd?: string | null
  publicationYear?: number | null
  legalStatus: string
  chunkCount: number
  updatedAt?: string | null
}

export interface SystemHealth {
  status: 'UP' | 'DEGRADED' | 'DOWN'
  timestamp: string
  components: {
    database: { status: string; details?: string | null }
    aiService: { status: string; details?: string | null }
  }
}

export interface ApiError {
  status: number
  error: string
  message: string
  path: string
  timestamp: string
  errors?: Array<{ field: string; message: string }>
}
