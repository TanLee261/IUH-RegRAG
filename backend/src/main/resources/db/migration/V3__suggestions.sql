CREATE TABLE suggestions (
    id uuid PRIMARY KEY,
    topic varchar(100) NOT NULL
        CONSTRAINT suggestions_topic_not_blank CHECK (btrim(topic) <> ''),
    question text NOT NULL
        CONSTRAINT suggestions_question_not_blank CHECK (btrim(question) <> ''),
    display_order integer NOT NULL DEFAULT 0,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX suggestions_active_order_idx ON suggestions (is_active, display_order, created_at);

-- Seed initial suggested questions for the 4 chosen IUH regulatory topics
INSERT INTO suggestions (id, topic, question, display_order, is_active) VALUES
('b0000000-0000-0000-0000-000000000001', 'Tốt nghiệp', 'Điều kiện để được xét tốt nghiệp là gì?', 1, true),
('b0000000-0000-0000-0000-000000000002', 'Tốt nghiệp', 'Quy trình và hồ sơ đăng ký xét tốt nghiệp như thế nào?', 2, true),
('b0000000-0000-0000-0000-000000000003', 'Đăng ký học phần', 'Số tín chỉ tối thiểu và tối đa được phép đăng ký trong một học kỳ là bao nhiêu?', 3, true),
('b0000000-0000-0000-0000-000000000004', 'Đăng ký học phần', 'Quy định về học phần tiên quyết và học phần học trước tại IUH?', 4, true),
('b0000000-0000-0000-0000-000000000005', 'Học lại / Học cải thiện', 'Điều kiện để sinh viên được đăng ký học cải thiện điểm?', 5, true),
('b0000000-0000-0000-0000-000000000006', 'Học lại / Học cải thiện', 'Cách tính điểm học phần sau khi học lại và quy định học phí?', 6, true),
('b0000000-0000-0000-0000-000000000007', 'Đồ án / Khóa luận tốt nghiệp', 'Điều kiện về số tín chỉ tích lũy để được nhận đồ án/khóa luận tốt nghiệp?', 7, true),
('b0000000-0000-0000-0000-000000000008', 'Đồ án / Khóa luận tốt nghiệp', 'Thời gian thực hiện và điều kiện bảo vệ đồ án tốt nghiệp?', 8, true);
