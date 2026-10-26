package uk.co.motormovers.platform.assessment.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import uk.co.motormovers.platform.common.model.ColourStage;

/**
 * A programme exercise in a colour (for example Green 3, Starfish), with its four score descriptions and the points
 * it is assessed at: the start of the colour, the end of each module it is in, and the end of the colour.
 */
@Entity
@Table(name = "exercise")
public class Exercise {

    public static final int MIN_SCORE = 1;
    public static final int MAX_SCORE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ColourStage stage;

    private int number;

    private String title;

    private String instructions;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exercise_level", joinColumns = @JoinColumn(name = "exercise_id"))
    @MapKeyColumn(name = "score")
    @Column(name = "description")
    private Map<Integer, String> levels = new TreeMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exercise_point", joinColumns = @JoinColumn(name = "exercise_id"))
    @OrderColumn(name = "position")
    @Column(name = "point")
    private List<String> points = new ArrayList<>();

    protected Exercise() {
    }

    public Exercise(ColourStage stage, int number, String title, String instructions, Map<Integer, String> levels,
            List<String> points) {
        this.stage = stage;
        this.number = number;
        this.title = title;
        this.instructions = instructions;
        this.levels = new TreeMap<>(levels);
        this.points = new ArrayList<>(points);
    }

    public Long getId() {
        return id;
    }

    public ColourStage getStage() {
        return stage;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getInstructions() {
        return instructions;
    }

    public Map<Integer, String> getLevels() {
        return levels;
    }

    public List<String> getPoints() {
        return points;
    }
}
