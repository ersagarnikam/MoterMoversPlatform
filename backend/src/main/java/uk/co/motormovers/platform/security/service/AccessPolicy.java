package uk.co.motormovers.platform.security.service;

import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.family.repository.ParentLinkRepository;
import uk.co.motormovers.platform.home.repository.FamilyMemberRepository;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.SchoolClass;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.user.model.Role;

/**
 * Who may see what. Each school controls its own children's records: teachers see their own class,
 * heads see their school, trust leads see summaries only unless the school grants access, and parents
 * see only the children the school has invited them to. A family's own child (Motor Movers at Home) is seen only by
 * that family's parents and carers, never by a school.
 */
@Component
public class AccessPolicy {

    private final CurrentUserResolver currentUser;
    private final ParentLinkRepository parentLinks;
    private final FamilyMemberRepository familyMembers;

    public AccessPolicy(CurrentUserResolver currentUser, ParentLinkRepository parentLinks,
            FamilyMemberRepository familyMembers) {
        this.currentUser = currentUser;
        this.parentLinks = parentLinks;
        this.familyMembers = familyMembers;
    }

    public CurrentUser requireClassAccess(SchoolClass schoolClass) {
        CurrentUser me = currentUser.current();
        if (schoolClass == null) {
            throw new AccessDeniedException("You do not have access to this class");
        }
        boolean allowed = switch (me.role()) {
            case ADMIN -> true;
            case HEAD -> Objects.equals(me.schoolId(), schoolClass.getSchool().getId());
            case TEACHER -> Objects.equals(me.schoolId(), schoolClass.getSchool().getId())
                    && schoolClass.getTeacher() != null
                    && Objects.equals(me.id(), schoolClass.getTeacher().getId());
            case TRUST_LEAD -> schoolClass.getSchool().isTrustCanSeeChildren()
                    && schoolClass.getSchool().getTrust() != null
                    && Objects.equals(me.trustId(), schoolClass.getSchool().getTrust().getId());
            default -> false;
        };
        if (!allowed) {
            throw new AccessDeniedException("You do not have access to this class");
        }
        return me;
    }

    /** Head-level view of a school (dashboards, exports). */
    public CurrentUser requireSchoolAccess(School school) {
        CurrentUser me = currentUser.current();
        boolean allowed = me.is(Role.ADMIN)
                || (me.is(Role.HEAD) && Objects.equals(me.schoolId(), school.getId()));
        if (!allowed) {
            throw new AccessDeniedException("You do not have access to this school");
        }
        return me;
    }

    /**
     * A parent sees a child only through a link the school issued, only while the school keeps parent
     * links switched on, and only while the child is still on roll.
     */
    public CurrentUser requireParentOf(Child child) {
        if (child.isAtHome()) {
            return requireFamilyOf(child);
        }
        CurrentUser me = currentUser.current();
        boolean allowed = me.is(Role.PARENT)
                && child.getLeftOn() == null
                && child.getSchoolClass().getSchool().isParentLinksEnabled()
                && parentLinks.existsByParentIdAndChildId(me.id(), child.getId());
        if (!allowed) {
            throw new AccessDeniedException("You do not have access to this child");
        }
        return me;
    }

    /** A home child: only a parent or carer in the child's family. */
    public CurrentUser requireFamilyOf(Child child) {
        CurrentUser me = currentUser.current();
        boolean allowed = me.is(Role.PARENT) && child.isAtHome()
                && familyMembers.existsByFamilyIdAndUserId(child.getFamilyId(), me.id());
        if (!allowed) {
            throw new AccessDeniedException("You do not have access to this child");
        }
        return me;
    }

    /** A child's own record (assessments): the family for a home child, otherwise staff with class access. */
    public CurrentUser requireRecordAccess(Child child) {
        return child.isAtHome() ? requireFamilyOf(child) : requireClassAccess(child.getSchoolClass());
    }

    public CurrentUser requireTrustAccess(Long trustId) {
        CurrentUser me = currentUser.current();
        boolean allowed = me.is(Role.ADMIN) || (me.is(Role.TRUST_LEAD) && Objects.equals(me.trustId(), trustId));
        if (!allowed) {
            throw new AccessDeniedException("You do not have access to this trust");
        }
        return me;
    }
}
