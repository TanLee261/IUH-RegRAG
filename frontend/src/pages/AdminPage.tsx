import { useState, useEffect, useMemo } from 'react'
import { useAuth } from '../context/AuthContext'
import {
  getDocumentsAdmin,
  uploadDocumentAdmin,
  deleteDocumentAdmin,
  getActiveVectorDocuments,
  getSystemHealth,
  getAllSuggestionsAdmin,
  createSuggestionAdmin,
  deleteSuggestionAdmin,
} from '../services/api'
import type {
  ActiveVectorDocument,
  DocumentResponse,
  Suggestion,
  SystemHealth,
} from '../types'
import {
  IconBook,
  IconCheck,
  IconClose,
  IconTrash,
  IconUpload,
} from '../components/icons/Icons'

export default function AdminPage() {
  const { isAuthenticated, isAdmin } = useAuth()

  // State
  const [activeTab, setActiveTab] = useState<'docs' | 'vector' | 'suggestions'>('docs')
  const [health, setHealth] = useState<SystemHealth | null>(null)

  // Document tab state
  const [documents, setDocuments] = useState<DocumentResponse[]>([])
  const [uploadTitle, setUploadTitle] = useState('')
  const [uploadVersion, setUploadVersion] = useState('')
  const [uploadSourceUrl, setUploadSourceUrl] = useState('')
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [isUploading, setIsUploading] = useState(false)

  // Vector DB tab state & pagination
  const [vectorDocs, setVectorDocs] = useState<ActiveVectorDocument[]>([])
  const [vectorPage, setVectorPage] = useState(1)
  const [vectorSearch, setVectorSearch] = useState('')
  const VECTOR_PAGE_SIZE = 10

  const totalChunks = vectorDocs.reduce((sum, d) => sum + (d.chunkCount || 0), 0)

  // Vector DB search filter & pagination logic (placed before any conditional returns)
  const filteredVectorDocs = useMemo(() => {
    if (!vectorSearch.trim()) return vectorDocs
    const q = vectorSearch.toLowerCase()
    return vectorDocs.filter(
      (d) =>
        d.title.toLowerCase().includes(q) ||
        (d.documentGroup && d.documentGroup.toLowerCase().includes(q)) ||
        (d.soQd && d.soQd.toLowerCase().includes(q))
    )
  }, [vectorDocs, vectorSearch])

  const totalVectorPages = Math.max(1, Math.ceil(filteredVectorDocs.length / VECTOR_PAGE_SIZE))

  const pagedVectorDocs = useMemo(() => {
    const start = (vectorPage - 1) * VECTOR_PAGE_SIZE
    return filteredVectorDocs.slice(start, start + VECTOR_PAGE_SIZE)
  }, [filteredVectorDocs, vectorPage])

  const getVectorPageNumbers = () => {
    const pages: (number | string)[] = []
    if (totalVectorPages <= 9) {
      for (let i = 1; i <= totalVectorPages; i++) pages.push(i)
    } else {
      pages.push(1)
      if (vectorPage > 4) {
        pages.push('...')
      }
      const start = Math.max(2, vectorPage - 2)
      const end = Math.min(totalVectorPages - 1, vectorPage + 2)
      for (let i = start; i <= end; i++) {
        pages.push(i)
      }
      if (vectorPage < totalVectorPages - 3) {
        pages.push('...')
      }
      pages.push(totalVectorPages)
    }
    return pages
  }

  // Suggestions tab state
  const [suggestions, setSuggestions] = useState<Suggestion[]>([])
  const [newTopic, setNewTopic] = useState('Tốt nghiệp')
  const [newQuestion, setNewQuestion] = useState('')

  // Notifications
  const [feedbackMsg, setFeedbackMsg] = useState<{ text: string; type: 'success' | 'error' } | null>(
    null
  )

  useEffect(() => {
    if (isAuthenticated && isAdmin) {
      loadHealth()
      loadDocuments()
      loadVectorDocs()
      loadSuggestions()
    }
  }, [isAuthenticated, isAdmin])

  const loadHealth = async () => {
    try {
      const h = await getSystemHealth()
      setHealth(h)
    } catch {}
  }

  const loadDocuments = async () => {
    try {
      const res = await getDocumentsAdmin(undefined, 0, 50)
      setDocuments(res.content || [])
    } catch (err: any) {
      setFeedbackMsg({ text: err.message, type: 'error' })
    }
  }

  const loadVectorDocs = async () => {
    try {
      const res = await getActiveVectorDocuments()
      setVectorDocs(res || [])
    } catch {}
  }

  const loadSuggestions = async () => {
    try {
      const res = await getAllSuggestionsAdmin()
      setSuggestions(res || [])
    } catch {}
  }

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!uploadFile || !uploadTitle.trim()) {
      setFeedbackMsg({ text: 'Vui lòng chọn file và nhập tiêu đề.', type: 'error' })
      return
    }

    setIsUploading(true)
    setFeedbackMsg(null)

    const formData = new FormData()
    formData.append('file', uploadFile)
    formData.append('title', uploadTitle.trim())
    if (uploadVersion.trim()) formData.append('version', uploadVersion.trim())
    if (uploadSourceUrl.trim()) formData.append('sourceUrl', uploadSourceUrl.trim())

    try {
      await uploadDocumentAdmin(formData)
      setFeedbackMsg({
        text: 'Tải lên tài liệu thành công! Trạng thái: PENDING.',
        type: 'success',
      })
      setUploadTitle('')
      setUploadVersion('')
      setUploadSourceUrl('')
      setUploadFile(null)
      loadDocuments()
    } catch (err: any) {
      setFeedbackMsg({ text: err.message || 'Tải lên thất bại.', type: 'error' })
    } finally {
      setIsUploading(false)
    }
  }

  const handleDeleteDocument = async (id: string, title: string) => {
    if (!window.confirm(`Bạn có chắc muốn xóa văn bản: "${title}"?`)) return

    try {
      await deleteDocumentAdmin(id)
      setFeedbackMsg({ text: 'Đã xóa văn bản thành công.', type: 'success' })
      loadDocuments()
    } catch (err: any) {
      setFeedbackMsg({ text: err.message || 'Xóa thất bại.', type: 'error' })
    }
  }

  const handleCreateSuggestion = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newQuestion.trim()) return

    try {
      await createSuggestionAdmin(newTopic, newQuestion.trim())
      setNewQuestion('')
      setFeedbackMsg({ text: 'Thêm câu gợi ý thành công.', type: 'success' })
      loadSuggestions()
    } catch (err: any) {
      setFeedbackMsg({ text: err.message, type: 'error' })
    }
  }

  const handleDeleteSuggestion = async (id: string) => {
    try {
      await deleteSuggestionAdmin(id)
      loadSuggestions()
    } catch (err: any) {
      setFeedbackMsg({ text: err.message, type: 'error' })
    }
  }

  if (!isAuthenticated || !isAdmin) {
    return (
      <div className="admin-restricted">
        <div className="restricted-card">
          <IconBook />
          <h1>Khu vực Quản trị viên (ADMIN)</h1>
          <p>
            Trang này dành riêng cho quản trị viên Phòng Đào tạo để quản lý kho văn
            bản quy chế, theo dõi pipeline nạp vector DB và cấu hình câu hỏi gợi ý.
          </p>
          <div className="alert-box alert-box--warning">
            Vui lòng đăng nhập với tài khoản có quyền <strong>ADMIN</strong> để tiếp tục.
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="admin-container">
      <div className="admin-header">
        <div>
          <h1>Quản trị Quy chế & Tri thức RAG</h1>
          <p>Theo dõi văn bản quy chế, chỉ mục pgvector và câu hỏi gợi ý sinh viên.</p>
        </div>
      </div>

      {feedbackMsg && (
        <div className={`alert-box alert-box--${feedbackMsg.type}`}>
          <span>{feedbackMsg.text}</span>
          <button
            type="button"
            className="icon-button"
            onClick={() => setFeedbackMsg(null)}
          >
            <IconClose />
          </button>
        </div>
      )}

      {/* ── KPI Metric Cards ── */}
      <div className="kpi-grid">
        <div className="kpi-card">
          <span className="kpi-label">Tổng văn bản quản lý</span>
          <span className="kpi-value">{documents.length}</span>
          <span className="kpi-sub">Văn bản đã đăng ký</span>
        </div>

        <div className="kpi-card">
          <span className="kpi-label">Chunks trong Vector DB</span>
          <span className="kpi-value">{totalChunks.toLocaleString()}</span>
          <span className="kpi-sub">{vectorDocs.length} văn bản active</span>
        </div>

        <div className="kpi-card">
          <span className="kpi-label">Trạng thái AI Service</span>
          <span
            className={`kpi-badge ${
              health?.status === 'UP' ? 'kpi-badge--online' : 'kpi-badge--warning'
            }`}
          >
            {health?.status || 'Đang kiểm tra'}
          </span>
          <span className="kpi-sub">
            DB: {health?.components?.database?.status || 'N/A'} | AI:{' '}
            {health?.components?.aiService?.status || 'N/A'}
          </span>
        </div>
      </div>

      {/* ── Tabs Navigation ── */}
      <div className="admin-tabs">
        <button
          type="button"
          className={`tab-btn ${activeTab === 'docs' ? 'tab-btn--active' : ''}`}
          onClick={() => setActiveTab('docs')}
        >
          Văn bản quy chế ({documents.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'vector' ? 'tab-btn--active' : ''}`}
          onClick={() => setActiveTab('vector')}
        >
          Vector DB Corpus ({vectorDocs.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === 'suggestions' ? 'tab-btn--active' : ''}`}
          onClick={() => setActiveTab('suggestions')}
        >
          Câu hỏi gợi ý ({suggestions.length})
        </button>
      </div>

      {/* ── Tab 1: Documents Management ── */}
      {activeTab === 'docs' && (
        <div className="tab-pane">
          {/* Upload Card */}
          <div className="admin-card">
            <h2>Tải lên văn bản quy chế mới</h2>
            <form onSubmit={handleUpload} className="upload-form">
              <div className="form-row">
                <div className="form-group flex-2">
                  <label htmlFor="upload-title">Tiêu đề quy chế *</label>
                  <input
                    id="upload-title"
                    type="text"
                    required
                    placeholder="ví dụ: Quy chế đào tạo trình độ đại học theo hệ thống tín chỉ"
                    value={uploadTitle}
                    onChange={(e) => setUploadTitle(e.target.value)}
                  />
                </div>
                <div className="form-group flex-1">
                  <label htmlFor="upload-version">Số Quyết định / Phiên bản</label>
                  <input
                    id="upload-version"
                    type="text"
                    placeholder="ví dụ: 1234/QĐ-ĐHCN 2024"
                    value={uploadVersion}
                    onChange={(e) => setUploadVersion(e.target.value)}
                  />
                </div>
              </div>

              <div className="form-row">
                <div className="form-group flex-2">
                  <label htmlFor="upload-url">Link văn bản gốc trên portal PDT (tùy chọn)</label>
                  <input
                    id="upload-url"
                    type="url"
                    placeholder="https://pdt.iuh.edu.vn/quy-che..."
                    value={uploadSourceUrl}
                    onChange={(e) => setUploadSourceUrl(e.target.value)}
                  />
                </div>
                <div className="form-group flex-1">
                  <label htmlFor="upload-file">Chọn file PDF / DOCX *</label>
                  <input
                    id="upload-file"
                    type="file"
                    required
                    accept=".pdf,.docx,.txt"
                    onChange={(e) => setUploadFile(e.target.files?.[0] || null)}
                  />
                </div>
              </div>

              <button
                type="submit"
                className="button button--primary"
                disabled={isUploading}
              >
                <IconUpload />
                <span>{isUploading ? 'Đang tải lên...' : 'Tải lên & Khởi tạo Ingest'}</span>
              </button>
            </form>
          </div>

          {/* Documents Table */}
          <div className="admin-card">
            <h2>Danh sách văn bản quản lý ({documents.length})</h2>
            {documents.length === 0 ? (
              <p className="empty-text">Chưa có văn bản nào được tải lên.</p>
            ) : (
              <div className="table-responsive">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Tiêu đề văn bản</th>
                      <th>Số QĐ / Phiên bản</th>
                      <th>SHA-256 Checksum</th>
                      <th>Trạng thái Ingest</th>
                      <th>Ngày tạo</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {documents.map((doc) => (
                      <tr key={doc.id}>
                        <td>
                          <strong>{doc.title}</strong>
                          {doc.originalFilename && (
                            <small className="block-filename">
                              File: {doc.originalFilename}
                            </small>
                          )}
                        </td>
                        <td>{doc.version || '—'}</td>
                        <td>
                          <code className="code-hash">
                            {doc.contentSha256
                              ? doc.contentSha256.slice(0, 10) + '...'
                              : '—'}
                          </code>
                        </td>
                        <td>
                          <span className={`status-badge status-badge--${doc.ingestStatus.toLowerCase()}`}>
                            {doc.ingestStatus}
                          </span>
                        </td>
                        <td>{new Date(doc.createdAt).toLocaleDateString('vi-VN')}</td>
                        <td>
                          <button
                            type="button"
                            className="icon-button icon-button--danger"
                            title="Xóa văn bản"
                            onClick={() => handleDeleteDocument(doc.id, doc.title)}
                          >
                            <IconTrash />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── Tab 2: Vector DB Corpus ── */}
      {activeTab === 'vector' && (
        <div className="tab-pane">
          <div className="admin-card">
            <h2>Các văn bản đang nạp trong Vector DB (pgvector)</h2>
            <p className="card-desc">
              Dữ liệu được nạp bởi Python data pipeline và hiện đang được sử dụng để
              truy vấn câu trả lời cho sinh viên.
            </p>

            {vectorDocs.length === 0 ? (
              <p className="empty-text">Chưa có văn bản nào trong bảng knowledge_documents.</p>
            ) : (
              <>
                <div className="table-toolbar">
                  <div className="table-search">
                    <input
                      type="text"
                      placeholder="Tìm kiếm văn bản trong Vector DB..."
                      value={vectorSearch}
                      onChange={(e) => {
                        setVectorSearch(e.target.value)
                        setVectorPage(1)
                      }}
                      className="table-search-input"
                    />
                  </div>
                  <div className="table-summary-info">
                    Tổng số: <strong>{filteredVectorDocs.length}</strong> văn bản (Trang {vectorPage}/{totalVectorPages})
                  </div>
                </div>

                <div className="table-responsive">
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th style={{ width: '48px', textAlign: 'center' }}>STT</th>
                        <th>Tiêu đề trong Vector DB</th>
                        <th>Nhóm tài liệu</th>
                        <th>Số QĐ / Năm</th>
                        <th>Trạng thái pháp lý</th>
                        <th>Số Chunks</th>
                      </tr>
                    </thead>
                    <tbody>
                      {pagedVectorDocs.length === 0 ? (
                        <tr>
                          <td colSpan={6} style={{ textAlign: 'center', padding: '24px', color: 'var(--text-muted)' }}>
                            Không tìm thấy văn bản nào phù hợp với từ khóa "{vectorSearch}".
                          </td>
                        </tr>
                      ) : (
                        pagedVectorDocs.map((vd, idx) => (
                          <tr key={vd.id}>
                            <td style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                              {(vectorPage - 1) * VECTOR_PAGE_SIZE + idx + 1}
                            </td>
                            <td>
                              <strong>{vd.title}</strong>
                              {vd.source && (
                                <a
                                  href={vd.source}
                                  target="_blank"
                                  rel="noreferrer"
                                  className="source-link-small"
                                >
                                  Link nguồn
                                </a>
                              )}
                            </td>
                            <td>
                              <span className="badge badge--group">{vd.documentGroup}</span>
                            </td>
                            <td>
                              {vd.soQd || '—'} {vd.publicationYear ? `(${vd.publicationYear})` : ''}
                            </td>
                            <td>
                              <span className="badge badge--success">{vd.legalStatus}</span>
                            </td>
                            <td>
                              <strong>{vd.chunkCount}</strong> chunks
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>

                {/* ── Phân trang 10 mục / trang ── */}
                {totalVectorPages > 1 && (
                  <div className="pagination-wrapper">
                    <div className="pagination-info">
                      Hiển thị <strong>{(vectorPage - 1) * VECTOR_PAGE_SIZE + 1}</strong> –{' '}
                      <strong>{Math.min(vectorPage * VECTOR_PAGE_SIZE, filteredVectorDocs.length)}</strong>{' '}
                      trong số <strong>{filteredVectorDocs.length}</strong> văn bản
                    </div>
                    <div className="pagination-controls">
                      <button
                        type="button"
                        className="pagination-btn"
                        onClick={() => setVectorPage((p) => Math.max(1, p - 1))}
                        disabled={vectorPage === 1}
                        title="Trang trước"
                      >
                        &larr; Trước
                      </button>

                      {getVectorPageNumbers().map((num, i) =>
                        num === '...' ? (
                          <span key={`dots-${i}`} className="pagination-ellipsis">
                            …
                          </span>
                        ) : (
                          <button
                            key={`page-${num}`}
                            type="button"
                            className={`pagination-btn ${vectorPage === num ? 'active' : ''}`}
                            onClick={() => setVectorPage(Number(num))}
                          >
                            {num}
                          </button>
                        )
                      )}

                      <button
                        type="button"
                        className="pagination-btn"
                        onClick={() => setVectorPage((p) => Math.min(totalVectorPages, p + 1))}
                        disabled={vectorPage === totalVectorPages}
                        title="Trang sau"
                      >
                        Sau &rarr;
                      </button>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}

      {/* ── Tab 3: Suggestions Management ── */}
      {activeTab === 'suggestions' && (
        <div className="tab-pane">
          <div className="admin-card">
            <h2>Thêm câu hỏi gợi ý mới</h2>
            <form onSubmit={handleCreateSuggestion} className="suggestion-form">
              <div className="form-row">
                <div className="form-group flex-1">
                  <label htmlFor="sug-topic">Chủ đề</label>
                  <select
                    id="sug-topic"
                    value={newTopic}
                    onChange={(e) => setNewTopic(e.target.value)}
                  >
                    <option value="Tốt nghiệp">Tốt nghiệp</option>
                    <option value="Đăng ký học phần">Đăng ký học phần</option>
                    <option value="Học lại / Học cải thiện">Học lại / Học cải thiện</option>
                    <option value="Đồ án / Khóa luận tốt nghiệp">
                      Đồ án / Khóa luận tốt nghiệp
                    </option>
                    <option value="Cảnh báo học tập">Cảnh báo học tập</option>
                    <option value="Thang điểm">Thang điểm</option>
                  </select>
                </div>
                <div className="form-group flex-3">
                  <label htmlFor="sug-q">Nội dung câu hỏi *</label>
                  <input
                    id="sug-q"
                    type="text"
                    required
                    placeholder="ví dụ: Quy định về bảo vệ đồ án tốt nghiệp đợt 2?"
                    value={newQuestion}
                    onChange={(e) => setNewQuestion(e.target.value)}
                  />
                </div>
                <div className="form-group flex-btn">
                  <button type="submit" className="button button--primary">
                    <IconCheck />
                    <span>Thêm gợi ý</span>
                  </button>
                </div>
              </div>
            </form>
          </div>

          <div className="admin-card">
            <h2>Danh sách câu hỏi gợi ý ({suggestions.length})</h2>
            <div className="table-responsive">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Chủ đề</th>
                    <th>Câu hỏi gợi ý</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {suggestions.map((s) => (
                    <tr key={s.id}>
                      <td>
                        <span className="badge badge--group">{s.topic}</span>
                      </td>
                      <td>{s.question}</td>
                      <td>
                        <span
                          className={`badge ${
                            s.active ? 'badge--success' : 'badge--secondary'
                          }`}
                        >
                          {s.active ? 'Hiển thị' : 'Ẩn'}
                        </span>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="icon-button icon-button--danger"
                          title="Xóa câu hỏi"
                          onClick={() => handleDeleteSuggestion(s.id)}
                        >
                          <IconTrash />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
