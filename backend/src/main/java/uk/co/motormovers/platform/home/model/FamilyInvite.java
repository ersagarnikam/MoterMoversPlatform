package uk.co.motormovers.platform.home.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A one-time code a parent gives a second carer so they can share the family's children. */
@Entity
@Table(name = "family_invite")
public class FamilyInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long familyId;

    private String code;

    private Long createdBy;

    private LocalDate expiresOn;

    private Long redeemedBy;

    private Instant redeemedAt;

    protected FamilyInvite() {
    }

    public FamilyInvite(Long familyId, String code, Long createdBy, LocalDate expiresOn) {
        this.familyId = familyId;
        this.code = code;
        this.createdBy = createdBy;
        this.expiresOn = expiresOn;
    }

    public boolean isRedeemable(LocalDate today) {
        return redeemedBy == null && !today.isAfter(expiresOn);
    }

    public void redeem(Long userId) {
        this.redeemedBy = userId;
        this.redeemedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public String getCode() {
        return code;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }
}
