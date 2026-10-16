package uk.co.motormovers.platform.family.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.GuidanceArticle;
import uk.co.motormovers.platform.content.model.HomeActivity;
import uk.co.motormovers.platform.family.model.ParentLink;
import uk.co.motormovers.platform.screening.model.MotorArea;
import uk.co.motormovers.platform.screening.model.ReadinessBand;

/** Response shapes for the parent app, and for the school's view of parent links. */
public final class FamilyViews {

    private FamilyViews() {
    }

    public record LinkedChild(Long childId, String name, String schoolName, String className, String yearGroup,
            ColourStage currentStage, String currentStageName, ColourStage startingStage, String startingStageName,
            boolean movedUp, LocalDate linkedOn) {

        public static LinkedChild of(ParentLink link) {
            Child c = link.getChild();
            return new LinkedChild(c.getId(), c.displayName(), c.getSchoolClass().getSchool().getName(),
                    c.getSchoolClass().getName(), c.getYearGroup(), c.getCurrentStage(), nameOf(c.getCurrentStage()),
                    c.getStartingStage(), nameOf(c.getStartingStage()), c.hasMovedUp(),
                    LocalDate.ofInstant(link.getLinkedAt(), ZoneOffset.UTC));
        }
    }

    /** Programme bands only. Parents are not shown raw scores or fractions, which read like test results. */
    public record AreaBand(MotorArea area, String label, ReadinessBand band, String bandLabel) {
    }

    public record Progress(LinkedChild child, String banner, boolean screened, List<AreaBand> areas,
            List<LocalDate> screenings, int classSessionsDone, int counterDays) {
    }

    public record ActivityCard(Long id, ColourStage stage, String stageName, int position, String title, String focus,
            int durationMinutes, String videoUrl, List<String> steps) {

        public static ActivityCard of(HomeActivity a) {
            return new ActivityCard(a.getId(), a.getStage(), a.getStage().displayName(), a.getPosition(), a.getTitle(),
                    a.getFocus(), a.getDurationMinutes(), a.getVideoUrl(), List.copyOf(a.getSteps()));
        }
    }

    public record Home(ColourStage stage, String stageName, ActivityCard next, List<ActivityCard> activities,
            boolean doneToday, int daysDone, int counterDays, List<Boolean> counterGrid, long totalSessions) {
    }

    public record HomeEntry(String clientId, Long activityId, Long childId, LocalDate doneOn) {
    }

    public record SubmitResult(int saved, int duplicates) {
    }

    public record ArticleSummary(Long id, String title, String summary, ColourStage stage, String stageName,
            int minutesToRead) {

        public static ArticleSummary of(GuidanceArticle a) {
            return new ArticleSummary(a.getId(), a.getTitle(), a.getSummary(), a.getStage(), nameOf(a.getStage()),
                    a.getMinutesToRead());
        }
    }

    public record Article(Long id, String title, String summary, List<String> paragraphs, ColourStage stage,
            String stageName, int minutesToRead) {

        public static Article of(GuidanceArticle a) {
            return new Article(a.getId(), a.getTitle(), a.getSummary(), a.paragraphs(), a.getStage(),
                    nameOf(a.getStage()), a.getMinutesToRead());
        }
    }

    /** What staff hand to the parent. */
    public record Invite(String code, String childName, String className, LocalDate expiresOn) {
    }

    /** A head teacher's view of who is linked to which child. */
    public record LinkRow(Long linkId, Long childId, String childName, String className, String parentName,
            String parentEmail, Instant linkedAt) {

        public static LinkRow of(ParentLink l) {
            return new LinkRow(l.getId(), l.getChild().getId(), l.getChild().displayName(),
                    l.getChild().getSchoolClass().getName(), l.getParent().getDisplayName(), l.getParent().getEmail(),
                    l.getLinkedAt());
        }
    }

    private static String nameOf(ColourStage stage) {
        return stage == null ? null : stage.displayName();
    }
}
