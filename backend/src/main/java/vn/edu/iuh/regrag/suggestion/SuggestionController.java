package vn.edu.iuh.regrag.suggestion;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("db")
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /**
     * Public endpoint: retrieve all active suggested questions.
     */
    @GetMapping
    public List<SuggestionResponse> list() {
        return suggestionService.getActiveSuggestions();
    }

    /**
     * Admin endpoint: retrieve all suggestions (including inactive ones).
     */
    @GetMapping("/all")
    public List<SuggestionResponse> listAll() {
        return suggestionService.getAllSuggestions();
    }

    /**
     * Admin endpoint: retrieve a single suggestion by id.
     */
    @GetMapping("/{id}")
    public SuggestionResponse get(@PathVariable UUID id) {
        return suggestionService.getSuggestion(id);
    }

    /**
     * Admin endpoint: create a new suggested question.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SuggestionResponse create(@Valid @RequestBody CreateSuggestionRequest request) {
        return suggestionService.createSuggestion(request);
    }

    /**
     * Admin endpoint: update an existing suggested question.
     */
    @PutMapping("/{id}")
    public SuggestionResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSuggestionRequest request) {
        return suggestionService.updateSuggestion(id, request);
    }

    /**
     * Admin endpoint: delete a suggested question.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        suggestionService.deleteSuggestion(id);
    }
}
