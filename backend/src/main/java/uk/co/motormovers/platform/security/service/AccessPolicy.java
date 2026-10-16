package uk.co.motormovers.platform.security.service;

import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.family.repository.ParentLinkRepository;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.SchoolClass;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.user.model.Role;

/**
 * Who may see what. Each school controls its own children's records: teachers see their own class,
 * heads see their school, trust leads see summaries only unless the school grants access, and parents
 * see only the children the school has invited them to.
 */
@Component
public class AccessPolicy {

    private final CurrentUserResolver currentUser;
    private final ParentLinkRepository parentLinks;

    public AccessPolicy(CurrentUserResolver currentUser, ParentLinkRepository parentLinks) {
        this.currentUser = currentUser;
        this.parentLinks = parentLinks;
    }

    public CurrentUser requireClassAccess(SchoolClass schoolClass) {
        CurrentUser me = currentUser.current();
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

    public CurrentUser requireTrustAccess(Long trustId) {
        CurrentUser me = currentUser.current();
        boolean allowed = me.is(Role.ADMIN) || (me.is(Role.TRUST_LEAD) && Objects.equals(me.trustId(), trustId));
        if (!allowed) {
            throw new AccessDeniedException("You do not have access to this trust");
        }
        return me;
    }
}
