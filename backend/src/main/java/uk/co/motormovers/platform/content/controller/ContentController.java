package uk.co.motormovers.platform.content.controller;

import java.time.Instant;
import java.util.ArrayList;
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
import uk.co.motormovers.platform.common.storage.FileLinks;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.ProgrammeSession;
import uk.co.motormovers.platform.content.repository.ProgrammeSessionRepository;
import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;

/** Content library administration: Pat, Laura and the team. Mapped under /api/admin (ADMIN only). */
@RestController
@RequestMapping("/api/admin/content")
public class ContentController {

    private final ProgrammeSessionRepository sessions;
    private final WordingGuard wordingGuard;
    private final CurrentUserResolver currentUser;
    private final FileLinks files;
    private final AccessPolicy access;

    public ContentController(ProgrammeSessionRepository sessions, WordingGuard wordingGuard,
            CurrentUserResolver currentUser, FileLinks files, AccessPolicy access) {
        this.sessions = sessions;
        this.wordingGuard = wordingGuard;
        this.currentUser = currentUser;
        this.files = files;
        this.access = access;
    }

    public record ContentView(Long id, ColourStage stage, String label, String title, String focus,
            int durationMinutes, List<String> activities, ContentStatus status, String reviewedBy,
            Instant reviewedAt, List<WordingGuard.Violation> wordingIssues, String fileLink, Integer unit) {

        /** Every kind of content but class sessions, which have a module. */
        public ContentView(Long id, ColourStage stage, String label, String title, String focus, int durationMinutes,
                List<String> activities, ContentStatus status, String reviewedBy, Instant reviewedAt,
                List<WordingGuard.Violation> wordingIssues, String fileLink) {
            this(id, stage, label, title, focus, durationMinutes, activities, status, reviewedBy, reviewedAt,
                    wordingIssues, fileLink, null);
        }
    }

    public record NewSession(@NotNull ColourStage stage, @Min(1) int unit, @Min(1) int day, @NotBlank String title,
            @NotBlank String focus, @Min(1) int durationMinutes, String videoUrl, String videoKey,
            List<String> activities) {
    }

    /** A whole colour, or one module of it when unit is given. */
    public record ApproveAll(@NotNull ColourStage stage, @Min(1) Integer unit) {
    }

    /** A draft left as it is because it failed the wording check. */
    public record LeftAsDraft(Long id, String label, String title, List<WordingGuard.Violation> wordingIssues) {
    }

    public record ApprovedAll(int approved, List<LeftAsDraft> left) {
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
        session.setVideoKey(files.requireKey(request.videoKey()));
        return view(sessions.save(session));
    }

    /** Approves every draft of a colour or module that passes the wording check; each one that fails is named. */
    @PostMapping("/approve-all")
    @Transactional
    public ApprovedAll approveAll(@Valid @RequestBody ApproveAll request) {
        CurrentUser me = access.requireAdmin();
        int approved = 0;
        List<LeftAsDraft> left = new ArrayList<>();
        for (ProgrammeSession session : sessions.findByStageAndStatusOrderByUnitAscDayAsc(request.stage(),
                ContentStatus.DRAFT)) {
            if (request.unit() != null && session.getUnit() != request.unit()) {
                continue;
            }
            List<WordingGuard.Violation> issues = wordingGuard.check(session.wording());
            if (issues.isEmpty()) {
                session.review(ContentStatus.APPROVED, me.email());
                approved++;
            } else {
                left.add(new LeftAsDraft(session.getId(), session.label(), session.getTitle(), issues));
            }
        }
        return new ApprovedAll(approved, left);
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
                s.getReviewedAt(), wordingGuard.check(s.wording()), files.link(s.getVideoKey(), s.getVideoUrl()),
                s.getUnit());
    }
}
