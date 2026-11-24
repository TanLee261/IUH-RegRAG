import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <section className="placeholder-page" aria-labelledby="not-found-title">
      <p className="eyebrow">404</p>
      <h1 id="not-found-title">Khong tim thay trang</h1>
      <p>Duong dan ban truy cap khong ton tai hoac da duoc thay doi.</p>
      <Link className="button button--primary" to="/">Ve trang chu</Link>
    </section>
  )
}
