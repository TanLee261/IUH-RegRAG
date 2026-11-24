import { Link } from 'react-router-dom'
import { IconBook, IconBot, IconShield } from '../components/icons/Icons'

export default function HomePage() {
  return (
    <div className="home-container">
      {/* ── Hero Section ── */}
      <section className="hero" aria-labelledby="home-title">
        <div className="hero-badge">
          <IconShield />
          <span>Hệ thống RAG tra cứu học vụ chuẩn xác IUH</span>
        </div>

        <h1 id="home-title" className="hero-title">
          Hỏi đáp quy chế đào tạo & học vụ <br />
          <span className="text-highlight">minh bạch, trích dẫn Điều/Khoản gốc.</span>
        </h1>

        <p className="hero-copy">
          IUH-RegRAG kết hợp mô hình ngôn ngữ lớn tiên tiến cùng hệ thống cơ sở tri thức
          chính thức của Trường Đại học Công nghiệp TP.HCM. Giải đáp tức thì về đăng ký
          học phần, điều kiện tốt nghiệp, học lại cải thiện và đồ án khóa luận.
        </p>

        <div className="hero-actions">
          <Link className="button button--primary button--large" to="/chat">
            <IconBot />
            <span>Bắt đầu hỏi đáp ngay</span>
          </Link>
          <Link className="button button--secondary button--large" to="/admin">
            <IconBook />
            <span>Khu vực quản trị quy chế</span>
          </Link>
        </div>
      </section>

      {/* ── Feature Grid ── */}
      <section className="features-section" aria-label="Tính năng nổi bật">
        <div className="feature-card">
          <div className="feature-icon feature-icon--blue">
            <IconBot />
          </div>
          <h3>Hỏi đáp tiếng Việt tự nhiên</h3>
          <p>
            Đặt câu hỏi thoải mái bằng ngôn ngữ đời thường, hệ thống tự động nhận diện
            thuật ngữ học vụ và tra cứu đúng trọng tâm.
          </p>
        </div>

        <div className="feature-card">
          <div className="feature-icon feature-icon--green">
            <IconShield />
          </div>
          <h3>Trích dẫn chính xác Điều / Khoản</h3>
          <p>
            Mọi câu trả lời đều đi kèm panel trích dẫn văn bản gốc, số Quyết định và trích
            đoạn pháp lý để sinh viên yên tâm đối chiếu.
          </p>
        </div>

        <div className="feature-card">
          <div className="feature-icon feature-icon--orange">
            <IconBook />
          </div>
          <h3>Kho văn bản cập nhật liên tục</h3>
          <p>
            Quản trị viên có thể cập nhật quy chế mới ban hành chỉ với một thao tác upload
            PDF, pipeline vector DB tự động lập chỉ mục.
          </p>
        </div>
      </section>
    </div>
  )
}
