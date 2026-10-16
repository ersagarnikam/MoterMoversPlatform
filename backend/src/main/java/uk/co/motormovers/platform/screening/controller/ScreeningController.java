package uk.co.motormovers.platform.screening.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.screening.dto.ScreeningViews;
import uk.co.motormovers.platform.screening.service.ScreeningService;

@RestController
public class ScreeningController {

    private final ScreeningService screening;

    public ScreeningController(ScreeningService screening) {
        this.screening = screening;
    }

    @GetMapping("/api/screening/items")
    public List<ScreeningViews.Item> items() {
        return screening.items();
    }

    @PostMapping("/api/screening/results")
    public ScreeningViews.SubmitResult submit(@RequestBody List<ScreeningViews.Score> scores) {
        return screening.submit(scores);
    }

    @GetMapping("/api/children/{childId}/profile")
    public ScreeningViews.Profile profile(@PathVariable Long childId) {
        return screening.profile(childId);
    }
}
