package uk.co.motormovers.platform.family.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.child.model.Child;

/**
 * A one-time code the school gives a parent or carer so they can see their child in the parent app.
 * Issuing the code is the school's decision; redeeming it is the parent's.
 */
@Entity
@Table(name = "parent_invite")
public class ParentInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Child child;

    private String code;

    private String createdBy;

    private Instant createdAt;

    private LocalDate expiresOn;

    private Long redeemedBy;

    private Instant redeemedAt;

    protected ParentInvite() {
    }

    public ParentInvite(Child child, String code, String createdBy, LocalDate expiresOn) {
        this.child = child;
        this.code = code;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.expiresOn = expiresOn;
    }

    public boolean isRedeemable(LocalDate today) {
        return redeemedBy == null && !today.isAfter(expiresOn);
    }

    public void redeem(Long parentId) {
        this.redeemedBy = parentId;
        this.redeemedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Child getChild() {
        return child;
    }

    public String getCode() {
        return code;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public Long getRedeemedBy() {
        return redeemedBy;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }
}
