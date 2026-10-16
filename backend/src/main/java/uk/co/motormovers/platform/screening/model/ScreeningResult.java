package uk.co.motormovers.platform.screening.model;

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
 * One scored item for one child. {@code clientId} is generated on the tablet, so a score saved
 * offline and sent twice when the connection returns is stored once.
 */
@Entity
@Table(name = "screening_result")
public class ScreeningResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Child child;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ScreeningItem item;

    private int roundNumber;

    private int score;

    private LocalDate screenedOn;

    private String recordedBy;

    protected ScreeningResult() {
    }

    public ScreeningResult(String clientId, Child child, ScreeningItem item, int roundNumber, int score,
            LocalDate screenedOn, String recordedBy) {
        this.clientId = clientId;
        this.child = child;
        this.item = item;
        this.roundNumber = roundNumber;
        this.score = score;
        this.screenedOn = screenedOn;
        this.recordedBy = recordedBy;
    }

    public Long getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public Child getChild() {
        return child;
    }

    public ScreeningItem getItem() {
        return item;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public int getScore() {
        return score;
    }

    public LocalDate getScreenedOn() {
        return screenedOn;
    }

    public String getRecordedBy() {
        return recordedBy;
    }
}
