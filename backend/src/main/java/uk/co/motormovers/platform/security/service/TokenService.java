package uk.co.motormovers.platform.security.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import uk.co.motormovers.platform.common.config.PlatformProperties;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final Duration parentTtl;

    public TokenService(JwtEncoder encoder, PlatformProperties properties) {
        this.encoder = encoder;
        this.ttl = Duration.ofMinutes(properties.jwt().ttlMinutes());
        long parentMinutes = properties.jwt().parentTtlMinutes();
        this.parentTtl = parentMinutes > 0 ? Duration.ofMinutes(parentMinutes) : ttl;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        // Parents stay signed in on their own phone; staff sessions end after a working day.
        Duration lifetime = user.getRole() == Role.PARENT ? parentTtl : ttl;
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("motor-movers")
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("name", user.getDisplayName())
                .claim("role", user.getRole().name());
        if (user.getSchoolId() != null) {
            claims.claim("schoolId", user.getSchoolId());
        }
        if (user.getTrustId() != null) {
            claims.claim("trustId", user.getTrustId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
