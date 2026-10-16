package uk.co.motormovers.platform.security.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.user.model.Role;

@Component
public class CurrentUserResolver {

    public CurrentUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No signed-in user");
        }
        return new CurrentUser(
                toLong(jwt.getClaim("uid")),
                jwt.getSubject(),
                jwt.getClaimAsString("name"),
                Role.valueOf(jwt.getClaimAsString("role")),
                toLong(jwt.getClaim("schoolId")),
                toLong(jwt.getClaim("trustId")));
    }

    private static Long toLong(Object claim) {
        return claim instanceof Number n ? n.longValue() : null;
    }
}
