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
import jakarta.validation.constraints.NotNull;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.UnitFilm;
import uk.co.motormovers.platform.content.service.UnitFilms;

/** The team's films, one for each rhyme or module of a colour. Mapped under /api/admin (ADMIN only). */
@RestController
@RequestMapping("/api/admin/content/films")
public class UnitFilmController {

    private final UnitFilms films;

    public UnitFilmController(UnitFilms films) {
        this.films = films;
    }

    /** uploaded: the film is a file in the store, not a pasted link. credit is null until the team types one. */
    public record FilmView(ColourStage stage, int unit, String fileLink, boolean uploaded, String credit,
            String attachedBy, Instant attachedAt) {
    }

    public record AttachFilm(@NotNull ColourStage stage, @Min(1) int unit, String videoKey, String videoUrl,
            String credit) {
    }

    public record Removed(boolean removed) {
    }

    @GetMapping
    public List<FilmView> list() {
        return films.all().stream().map(this::view).toList();
    }

    @PutMapping
    public FilmView attach(@Valid @RequestBody AttachFilm request) {
        return view(films.attach(request.stage(), request.unit(), request.videoKey(), request.videoUrl(),
                request.credit()));
    }

    @DeleteMapping
    public Removed remove(@RequestParam ColourStage stage, @RequestParam int unit) {
        return new Removed(films.remove(stage, unit));
    }

    private FilmView view(UnitFilm film) {
        return new FilmView(film.getStage(), film.getUnit(), films.link(film), film.getVideoKey() != null,
                film.getCredit(), film.getAttachedBy(), film.getAttachedAt());
    }
}
