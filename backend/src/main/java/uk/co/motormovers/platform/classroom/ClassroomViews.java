package uk.co.motormovers.platform.classroom;

import java.time.LocalDate;
import java.util.List;

import uk.co.motormovers.platform.child.Child;
import uk.co.motormovers.platform.common.ColourStage;
import uk.co.motormovers.platform.content.ProgrammeSession;
import uk.co.motormovers.platform.school.SchoolClass;

/** Response shapes for the teacher screens. */
public final class ClassroomViews {

    private ClassroomViews() {
    }

    public record ClassSummary(Long id, String name, String yearGroup, Long schoolId, String schoolName,
            String teacherName, ColourStage stage, String stageName) {

        static ClassSummary of(SchoolClass c) {
            return new ClassSummary(c.getId(), c.getName(), c.getYearGroup(), c.getSchool().getId(),
                    c.getSchool().getName(), c.getTeacher() == null ? null : c.getTeacher().getDisplayName(),
                    c.getStage(), c.getStage().displayName());
        }
    }

    public record SessionCard(Long id, String label, String title, String focus, int durationMinutes,
            String videoUrl, List<String> activities) {

        static SessionCard of(ProgrammeSession s) {
            return new SessionCard(s.getId(), s.label(), s.getTitle(), s.getFocus(), s.getDurationMinutes(),
                    s.getVideoUrl(), List.copyOf(s.getActivities()));
        }
    }

    public record RosterEntry(Long id, String name, String yearGroup, ColourStage currentStage) {

        static RosterEntry of(Child c) {
            return new RosterEntry(c.getId(), c.displayName(), c.getYearGroup(), c.getCurrentStage());
        }
    }

    public record Today(ClassSummary schoolClass, boolean stageLicensed, SessionCard nextSession, boolean doneToday,
            int sessionsDone, int counterDays, List<Boolean> counterGrid, List<RosterEntry> roster,
            boolean fullNamesEnabled) {
    }

    public record Completed(LocalDate date, int present, int absent) {
    }
}
