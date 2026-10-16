package uk.co.motormovers.platform.user.model;

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
}
