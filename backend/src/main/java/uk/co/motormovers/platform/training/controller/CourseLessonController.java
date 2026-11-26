package uk.co.motormovers.platform.training.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import uk.co.motormovers.platform.training.dto.CourseLessonViews;
import uk.co.motormovers.platform.training.service.CourseLessonService;

/**
 * A recorded course's lessons (go-live task 89): the team writes and approves them under /api/admin (ADMIN only);
 * a learner with a place on the course takes them under /api/training. Access is checked in the service.
 */
@RestController
public class CourseLessonController {

    private final CourseLessonService lessons;

    public CourseLessonController(CourseLessonService lessons) {
        this.lessons = lessons;
    }

    @GetMapping("/api/admin/courses/{courseId}/lessons")
    public List<CourseLessonViews.AdminLesson> all(@PathVariable Long courseId) {
        return lessons.all(courseId);
    }

    @PostMapping("/api/admin/courses/{courseId}/lessons")
    public CourseLessonViews.AdminLesson create(@PathVariable Long courseId,
            @Valid @RequestBody CourseLessonViews.LessonInput input) {
        return lessons.create(courseId, input);
    }

    @PutMapping("/api/admin/lessons/{lessonId}")
    public CourseLessonViews.AdminLesson update(@PathVariable Long lessonId,
            @Valid @RequestBody CourseLessonViews.LessonInput input) {
        return lessons.update(lessonId, input);
    }

    @PostMapping("/api/admin/lessons/{lessonId}/approve")
    public CourseLessonViews.AdminLesson approve(@PathVariable Long lessonId) {
        return lessons.approve(lessonId);
    }

    @PostMapping("/api/admin/lessons/{lessonId}/reject")
    public CourseLessonViews.AdminLesson reject(@PathVariable Long lessonId) {
        return lessons.reject(lessonId);
    }

    @DeleteMapping("/api/admin/lessons/{lessonId}")
    public ResponseEntity<Void> delete(@PathVariable Long lessonId) {
        lessons.delete(lessonId);
        return ResponseEntity.noContent().build();
    }

    /** A learner's place on a recorded course, with its lessons in order. */
    @GetMapping("/api/training/enrolments/{enrolmentId}/lessons")
    public CourseLessonViews.Player player(@PathVariable Long enrolmentId) {
        return lessons.player(enrolmentId);
    }

    @PostMapping("/api/training/enrolments/{enrolmentId}/lessons/{lessonId}/complete")
    public CourseLessonViews.Player complete(@PathVariable Long enrolmentId, @PathVariable Long lessonId) {
        return lessons.complete(enrolmentId, lessonId);
    }
}
