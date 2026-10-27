package uk.co.motormovers.platform.home.model;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** A parent or carer in a family. Each account belongs to one family at most. */
@Entity
@Table(name = "family_member")
@IdClass(FamilyMember.Key.class)
public class FamilyMember {

    /** OWNER started the family and can remove carers; a CARER joined with a code. */
    public enum Role {
        OWNER, CARER
    }

    public record Key(Long familyId, Long userId) implements Serializable {

        public Key() {
            this(null, null);
        }
    }

    @Id
    private Long familyId;

    @Id
    private Long userId;

    @Enumerated(EnumType.STRING)
    private Role role;

    private Instant joinedAt;

    protected FamilyMember() {
    }

    public FamilyMember(Long familyId, Long userId, Role role) {
        this.familyId = familyId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public Long getFamilyId() {
        return familyId;
    }

    public Long getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
