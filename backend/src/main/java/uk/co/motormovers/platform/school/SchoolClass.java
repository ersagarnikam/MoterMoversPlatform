package uk.co.motormovers.platform.school;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import uk.co.motormovers.platform.common.ColourStage;
import uk.co.motormovers.platform.user.AppUser;

@Entity
@Table(name = "school_class")
public class SchoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private School school;

    private String name;

    private String yearGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser teacher;

    @Enumerated(EnumType.STRING)
    private ColourStage stage;

    protected SchoolClass() {
    }

    public SchoolClass(School school, String name, String yearGroup, AppUser teacher, ColourStage stage) {
        this.school = school;
        this.name = name;
        this.yearGroup = yearGroup;
        this.teacher = teacher;
        this.stage = stage;
    }

    public Long getId() {
        return id;
    }

    public School getSchool() {
        return school;
    }

    public String getName() {
        return name;
    }

    public String getYearGroup() {
        return yearGroup;
    }

    public AppUser getTeacher() {
        return teacher;
    }

    public ColourStage getStage() {
        return stage;
    }

    public void moveToStage(ColourStage stage) {
        this.stage = stage;
    }
}
