package uk.co.motormovers.platform.training.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.training.dto.TrainingViews;
import uk.co.motormovers.platform.training.service.LiveSessionService;

/** Live course sessions on Zoom: upcoming sessions, joining one, and the register (the team only). */
@RestController
public class LiveSessionController {

    private final LiveSessionService live;

    public LiveSessionController(LiveSessionService live) {
        this.live = live;
    }

    public record Presence(boolean present) {
    }

    @GetMapping("/api/training/live")
    public List<TrainingViews.LiveSessionView> upcoming() {
        return live.upcoming();
    }

    @PostMapping("/api/training/live/{sessionId}/join")
    public TrainingViews.JoinView join(@PathVariable Long sessionId) {
        return live.join(sessionId);
    }

    @GetMapping("/api/admin/live/{sessionId}/register")
    public TrainingViews.RegisterView register(@PathVariable Long sessionId) {
        return live.register(sessionId);
    }

    @PostMapping("/api/admin/live/{sessionId}/register/mark-joined")
    public TrainingViews.RegisterView markJoinedPresent(@PathVariable Long sessionId) {
        return live.markJoinedPresent(sessionId);
    }

    @PutMapping("/api/admin/live/{sessionId}/register/{enrolmentId}")
    public TrainingViews.EnrolmentView mark(@PathVariable Long sessionId, @PathVariable Long enrolmentId,
            @RequestBody Presence request) {
        return live.mark(sessionId, enrolmentId, request.present());
    }
}
