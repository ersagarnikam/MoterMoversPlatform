package uk.co.motormovers.platform.school.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.school.dto.PlanViews;
import uk.co.motormovers.platform.school.service.PlanService;

/** Licence plans and prices, managed by the platform team. ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/plans")
public class AdminPlanController {

    private final PlanService plans;

    public AdminPlanController(PlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    public List<PlanViews.PlanView> all() {
        return plans.all();
    }

    @PostMapping
    public PlanViews.PlanView create(@Valid @RequestBody PlanViews.NewPlan request) {
        return plans.create(request);
    }

    @PutMapping("/{code}")
    public PlanViews.PlanView update(@PathVariable String code, @Valid @RequestBody PlanViews.PlanUpdate request) {
        return plans.update(code, request);
    }

    @PostMapping("/{code}/prices")
    public PlanViews.PlanView changePrice(@PathVariable String code,
            @Valid @RequestBody PlanViews.PriceChange request) {
        return plans.changePrice(code, request);
    }
}
