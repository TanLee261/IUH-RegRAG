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

export type CompareWinner = 'A' | 'B' | 'TIE' | 'BOTH_BAD'

export type CompareStreamEvent =
  | { type: 'token'; slot: 'A' | 'B'; content: string }
  | { type: 'slot_error'; slot: 'A' | 'B'; message: string }
  | {
      type: 'compare_done'
      comparisonId: string
      sessionId: string
      userMessageId: string
      assistantMessageId: string
      answerA: string
      answerB: string
      sourcesA: Source[]
      sourcesB: Source[]
    }
  | { type: 'error'; message: string }

export interface CompareVoteResponse {
  comparisonId: string
  winner: CompareWinner
  modelA: string
  modelB: string
}

export interface ModelScoreSummary {
  model: string
  wins: number
  losses: number
  ties: number
  bothBad: number
  totalVoted: number
  winRate: number
}

export interface RecentComparisonItem {
  id: string
  question: string
  modelA: string
  modelB: string
  winner: CompareWinner | null
  votedAt: string | null
  createdAt: string
}

export interface ComparisonStatsResponse {
  totalComparisons: number
  totalVoted: number
  models: ModelScoreSummary[]
  recent: RecentComparisonItem[]
}

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
