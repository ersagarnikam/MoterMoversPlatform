package uk.co.motormovers.platform.school.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import uk.co.motormovers.platform.school.dto.SchoolSetupViews;
import uk.co.motormovers.platform.school.service.SchoolSetupService;

/** School setup for heads (their own school) and the platform team (any school). */
@RestController
@RequestMapping("/api/schools/{schoolId}")
public class SchoolSetupController {

    private final SchoolSetupService setup;

    public SchoolSetupController(SchoolSetupService setup) {
        this.setup = setup;
    }

    @GetMapping("/setup")
    public SchoolSetupViews.Setup setup(@PathVariable Long schoolId) {
        return setup.setup(schoolId);
    }

    @PutMapping("/settings")
    public SchoolSetupViews.Settings settings(@PathVariable Long schoolId,
            @RequestBody SchoolSetupViews.Settings settings) {
        return setup.updateSettings(schoolId, settings);
    }

    @PostMapping("/staff")
    public SchoolSetupViews.StaffMember addStaff(@PathVariable Long schoolId,
            @Valid @RequestBody SchoolSetupViews.NewStaff request) {
        return setup.addStaff(schoolId, request);
    }

    @PostMapping("/staff/{userId}/enabled")
    public SchoolSetupViews.StaffMember setEnabled(@PathVariable Long schoolId, @PathVariable Long userId,
            @RequestBody SchoolSetupViews.EnabledRequest request) {
        return setup.setStaffEnabled(schoolId, userId, request.enabled());
    }

    @PostMapping("/staff/{userId}/invite")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendInvite(@PathVariable Long schoolId, @PathVariable Long userId) {
        setup.resendInvite(schoolId, userId);
    }

    @PostMapping("/classes")
    public SchoolSetupViews.ClassRow addClass(@PathVariable Long schoolId,
            @Valid @RequestBody SchoolSetupViews.ClassRequest request) {
        return setup.addClass(schoolId, request);
    }

    @PutMapping("/classes/{classId}")
    public SchoolSetupViews.ClassRow updateClass(@PathVariable Long schoolId, @PathVariable Long classId,
            @Valid @RequestBody SchoolSetupViews.ClassRequest request) {
        return setup.updateClass(schoolId, classId, request);
    }

    @GetMapping("/classes/{classId}/children")
    public List<SchoolSetupViews.ChildRow> children(@PathVariable Long schoolId, @PathVariable Long classId) {
        return setup.classChildren(schoolId, classId);
    }

    @PostMapping("/classes/{classId}/children")
    public List<SchoolSetupViews.ChildRow> addChildren(@PathVariable Long schoolId, @PathVariable Long classId,
            @Valid @RequestBody SchoolSetupViews.NewChildren request) {
        return setup.addChildren(schoolId, classId, request);
    }

    @PostMapping("/children/{childId}/left")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markLeft(@PathVariable Long schoolId, @PathVariable Long childId,
            @Valid @RequestBody SchoolSetupViews.LeftRequest request) {
        setup.markLeft(schoolId, childId, request.leftOn());
    }

    @PostMapping("/children/{childId}/move")
    public SchoolSetupViews.ChildRow move(@PathVariable Long schoolId, @PathVariable Long childId,
            @Valid @RequestBody SchoolSetupViews.MoveRequest request) {
        return setup.moveChild(schoolId, childId, request.classId());
    }
}
