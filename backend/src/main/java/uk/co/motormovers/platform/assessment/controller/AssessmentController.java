package uk.co.motormovers.platform.assessment.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.assessment.dto.AssessmentViews;
import uk.co.motormovers.platform.assessment.service.AssessmentService;
import uk.co.motormovers.platform.common.model.ColourStage;

/** Pat's developmental charts and exercise scores. Access is checked in {@link AssessmentService}. */
@RestController
public class AssessmentController {

    private final AssessmentService assessments;

    public AssessmentController(AssessmentService assessments) {
        this.assessments = assessments;
    }

    @GetMapping("/api/assessment/milestones")
    public List<AssessmentViews.MilestoneView> milestones() {
        return assessments.milestones();
    }

    @GetMapping("/api/assessment/exercises")
    public List<AssessmentViews.ExerciseView> exercises(@RequestParam ColourStage stage) {
        return assessments.exercises(stage);
    }

    @GetMapping("/api/classes/{classId}/exercise-sheet")
    public AssessmentViews.ClassSheet classSheet(@PathVariable Long classId,
            @RequestParam(required = false) ColourStage stage) {
        return assessments.classSheet(classId, stage);
    }

    @PostMapping("/api/assessment/exercise-scores")
    public AssessmentViews.SubmitResult submitScores(@RequestBody List<AssessmentViews.ExerciseScoreIn> scores) {
        return assessments.submitScores(scores);
    }

    @PostMapping("/api/assessment/milestones")
    public AssessmentViews.SubmitResult submitAchievements(
            @RequestBody List<AssessmentViews.AchievementIn> achievements) {
        return assessments.submitAchievements(achievements);
    }

    @GetMapping("/api/children/{childId}/development")
    public AssessmentViews.Development development(@PathVariable Long childId) {
        return assessments.development(childId);
    }

    @DeleteMapping("/api/children/{childId}/milestones/{milestoneId}")
    public ResponseEntity<Void> clearAchievement(@PathVariable Long childId, @PathVariable Long milestoneId) {
        assessments.clearAchievement(childId, milestoneId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/children/{childId}/development-comments")
    public ResponseEntity<Void> writeComment(@PathVariable Long childId,
            @RequestBody AssessmentViews.CommentIn comment) {
        assessments.writeComment(childId, comment);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/children/{childId}/assessment")
    public AssessmentViews.Profile profile(@PathVariable Long childId,
            @RequestParam(required = false) ColourStage stage) {
        return assessments.profile(childId, stage);
    }

    @PutMapping("/api/children/{childId}/colour-summary")
    public ResponseEntity<Void> writeSummary(@PathVariable Long childId,
            @RequestBody AssessmentViews.SummaryIn summary) {
        assessments.writeSummary(childId, summary);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/schools/{schoolId}/assessment-overview")
    public AssessmentViews.SchoolOverview schoolOverview(@PathVariable Long schoolId) {
        return assessments.schoolOverview(schoolId);
    }
}
