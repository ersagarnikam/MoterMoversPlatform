package uk.co.motormovers.platform.content.service;

import java.time.Clock;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.common.Brand;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.common.storage.FileLinks;
import uk.co.motormovers.platform.common.storage.FileTypes;
import uk.co.motormovers.platform.content.model.ProgrammeFilm;
import uk.co.motormovers.platform.content.model.ProgrammeSession;
import uk.co.motormovers.platform.content.repository.ProgrammeFilmRepository;
import uk.co.motormovers.platform.content.repository.ProgrammeSessionRepository;
import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;

/**
 * The programme's films (go-live task 57). The team attaches a film to a colour's rhyme or module, which every session
 * of it then plays, or to one exercise of a colour's grid from Green to Violet, which each step of a session then
 * plays as its own. Both reach school and home sessions, including sessions added later. A film attached to one session
 * itself still wins over its rhyme's or module's.
 */
@Service
public class ProgrammeFilms {

    /** Shown under a film whose credit line the team has not typed. */
    public static final String DEFAULT_CREDIT = "Film: " + Brand.NAME;

    /** The colours whose unit is one rhyme, with one film for the rhyme; the others have a film for each exercise. */
    public static final Set<ColourStage> RHYME_COLOURS = EnumSet.of(ColourStage.RED, ColourStage.ORANGE,
            ColourStage.YELLOW);

    /** What a screen plays: a link ready to use, and the line under it. Both null where there is no film. */
    public record Film(String videoUrl, String credit) {

        public static final Film NONE = new Film(null, null);
    }

    private final ProgrammeFilmRepository films;
    private final ProgrammeSessionRepository sessions;
    private final ExerciseRepository exercises;
    private final FileLinks files;
    private final WordingGuard wordingGuard;
    private final AccessPolicy access;
    private final Clock clock;

    public ProgrammeFilms(ProgrammeFilmRepository films, ProgrammeSessionRepository sessions,
            ExerciseRepository exercises, FileLinks files, WordingGuard wordingGuard, AccessPolicy access,
            Clock clock) {
        this.films = films;
        this.sessions = sessions;
        this.exercises = exercises;
        this.files = files;
        this.wordingGuard = wordingGuard;
        this.access = access;
        this.clock = clock;
    }

    /**
     * The film a class session plays: its own, else its unit's. The link is signed here, so call this only after the
     * caller's access checks (a licence for the colour).
     */
    @Transactional(readOnly = true)
    public Film forSession(ProgrammeSession session) {
        if (session.getVideoKey() != null || session.getVideoUrl() != null) {
            return new Film(files.link(session.getVideoKey(), session.getVideoUrl()), null);
        }
        return forUnit(session.getStage(), session.getUnit());
    }

    /** The film of a rhyme or module. Signed here: call it only after the access checks. */
    @Transactional(readOnly = true)
    public Film forUnit(ColourStage stage, int unit) {
        return films.findByStageAndUnit(stage, unit).map(this::film).orElse(Film.NONE);
    }

    /** The films of these exercises, by exercise id; one with none is left out. Signed here, as above. */
    @Transactional(readOnly = true)
    public Map<Long, Film> forExercises(Collection<Long> exerciseIds) {
        if (exerciseIds.isEmpty()) {
            return Map.of();
        }
        return films.findByExerciseIdIn(exerciseIds).stream()
                .collect(Collectors.toMap(ProgrammeFilm::getExerciseId, this::film));
    }

    @Transactional(readOnly = true)
    public List<ProgrammeFilm> all() {
        access.requireAdmin();
        return films.findAllByOrderByStageAscUnitAscIdAsc();
    }

    /** The exercises of a colour's grid, in order, for the team to attach films to. */
    @Transactional(readOnly = true)
    public List<Exercise> exercisesOf(ColourStage stage) {
        access.requireAdmin();
        return exercises.findByStageOrderByNumber(stage);
    }

    /** Attaches a film to a rhyme or module, in place of any it has. */
    @Transactional
    public ProgrammeFilm attachToUnit(ColourStage stage, int unit, String videoKey, String videoUrl, String credit) {
        CurrentUser me = access.requireAdmin();
        if (sessions.countByStageAndUnit(stage, unit) == 0) {
            throw new IllegalArgumentException("There are no " + stage.label() + " sessions numbered " + unit + ".");
        }
        ProgrammeFilm film = films.findByStageAndUnit(stage, unit).orElseGet(() -> ProgrammeFilm.forUnit(stage, unit));
        return save(film, videoKey, videoUrl, credit, me);
    }

    /** Attaches a film to one exercise of a colour from Green to Violet, in place of any it has. */
    @Transactional
    public ProgrammeFilm attachToExercise(Long exerciseId, String videoKey, String videoUrl, String credit) {
        CurrentUser me = access.requireAdmin();
        Exercise exercise = exercises.findById(exerciseId).orElseThrow(() -> new NotFoundException("Exercise", exerciseId));
        if (RHYME_COLOURS.contains(exercise.getStage())) {
            throw new IllegalArgumentException(exercise.getStage().label()
                    + " has one film for each rhyme. Attach it to the rhyme.");
        }
        ProgrammeFilm film = films.findByExerciseId(exerciseId)
                .orElseGet(() -> ProgrammeFilm.forExercise(exercise.getStage(), exerciseId));
        return save(film, videoKey, videoUrl, credit, me);
    }

    /** Takes the film off a rhyme or module. The uploaded file itself stays in the store. */
    @Transactional
    public boolean removeFromUnit(ColourStage stage, int unit) {
        access.requireAdmin();
        return remove(films.findByStageAndUnit(stage, unit));
    }

    @Transactional
    public boolean removeFromExercise(Long exerciseId) {
        access.requireAdmin();
        return remove(films.findByExerciseId(exerciseId));
    }

    /** The link the team opens to check a film. */
    public String link(ProgrammeFilm film) {
        return files.link(film.getVideoKey(), film.getVideoUrl());
    }

    /** The line shown under a film: the team's, or the default. */
    public static String creditShown(ProgrammeFilm film) {
        return film.getCredit() == null ? DEFAULT_CREDIT : film.getCredit();
    }

    /** Exactly one of an uploaded video's key and a pasted https link, with an optional credit line. */
    private ProgrammeFilm save(ProgrammeFilm film, String videoKey, String videoUrl, String credit, CurrentUser me) {
        String key = blankToNull(videoKey);
        String url = blankToNull(videoUrl);
        String line = blankToNull(credit);
        if ((key == null) == (url == null)) {
            throw new IllegalArgumentException("Upload a film or paste its link, one or the other.");
        }
        if (url != null && (!url.startsWith("https://") || url.length() > 500)) {
            throw new IllegalArgumentException("A film's link must be an https:// address of up to 500 characters.");
        }
        if (key != null && FileTypes.isKey(key) && !FileTypes.servedAs(key).startsWith("video/")) {
            throw new IllegalArgumentException("A film must be an MP4 or WebM video.");
        }
        if (line != null) {
            if (line.length() > 200) {
                throw new IllegalArgumentException("The credit line can be up to 200 characters.");
            }
            wordingGuard.requireClean(List.of(line));
        }
        // An unchanged key is already saved; a new one must be a file the API handed out and the store holds.
        String saved = key == null || key.equals(film.getVideoKey()) ? key : files.requireKey(key);
        film.attach(saved, url, line, me.email(), clock.instant());
        return films.save(film);
    }

    private boolean remove(Optional<ProgrammeFilm> film) {
        film.ifPresent(films::delete);
        return film.isPresent();
    }

    private Film film(ProgrammeFilm film) {
        return new Film(link(film), creditShown(film));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
