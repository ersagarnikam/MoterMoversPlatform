package uk.co.motormovers.platform.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

import uk.co.motormovers.platform.school.model.Licence;

public final class DashboardViews {

    private DashboardViews() {
    }

    public record ClassParticipation(Long classId, String name, String stageName, double participation,
            boolean flagged) {
    }

    public record SchoolDashboard(Long schoolId, String schoolName, int childrenScreened, int childrenTotal,
            double movedUpRate, double dailyParticipation, int classesActive, int classesTotal, int windowDays,
            double flagThreshold, List<ClassParticipation> classes) {
    }

    /** Trust leads see summaries only: no individual children. */
    public record TrustSchoolRow(Long schoolId, String schoolName, Licence.Status licenceStatus, String plan,
            LocalDate renewalDate, String stages, double screenedRate, double participation) {
    }

    public record TrustDashboard(Long trustId, String trustName, List<TrustSchoolRow> schools) {
    }

    public record AccessLogEntry(Long childId, String userEmail, String action, String at) {
    }
}
