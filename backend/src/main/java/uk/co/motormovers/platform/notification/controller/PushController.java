package uk.co.motormovers.platform.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import uk.co.motormovers.platform.notification.service.PushService;

/** The parent app registers the phone for notifications after signing in, and removes it on signing out. */
@RestController
public class PushController {

    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    public record Registration(@NotBlank @Size(max = 200) String token, @Size(max = 20) String platform) {
    }

    public record Removal(@NotBlank @Size(max = 200) String token) {
    }

    @PostMapping("/api/me/push-tokens")
    public ResponseEntity<Void> register(@Valid @RequestBody Registration request) {
        push.register(request.token().strip(), request.platform() == null ? "unknown" : request.platform());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/me/push-tokens/remove")
    public ResponseEntity<Void> remove(@Valid @RequestBody Removal request) {
        push.remove(request.token().strip());
        return ResponseEntity.noContent().build();
    }
}
