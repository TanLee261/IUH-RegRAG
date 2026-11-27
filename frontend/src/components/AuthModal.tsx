import { useState, useEffect } from 'react'
import { loginApi, registerApi } from '../services/api'
import { useAuth } from '../context/AuthContext'
import { IconClose } from './icons/Icons'

interface AuthModalProps {
  isOpen: boolean
  initialMode?: 'login' | 'register'
  onClose: () => void
}

export default function AuthModal({ isOpen, initialMode = 'login', onClose }: AuthModalProps) {
  const { loginWithToken } = useAuth()
  const [isRegister, setIsRegister] = useState(initialMode === 'register')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (isOpen) {
      setIsRegister(initialMode === 'register')
      setError(null)
    }
  }, [isOpen, initialMode])

  if (!isOpen) return null

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      let res
      if (isRegister) {
        res = await registerApi(email, password)
      } else {
        res = await loginApi(email, password)
      }
      localStorage.setItem('regrag_user_email', email)
      loginWithToken(res.accessToken)
      onClose()
    } catch (err: any) {
      setError(err.message || 'Xác thực thất bại. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="modal-overlay auth-overlay-blur" onClick={onClose} role="dialog" aria-modal="true">
      <div className="auth-uiverse-card" onClick={(e) => e.stopPropagation()}>
        <button className="auth-close-btn" onClick={onClose} aria-label="Đóng">
          <IconClose />
        </button>

        <div className="auth-brand-header">
          <div className="auth-brand-badge">IUH RegRAG</div>
          <h2 className="auth-title">
            {isRegister ? 'Tạo tài khoản mới' : 'Chào mừng trở lại'}
          </h2>
          <p className="auth-subtitle">
            {isRegister
              ? 'Đăng ký để lưu lịch sử tra cứu quy chế học vụ'
              : 'Đăng nhập để tiếp tục tra cứu quy chế IUH'}
          </p>
        </div>

        <div className="auth-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={!isRegister}
            className={`auth-tab ${!isRegister ? 'auth-tab--active' : ''}`}
            onClick={() => {
              setIsRegister(false)
              setError(null)
            }}
          >
            Đăng nhập
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={isRegister}
            className={`auth-tab ${isRegister ? 'auth-tab--active' : ''}`}
            onClick={() => {
              setIsRegister(true)
              setError(null)
            }}
          >
            Đăng ký
          </button>
        </div>

        {error && <div className="alert-box alert-box--error">{error}</div>}

        <form onSubmit={handleSubmit} className="auth-uiverse-form">
          <div className="auth-field">
            <label htmlFor="auth-email" className="auth-label">Email tài khoản</label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect width="20" height="16" x="2" y="4" rx="2" />
                  <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
                </svg>
              </span>
              <input
                id="auth-email"
                type="email"
                required
                className="auth-input"
                placeholder="sinhvien@iuh.edu.vn"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
          </div>

          <div className="auth-field">
            <label htmlFor="auth-password" className="auth-label">
              Mật khẩu <span className="auth-label-hint">(tối thiểu 8 ký tự)</span>
            </label>
            <div className="auth-input-wrap">
              <span className="auth-input-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
                  <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                </svg>
              </span>
              <input
                id="auth-password"
                type={showPassword ? 'text' : 'password'}
                required
                minLength={8}
                className="auth-input auth-input--password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <button
                type="button"
                className="auth-eye-btn"
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              >
                {showPassword ? (
                  <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24" />
                    <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68" />
                    <path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61" />
                    <line x1="2" x2="22" y1="2" y2="22" />
                  </svg>
                ) : (
                  <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
                    <circle cx="12" cy="12" r="3" />
                  </svg>
                )}
              </button>
            </div>
          </div>

          <button
            type="submit"
            className="auth-submit-btn"
            disabled={loading}
          >
            {loading ? 'Đang xử lý...' : isRegister ? 'Tạo tài khoản' : 'Đăng nhập'}
          </button>
        </form>
      </div>
    </div>
  )
}
