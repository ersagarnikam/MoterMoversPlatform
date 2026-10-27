package uk.co.motormovers.platform.dashboard.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.dashboard.service.TeamOverviewService;

/** The Motor Movers team's home page: what needs attention today. Under /api/admin (ADMIN only). */
@RestController
public class TeamOverviewController {

    private final TeamOverviewService overview;

    public TeamOverviewController(TeamOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping("/api/admin/overview")
    public TeamOverviewService.TeamOverview overview() {
        return overview.overview();
    }
}
