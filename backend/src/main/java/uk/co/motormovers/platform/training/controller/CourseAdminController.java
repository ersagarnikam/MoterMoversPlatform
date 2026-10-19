package uk.co.motormovers.platform.training.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.training.dto.CourseInputs.CohortInput;
import uk.co.motormovers.platform.training.dto.CourseInputs.CourseInput;
import uk.co.motormovers.platform.training.dto.TrainingViews.AdminCourseView;
import uk.co.motormovers.platform.training.service.CourseAdminService;

/** Setting up live courses and their cohorts. Mapped under /api/admin (ADMIN only). */
@RestController
@RequestMapping("/api/admin")
public class CourseAdminController {

    private final CourseAdminService courses;

    public CourseAdminController(CourseAdminService courses) {
        this.courses = courses;
    }

    @GetMapping("/courses")
    public List<AdminCourseView> all() {
        return courses.all();
    }

    @PostMapping("/courses")
    public AdminCourseView create(@Valid @RequestBody CourseInput request) {
        return courses.create(request);
    }

    @PutMapping("/courses/{courseId}")
    public AdminCourseView update(@PathVariable Long courseId, @Valid @RequestBody CourseInput request) {
        return courses.update(courseId, request);
    }

    @PostMapping("/courses/{courseId}/cohorts")
    public AdminCourseView addCohort(@PathVariable Long courseId, @Valid @RequestBody CohortInput request) {
        return courses.addCohort(courseId, request);
    }

    @PutMapping("/cohorts/{cohortId}")
    public AdminCourseView updateCohort(@PathVariable Long cohortId, @Valid @RequestBody CohortInput request) {
        return courses.updateCohort(cohortId, request);
    }
}
