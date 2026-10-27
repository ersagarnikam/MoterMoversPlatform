package uk.co.motormovers.platform.home.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.common.model.ColourStage;

/**
 * A daily home session with Pat's exercises. {@code clientId} comes from the phone, so a session saved offline and
 * sent twice is stored once.
 */
@Entity
@Table(name = "home_programme_session")
public class HomeProgrammeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clientId;

    private Long childId;

    @Enumerated(EnumType.STRING)
    private ColourStage stage;

    private LocalDate doneOn;

    private String recordedBy;

    protected HomeProgrammeSession() {
    }

    public HomeProgrammeSession(String clientId, Long childId, ColourStage stage, LocalDate doneOn,
            String recordedBy) {
        this.clientId = clientId;
        this.childId = childId;
        this.stage = stage;
        this.doneOn = doneOn;
        this.recordedBy = recordedBy;
    }

    public Long getId() {
        return id;
    }

    public Long getChildId() {
        return childId;
    }

    public ColourStage getStage() {
        return stage;
    }

    public LocalDate getDoneOn() {
        return doneOn;
    }
}
