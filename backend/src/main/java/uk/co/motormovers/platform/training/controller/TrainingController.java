package uk.co.motormovers.platform.training.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.service.TrainingService;

@RestController
public class TrainingController {

    private final TrainingService training;

    public TrainingController(TrainingService training) {
        this.training = training;
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

    @GetMapping("/api/training/enrolments/mine")
    public List<TrainingViews.EnrolmentView> mine() {
        return training.mine();
    }

    @GetMapping("/api/admin/cohorts/{cohortId}/enrolments")
    public List<TrainingViews.EnrolmentView> cohortEnrolments(@PathVariable Long cohortId) {
        return training.cohortEnrolments(cohortId);
    }

    @PostMapping("/api/admin/enrolments/{enrolmentId}/attendance")
    public TrainingViews.EnrolmentView attendance(@PathVariable Long enrolmentId) {
        return training.recordAttendance(enrolmentId);
    }

    @PostMapping("/api/admin/enrolments/{enrolmentId}/attendance/undo")
    public TrainingViews.EnrolmentView undoAttendance(@PathVariable Long enrolmentId) {
        return training.undoAttendance(enrolmentId);
    }

    @PostMapping("/api/admin/enrolments/{enrolmentId}/assessment")
    public TrainingViews.EnrolmentView assessment(@PathVariable Long enrolmentId,
            @RequestBody AssessmentRequest request) {
        return training.recordAssessment(enrolmentId, request.passed());
    }
}
