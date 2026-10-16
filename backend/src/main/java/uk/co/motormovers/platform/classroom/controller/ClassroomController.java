package uk.co.motormovers.platform.classroom.controller;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import uk.co.motormovers.platform.classroom.dto.ClassroomViews;
import uk.co.motormovers.platform.classroom.service.ClassroomService;

@RestController
@RequestMapping("/api/classes")
public class ClassroomController {

    private final ClassroomService classroom;

    public ClassroomController(ClassroomService classroom) {
        this.classroom = classroom;
    }

    public record CompleteRequest(@NotNull Long sessionId, Set<Long> absentChildIds) {
    }

    @GetMapping
    public List<ClassroomViews.ClassSummary> myClasses() {
        return classroom.myClasses();
    }

    @GetMapping("/{classId}/today")
    public ClassroomViews.Today today(@PathVariable Long classId) {
        return classroom.today(classId);
    }

    @PostMapping("/{classId}/sessions")
    public ClassroomViews.Completed complete(@PathVariable Long classId, @Valid @RequestBody CompleteRequest request) {
        return classroom.complete(classId, request.sessionId(),
                request.absentChildIds() == null ? Set.of() : request.absentChildIds());
    }
}
