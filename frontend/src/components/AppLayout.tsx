import { useState, useEffect } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { getSystemHealth } from '../services/api'
import AuthModal from './AuthModal'
import { IconShield, IconUser } from './icons/Icons'

const navClassName = ({ isActive }: { isActive: boolean }) =>
  isActive ? 'nav-link nav-link--active' : 'nav-link'

export default function AppLayout() {
  const { user, isAuthenticated, isAdmin, logout } = useAuth()
  const [authModalOpen, setAuthModalOpen] = useState(false)
  const [authModalMode, setAuthModalMode] = useState<'login' | 'register'>('login')
  const [systemOnline, setSystemOnline] = useState<boolean | null>(null)

  useEffect(() => {
    getSystemHealth()
      .then((h) => setSystemOnline(h.status === 'UP' || h.status === 'DEGRADED'))
      .catch(() => setSystemOnline(false))
  }, [])

  const openLogin = () => {
    setAuthModalMode('login')
    setAuthModalOpen(true)
  }

  const openRegister = () => {
    setAuthModalMode('register')
    setAuthModalOpen(true)
  }

  return (
    <div className="app-shell">
      {/* ── Top Site Header ── */}
      <header className="site-header">
        <div className="header-left">
          <NavLink className="brand" to="/" aria-label="IUH-RegRAG - Trang chủ">
            <span className="brand-mark" aria-hidden="true">
              IUH
            </span>
            <div className="brand-text">
              <strong>IUH-RegRAG</strong>
              <small>Hỏi đáp quy chế học vụ thông minh</small>
            </div>
          </NavLink>

          <div
            className="system-status-indicator"
            title={
              systemOnline === true
                ? 'Hệ thống AI & Vector DB đang trực tuyến'
                : systemOnline === false
                ? 'Hệ thống ngoại tuyến'
                : 'Đang kiểm tra kết nối'
            }
          >
            <span
              className={`status-dot ${
                systemOnline === true
                  ? 'status-dot--online'
                  : systemOnline === false
                  ? 'status-dot--offline'
                  : ''
              }`}
            />
            <span className="status-label">
              {systemOnline === true
                ? 'AI Sẵn sàng'
                : systemOnline === false
                ? 'Mất kết nối'
                : 'Đang kết nối'}
            </span>
          </div>
        </div>

        <nav className="main-nav" aria-label="Điều hướng chính">
          <NavLink className={navClassName} to="/" end>
            Trang chủ
          </NavLink>
          <NavLink className={navClassName} to="/chat">
            Hỏi đáp
          </NavLink>
          {isAdmin && (
            <NavLink className={navClassName} to="/admin">
              Quản trị <span className="badge badge--admin">ADMIN</span>
            </NavLink>
          )}
        </nav>

        <div className="header-right">
          {isAuthenticated && user ? (
            <div className="user-profile">
              <span className="user-avatar" title={user.email}>
                <IconUser />
              </span>
              <span className="user-email">{user.email}</span>
              <button
                type="button"
                className="button button--ghost button--small"
                onClick={logout}
              >
                Đăng xuất
              </button>
            </div>
          ) : (
            <div className="header-auth-buttons">
              <button
                type="button"
                className="button button--secondary button--small"
                onClick={openLogin}
              >
                Đăng nhập
              </button>
              <button
                type="button"
                className="button button--accent button--small"
                onClick={openRegister}
              >
                Đăng ký
              </button>
            </div>
          )}
        </div>
      </header>

      {/* ── Legal Disclaimer Banner ── */}
      <div className="disclaimer-banner" role="alert">
        <IconShield className="disclaimer-icon" />
        <span className="disclaimer-text">
          <strong>Lưu ý:</strong> Câu trả lời được tổng hợp tự động từ các quy chế
          chính thức của IUH. Sinh viên đối chiếu với văn bản gốc cho các thủ tục
          học vụ chính thức.
        </span>
      </div>

      {/* ── Main Content Area ── */}
      <main className="main-content">
        <Outlet />
      </main>

      {/* ── Auth Modal ── */}
      <AuthModal
        isOpen={authModalOpen}
        initialMode={authModalMode}
        onClose={() => setAuthModalOpen(false)}
      />
    </div>
  )
}
