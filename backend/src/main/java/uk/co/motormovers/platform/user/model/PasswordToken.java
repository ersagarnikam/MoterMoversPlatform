package uk.co.motormovers.platform.user.model;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A one-time link to set a password. Only the SHA-256 of the token is stored. */
@Entity
@Table(name = "password_token")
public class PasswordToken {

    public enum Purpose {
        /** A new member of staff sets their first password. */
        INVITE,
        /** Anyone who has forgotten their password. */
        RESET
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    private Purpose purpose;

    private Instant createdAt;

    private Instant expiresAt;

    private Instant usedAt;

    protected PasswordToken() {
    }

    public PasswordToken(String tokenHash, AppUser user, Purpose purpose, Instant now, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.purpose = purpose;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void use(Instant now) {
        this.usedAt = now;
    }

    public AppUser getUser() {
        return user;
    }

    public Purpose getPurpose() {
        return purpose;
    }
}
