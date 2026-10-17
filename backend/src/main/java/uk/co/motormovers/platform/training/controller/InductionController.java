package uk.co.motormovers.platform.training.controller;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.content.controller.ContentController.ContentView;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.model.InductionLesson;
import uk.co.motormovers.platform.training.repository.InductionLessonRepository;
import uk.co.motormovers.platform.training.service.InductionService;

/**
 * The one-hour induction for school staff, and its sign-off under /api/admin (ADMIN only) in the same
 * shape as the other content.
 */
@RestController
public class InductionController {

    private final InductionService induction;
    private final InductionLessonRepository lessons;
    private final WordingGuard wordingGuard;
    private final CurrentUserResolver currentUser;

    public InductionController(InductionService induction, InductionLessonRepository lessons,
            WordingGuard wordingGuard, CurrentUserResolver currentUser) {
        this.induction = induction;
        this.lessons = lessons;
        this.wordingGuard = wordingGuard;
        this.currentUser = currentUser;
    }

    public record NewLesson(@Min(1) int position, @NotBlank String title, @NotBlank String summary,
            @NotBlank String body, @Min(1) int minutes, String videoUrl) {
    }

    @GetMapping("/api/training/induction")
    public TrainingViews.InductionView mine() {
        return induction.mine();
    }

    @PostMapping("/api/training/induction/lessons/{lessonId}/complete")
    public TrainingViews.InductionView completeLesson(@PathVariable Long lessonId) {
        return induction.completeLesson(lessonId);
    }

    @GetMapping("/api/admin/induction")
    public List<ContentView> all() {
        return lessons.findAllByOrderByPosition().stream().map(this::view).toList();
    }

    @PostMapping("/api/admin/induction")
    @Transactional
    public ContentView create(@Valid @RequestBody NewLesson r) {
        return view(lessons.save(new InductionLesson(r.position(), r.title(), r.summary(), r.body(), r.minutes(),
                r.videoUrl())));
    }

    @PostMapping("/api/admin/induction/{id}/approve")
    @Transactional
    public ContentView approve(@PathVariable Long id) {
        InductionLesson l = find(id);
        wordingGuard.requireClean(l.wording());
        l.review(ContentStatus.APPROVED, currentUser.current().email());
        return view(l);
    }

    @PostMapping("/api/admin/induction/{id}/reject")
    @Transactional
    public ContentView reject(@PathVariable Long id) {
        InductionLesson l = find(id);
        l.review(ContentStatus.REJECTED, currentUser.current().email());
        return view(l);
    }

    private InductionLesson find(Long id) {
        return lessons.findById(id).orElseThrow(() -> new NotFoundException("Induction lesson", id));
    }

    private ContentView view(InductionLesson l) {
        return new ContentView(l.getId(), null, l.label(), l.getTitle(), l.getSummary(), l.getMinutes(),
                l.paragraphs(), l.getStatus(), l.getReviewedBy(), l.getReviewedAt(), wordingGuard.check(l.wording()));
    }
}
