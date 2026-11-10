package uk.co.motormovers.platform.classroom.dto;

import java.time.LocalDate;
import java.util.List;

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.common.storage.FileLinks;
import uk.co.motormovers.platform.content.model.LibraryResource;
import uk.co.motormovers.platform.content.model.ProgrammeSession;
import uk.co.motormovers.platform.home.dto.HomeViews;
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

    /** One step of a session: instructions come from the colour's grid, and are null for a step the team wrote. */
    public record SessionStep(String title, String instructions) {
    }

    /** coolDown is the module's, the last step after the exercises; null for rhyme colours, which end in the rhyme. */
    public record SessionCard(Long id, String label, String title, String focus, int durationMinutes,
            String videoUrl, List<String> activities, List<SessionStep> steps, HomeViews.CoolDown coolDown,
            RhymeCard rhyme) {

        /** Links are signed here, so call this only for a class whose school is licensed for the session. */
        public static SessionCard of(ProgrammeSession s, FileLinks files, List<SessionStep> steps,
                HomeViews.CoolDown coolDown) {
            LibraryResource rhyme = s.getRhyme();
            return new SessionCard(s.getId(), s.label(), s.getTitle(), s.getFocus(), s.getDurationMinutes(),
                    files.link(s.getVideoKey(), s.getVideoUrl()), List.copyOf(s.getActivities()), steps, coolDown,
                    rhyme != null && rhyme.isApproved() ? RhymeCard.of(rhyme, files) : null);
        }
    }

    /** The session's nursery rhyme: the recording, once uploaded, and the words. */
    public record RhymeCard(Long id, String title, String audioUrl, List<String> lines) {

        public static RhymeCard of(LibraryResource r, FileLinks files) {
            return new RhymeCard(r.getId(), r.getTitle(), files.link(r.getFileKey(), r.getFileUrl()), r.lines());
        }
    }

    /** name is the full name when the school shows full names, otherwise the initials. */
    public record RosterEntry(Long id, String name, String initials, String yearGroup, ColourStage currentStage) {

        public static RosterEntry of(Child c) {
            return new RosterEntry(c.getId(), c.displayName(), c.getInitials(), c.getYearGroup(),
                    c.getCurrentStage());
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
