package vn.edu.iuh.regrag.chat;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelComparisonRepository extends JpaRepository<ModelComparison, UUID> {

    List<ModelComparison> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
