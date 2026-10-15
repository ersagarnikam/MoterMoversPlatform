package uk.co.motormovers.platform.content;

import java.time.Instant;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uk.co.motormovers.platform.common.ColourStage;
import uk.co.motormovers.platform.common.NotFoundException;
import uk.co.motormovers.platform.security.CurrentUserResolver;

/** Content library administration: Pat, Laura and the team. Mapped under /api/admin (ADMIN only). */
@RestController
@RequestMapping("/api/admin/content")
public class ContentController {

    private final ProgrammeSessionRepository sessions;
    private final WordingGuard wordingGuard;
    private final CurrentUserResolver currentUser;

    public ContentController(ProgrammeSessionRepository sessions, WordingGuard wordingGuard,
            CurrentUserResolver currentUser) {
        this.sessions = sessions;
        this.wordingGuard = wordingGuard;
        this.currentUser = currentUser;
    }

    public record ContentView(Long id, ColourStage stage, String label, String title, String focus,
            int durationMinutes, List<String> activities, ContentStatus status, String reviewedBy,
            Instant reviewedAt, List<WordingGuard.Violation> wordingIssues) {
    }

    public record NewSession(@NotNull ColourStage stage, @Min(1) int unit, @Min(1) int day, @NotBlank String title,
            @NotBlank String focus, @Min(1) int durationMinutes, String videoUrl, List<String> activities) {
    }

    @GetMapping
    public List<ContentView> list() {
        return sessions.findAllByOrderByStageAscUnitAscDayAsc().stream().map(this::view).toList();
    }

    @PostMapping
    @Transactional
    public ContentView create(@Valid @RequestBody NewSession request) {
        ProgrammeSession session = new ProgrammeSession(request.stage(), request.unit(), request.day(),
                request.title(), request.focus(), request.durationMinutes(), request.videoUrl(),
                request.activities() == null ? List.of() : request.activities());
        return view(sessions.save(session));
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public ContentView approve(@PathVariable Long id) {
        ProgrammeSession session = find(id);
        wordingGuard.requireClean(session.wording());
        session.review(ContentStatus.APPROVED, currentUser.current().email());
        return view(session);
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public ContentView reject(@PathVariable Long id) {
        ProgrammeSession session = find(id);
        session.review(ContentStatus.REJECTED, currentUser.current().email());
        return view(session);
    }

    private ProgrammeSession find(Long id) {
        return sessions.findById(id).orElseThrow(() -> new NotFoundException("Session", id));
    }

    private ContentView view(ProgrammeSession s) {
        return new ContentView(s.getId(), s.getStage(), s.label(), s.getTitle(), s.getFocus(),
                s.getDurationMinutes(), List.copyOf(s.getActivities()), s.getStatus(), s.getReviewedBy(),
                s.getReviewedAt(), wordingGuard.check(s.wording()));
    }
}
