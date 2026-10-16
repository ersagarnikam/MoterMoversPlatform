package uk.co.motormovers.platform.family.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.HomeActivity;

/** A home activity a parent marked as done. The parent's own record: it is not shown to the school. */
@Entity
@Table(name = "home_session_record")
public class HomeSessionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clientId;

    private Long parentId;

    private Long childId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private HomeActivity activity;

    @Enumerated(EnumType.STRING)
    private ColourStage stage;

    private LocalDate doneOn;

    protected HomeSessionRecord() {
    }

    public HomeSessionRecord(String clientId, Long parentId, Long childId, HomeActivity activity, LocalDate doneOn) {
        this.clientId = clientId;
        this.parentId = parentId;
        this.childId = childId;
        this.activity = activity;
        this.stage = activity.getStage();
        this.doneOn = doneOn;
    }

    public Long getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public Long getParentId() {
        return parentId;
    }

    public Long getChildId() {
        return childId;
    }

    public HomeActivity getActivity() {
        return activity;
    }

    public ColourStage getStage() {
        return stage;
    }

    public LocalDate getDoneOn() {
        return doneOn;
    }
}
