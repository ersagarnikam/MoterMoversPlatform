package uk.co.motormovers.platform.training.model;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** One multiple-choice question. The options are stored one per line; {@code correctOption} counts from 0. */
@Entity
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CourseQuiz quiz;

    private int position;

    private String text;

    private String options;

    private int correctOption;

    protected QuizQuestion() {
    }

    public QuizQuestion(CourseQuiz quiz, int position, String text, List<String> options, int correctOption) {
        this.quiz = quiz;
        this.position = position;
        this.text = text;
        this.options = String.join("\n", options);
        this.correctOption = correctOption;
    }

    public Long getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getText() {
        return text;
    }

    public List<String> options() {
        return List.of(options.split("\n"));
    }

    public int getCorrectOption() {
        return correctOption;
    }
}
