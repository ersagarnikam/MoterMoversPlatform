package uk.co.motormovers.platform.classroom.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.child.repository.ChildRepository;
import uk.co.motormovers.platform.classroom.dto.ClassroomViews;
import uk.co.motormovers.platform.classroom.model.DailySessionRecord;
import uk.co.motormovers.platform.classroom.repository.DailySessionRecordRepository;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.common.storage.FileLinks;
import uk.co.motormovers.platform.common.util.SchoolDays;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.ProgrammeSession;
import uk.co.motormovers.platform.content.repository.ProgrammeSessionRepository;
import uk.co.motormovers.platform.home.service.CoolDowns;
import uk.co.motormovers.platform.school.model.SchoolClass;
import uk.co.motormovers.platform.school.repository.SchoolClassRepository;
import uk.co.motormovers.platform.school.service.LicenceService;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.training.service.InductionService;

/** The teacher's day: today's session, the half-term counter, the roster, and completing a session. */
@Service
public class ClassroomService {

    /** Days shown on the half-term counter. */
    public static final int COUNTER_DAYS = 20;

    private final SchoolClassRepository classes;
    private final ChildRepository children;
    private final ProgrammeSessionRepository content;
    private final DailySessionRecordRepository records;
    private final LicenceService licences;
    private final AccessPolicy access;
    private final CurrentUserResolver currentUser;
    private final InductionService induction;
    private final Clock clock;
    private final FileLinks files;
    private final ExerciseRepository exercises;
    private final CoolDowns coolDowns;

    public ClassroomService(SchoolClassRepository classes, ChildRepository children,
            ProgrammeSessionRepository content, DailySessionRecordRepository records, LicenceService licences,
            AccessPolicy access, CurrentUserResolver currentUser, InductionService induction, Clock clock,
            FileLinks files, ExerciseRepository exercises, CoolDowns coolDowns) {
        this.classes = classes;
        this.children = children;
        this.content = content;
        this.records = records;
        this.licences = licences;
        this.access = access;
        this.currentUser = currentUser;
        this.induction = induction;
        this.clock = clock;
        this.files = files;
        this.exercises = exercises;
        this.coolDowns = coolDowns;
    }

    @Transactional(readOnly = true)
    public List<ClassroomViews.ClassSummary> myClasses() {
        CurrentUser me = currentUser.current();
        List<SchoolClass> list = switch (me.role()) {
            case TEACHER -> classes.findByTeacherIdOrderByName(me.id());
            case HEAD -> classes.findBySchoolIdOrderByName(me.schoolId());
            case ADMIN -> classes.findAll();
            default -> List.of();
        };
        return list.stream().map(ClassroomViews.ClassSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public ClassroomViews.Today today(Long classId) {
        SchoolClass schoolClass = findClass(classId);
        CurrentUser me = access.requireClassAccess(schoolClass);
        LocalDate today = LocalDate.now(clock);

        boolean licensed = licensedToday(schoolClass, today);

        // Once today's session is done, keep showing it rather than jumping ahead to tomorrow's.
        Optional<ProgrammeSession> todays = records.findBySchoolClassIdAndSessionDate(classId, today)
                .map(DailySessionRecord::getSession);
        ClassroomViews.SessionCard next = licensed
                ? todays.or(() -> nextSession(schoolClass)).map(this::card).orElse(null)
                : null;

        List<LocalDate> window = SchoolDays.lastSchoolDays(today, COUNTER_DAYS);
        Set<LocalDate> done = new HashSet<>();
        records.findBySchoolClassIdInAndSessionDateBetween(List.of(classId), window.getFirst(), window.getLast())
                .forEach(r -> done.add(r.getSessionDate()));
        List<Boolean> grid = window.stream().map(done::contains).toList();
        boolean doneToday = done.contains(today);

        List<ClassroomViews.RosterEntry> roster = children
                .findBySchoolClassIdAndLeftOnIsNullOrderByInitials(classId).stream()
                .map(ClassroomViews.RosterEntry::of)
                .toList();

        return new ClassroomViews.Today(ClassroomViews.ClassSummary.of(schoolClass), licensed,
                induction.isComplete(me), next, doneToday,
                (int) grid.stream().filter(Boolean::booleanValue).count(), COUNTER_DAYS, grid, roster,
                schoolClass.getSchool().isFullNamesEnabled());
    }

    /**
     * Marks today's session as done. Absent children are removed from today's count. The licence is checked here as
     * well as when today's session is shown: a direct call, or a session queued offline before the licence ran out,
     * is refused.
     */
    @Transactional
    public ClassroomViews.Completed complete(Long classId, Long sessionId, Set<Long> absentChildIds) {
        SchoolClass schoolClass = findClass(classId);
        CurrentUser me = access.requireClassAccess(schoolClass);
        induction.requireComplete(me);
        LocalDate today = LocalDate.now(clock);
        if (!licensedToday(schoolClass, today)) {
            throw new IllegalStateException(
                    "This class's colour is not covered by your school's licence today. Please ask your head teacher.");
        }
        ProgrammeSession session = content.findById(sessionId)
                .filter(s -> s.getStatus() == ContentStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Approved session", sessionId));
        if (session.getStage() != schoolClass.getStage()) {
            throw new IllegalArgumentException("Session is not for this class's stage");
        }
        if (!SchoolDays.isSchoolDay(today)) {
            throw new IllegalStateException("Sessions are recorded on school days only");
        }
        List<Child> roster = children.findBySchoolClassIdAndLeftOnIsNullOrderByInitials(classId);
        long absent = roster.stream().filter(c -> absentChildIds.contains(c.getId())).count();
        int present = roster.size() - (int) absent;

        Optional<DailySessionRecord> existing = records.findBySchoolClassIdAndSessionDate(classId, today);
        DailySessionRecord record = existing.orElseGet(() -> new DailySessionRecord(schoolClass, session, today,
                present, (int) absent, me.email()));
        record.update(session, present, (int) absent, me.email());
        records.save(record);
        return new ClassroomViews.Completed(today, present, (int) absent);
    }

    private Optional<ProgrammeSession> nextSession(SchoolClass schoolClass) {
        long approved = content.countByStageAndStatus(schoolClass.getStage(), ContentStatus.APPROVED);
        if (approved == 0) {
            return Optional.empty();
        }
        long completed = records.countBySchoolClassIdAndSessionStage(schoolClass.getId(), schoolClass.getStage());
        PageRequest place = PageRequest.of((int) (completed % approved), 1, Sort.by("unit", "day"));
        return content.findByStageAndStatus(schoolClass.getStage(), ContentStatus.APPROVED, place).stream().findFirst();
    }

    /** The session as the teacher sees it: each exercise with the grid's instructions, then the module's cool-down. */
    private ClassroomViews.SessionCard card(ProgrammeSession session) {
        List<String> names = List.copyOf(session.getActivities());
        Map<String, String> instructions = names.isEmpty() ? Map.of()
                : exercises.findByStageAndTitleIn(session.getStage(), names).stream()
                        .collect(Collectors.toMap(Exercise::getTitle, Exercise::getInstructions, (a, b) -> a));
        List<ClassroomViews.SessionStep> steps = names.stream()
                .map(name -> new ClassroomViews.SessionStep(name, instructions.get(name)))
                .toList();
        return ClassroomViews.SessionCard.of(session, files, steps,
                coolDowns.forModule(session.getStage(), session.getUnit()));
    }

    private boolean licensedToday(SchoolClass schoolClass, LocalDate today) {
        return licences.licensedFor(schoolClass.getSchool().getId(), schoolClass.getStage(), today);
    }

    private SchoolClass findClass(Long id) {
        return classes.findById(id).orElseThrow(() -> new NotFoundException("Class", id));
    }
}
