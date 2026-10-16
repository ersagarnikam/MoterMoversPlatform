package uk.co.motormovers.platform.family.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.child.model.ChildAccessLog;
import uk.co.motormovers.platform.child.repository.ChildAccessLogRepository;
import uk.co.motormovers.platform.child.repository.ChildRepository;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.family.dto.FamilyViews;
import uk.co.motormovers.platform.family.model.ParentInvite;
import uk.co.motormovers.platform.family.model.ParentLink;
import uk.co.motormovers.platform.family.repository.ParentInviteRepository;
import uk.co.motormovers.platform.family.repository.ParentLinkRepository;
import uk.co.motormovers.platform.family.util.InviteCodes;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.user.model.Role;

/** The school's side of parent links: issuing invite codes, and seeing or removing links. */
@Service
public class ParentInviteService {

    /** Invite codes expire after this many days if not used. */
    public static final int INVITE_DAYS = 14;

    private final ParentInviteRepository invites;
    private final ParentLinkRepository links;
    private final ChildRepository children;
    private final SchoolRepository schools;
    private final ChildAccessLogRepository accessLog;
    private final AccessPolicy access;
    private final Clock clock;

    public ParentInviteService(ParentInviteRepository invites, ParentLinkRepository links, ChildRepository children,
            SchoolRepository schools, ChildAccessLogRepository accessLog, AccessPolicy access, Clock clock) {
        this.invites = invites;
        this.links = links;
        this.children = children;
        this.schools = schools;
        this.accessLog = accessLog;
        this.access = access;
        this.clock = clock;
    }

    /** A teacher or head issues a one-time code for one child's parent or carer. */
    @Transactional
    public FamilyViews.Invite create(Long childId) {
        Child child = children.findById(childId).orElseThrow(() -> new NotFoundException("Child", childId));
        CurrentUser me = access.requireClassAccess(child.getSchoolClass());
        if (!(me.is(Role.TEACHER) || me.is(Role.HEAD) || me.is(Role.ADMIN))) {
            throw new AccessDeniedException("Only school staff can invite parents");
        }
        School school = child.getSchoolClass().getSchool();
        if (!school.isParentLinksEnabled()) {
            throw new IllegalStateException("Parent links are switched off for this school");
        }
        if (child.getLeftOn() != null) {
            throw new IllegalStateException("This child has left the school");
        }
        String code;
        do {
            code = InviteCodes.generate();
        } while (invites.existsByCode(code));
        ParentInvite invite = invites.save(new ParentInvite(child, code, me.email(),
                LocalDate.now(clock).plusDays(INVITE_DAYS)));
        accessLog.save(new ChildAccessLog(child.getId(), school.getId(), me.email(), "CREATE_PARENT_INVITE"));
        return new FamilyViews.Invite(InviteCodes.format(invite.getCode()), child.displayName(),
                child.getSchoolClass().getName(), invite.getExpiresOn());
    }

    @Transactional(readOnly = true)
    public List<FamilyViews.LinkRow> schoolLinks(Long schoolId) {
        access.requireSchoolAccess(findSchool(schoolId));
        return links.findByChildSchoolClassSchoolIdOrderByLinkedAtDesc(schoolId).stream()
                .map(FamilyViews.LinkRow::of).toList();
    }

    @Transactional
    public void revoke(Long schoolId, Long linkId) {
        CurrentUser me = access.requireSchoolAccess(findSchool(schoolId));
        ParentLink link = links.findById(linkId)
                .filter(l -> l.getChild().getSchoolClass().getSchool().getId().equals(schoolId))
                .orElseThrow(() -> new NotFoundException("Parent link", linkId));
        links.delete(link);
        accessLog.save(new ChildAccessLog(link.getChild().getId(), schoolId, me.email(), "PARENT_LINK_REMOVED"));
    }

    private School findSchool(Long id) {
        return schools.findById(id).orElseThrow(() -> new NotFoundException("School", id));
    }
}
