package uk.co.motormovers.platform.security;

import uk.co.motormovers.platform.user.Role;

/** The signed-in user, as carried in the access token. */
public record CurrentUser(Long id, String email, String displayName, Role role, Long schoolId, Long trustId) {

    public boolean is(Role r) {
        return role == r;
    }
}
