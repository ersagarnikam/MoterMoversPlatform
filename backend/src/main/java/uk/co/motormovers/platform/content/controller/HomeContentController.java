package uk.co.motormovers.platform.content.controller;

import java.util.Comparator;
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

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.controller.ContentController.ContentView;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.GuidanceArticle;
import uk.co.motormovers.platform.content.model.HomeActivity;
import uk.co.motormovers.platform.content.repository.GuidanceArticleRepository;
import uk.co.motormovers.platform.content.repository.HomeActivityRepository;
import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;

/**
 * Parent app content: home activities and guidance articles. Same sign-off and wording check as the
 * class sessions. Mapped under /api/admin (ADMIN only). Returns the same shape as the class content
 * list so one approval screen serves all three.
 */
@RestController
@RequestMapping("/api/admin")
public class HomeContentController {

    private final HomeActivityRepository activities;
    private final GuidanceArticleRepository articles;
    private final WordingGuard wordingGuard;
    private final CurrentUserResolver currentUser;

    public HomeContentController(HomeActivityRepository activities, GuidanceArticleRepository articles,
            WordingGuard wordingGuard, CurrentUserResolver currentUser) {
        this.activities = activities;
        this.articles = articles;
        this.wordingGuard = wordingGuard;
        this.currentUser = currentUser;
    }

    public record NewActivity(@NotNull ColourStage stage, @Min(1) int position, @NotBlank String title,
            @NotBlank String focus, @Min(1) int durationMinutes, String videoUrl, List<String> steps) {
    }

    public record NewArticle(@NotBlank String title, @NotBlank String summary, @NotBlank String body,
            ColourStage stage, @Min(1) int minutesToRead) {
    }

    @GetMapping("/home-activities")
    public List<ContentView> activities() {
        // Programme order (Red first), not the alphabetical order the database would give the stage names.
        return activities.findAll().stream()
                .sorted(Comparator.comparing(HomeActivity::getStage).thenComparing(HomeActivity::getPosition))
                .map(this::view).toList();
    }

    @PostMapping("/home-activities")
    @Transactional
    public ContentView createActivity(@Valid @RequestBody NewActivity r) {
        return view(activities.save(new HomeActivity(r.stage(), r.position(), r.title(), r.focus(),
                r.durationMinutes(), r.videoUrl(), r.steps() == null ? List.of() : r.steps())));
    }

    @PostMapping("/home-activities/{id}/approve")
    @Transactional
    public ContentView approveActivity(@PathVariable Long id) {
        HomeActivity a = findActivity(id);
        wordingGuard.requireClean(a.wording());
        a.review(ContentStatus.APPROVED, currentUser.current().email());
        return view(a);
    }

    @PostMapping("/home-activities/{id}/reject")
    @Transactional
    public ContentView rejectActivity(@PathVariable Long id) {
        HomeActivity a = findActivity(id);
        a.review(ContentStatus.REJECTED, currentUser.current().email());
        return view(a);
    }

    @GetMapping("/guidance")
    public List<ContentView> articles() {
        return articles.findAllByOrderByIdDesc().stream().map(this::view).toList();
    }

    @PostMapping("/guidance")
    @Transactional
    public ContentView createArticle(@Valid @RequestBody NewArticle r) {
        return view(articles.save(new GuidanceArticle(r.title(), r.summary(), r.body(), r.stage(),
                r.minutesToRead())));
    }

    @PostMapping("/guidance/{id}/approve")
    @Transactional
    public ContentView approveArticle(@PathVariable Long id) {
        GuidanceArticle a = findArticle(id);
        wordingGuard.requireClean(a.wording());
        a.review(ContentStatus.APPROVED, currentUser.current().email());
        return view(a);
    }

    @PostMapping("/guidance/{id}/reject")
    @Transactional
    public ContentView rejectArticle(@PathVariable Long id) {
        GuidanceArticle a = findArticle(id);
        a.review(ContentStatus.REJECTED, currentUser.current().email());
        return view(a);
    }

    private HomeActivity findActivity(Long id) {
        return activities.findById(id).orElseThrow(() -> new NotFoundException("Home activity", id));
    }

    private GuidanceArticle findArticle(Long id) {
        return articles.findById(id).orElseThrow(() -> new NotFoundException("Article", id));
    }

    private ContentView view(HomeActivity a) {
        return new ContentView(a.getId(), a.getStage(), a.label(), a.getTitle(), a.getFocus(), a.getDurationMinutes(),
                List.copyOf(a.getSteps()), a.getStatus(), a.getReviewedBy(), a.getReviewedAt(),
                wordingGuard.check(a.wording()));
    }

    private ContentView view(GuidanceArticle a) {
        return new ContentView(a.getId(), a.getStage(), a.label(), a.getTitle(), a.getSummary(),
                a.getMinutesToRead(), a.paragraphs(), a.getStatus(), a.getReviewedBy(), a.getReviewedAt(),
                wordingGuard.check(a.wording()));
    }
}
