package vn.edu.iuh.regrag.chat;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findBySession_IdOrderByCreatedAtAscIdAsc(UUID sessionId, Pageable pageable);
}
