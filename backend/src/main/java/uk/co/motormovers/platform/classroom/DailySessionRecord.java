package uk.co.motormovers.platform.classroom;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import uk.co.motormovers.platform.content.ProgrammeSession;
import uk.co.motormovers.platform.school.SchoolClass;

/** A class completed its daily session on a given day. One per class per day. */
@Entity
@Table(name = "daily_session_record")
public class DailySessionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ProgrammeSession session;

    private LocalDate sessionDate;

    private int presentCount;

    private int absentCount;

    private String completedBy;

    protected DailySessionRecord() {
    }

    public DailySessionRecord(SchoolClass schoolClass, ProgrammeSession session, LocalDate sessionDate,
            int presentCount, int absentCount, String completedBy) {
        this.schoolClass = schoolClass;
        this.session = session;
        this.sessionDate = sessionDate;
        this.presentCount = presentCount;
        this.absentCount = absentCount;
        this.completedBy = completedBy;
    }

    public void update(ProgrammeSession session, int presentCount, int absentCount, String completedBy) {
        this.session = session;
        this.presentCount = presentCount;
        this.absentCount = absentCount;
        this.completedBy = completedBy;
    }

    public Long getId() {
        return id;
    }

    public SchoolClass getSchoolClass() {
        return schoolClass;
    }

    public ProgrammeSession getSession() {
        return session;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public int getPresentCount() {
        return presentCount;
    }

    public int getAbsentCount() {
        return absentCount;
    }

    public String getCompletedBy() {
        return completedBy;
    }
}
