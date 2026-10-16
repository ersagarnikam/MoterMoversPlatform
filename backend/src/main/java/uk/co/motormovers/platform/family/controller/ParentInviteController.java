package uk.co.motormovers.platform.family.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.family.dto.FamilyViews;
import uk.co.motormovers.platform.family.service.ParentInviteService;

/** School staff: invite a parent to the parent app, and see or remove parent links. */
@RestController
public class ParentInviteController {

    private final ParentInviteService invites;

    public ParentInviteController(ParentInviteService invites) {
        this.invites = invites;
    }

    @PostMapping("/api/children/{childId}/parent-invites")
    public FamilyViews.Invite create(@PathVariable Long childId) {
        return invites.create(childId);
    }

    @GetMapping("/api/schools/{schoolId}/parent-links")
    public List<FamilyViews.LinkRow> links(@PathVariable Long schoolId) {
        return invites.schoolLinks(schoolId);
    }

    @DeleteMapping("/api/schools/{schoolId}/parent-links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long schoolId, @PathVariable Long linkId) {
        invites.revoke(schoolId, linkId);
    }
}
