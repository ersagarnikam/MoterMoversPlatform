package uk.co.motormovers.platform.dashboard.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.dashboard.dto.DashboardViews;
import uk.co.motormovers.platform.dashboard.service.DashboardService;

@RestController
public class DashboardController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv");

    private final DashboardService dashboards;

    public DashboardController(DashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping("/api/schools/{schoolId}/dashboard")
    public DashboardViews.SchoolDashboard school(@PathVariable Long schoolId) {
        return dashboards.school(schoolId);
    }

    @GetMapping("/api/schools/{schoolId}/access-log")
    public List<DashboardViews.AccessLogEntry> accessLog(@PathVariable Long schoolId) {
        return dashboards.accessLog(schoolId);
    }

    @GetMapping("/api/schools/{schoolId}/exports/governor-summary.csv")
    public ResponseEntity<String> governorSummary(@PathVariable Long schoolId) {
        return csv("governor-summary.csv", dashboards.governorSummaryCsv(schoolId));
    }

    @GetMapping("/api/schools/{schoolId}/exports/research.csv")
    public ResponseEntity<String> research(@PathVariable Long schoolId) {
        return csv("anonymised-research.csv", dashboards.researchCsv(schoolId));
    }

    @GetMapping("/api/trusts/{trustId}/dashboard")
    public DashboardViews.TrustDashboard trust(@PathVariable Long trustId) {
        return dashboards.trust(trustId);
    }

    private static ResponseEntity<String> csv(String filename, String body) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
