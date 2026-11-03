package uk.co.motormovers.platform.school.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.school.dto.PlanViews;
import uk.co.motormovers.platform.school.service.PlanService;

/** The price chart on the public website's For schools page. Open without signing in (see SecurityConfig). */
@RestController
@RequestMapping("/api/plans")
public class PublicPlanController {

    private final PlanService plans;

    public PublicPlanController(PlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    public List<PlanViews.PublicPlan> forSale() {
        return plans.forSale();
    }
}
