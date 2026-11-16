package uk.co.motormovers.platform.content.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.common.storage.FileLinks;
import uk.co.motormovers.platform.common.storage.FileTypes;
import uk.co.motormovers.platform.content.model.ProgrammeSession;
import uk.co.motormovers.platform.content.model.UnitFilm;
import uk.co.motormovers.platform.content.repository.ProgrammeSessionRepository;
import uk.co.motormovers.platform.content.repository.UnitFilmRepository;
import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;

/**
 * The film of a colour's rhyme or module (go-live task 57): the team attaches one, and every session of that unit plays
 * it, at school and at home, including sessions added later. A film attached to one session itself still wins there.
 */
@Service
public class UnitFilms {

    /** Shown under a film that has no credit line yet. The agreed wording replaces it; no names belong here. */
    public static final String CREDIT_TO_CONFIRM = "Film credit to be confirmed.";

    /** What a screen plays: a link ready to use, and the line under it. Both null where there is no film. */
    public record Film(String videoUrl, String credit) {

        public static final Film NONE = new Film(null, null);
    }

    private final UnitFilmRepository films;
    private final ProgrammeSessionRepository sessions;
    private final FileLinks files;
    private final WordingGuard wordingGuard;
    private final AccessPolicy access;
    private final Clock clock;

    public UnitFilms(UnitFilmRepository films, ProgrammeSessionRepository sessions, FileLinks files,
            WordingGuard wordingGuard, AccessPolicy access, Clock clock) {
        this.films = films;
        this.sessions = sessions;
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

    /** The film of a rhyme or module, for a home session. Signed here: call it only after the access checks. */
    @Transactional(readOnly = true)
    public Film forUnit(ColourStage stage, int unit) {
        return films.findByStageAndUnit(stage, unit).map(this::film).orElse(Film.NONE);
    }

    @Transactional(readOnly = true)
    public List<UnitFilm> all() {
        access.requireAdmin();
        return films.findAllByOrderByStageAscUnitAsc();
    }

    /** Attaches a film to a unit, in place of any it has. Exactly one of an uploaded file's key and a pasted link. */
    @Transactional
    public UnitFilm attach(ColourStage stage, int unit, String videoKey, String videoUrl, String credit) {
        CurrentUser me = access.requireAdmin();
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
        if (sessions.countByStageAndUnit(stage, unit) == 0) {
            throw new IllegalArgumentException("There are no " + stage.label() + " sessions numbered " + unit + ".");
        }
        UnitFilm film = films.findByStageAndUnit(stage, unit).orElseGet(() -> new UnitFilm(stage, unit));
        // An unchanged key is already saved; a new one must be a file the API handed out and the store holds.
        String saved = key == null || key.equals(film.getVideoKey()) ? key : files.requireKey(key);
        film.attach(saved, url, line, me.email(), clock.instant());
        return films.save(film);
    }

    /** Takes the film off a unit. The uploaded file itself stays in the store. Returns whether there was one. */
    @Transactional
    public boolean remove(ColourStage stage, int unit) {
        access.requireAdmin();
        Optional<UnitFilm> film = films.findByStageAndUnit(stage, unit);
        film.ifPresent(films::delete);
        return film.isPresent();
    }

    /** The link the team opens to check a film. */
    public String link(UnitFilm film) {
        return files.link(film.getVideoKey(), film.getVideoUrl());
    }

    private Film film(UnitFilm film) {
        return new Film(link(film), film.getCredit() == null ? CREDIT_TO_CONFIRM : film.getCredit());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
