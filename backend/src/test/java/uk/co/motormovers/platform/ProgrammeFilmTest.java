package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.assessment.service.SessionPlans;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.common.model.Region;
import uk.co.motormovers.platform.common.storage.UnsavedUploadRepository;
import uk.co.motormovers.platform.content.model.ProgrammeSession;
import uk.co.motormovers.platform.content.repository.ProgrammeSessionRepository;
import uk.co.motormovers.platform.content.repository.ProgrammeFilmRepository;
import uk.co.motormovers.platform.content.service.ProgrammeFilms;
import uk.co.motormovers.platform.school.model.Licence;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.SchoolClass;
import uk.co.motormovers.platform.school.repository.LicenceRepository;
import uk.co.motormovers.platform.school.repository.SchoolClassRepository;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.school.service.PlanService;

/**
 * Go-live task 57: the team attaches one film to a colour's rhyme or module, and every session of it plays the film, at
 * school with a licence and at home with a pass, and still after the session plans load again. From Green to Violet an
 * exercise has its own film, which each step plays. Its own database, as it
 * leaves films on the demo's sessions.
 */
@SpringBootTest(properties = {"platform.login-limit-per-ip=500",
        "spring.datasource.url=jdbc:h2:mem:programmefilm;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"})
@AutoConfigureMockMvc
class ProgrammeFilmTest {

    private static final byte[] FILE = "not really a film".getBytes(StandardCharsets.UTF_8);
    private static final String LOCAL = "/api/files/local/uploads/";
    private static final String FILMS = "/api/admin/content/films";

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    SchoolRepository schools;

    @Autowired
    SchoolClassRepository classes;

    @Autowired
    LicenceRepository licences;

    @Autowired
    PlanService plans;

    @Autowired
    ProgrammeSessionRepository sessions;

    @Autowired
    ProgrammeFilmRepository films;

    @Autowired
    UnsavedUploadRepository unsaved;

    @Autowired
    SessionPlans sessionPlans;

    /** Each test starts with no films: they share the database. */
    @BeforeEach
    void noFilms() {
        films.deleteAll();
    }

    @Test
    void onlyTheTeamAttachesAndRemovesFilms() throws Exception {
        String body = film(ColourStage.RED, 1, null, "https://example.org/film.mp4", null);
        for (String email : List.of("teacher@oakfield.test", "head@oakfield.test", "parent@example.test",
                "home@example.test")) {
            String token = login(email);
            mvc.perform(get(FILMS).header("Authorization", bearer(token))).andExpect(status().isForbidden());
            mvc.perform(put(FILMS).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isForbidden());
            mvc.perform(delete(FILMS + "?stage=RED&unit=1").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put(FILMS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertThat(films.findByStageAndUnit(ColourStage.RED, 1)).isEmpty();
    }

    @Test
    void aFilmIsOneUploadedVideoOrOneHttpsLinkForAUnitThatExists() throws Exception {
        String key = upload("video/mp4");
        attach(ColourStage.RED, 2, null, null, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Upload a film or paste its link, one or the other."));
        attach(ColourStage.RED, 2, key, "https://example.org/film.mp4", null).andExpect(status().isBadRequest());
        attach(ColourStage.RED, 2, null, "http://example.org/film.mp4", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("https://")));
        attach(ColourStage.RED, 2, upload("audio/mpeg"), null, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A film must be an MP4 or WebM video."));
        attach(ColourStage.RED, 2, "uploads/" + UUID.randomUUID() + ".mp4", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Please upload it again")));
        attach(ColourStage.RED, 99, key, null, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("There are no Red sessions numbered 99."));
        attach(ColourStage.RED, 2, key, null, "x".repeat(201)).andExpect(status().isBadRequest());
        assertThat(films.findByStageAndUnit(ColourStage.RED, 2)).isEmpty();

        attach(ColourStage.RED, 2, key, null, "  Filmed for the programme  ").andExpect(status().isOk())
                .andExpect(jsonPath("$.fileLink").value(startsWith(LOCAL)))
                .andExpect(jsonPath("$.uploaded").value(true))
                .andExpect(jsonPath("$.credit").value("Filmed for the programme"))
                .andExpect(jsonPath("$.attachedBy").value("admin@motormovers.test"));
        // The key is saved on an item now, so the clean-up of unsaved uploads leaves the file alone.
        assertThat(unsaved.findById(key)).isEmpty();
        assertThat(unsaved.uses(key)).isEqualTo(1);
        // Saving it again with the same key (a new credit line) needs no new upload; a link replaces the file.
        attach(ColourStage.RED, 2, key, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.credit").doesNotExist());
        attach(ColourStage.RED, 2, null, "https://example.org/film.mp4", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.fileLink").value("https://example.org/film.mp4"))
                .andExpect(jsonPath("$.uploaded").value(false));
        mvc.perform(get(FILMS).header("Authorization", bearer(admin())))
                .andExpect(jsonPath("$[?(@.stage == 'RED' && @.unit == 2)].fileLink")
                        .value("https://example.org/film.mp4"));
        remove(ColourStage.RED, 2).andExpect(jsonPath("$.removed").value(true));
        remove(ColourStage.RED, 2).andExpect(jsonPath("$.removed").value(false));
    }

    @Test
    void aLicensedClassPlaysItsRhymesFilmAndAnUnlicensedOneGetsNoLink() throws Exception {
        LocalDate today = LocalDate.now(clock);
        SchoolClass licensed = classWith(ColourStage.ORANGE, today.minusMonths(1), today.plusMonths(1));
        SchoolClass expired = classWith(ColourStage.ORANGE, today.minusYears(1), today.minusDays(1));
        assertThat((Object) JsonPath.read(today(licensed), "$.nextSession.videoUrl")).isNull();

        attach(ColourStage.ORANGE, 1, upload("video/mp4"), null, null).andExpect(status().isOk());
        String card = today(licensed);
        assertThat((String) JsonPath.read(card, "$.nextSession.label")).contains("Module 1 · Day 1");
        assertThat((String) JsonPath.read(card, "$.nextSession.videoUrl")).startsWith(LOCAL);
        assertThat((String) JsonPath.read(card, "$.nextSession.filmCredit")).isEqualTo(ProgrammeFilms.DEFAULT_CREDIT);
        assertThat(today(expired)).doesNotContain(LOCAL);
        // The link plays without a sign-in, as a video element needs, and seeks with ranges.
        String link = JsonPath.read(card, "$.nextSession.videoUrl");
        mvc.perform(get(URI.create(link)).header("Range", "bytes=0-2")).andExpect(status().isPartialContent());

        attach(ColourStage.ORANGE, 1, null, "https://example.org/orange-1.mp4", "Filmed by a class teacher")
                .andExpect(status().isOk());
        card = today(licensed);
        assertThat((String) JsonPath.read(card, "$.nextSession.videoUrl")).isEqualTo("https://example.org/orange-1.mp4");
        assertThat((String) JsonPath.read(card, "$.nextSession.filmCredit")).isEqualTo("Filmed by a class teacher");

        // Every session of the rhyme has it, the next rhyme's have none, and a session's own film wins.
        ProgrammeFilms.Film none = new ProgrammeFilms.Film(null, null);
        assertThat(sessions.findByStage(ColourStage.ORANGE)).allSatisfy(s -> assertThat(filmOf(s))
                .isEqualTo(s.getUnit() == 1 ? new ProgrammeFilms.Film("https://example.org/orange-1.mp4",
                        "Filmed by a class teacher") : none));
        ProgrammeSession first = session(ColourStage.ORANGE, 1, 1);
        first.setVideoKey(upload("video/mp4"));
        sessions.save(first);
        card = today(licensed);
        assertThat((String) JsonPath.read(card, "$.nextSession.videoUrl")).startsWith(LOCAL);
        assertThat((Object) JsonPath.read(card, "$.nextSession.filmCredit")).isNull();
        first.setVideoKey(null);
        sessions.save(first);

        remove(ColourStage.ORANGE, 1).andExpect(status().isOk());
        assertThat((Object) JsonPath.read(today(licensed), "$.nextSession.videoUrl")).isNull();
    }

    @Test
    void theFilmIsKeptWhenTheSessionPlansLoadAgain() throws Exception {
        attach(ColourStage.YELLOW, 18, upload("video/mp4"), null, null).andExpect(status().isOk());
        ProgrammeSession last = session(ColourStage.YELLOW, 18, 5);
        sessions.delete(last);

        sessionPlans.loadMissing();

        ProgrammeSession again = session(ColourStage.YELLOW, 18, 5);
        assertThat(again.getId()).isNotEqualTo(last.getId());
        assertThat(filmOf(again).videoUrl()).startsWith(LOCAL);
        assertThat(films.findByStageAndUnit(ColourStage.YELLOW, 18)).isPresent();
    }

    @Test
    void aFamilyWithAPassPlaysTheModulesFilmAndALapsedOneDoesNot() throws Exception {
        // Priya's pass runs and Ava is in Green module 2.
        String priya = login("home@example.test");
        Integer ava = homeChild(priya, "Ava");
        attach(ColourStage.GREEN, 2, upload("video/mp4"), null, "Filmed for the programme").andExpect(status().isOk());
        homeToday(priya, ava).andExpect(jsonPath("$.module").value(2))
                .andExpect(jsonPath("$.videoUrl").value(startsWith(LOCAL)))
                .andExpect(jsonPath("$.filmCredit").value("Filmed for the programme"));
        remove(ColourStage.GREEN, 2);
        attach(ColourStage.GREEN, 1, upload("video/mp4"), null, null).andExpect(status().isOk());
        homeToday(priya, ava).andExpect(jsonPath("$.videoUrl").doesNotExist())
                .andExpect(jsonPath("$.filmCredit").doesNotExist());

        // Tom's pass has ended: a film for exactly where Noah is still sends no link.
        String tom = login("lapsed@example.test");
        Integer noah = homeChild(tom, "Noah");
        String locked = homeToday(tom, noah).andReturn().getResponse().getContentAsString();
        ColourStage stage = ColourStage.valueOf(JsonPath.read(locked, "$.stage"));
        int module = JsonPath.read(locked, "$.module");
        attach(stage, module, upload("video/mp4"), null, null).andExpect(status().isOk());
        String still = homeToday(tom, noah).andExpect(jsonPath("$.passRunning").value(false))
                .andExpect(jsonPath("$.videoUrl").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(still).doesNotContain(LOCAL);

        // A parent whose child is linked to a school plays the film of where the child is; another family is refused.
        String jo = login("parent@example.test");
        Integer linked = JsonPath.read(mvc.perform(get("/api/family/children").header("Authorization", bearer(jo)))
                .andReturn().getResponse().getContentAsString(), "$[0].childId");
        String theirs = homeToday(jo, linked).andReturn().getResponse().getContentAsString();
        attach(ColourStage.valueOf(JsonPath.read(theirs, "$.stage")), JsonPath.read(theirs, "$.module"), null,
                "https://example.org/linked.mp4", null).andExpect(status().isOk());
        homeToday(jo, linked).andExpect(jsonPath("$.videoUrl").value("https://example.org/linked.mp4"));
        mvc.perform(get("/api/home/children/" + ava + "/today").header("Authorization", bearer(jo)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anExerciseHasItsOwnFilmFromGreenToVioletAndEachStepPlaysIt() throws Exception {
        LocalDate today = LocalDate.now(clock);
        SchoolClass licensed = classWith(ColourStage.GREEN, today.minusMonths(1), today.plusMonths(1));
        SchoolClass expired = classWith(ColourStage.GREEN, today.minusYears(1), today.minusDays(1));
        List<String> titles = JsonPath.read(today(licensed), "$.nextSession.steps[*].title");
        Exercise first = exercises.findByStageAndTitleIn(ColourStage.GREEN, List.of(titles.getFirst())).getFirst();

        // Only the team, one target at a time, and a rhyme colour's film goes on the rhyme.
        String teacher = login("teacher@oakfield.test");
        mvc.perform(get(FILMS + "/exercises?stage=GREEN").header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden());
        mvc.perform(put(FILMS).header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content(exerciseFilm(first.getId(), "https://example.org/x.mp4"))).andExpect(status().isForbidden());
        mvc.perform(get(FILMS + "/exercises?stage=GREEN").header("Authorization", bearer(admin())))
                .andExpect(jsonPath("$.length()").value(27))
                .andExpect(jsonPath("$[0].number").value(1));
        attachJson("{\"stage\":\"GREEN\",\"unit\":1,\"exerciseId\":" + first.getId()
                + ",\"videoUrl\":\"https://example.org/x.mp4\"}").andExpect(status().isBadRequest());
        attachJson("{\"videoUrl\":\"https://example.org/x.mp4\"}").andExpect(status().isBadRequest());
        Exercise rhyme = exercises.findByStageOrderByNumber(ColourStage.ORANGE).getFirst();
        attachJson(exerciseFilm(rhyme.getId(), "https://example.org/x.mp4")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Orange has one film for each rhyme. Attach it to the rhyme."));
        attachJson(exerciseFilm(999_999L, "https://example.org/x.mp4")).andExpect(status().isNotFound());

        String key = upload("video/mp4");
        attachJson("{\"exerciseId\":" + first.getId() + ",\"videoKey\":\"" + key + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("GREEN"))
                .andExpect(jsonPath("$.exerciseId").value(first.getId()))
                .andExpect(jsonPath("$.unit").doesNotExist())
                .andExpect(jsonPath("$.creditShown").value(ProgrammeFilms.DEFAULT_CREDIT));
        assertThat(unsaved.uses(key)).isEqualTo(1);

        // The step for that exercise plays it; the other steps and the session as a whole have none.
        String card = today(licensed);
        assertThat((String) JsonPath.read(card, "$.nextSession.steps[0].videoUrl")).startsWith(LOCAL);
        assertThat((String) JsonPath.read(card, "$.nextSession.steps[0].filmCredit"))
                .isEqualTo(ProgrammeFilms.DEFAULT_CREDIT);
        assertThat((Object) JsonPath.read(card, "$.nextSession.steps[1].videoUrl")).isNull();
        assertThat((Object) JsonPath.read(card, "$.nextSession.videoUrl")).isNull();
        assertThat(today(expired)).doesNotContain(LOCAL);

        // At home, Ava's Green session carries it for that exercise if it is in her session; Tom's locked one never.
        String priya = login("home@example.test");
        Integer ava = homeChild(priya, "Ava");
        String hers = homeToday(priya, ava).andReturn().getResponse().getContentAsString();
        Integer inSession = JsonPath.<List<Integer>>read(hers, "$.exercises[*].id").getFirst();
        attachJson(exerciseFilm(inSession.longValue(), "https://example.org/home.mp4")).andExpect(status().isOk());
        homeToday(priya, ava)
                .andExpect(jsonPath("$.exerciseFilms[?(@.exerciseId == " + inSession + ")].videoUrl")
                        .value("https://example.org/home.mp4"))
                .andExpect(jsonPath("$.exerciseFilms[?(@.exerciseId == " + inSession + ")].filmCredit")
                        .value(ProgrammeFilms.DEFAULT_CREDIT));
        String tom = login("lapsed@example.test");
        homeToday(tom, homeChild(tom, "Noah")).andExpect(jsonPath("$.exerciseFilms.length()").value(0));

        mvc.perform(delete(FILMS + "?exerciseId=" + first.getId()).header("Authorization", bearer(admin())))
                .andExpect(jsonPath("$.removed").value(true));
        assertThat(today(licensed)).doesNotContain(LOCAL);
        mvc.perform(delete(FILMS).header("Authorization", bearer(admin()))).andExpect(status().isBadRequest());
    }

    private ResultActions attachJson(String body) throws Exception {
        return mvc.perform(put(FILMS).header("Authorization", bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String exerciseFilm(Long exerciseId, String link) {
        return "{\"exerciseId\":" + exerciseId + ",\"videoUrl\":\"" + link + "\"}";
    }

    @Autowired
    ExerciseRepository exercises;

    private ProgrammeFilms.Film filmOf(ProgrammeSession session) {
        return programmeFilms.forSession(session);
    }

    @Autowired
    ProgrammeFilms programmeFilms;

    private ProgrammeSession session(ColourStage stage, int unit, int day) {
        return sessions.findByStage(stage).stream().filter(s -> s.getUnit() == unit && s.getDay() == day).findFirst()
                .orElseThrow();
    }

    private SchoolClass classWith(ColourStage stage, LocalDate from, LocalDate to) {
        School school = schools.save(new School("Films " + UUID.randomUUID(), null, Region.UK));
        licences.save(new Licence(school, plans.find("TRUST"), 0, from, to, EnumSet.of(stage)));
        return classes.save(new SchoolClass(school, "Films F", "Year 1", null, stage));
    }

    private String today(SchoolClass schoolClass) throws Exception {
        return mvc.perform(get("/api/classes/" + schoolClass.getId() + "/today").header("Authorization", bearer(admin())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions homeToday(String token, Integer child) throws Exception {
        return mvc.perform(get("/api/home/children/" + child + "/today").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private Integer homeChild(String token, String name) throws Exception {
        String home = mvc.perform(get("/api/home").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Integer>>read(home, "$.children[?(@.name == '" + name + "')].id").getFirst();
    }

    private ResultActions attach(ColourStage stage, int unit, String key, String link, String credit) throws Exception {
        return mvc.perform(put(FILMS).header("Authorization", bearer(admin())).contentType(MediaType.APPLICATION_JSON)
                .content(film(stage, unit, key, link, credit)));
    }

    private ResultActions remove(ColourStage stage, int unit) throws Exception {
        return mvc.perform(delete(FILMS + "?stage=" + stage + "&unit=" + unit).header("Authorization", bearer(admin())));
    }

    private static String film(ColourStage stage, int unit, String key, String link, String credit) {
        return "{\"stage\":\"" + stage + "\",\"unit\":" + unit
                + (key == null ? "" : ",\"videoKey\":\"" + key + "\"")
                + (link == null ? "" : ",\"videoUrl\":\"" + link + "\"")
                + (credit == null ? "" : ",\"credit\":\"" + credit + "\"") + "}";
    }

    /** Uploads a file the way the browser does, and returns its key. */
    private String upload(String type) throws Exception {
        String body = mvc.perform(post("/api/admin/files").header("Authorization", bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"" + type + "\",\"size\":" + FILE.length + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(put(URI.create(JsonPath.<String>read(body, "$.url"))).contentType(type).content(FILE))
                .andExpect(status().isOk());
        return JsonPath.read(body, "$.key");
    }

    private String admin() throws Exception {
        return login("admin@motormovers.test");
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
