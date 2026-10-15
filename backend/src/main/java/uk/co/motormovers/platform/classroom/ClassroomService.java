package uk.co.motormovers.platform.classroom;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.child.Child;
import uk.co.motormovers.platform.child.ChildRepository;
import uk.co.motormovers.platform.common.NotFoundException;
import uk.co.motormovers.platform.common.SchoolDays;
import uk.co.motormovers.platform.content.ContentStatus;
import uk.co.motormovers.platform.content.ProgrammeSession;
import uk.co.motormovers.platform.content.ProgrammeSessionRepository;
import uk.co.motormovers.platform.school.Licence;
import uk.co.motormovers.platform.school.LicenceRepository;
import uk.co.motormovers.platform.school.SchoolClass;
import uk.co.motormovers.platform.school.SchoolClassRepository;
import uk.co.motormovers.platform.security.AccessPolicy;
import uk.co.motormovers.platform.security.CurrentUser;
import uk.co.motormovers.platform.security.CurrentUserResolver;

/** The teacher's day: today's session, the half-term counter, the roster, and completing a session. */
@Service
public class ClassroomService {

    /** Days shown on the half-term counter. */
    public static final int COUNTER_DAYS = 20;

    private final SchoolClassRepository classes;
    private final ChildRepository children;
    private final ProgrammeSessionRepository content;
    private final DailySessionRecordRepository records;
    private final LicenceRepository licences;
    private final AccessPolicy access;
    private final CurrentUserResolver currentUser;
    private final Clock clock;

    public ClassroomService(SchoolClassRepository classes, ChildRepository children,
            ProgrammeSessionRepository content, DailySessionRecordRepository records, LicenceRepository licences,
            AccessPolicy access, CurrentUserResolver currentUser, Clock clock) {
        this.classes = classes;
        this.children = children;
        this.content = content;
        this.records = records;
        this.licences = licences;
        this.access = access;
        this.currentUser = currentUser;
        this.clock = clock;
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
        access.requireClassAccess(schoolClass);
        LocalDate today = LocalDate.now(clock);

        boolean licensed = licences.findFirstBySchoolIdOrderByEndsOnDesc(schoolClass.getSchool().getId())
                .filter(l -> l.statusOn(today) != Licence.Status.EXPIRED)
                .map(l -> l.covers(schoolClass.getStage()))
                .orElse(false);

        // Once today's session is done, keep showing it rather than jumping ahead to tomorrow's.
        Optional<ProgrammeSession> todays = records.findBySchoolClassIdAndSessionDate(classId, today)
                .map(DailySessionRecord::getSession);
        ClassroomViews.SessionCard next = licensed
                ? todays.or(() -> nextSession(schoolClass)).map(ClassroomViews.SessionCard::of).orElse(null)
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

        return new ClassroomViews.Today(ClassroomViews.ClassSummary.of(schoolClass), licensed, next, doneToday,
                (int) grid.stream().filter(Boolean::booleanValue).count(), COUNTER_DAYS, grid, roster,
                schoolClass.getSchool().isFullNamesEnabled());
    }

    /** Marks today's session as done. Absent children are removed from today's count. */
    @Transactional
    public ClassroomViews.Completed complete(Long classId, Long sessionId, Set<Long> absentChildIds) {
        SchoolClass schoolClass = findClass(classId);
        CurrentUser me = access.requireClassAccess(schoolClass);
        ProgrammeSession session = content.findById(sessionId)
                .filter(s -> s.getStatus() == ContentStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Approved session", sessionId));
        if (session.getStage() != schoolClass.getStage()) {
            throw new IllegalArgumentException("Session is not for this class's stage");
        }
        LocalDate today = LocalDate.now(clock);
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
        List<ProgrammeSession> sequence = content.findByStageAndStatusOrderByUnitAscDayAsc(schoolClass.getStage(),
                ContentStatus.APPROVED);
        if (sequence.isEmpty()) {
            return Optional.empty();
        }
        long completed = records.countBySchoolClassIdAndSessionStage(schoolClass.getId(), schoolClass.getStage());
        return Optional.of(sequence.get((int) (completed % sequence.size())));
    }

    private SchoolClass findClass(Long id) {
        return classes.findById(id).orElseThrow(() -> new NotFoundException("Class", id));
    }
}
