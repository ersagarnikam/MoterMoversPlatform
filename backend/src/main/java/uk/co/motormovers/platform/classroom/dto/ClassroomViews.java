package uk.co.motormovers.platform.classroom.dto;

import java.time.LocalDate;
import java.util.List;

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.LibraryResource;
import uk.co.motormovers.platform.content.model.ProgrammeSession;
import uk.co.motormovers.platform.school.model.SchoolClass;

/** Response shapes for the teacher screens. */
public final class ClassroomViews {

    private ClassroomViews() {
    }

    public record ClassSummary(Long id, String name, String yearGroup, Long schoolId, String schoolName,
            String teacherName, ColourStage stage, String stageName) {

        public static ClassSummary of(SchoolClass c) {
            return new ClassSummary(c.getId(), c.getName(), c.getYearGroup(), c.getSchool().getId(),
                    c.getSchool().getName(), c.getTeacher() == null ? null : c.getTeacher().getDisplayName(),
                    c.getStage(), c.getStage().displayName());
        }
    }

    public record SessionCard(Long id, String label, String title, String focus, int durationMinutes,
            String videoUrl, List<String> activities, RhymeCard rhyme) {

        public static SessionCard of(ProgrammeSession s) {
            LibraryResource rhyme = s.getRhyme();
            return new SessionCard(s.getId(), s.label(), s.getTitle(), s.getFocus(), s.getDurationMinutes(),
                    s.getVideoUrl(), List.copyOf(s.getActivities()),
                    rhyme != null && rhyme.isApproved() ? RhymeCard.of(rhyme) : null);
        }
    }

    /** The session's nursery rhyme: the recording, once uploaded, and the words. */
    public record RhymeCard(Long id, String title, String audioUrl, List<String> lines) {

        public static RhymeCard of(LibraryResource r) {
            return new RhymeCard(r.getId(), r.getTitle(), r.getFileUrl(), r.lines());
        }
    }

    public record RosterEntry(Long id, String name, String yearGroup, ColourStage currentStage) {

        public static RosterEntry of(Child c) {
            return new RosterEntry(c.getId(), c.displayName(), c.getYearGroup(), c.getCurrentStage());
        }
    }

    public record Today(ClassSummary schoolClass, boolean stageLicensed, boolean inductionComplete,
            SessionCard nextSession, boolean doneToday,
            int sessionsDone, int counterDays, List<Boolean> counterGrid, List<RosterEntry> roster,
            boolean fullNamesEnabled) {
    }

    public record Completed(LocalDate date, int present, int absent) {
    }
}
