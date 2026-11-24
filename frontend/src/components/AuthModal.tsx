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
    <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>{isRegister ? 'Đăng ký tài khoản' : 'Đăng nhập'}</h2>
          <button className="icon-button" onClick={onClose} aria-label="Đóng">
            <IconClose />
          </button>
        </div>

        {error && <div className="alert-box alert-box--error">{error}</div>}

        <form onSubmit={handleSubmit} className="auth-form">
          <div className="form-group">
            <label htmlFor="auth-email">Email tài khoản</label>
            <input
              id="auth-email"
              type="email"
              required
              placeholder="ví dụ: sinhvien@iuh.edu.vn"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label htmlFor="auth-password">Mật khẩu (tối thiểu 8 ký tự)</label>
            <input
              id="auth-password"
              type="password"
              required
              minLength={8}
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <button
            type="submit"
            className="button button--primary button--full"
            disabled={loading}
          >
            {loading ? 'Đang xử lý...' : isRegister ? 'Tạo tài khoản' : 'Đăng nhập'}
          </button>
        </form>

        <div className="modal-footer">
          {isRegister ? (
            <p>
              Đã có tài khoản?{' '}
              <button
                type="button"
                className="link-button"
                onClick={() => {
                  setIsRegister(false)
                  setError(null)
                }}
              >
                Đăng nhập ngay
              </button>
            </p>
          ) : (
            <p>
              Chưa có tài khoản?{' '}
              <button
                type="button"
                className="link-button"
                onClick={() => {
                  setIsRegister(true)
                  setError(null)
                }}
              >
                Đăng ký tài khoản mới
              </button>
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
