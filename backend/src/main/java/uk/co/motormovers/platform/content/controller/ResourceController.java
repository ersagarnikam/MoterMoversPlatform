package uk.co.motormovers.platform.content.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.controller.ContentController.ContentView;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.LibraryResource;
import uk.co.motormovers.platform.content.model.ResourceKind;
import uk.co.motormovers.platform.content.repository.LibraryResourceRepository;
import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.school.model.Licence;
import uk.co.motormovers.platform.school.repository.LicenceRepository;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;

/**
 * The resource library: rhyme recordings, the rhyme book and printables. School staff see approved
 * resources for the stages on their school's licence. Sign-off is under /api/admin (ADMIN only), in the
 * same shape as the other content so one approval screen serves all of it.
 */
@RestController
public class ResourceController {

    private final LibraryResourceRepository resources;
    private final LicenceRepository licences;
    private final WordingGuard wordingGuard;
    private final CurrentUserResolver currentUser;
    private final Clock clock;

    public ResourceController(LibraryResourceRepository resources, LicenceRepository licences,
            WordingGuard wordingGuard, CurrentUserResolver currentUser, Clock clock) {
        this.resources = resources;
        this.licences = licences;
        this.wordingGuard = wordingGuard;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public record ResourceView(Long id, ResourceKind kind, String title, String description, ColourStage stage,
            String label, String fileUrl, List<String> lines) {

        public static ResourceView of(LibraryResource r) {
            return new ResourceView(r.getId(), r.getKind(), r.getTitle(), r.getDescription(), r.getStage(),
                    r.label(), r.getFileUrl(), r.lines());
        }
    }

    public record NewResource(@NotNull ResourceKind kind, @NotBlank String title, @NotBlank String description,
            ColourStage stage, String fileUrl, String words) {
    }

    @GetMapping("/api/resources")
    @Transactional(readOnly = true)
    public List<ResourceView> library() {
        Set<ColourStage> stages = licensedStages(currentUser.current());
        return resources.findByStatusOrderByTitle(ContentStatus.APPROVED).stream()
                .filter(r -> r.getStage() == null || stages.contains(r.getStage()))
                .sorted(Comparator.comparing(LibraryResource::getStage,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(ResourceView::of).toList();
    }

    @GetMapping("/api/admin/resources")
    public List<ContentView> all() {
        return resources.findAllByOrderByIdDesc().stream().map(this::view).toList();
    }

    @PostMapping("/api/admin/resources")
    @Transactional
    public ContentView create(@Valid @RequestBody NewResource r) {
        return view(resources.save(new LibraryResource(r.kind(), r.title(), r.description(), r.stage(),
                r.fileUrl(), r.words())));
    }

    @PostMapping("/api/admin/resources/{id}/approve")
    @Transactional
    public ContentView approve(@PathVariable Long id) {
        LibraryResource r = find(id);
        wordingGuard.requireClean(r.wording());
        r.review(ContentStatus.APPROVED, currentUser.current().email());
        return view(r);
    }

    @PostMapping("/api/admin/resources/{id}/reject")
    @Transactional
    public ContentView reject(@PathVariable Long id) {
        LibraryResource r = find(id);
        r.review(ContentStatus.REJECTED, currentUser.current().email());
        return view(r);
    }

    private Set<ColourStage> licensedStages(CurrentUser me) {
        return switch (me.role()) {
            case ADMIN -> Set.of(ColourStage.values());
            case TEACHER, HEAD -> {
                LocalDate today = LocalDate.now(clock);
                yield licences.findFirstBySchoolIdOrderByEndsOnDesc(me.schoolId())
                        .filter(l -> l.statusOn(today) != Licence.Status.EXPIRED)
                        .map(Licence::getStages)
                        .map(Set::copyOf)
                        .orElse(Set.of());
            }
            default -> throw new AccessDeniedException("The resource library is for school staff");
        };
    }

    private LibraryResource find(Long id) {
        return resources.findById(id).orElseThrow(() -> new NotFoundException("Resource", id));
    }

    private ContentView view(LibraryResource r) {
        return new ContentView(r.getId(), r.getStage(), r.label(), r.getTitle(), r.getDescription(), 0, r.lines(),
                r.getStatus(), r.getReviewedBy(), r.getReviewedAt(), wordingGuard.check(r.wording()));
    }
}
