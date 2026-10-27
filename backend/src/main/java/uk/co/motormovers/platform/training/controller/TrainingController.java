package uk.co.motormovers.platform.training.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.training.service.CourseAdminService;

import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.service.TrainingService;

@RestController
public class TrainingController {

    private final TrainingService training;
    private final CourseAdminService courseAdmin;

    public TrainingController(TrainingService training, CourseAdminService courseAdmin) {
        this.training = training;
        this.courseAdmin = courseAdmin;
    }

    public record AssessmentRequest(boolean passed) {
    }

    @GetMapping("/api/training/courses")
    public Map<String, Object> catalogue() {
        return Map.of("rules", TrainingViews.LIVE_SESSION_RULES, "courses", training.catalogue());
    }

    @PostMapping("/api/training/cohorts/{cohortId}/enrol")
    public TrainingViews.EnrolmentView enrol(@PathVariable Long cohortId) {
        return training.enrol(cohortId);
    }

    /** Staff whose school pays ask their head for a place. */
    @PostMapping("/api/training/cohorts/{cohortId}/request")
    public TrainingViews.CourseRequestView requestPlace(@PathVariable Long cohortId) {
        return training.requestPlace(cohortId);
    }

    @GetMapping("/api/training/requests/mine")
    public List<TrainingViews.CourseRequestView> myRequests() {
        return training.myRequests();
    }

    @PostMapping("/api/training/requests/{requestId}/withdraw")
    public TrainingViews.CourseRequestView withdraw(@PathVariable Long requestId) {
        return training.withdraw(requestId);
    }

    @GetMapping("/api/schools/{schoolId}/course-requests")
    public List<TrainingViews.CourseRequestView> schoolRequests(@PathVariable Long schoolId) {
        return training.schoolRequests(schoolId);
    }

    @PostMapping("/api/course-requests/{requestId}/approve")
    public TrainingViews.CourseRequestView approve(@PathVariable Long requestId) {
        return training.approve(requestId);
    }

    @PostMapping("/api/course-requests/{requestId}/decline")
    public TrainingViews.CourseRequestView decline(@PathVariable Long requestId) {
        return training.decline(requestId);
    }

    @PostMapping("/api/training/enrolments/{enrolmentId}/cancel")
    public TrainingViews.CancelView cancel(@PathVariable Long enrolmentId) {
        return training.cancel(enrolmentId);
    }

    /** A trainer's own cohorts, with the courses they belong to. */
    @GetMapping("/api/trainer/cohorts")
    public List<TrainingViews.AdminCourseView> trainerCohorts() {
        return courseAdmin.trainerCohorts();
    }

    @GetMapping("/api/training/enrolments/mine")
    public List<TrainingViews.EnrolmentView> mine() {
        return training.mine();
    }

    @GetMapping("/api/admin/cohorts/{cohortId}/enrolments")
    public List<TrainingViews.EnrolmentView> cohortEnrolments(@PathVariable Long cohortId) {
        return training.cohortEnrolments(cohortId);
    }

    @PostMapping("/api/admin/enrolments/{enrolmentId}/assessment")
    public TrainingViews.EnrolmentView assessment(@PathVariable Long enrolmentId,
            @RequestBody AssessmentRequest request) {
        return training.recordAssessment(enrolmentId, request.passed());
    }
}
