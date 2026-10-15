package uk.co.motormovers.platform.screening;

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
import jakarta.persistence.Table;

/** One movement the teacher watches, with what to look for and a short scoring scale (0 to 3). */
@Entity
@Table(name = "screening_item")
public class ScreeningItem {

    public static final int MAX_SCORE = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    private int position;

    @Enumerated(EnumType.STRING)
    private MotorArea area;

    private String title;

    private String instructions;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "screening_item_level", joinColumns = @JoinColumn(name = "item_id"))
    @MapKeyColumn(name = "score")
    @Column(name = "description")
    private Map<Integer, String> levels = new TreeMap<>();

    protected ScreeningItem() {
    }

    public ScreeningItem(String code, int position, MotorArea area, String title, String instructions,
            Map<Integer, String> levels) {
        this.code = code;
        this.position = position;
        this.area = area;
        this.title = title;
        this.instructions = instructions;
        this.levels = new TreeMap<>(levels);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getPosition() {
        return position;
    }

    public MotorArea getArea() {
        return area;
    }

    public String getTitle() {
        return title;
    }

    public String getInstructions() {
        return instructions;
    }

    public Map<Integer, String> getLevels() {
        return new TreeMap<>(levels);
    }
}
