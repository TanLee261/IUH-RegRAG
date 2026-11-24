package vn.edu.iuh.regrag.suggestion;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("db")
public class SuggestionService {

    private final SuggestionRepository repository;

    public SuggestionService(SuggestionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> getActiveSuggestions() {
        return repository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc().stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> getAllSuggestions() {
        return repository.findAllByOrderByDisplayOrderAscCreatedAtAsc().stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SuggestionResponse getSuggestion(UUID id) {
        Suggestion suggestion = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggestion not found"));
        return SuggestionResponse.from(suggestion);
    }

    @Transactional
    public SuggestionResponse createSuggestion(CreateSuggestionRequest request) {
        int order = request.displayOrder() != null ? request.displayOrder() : 0;
        boolean active = request.active() != null ? request.active() : true;
        Suggestion suggestion = new Suggestion(request.topic(), request.question(), order, active);
        Suggestion saved = repository.save(suggestion);
        return SuggestionResponse.from(saved);
    }

    @Transactional
    public SuggestionResponse updateSuggestion(UUID id, UpdateSuggestionRequest request) {
        Suggestion suggestion = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggestion not found"));

        suggestion.setTopic(request.topic());
        suggestion.setQuestion(request.question());
        if (request.displayOrder() != null) {
            suggestion.setDisplayOrder(request.displayOrder());
        }
        if (request.active() != null) {
            suggestion.setActive(request.active());
        }

        Suggestion saved = repository.save(suggestion);
        return SuggestionResponse.from(saved);
    }

    @Transactional
    public void deleteSuggestion(UUID id) {
        Suggestion suggestion = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggestion not found"));
        repository.delete(suggestion);
    }
}
