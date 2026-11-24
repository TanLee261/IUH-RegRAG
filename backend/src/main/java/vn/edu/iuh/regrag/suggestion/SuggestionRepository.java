package vn.edu.iuh.regrag.suggestion;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuggestionRepository extends JpaRepository<Suggestion, UUID> {

    List<Suggestion> findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc();

    List<Suggestion> findAllByOrderByDisplayOrderAscCreatedAtAsc();
}
