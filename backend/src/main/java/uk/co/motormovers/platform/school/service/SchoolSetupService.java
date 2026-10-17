package uk.co.motormovers.platform.school.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.child.model.ChildAccessLog;
import uk.co.motormovers.platform.child.repository.ChildAccessLogRepository;
import uk.co.motormovers.platform.child.repository.ChildRepository;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.school.dto.SchoolSetupViews;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.SchoolClass;
import uk.co.motormovers.platform.school.repository.SchoolClassRepository;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.security.service.ActiveUserFilter;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;
import uk.co.motormovers.platform.user.service.PasswordService;

/**
 * Setting a school up and keeping it current: staff accounts, classes, the children in each class and
 * the school's privacy settings. Heads manage their own school; the platform team can manage any.
 */
@Service
public class SchoolSetupService {

    private final SchoolRepository schools;
    private final SchoolClassRepository classes;
    private final ChildRepository children;
    private final ChildAccessLogRepository accessLog;
    private final AppUserRepository users;
    private final PasswordService passwords;
    private final PasswordEncoder passwordEncoder;
    private final AccessPolicy access;
    private final ActiveUserFilter activeUsers;
    private final Clock clock;

    public SchoolSetupService(SchoolRepository schools, SchoolClassRepository classes, ChildRepository children,
            ChildAccessLogRepository accessLog, AppUserRepository users, PasswordService passwords,
            PasswordEncoder passwordEncoder, AccessPolicy access, ActiveUserFilter activeUsers, Clock clock) {
        this.schools = schools;
        this.classes = classes;
        this.children = children;
        this.accessLog = accessLog;
        this.users = users;
        this.passwords = passwords;
        this.passwordEncoder = passwordEncoder;
        this.access = access;
        this.activeUsers = activeUsers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SchoolSetupViews.Setup setup(Long schoolId) {
        School school = school(schoolId);
        access.requireSchoolAccess(school);
        List<SchoolSetupViews.StaffMember> staff = users.findBySchoolIdOrderByDisplayName(schoolId).stream()
                .filter(u -> u.getRole() == Role.TEACHER || u.getRole() == Role.HEAD)
                .map(SchoolSetupViews.StaffMember::of).toList();
        List<SchoolSetupViews.ClassRow> rows = classes.findBySchoolIdOrderByName(schoolId).stream()
                .map(c -> SchoolSetupViews.ClassRow.of(c,
                        children.findBySchoolClassIdAndLeftOnIsNullOrderByInitials(c.getId()).size()))
                .toList();
        return new SchoolSetupViews.Setup(school.getId(), school.getName(), SchoolSetupViews.Settings.of(school),
                staff, rows);
    }

    @Transactional
    public SchoolSetupViews.Settings updateSettings(Long schoolId, SchoolSetupViews.Settings settings) {
        School school = school(schoolId);
        access.requireSchoolAccess(school);
        school.setPrivacy(settings.fullNamesEnabled(), settings.videoEnabled(), settings.trustCanSeeChildren());
        return SchoolSetupViews.Settings.of(school);
    }

    /** Creates a teacher or head account and emails them a link to choose their password. */
    @Transactional
    public SchoolSetupViews.StaffMember addStaff(Long schoolId, SchoolSetupViews.NewStaff request) {
        School school = school(schoolId);
        CurrentUser me = access.requireSchoolAccess(school);
        if (request.role() != Role.TEACHER && request.role() != Role.HEAD) {
            throw new IllegalArgumentException("Schools can add teachers and heads only");
        }
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        Long trustId = school.getTrust() == null ? null : school.getTrust().getId();
        // No usable password until they follow the invite link.
        AppUser user = users.save(new AppUser(email, request.displayName().strip(),
                passwordEncoder.encode(UUID.randomUUID().toString()), request.role(), school.getId(), trustId));
        passwords.sendInvite(user, me.displayName());
        return SchoolSetupViews.StaffMember.of(user);
    }

    @Transactional
    public SchoolSetupViews.StaffMember setStaffEnabled(Long schoolId, Long userId, boolean enabled) {
        School school = school(schoolId);
        CurrentUser me = access.requireSchoolAccess(school);
        AppUser user = staff(school, userId);
        if (!enabled && Objects.equals(user.getId(), me.id())) {
            throw new IllegalArgumentException("You cannot switch off your own account");
        }
        user.setEnabled(enabled);
        activeUsers.evict(user.getId());
        return SchoolSetupViews.StaffMember.of(user);
    }

    @Transactional
    public void resendInvite(Long schoolId, Long userId) {
        School school = school(schoolId);
        CurrentUser me = access.requireSchoolAccess(school);
        AppUser user = staff(school, userId);
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Switch the account on first");
        }
        passwords.sendInvite(user, me.displayName());
    }

    @Transactional
    public SchoolSetupViews.ClassRow addClass(Long schoolId, SchoolSetupViews.ClassRequest request) {
        School school = school(schoolId);
        access.requireSchoolAccess(school);
        SchoolClass c = classes.save(new SchoolClass(school, request.name().strip(), request.yearGroup().strip(),
                teacher(school, request.teacherId()), request.stage()));
        return SchoolSetupViews.ClassRow.of(c, 0);
    }

    @Transactional
    public SchoolSetupViews.ClassRow updateClass(Long schoolId, Long classId, SchoolSetupViews.ClassRequest request) {
        School school = school(schoolId);
        access.requireSchoolAccess(school);
        SchoolClass c = schoolClass(school, classId);
        c.update(request.name().strip(), request.yearGroup().strip(), teacher(school, request.teacherId()),
                request.stage());
        return SchoolSetupViews.ClassRow.of(c, children.findBySchoolClassIdAndLeftOnIsNullOrderByInitials(classId).size());
    }

    @Transactional(readOnly = true)
    public List<SchoolSetupViews.ChildRow> classChildren(Long schoolId, Long classId) {
        School school = school(schoolId);
        access.requireSchoolAccess(school);
        return children.findBySchoolClassIdAndLeftOnIsNullOrderByInitials(schoolClass(school, classId).getId())
                .stream().map(SchoolSetupViews.ChildRow::of).toList();
    }

    /** Adds children to a class, by initials (and full name only if the school wants it). */
    @Transactional
    public List<SchoolSetupViews.ChildRow> addChildren(Long schoolId, Long classId, SchoolSetupViews.NewChildren request) {
        School school = school(schoolId);
        CurrentUser me = access.requireSchoolAccess(school);
        SchoolClass c = schoolClass(school, classId);
        return request.children().stream().map(n -> {
            Child child = new Child(c, n.initials().strip().toUpperCase(Locale.ROOT), c.getYearGroup());
            if (n.fullName() != null && !n.fullName().isBlank()) {
                child.setFullName(n.fullName().strip());
            }
            Child saved = children.save(child);
            accessLog.save(new ChildAccessLog(saved.getId(), school.getId(), me.email(), "CHILD_ADDED"));
            return SchoolSetupViews.ChildRow.of(saved);
        }).toList();
    }

    /** Marks a child as having left. Their record is deleted automatically after the retention period. */
    @Transactional
    public void markLeft(Long schoolId, Long childId, LocalDate leftOn) {
        School school = school(schoolId);
        CurrentUser me = access.requireSchoolAccess(school);
        Child child = child(school, childId);
        if (leftOn.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("The leaving date cannot be in the future");
        }
        child.markLeft(leftOn);
        accessLog.save(new ChildAccessLog(child.getId(), school.getId(), me.email(), "CHILD_LEFT"));
    }

    @Transactional
    public SchoolSetupViews.ChildRow moveChild(Long schoolId, Long childId, Long classId) {
        School school = school(schoolId);
        CurrentUser me = access.requireSchoolAccess(school);
        Child child = child(school, childId);
        child.moveTo(schoolClass(school, classId));
        accessLog.save(new ChildAccessLog(child.getId(), school.getId(), me.email(), "CHILD_MOVED"));
        return SchoolSetupViews.ChildRow.of(child);
    }

    private School school(Long id) {
        return schools.findById(id).orElseThrow(() -> new NotFoundException("School", id));
    }

    private SchoolClass schoolClass(School school, Long classId) {
        return classes.findById(classId)
                .filter(c -> c.getSchool().getId().equals(school.getId()))
                .orElseThrow(() -> new NotFoundException("Class", classId));
    }

    private Child child(School school, Long childId) {
        return children.findById(childId)
                .filter(c -> c.getSchoolClass().getSchool().getId().equals(school.getId()) && c.getLeftOn() == null)
                .orElseThrow(() -> new NotFoundException("Child", childId));
    }

    private AppUser staff(School school, Long userId) {
        return users.findById(userId)
                .filter(u -> Objects.equals(u.getSchoolId(), school.getId()))
                .filter(u -> u.getRole() == Role.TEACHER || u.getRole() == Role.HEAD)
                .orElseThrow(() -> new NotFoundException("Staff member", userId));
    }

    private AppUser teacher(School school, Long teacherId) {
        if (teacherId == null) {
            return null;
        }
        AppUser teacher = staff(school, teacherId);
        if (!teacher.isEnabled()) {
            throw new IllegalArgumentException("That account is switched off");
        }
        return teacher;
    }
}
