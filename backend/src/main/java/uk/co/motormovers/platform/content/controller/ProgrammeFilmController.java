package uk.co.motormovers.platform.content.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.ProgrammeFilm;
import uk.co.motormovers.platform.content.service.ProgrammeFilms;

/**
 * The team's films: one for a rhyme or module of a colour, or one for an exercise of a colour's grid. Mapped under
 * /api/admin (ADMIN only).
 */
@RestController
@RequestMapping("/api/admin/content/films")
public class ProgrammeFilmController {

    private final ProgrammeFilms films;

    public ProgrammeFilmController(ProgrammeFilms films) {
        this.films = films;
    }

    /**
     * unit or exerciseId says what the film belongs to. uploaded: the film is a file in the store, not a pasted link.
     * credit is what the team typed, null for the default; creditShown is the line the screens show.
     */
    public record FilmView(ColourStage stage, Integer unit, Long exerciseId, String fileLink, boolean uploaded,
            String credit, String creditShown, String attachedBy, Instant attachedAt) {
    }

    /** A rhyme or module (stage and unit), or an exercise (exerciseId): one or the other. */
    public record AttachFilm(ColourStage stage, @Min(1) Integer unit, Long exerciseId, String videoKey,
            String videoUrl, String credit) {
    }

    public record Removed(boolean removed) {
    }

    public record ExerciseRow(Long id, int number, String title) {
    }

    @GetMapping
    public List<FilmView> list() {
        return films.all().stream().map(this::view).toList();
    }

    /** A colour's grid exercises, in order, to attach films to. */
    @GetMapping("/exercises")
    public List<ExerciseRow> exercises(@RequestParam ColourStage stage) {
        return films.exercisesOf(stage).stream()
                .map(e -> new ExerciseRow(e.getId(), e.getNumber(), e.getTitle())).toList();
    }

    @PutMapping
    public FilmView attach(@Valid @RequestBody AttachFilm request) {
        if (request.exerciseId() != null && request.unit() == null) {
            return view(films.attachToExercise(request.exerciseId(), request.videoKey(), request.videoUrl(),
                    request.credit()));
        }
        if (request.exerciseId() == null && request.unit() != null && request.stage() != null) {
            return view(films.attachToUnit(request.stage(), request.unit(), request.videoKey(), request.videoUrl(),
                    request.credit()));
        }
        throw new IllegalArgumentException("Say which rhyme or module, or which exercise, the film is for.");
    }

    @DeleteMapping
    public Removed remove(@RequestParam(required = false) ColourStage stage,
            @RequestParam(required = false) Integer unit, @RequestParam(required = false) Long exerciseId) {
        if (exerciseId != null && unit == null) {
            return new Removed(films.removeFromExercise(exerciseId));
        }
        if (exerciseId == null && unit != null && stage != null) {
            return new Removed(films.removeFromUnit(stage, unit));
        }
        throw new IllegalArgumentException("Say which rhyme or module, or which exercise, the film is for.");
    }

    private FilmView view(ProgrammeFilm film) {
        return new FilmView(film.getStage(), film.getUnit(), film.getExerciseId(), films.link(film),
                film.getVideoKey() != null, film.getCredit(), ProgrammeFilms.creditShown(film), film.getAttachedBy(),
                film.getAttachedAt());
    }
}
