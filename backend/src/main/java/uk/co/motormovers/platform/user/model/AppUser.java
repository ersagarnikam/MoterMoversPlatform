package uk.co.motormovers.platform.user.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String displayName;

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private Long schoolId;

    private Long trustId;

    /** When this member of staff finished the one-hour induction. Sessions stay locked until then. */
    private LocalDate inductionCompletedOn;

    /** A switched-off account cannot sign in, and its existing sign-ins stop working within a minute. */
    private boolean enabled = true;

    /** The Zoom login this person hosts live sessions with, when it differs from their email. Null: not set. */
    private String zoomEmail;

    protected AppUser() {
    }

    public AppUser(String email, String displayName, String passwordHash, Role role, Long schoolId, Long trustId) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.schoolId = schoolId;
        this.trustId = trustId;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Long getSchoolId() {
        return schoolId;
    }

    public Long getTrustId() {
        return trustId;
    }

    public LocalDate getInductionCompletedOn() {
        return inductionCompletedOn;
    }

    public String getZoomEmail() {
        return zoomEmail;
    }

    /** Blank clears it, so the platform's default Zoom host is used. */
    public void setZoomEmail(String zoomEmail) {
        this.zoomEmail = zoomEmail == null || zoomEmail.isBlank() ? null : zoomEmail.strip().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public void completeInduction(LocalDate on) {
        if (inductionCompletedOn == null) {
            inductionCompletedOn = on;
        }
    }
}
