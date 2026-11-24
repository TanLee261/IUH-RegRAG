import type { Source } from '../types'
import { IconClose, IconExternalLink, IconSource } from './icons/Icons'

interface SourceDrawerProps {
  isOpen: boolean
  onClose: () => void
  sources: Source[]
  activeCitation?: string | null
}

export default function SourceDrawer({
  isOpen,
  onClose,
  sources,
  activeCitation,
}: SourceDrawerProps) {
  if (!isOpen) return null

  return (
    <aside className="source-drawer" aria-labelledby="source-drawer-title">
      <div className="source-drawer__header">
        <div className="title-with-badge">
          <IconSource />
          <h2 id="source-drawer-title">Căn cứ trích dẫn quy chế</h2>
          <span className="badge badge--primary">{sources.length} nguồn</span>
        </div>
        <button
          className="icon-button"
          onClick={onClose}
          aria-label="Đóng bảng nguồn"
        >
          <IconClose />
        </button>
      </div>

      <div className="source-drawer__content">
        {sources.length === 0 ? (
          <div className="empty-notice">
            <p>Không có trích dẫn văn bản nào cho câu trả lời này.</p>
          </div>
        ) : (
          sources.map((src, idx) => {
            const isHighlight =
              activeCitation &&
              (activeCitation.includes(src.title) ||
                (src.dieu && activeCitation.includes(`Điều ${src.dieu}`)))

            return (
              <article
                key={src.chunk_id || idx}
                className={`source-card ${isHighlight ? 'source-card--highlight' : ''}`}
              >
                <div className="source-card__meta">
                  <span className="source-card__group">{src.title}</span>
                  {src.score > 0 && (
                    <span className="badge badge--score">
                      Độ khớp: {Math.round(src.score * 100)}%
                    </span>
                  )}
                </div>

                <div className="source-card__locator">
                  {src.dieu && <strong>Điều {src.dieu}</strong>}
                  {src.khoan && <span>, Khoản {src.khoan}</span>}
                </div>

                <blockquote className="source-card__snippet">
                  "{src.snippet}"
                </blockquote>

                {src.source && (
                  <div className="source-card__footer">
                    <a
                      href={src.source}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="source-link"
                    >
                      <span>Xem văn bản quy chế gốc</span>
                      <IconExternalLink />
                    </a>
                  </div>
                )}
              </article>
            )
          })
        )}
      </div>
    </aside>
  )
}
