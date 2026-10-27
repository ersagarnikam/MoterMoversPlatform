package uk.co.motormovers.platform.training.controller;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import uk.co.motormovers.platform.training.dto.QuizViews;
import uk.co.motormovers.platform.training.service.CertificateService;
import uk.co.motormovers.platform.training.service.QuizService;

/** Quizzes between live sessions (the team writes them under /api/admin), and certificate PDFs. */
@RestController
public class QuizController {

    private final QuizService quizzes;
    private final CertificateService certificates;

    public QuizController(QuizService quizzes, CertificateService certificates) {
        this.quizzes = quizzes;
        this.certificates = certificates;
    }

    @GetMapping("/api/admin/courses/{courseId}/quizzes")
    public List<QuizViews.QuizAdminView> forCourse(@PathVariable Long courseId) {
        return quizzes.forCourse(courseId);
    }

    @PostMapping("/api/admin/courses/{courseId}/quizzes")
    public QuizViews.QuizAdminView create(@PathVariable Long courseId,
            @Valid @RequestBody QuizViews.QuizInput request) {
        return quizzes.create(courseId, request);
    }

    @PutMapping("/api/admin/quizzes/{quizId}")
    public QuizViews.QuizAdminView update(@PathVariable Long quizId, @Valid @RequestBody QuizViews.QuizInput request) {
        return quizzes.update(quizId, request);
    }

    @DeleteMapping("/api/admin/quizzes/{quizId}")
    public ResponseEntity<Void> delete(@PathVariable Long quizId) {
        quizzes.delete(quizId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/training/enrolments/{enrolmentId}/quizzes")
    public List<QuizViews.QuizSummary> forEnrolment(@PathVariable Long enrolmentId) {
        return quizzes.forEnrolment(enrolmentId);
    }

    @GetMapping("/api/training/enrolments/{enrolmentId}/quizzes/{quizId}")
    public QuizViews.QuizPaper paper(@PathVariable Long enrolmentId, @PathVariable Long quizId) {
        return quizzes.paper(enrolmentId, quizId);
    }

    @PostMapping("/api/training/enrolments/{enrolmentId}/quizzes/{quizId}/attempts")
    public QuizViews.AttemptResult attempt(@PathVariable Long enrolmentId, @PathVariable Long quizId,
            @Valid @RequestBody QuizViews.Answers request) {
        return quizzes.attempt(enrolmentId, quizId, request.answers());
    }

    @GetMapping("/api/training/enrolments/{enrolmentId}/certificate.pdf")
    public ResponseEntity<byte[]> certificate(@PathVariable Long enrolmentId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("motor-movers-certificate.pdf").build().toString())
                .body(certificates.download(enrolmentId));
    }
}
