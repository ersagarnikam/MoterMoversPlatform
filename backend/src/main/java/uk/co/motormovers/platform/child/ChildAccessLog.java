package uk.co.motormovers.platform.child;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row every time someone opens a child's record, so the school's data officer can check. */
@Entity
@Table(name = "child_access_log")
public class ChildAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long childId;

    private Long schoolId;

    private String userEmail;

    private String action;

    private Instant at;

    protected ChildAccessLog() {
    }

    public ChildAccessLog(Long childId, Long schoolId, String userEmail, String action) {
        this.childId = childId;
        this.schoolId = schoolId;
        this.userEmail = userEmail;
        this.action = action;
        this.at = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getChildId() {
        return childId;
    }

    public Long getSchoolId() {
        return schoolId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getAction() {
        return action;
    }

    public Instant getAt() {
        return at;
    }
}
