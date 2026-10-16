package uk.co.motormovers.platform.family.model;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.user.model.AppUser;

/** A parent or carer who may see one child's progress. Either the parent or the school can remove it. */
@Entity
@Table(name = "parent_link")
public class ParentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Child child;

    private Instant linkedAt;

    protected ParentLink() {
    }

    public ParentLink(AppUser parent, Child child) {
        this.parent = parent;
        this.child = child;
        this.linkedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getParent() {
        return parent;
    }

    public Child getChild() {
        return child;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }
}
