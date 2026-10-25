package uk.co.motormovers.platform.notification.model;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** A phone with the parent app that may be sent notifications for an account (an Expo push token). */
@Entity
public class PushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String token;

    private String platform;

    private Instant createdAt;

    protected PushToken() {
    }

    public PushToken(Long userId, String token, String platform, Instant createdAt) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
        this.createdAt = createdAt;
    }

    /** The same phone signed in to another account: it now belongs to that one. */
    public void moveTo(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }
}
