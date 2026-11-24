import React from 'react'
import type { Source } from '../types'

interface FormattedAnswerProps {
  content: string
  citations?: Source[]
  onSourceClick?: (locator: string) => void
}

/**
 * Tokenize a text string to render inline citations, bold markdown, and code spans.
 */
function renderInlineContent(
  text: string,
  citations?: Source[],
  onSourceClick?: (locator: string) => void
): React.ReactNode[] {
  // Regex matches:
  // 1. [Nguồn X: ... Điều Y, Khoản Z] or [Nguồn X: ...] or [Nguồn X]
  // 2. § Điều ...
  // 3. **bold text**
  // 4. `inline code`
  const tokenRegex = /(\[Nguồn\s*\d+(?::\s*[^\]]+)?\]|§\s*Điều\s*[\w\.\,]+(?:\s*K\.[\w\.\,]+)?|\*\*[^*]+\*\*|`[^`]+`)/gi
  const parts = text.split(tokenRegex)

  return parts.map((part, index) => {
    if (!part) return null

    // Direct § Điều ... badge
    const directBadgeMatch = part.match(/^§\s*(Điều\s*[\w\.\,]+(?:\s*K\.[\w\.\,]+)?)/i)
    if (directBadgeMatch) {
      const badgeLabel = directBadgeMatch[1].trim()
      return (
        <button
          key={`cit-direct-${index}`}
          type="button"
          className="citation-inline-badge"
          title={`Xem trích dẫn ${badgeLabel}`}
          onClick={(e) => {
            e.stopPropagation()
            onSourceClick?.(badgeLabel)
          }}
          aria-label={`Xem trích dẫn ${badgeLabel}`}
        >
          <span className="citation-badge-icon">§</span>
          <span className="citation-badge-text">{badgeLabel}</span>
        </button>
      )
    }

    // Citation badge [Nguồn ...]
    const citationMatch = part.match(/^\[Nguồn\s*(\d+)(?::\s*([^\]]+))?\]$/i)
    if (citationMatch) {
      const sourceIndex = parseInt(citationMatch[1], 10)
      const detail = (citationMatch[2] || '').trim()

      // Look up corresponding source in citations prop if available
      const source = citations && citations[sourceIndex - 1]

      let badgeLabel = ''
      let locator = ''

      if (source?.dieu) {
        badgeLabel = `Điều ${source.dieu}`
        if (source.khoan) {
          badgeLabel += `, K.${source.khoan}`
        }
        locator = `Điều ${source.dieu}`
      } else {
        // Fallback: search for "Điều XX" inside detail string
        const dieuExtract = detail.match(/Điều\s*([0-9a-zA-Z]+)/i)
        const khoanExtract = detail.match(/Khoản\s*([0-9a-zA-Z]+)/i)
        if (dieuExtract) {
          badgeLabel = `Điều ${dieuExtract[1]}`
          if (khoanExtract) {
            badgeLabel += `, K.${khoanExtract[1]}`
          }
          locator = `Điều ${dieuExtract[1]}`
        } else {
          badgeLabel = `Nguồn ${sourceIndex}`
          locator = source?.title || `Nguồn ${sourceIndex}`
        }
      }

      const fullTooltip = source
        ? `${source.title} (${source.dieu ? `Điều ${source.dieu}` : ''}${source.khoan ? `, Khoản ${source.khoan}` : ''})`
        : detail || `Nguồn tham chiếu số ${sourceIndex}`

      return (
        <button
          key={`cit-${index}`}
          type="button"
          className="citation-inline-badge"
          title={fullTooltip}
          onClick={(e) => {
            e.stopPropagation()
            onSourceClick?.(locator)
          }}
          aria-label={`Xem trích dẫn ${badgeLabel}`}
        >
          <span className="citation-badge-icon">§</span>
          <span className="citation-badge-text">{badgeLabel}</span>
        </button>
      )
    }

    // Bold text: **text**
    const boldMatch = part.match(/^\*\*([^*]+)\*\*$/)
    if (boldMatch) {
      return (
        <strong key={`bold-${index}`} className="formatted-bold">
          {boldMatch[1]}
        </strong>
      )
    }

    // Inline code: `code`
    const codeMatch = part.match(/^`([^`]+)`$/)
    if (codeMatch) {
      return (
        <code key={`code-${index}`} className="formatted-code">
          {codeMatch[1]}
        </code>
      )
    }

    // Plain text
    return <React.Fragment key={`text-${index}`}>{part}</React.Fragment>
  })
}

/**
 * FormattedAnswer: Splits AI responses into structured paragraphs, numbered lists,
 * and bullet sub-items with interactive citation badges.
 */
export default function FormattedAnswer({
  content,
  citations,
  onSourceClick,
}: FormattedAnswerProps) {
  if (!content) return null

  // Normalize newlines and split glued numbered list items if any (e.g. "xong. 1. Đạt...")
  const normalized = content
    .replace(/\r\n/g, '\n')
    .replace(/([.:;\]])\s+(\d+\.\s+)/g, '$1\n\n$2')
    .trim()

  const rawLines = normalized.split('\n')

  // Auto-collapse multi-line fragmented math symbols (<= 2 characters per line)
  const processedLines: string[] = []
  let shortBuf: string[] = []

  const flushShortBuf = () => {
    if (shortBuf.length === 0) return
    if (shortBuf.length >= 3) {
      const tokens: string[] = []
      let word = ''
      const opSet = new Set(['=', '+', '-', '⋅', '*', '/', '(', ')', '[', ']', '∑', '÷'])
      for (const item of shortBuf) {
        if (opSet.has(item)) {
          if (word) {
            tokens.push(word)
            word = ''
          }
          tokens.push(item)
        } else {
          word += item
        }
      }
      if (word) tokens.push(word)
      const formula = tokens.join(' ').replace(/([a-zA-ZÀ-ỹ])\1+/g, '$1')
      processedLines.push(`\`${formula}\``)
    } else {
      processedLines.push(...shortBuf)
    }
    shortBuf = []
  }

  for (const line of rawLines) {
    const trimmed = line.trim()
    if (trimmed.length > 0 && trimmed.length <= 2) {
      shortBuf.push(trimmed)
    } else {
      flushShortBuf()
      processedLines.push(line)
    }
  }
  flushShortBuf()

  const blocks: React.ReactNode[] = []
  let keyCounter = 0

  for (let i = 0; i < processedLines.length; i++) {
    const rawLine = processedLines[i].trim()
    if (!rawLine) continue

    // Formula or Code block line
    if (rawLine.startsWith('`') && rawLine.endsWith('`') && rawLine.length > 2) {
      blocks.push(
        <div key={`formula-${keyCounter++}`} className="formatted-formula-block">
          {renderInlineContent(rawLine, citations, onSourceClick)}
        </div>
      )
      continue
    }

    // 1. Line starts with citation badge (like Image 2):
    // e.g. "§ Điều 23, K.2" alone on a line, or "[Nguồn 1: Điều 23, Khoản 2] Để được xét..."
    const citationStartMatch = rawLine.match(/^[-*•]?\s*(\[Nguồn\s*\d+[^\]]*\]|§\s*Điều\s*[\w\.\,]+(?:\s*K\.[\w\.\,]+)?)\s*(.*)$/i)
    if (citationStartMatch) {
      const citToken = citationStartMatch[1]
      const restText = citationStartMatch[2].trim()
      blocks.push(
        <div key={`cit-block-${keyCounter++}`} className="formatted-citation-block">
          <div className="formatted-citation-standalone">
            {renderInlineContent(citToken, citations, onSourceClick)}
          </div>
          {restText && (
            <p className="formatted-paragraph">
              {renderInlineContent(restText, citations, onSourceClick)}
            </p>
          )}
        </div>
      )
      continue
    }

    // 2. Numbered Item / Section Banner (like Image 1):
    // e.g. "1. Miễn, giảm học phí và hỗ trợ chi phí học tập:"
    const numMatch = rawLine.match(/^(\d+)[\.\)]\s+(.*)$/)
    if (numMatch) {
      const num = numMatch[1]
      const body = numMatch[2]
      blocks.push(
        <div key={`section-${keyCounter++}`} className="formatted-section-header">
          <span className="formatted-section-num">{num}</span>
          <div className="formatted-section-title">
            {renderInlineContent(body, citations, onSourceClick)}
          </div>
        </div>
      )
      continue
    }

    // 3. Sub-bullet Item (like Image 1):
    // e.g. "• Sinh viên thuộc diện..." or "- Sinh viên..."
    const subMatch = rawLine.match(/^[-+*•]\s+(.*)$/)
    if (subMatch) {
      const subBody = subMatch[1]
      blocks.push(
        <div key={`sub-${keyCounter++}`} className="formatted-sub-item">
          <span className="formatted-sub-bullet">•</span>
          <div className="formatted-sub-body">
            {renderInlineContent(subBody, citations, onSourceClick)}
          </div>
        </div>
      )
      continue
    }

    // Section Heading: "### ..." or "## ..."
    const headingMatch = rawLine.match(/^#{1,4}\s+(.*)$/)
    if (headingMatch) {
      blocks.push(
        <h4 key={`head-${keyCounter++}`} className="formatted-heading">
          {renderInlineContent(headingMatch[1], citations, onSourceClick)}
        </h4>
      )
      continue
    }

    // Regular Paragraph
    blocks.push(
      <p key={`p-${keyCounter++}`} className="formatted-paragraph">
        {renderInlineContent(rawLine, citations, onSourceClick)}
      </p>
    )
  }

  return <div className="formatted-answer">{blocks}</div>
}

