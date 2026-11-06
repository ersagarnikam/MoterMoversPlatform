package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.common.model.Region;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.school.model.Licence;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.repository.LicenceRepository;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.school.service.PlanService;
import uk.co.motormovers.platform.training.model.InductionAudience;
import uk.co.motormovers.platform.training.model.InductionLesson;
import uk.co.motormovers.platform.training.repository.InductionLessonRepository;
import uk.co.motormovers.platform.training.repository.InductionProgressRepository;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * Staff induction films are for the team and staff at licensed schools. Everyone else who takes the induction keeps
 * the lesson, without the film. The lessons added here are removed after each test, as the database is shared.
 */
@SpringBootTest(properties = "platform.login-limit-per-ip=500")
@AutoConfigureMockMvc
class InductionFilmsTest {

    private static final String FILM = "https://films.example.test/lesson.mp4";

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    SchoolRepository schools;

    @Autowired
    LicenceRepository licences;

    @Autowired
    PlanService plans;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    InductionLessonRepository lessons;

    @Autowired
    InductionProgressRepository progress;

    private InductionLesson staffLesson;
    private InductionLesson parentLesson;
    private LocalDate today;

    @BeforeEach
    void addLessons() {
        today = LocalDate.now(clock);
        staffLesson = lessons.save(approved(new InductionLesson(InductionAudience.STAFF, 901, "Staff film", "A film",
                "Watch the warm up.", 5, FILM)));
        parentLesson = lessons.save(approved(new InductionLesson(InductionAudience.PARENT, 901, "Parent film",
                "A film", "Watch the warm up.", 5, FILM)));
    }

    @AfterEach
    void removeLessons() {
        progress.findAll().stream().filter(p -> p.getLessonId().equals(staffLesson.getId())).forEach(progress::delete);
        lessons.delete(staffLesson);
        lessons.delete(parentLesson);
    }

    @Test
    void theTeamAndStaffAtALicensedSchoolGetTheFilm() throws Exception {
        for (String email : List.of("admin@motormovers.test", "teacher@oakfield.test", "head@oakfield.test")) {
            assertWatches(login(email));
        }
    }

    @Test
    void aLicenceCountsFromItsFirstDayToItsLast() throws Exception {
        assertWatches(staffAt(school(new Dates(today, today.plusMonths(1))), Role.TEACHER));
        assertWatches(staffAt(school(new Dates(today.minusMonths(1), today)), Role.HEAD));
        assertWatches(staffAt(school(new Dates(today.minusMonths(2), today.minusMonths(1)),
                new Dates(today.minusDays(1), today.plusDays(1))), Role.TEACHER));
    }

    @Test
    void staffWithNoLicenceRunningKeepTheLessonWithoutTheFilm() throws Exception {
        assertHeld(staffAt(school(), Role.TEACHER));
        assertHeld(staffAt(school(), Role.HEAD));
        assertHeld(staffAt(school(new Dates(today.minusMonths(2), today.minusDays(1))), Role.TEACHER));
        assertHeld(staffAt(school(new Dates(today.plusDays(1), today.plusMonths(2))), Role.TEACHER));
    }

    @Test
    void peopleOutsideASchoolKeepTheLessonWithoutTheFilm() throws Exception {
        for (String email : List.of("trust@northbridge.test", "trainer@motormovers.test",
                "practitioner@example.test")) {
            assertHeld(login(email));
        }
    }

    @Test
    void onlyTeachersAndHeadsWatchThroughTheirSchool() throws Exception {
        School licensed = school(new Dates(today.minusMonths(1), today.plusMonths(1)));
        assertHeld(staffAt(licensed, Role.TRUST_LEAD));
        assertHeld(staffAt(licensed, Role.PRACTITIONER));
        assertHeld(staffAt(licensed, Role.TRAINER));
    }

    @Test
    void completingALessonDoesNotHandOverTheFilm() throws Exception {
        String token = staffAt(school(), Role.TEACHER);
        String body = mvc.perform(post("/api/training/induction/lessons/" + staffLesson.getId() + "/complete")
                .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        Map<String, Object> lesson = lesson(body, staffLesson);
        assertThat(lesson.get("done")).isEqualTo(true);
        assertThat(lesson.get("videoUrl")).isNull();
        assertThat(body).doesNotContain(FILM);
    }

    @Test
    void theTeamsLessonListStaysWithTheTeam() throws Exception {
        assertThat(fetch(login("admin@motormovers.test"), "/api/admin/induction")).contains(FILM);
        for (String email : List.of("trust@northbridge.test", "trainer@motormovers.test", "teacher@oakfield.test")) {
            mvc.perform(get("/api/admin/induction").header("Authorization", bearer(login(email))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void parentsAreStillRefusedAndKeepTheirOwnFilm() throws Exception {
        String parent = login("parent@example.test");
        mvc.perform(get("/api/training/induction").header("Authorization", bearer(parent)))
                .andExpect(status().isForbidden());

        Map<String, Object> lesson = lesson(fetch(parent, "/api/home/induction"), parentLesson);
        assertThat(lesson.get("videoUrl")).isEqualTo(FILM);
        assertThat(lesson.get("filmHeld")).isEqualTo(false);
    }

    @Test
    void aLessonWithNoFilmHasNothingToHold() throws Exception {
        String body = fetch(staffAt(school(), Role.TEACHER), "/api/training/induction");
        List<Boolean> others = JsonPath.read(body, "$.lessons[?(@.id != " + staffLesson.getId() + ")].filmHeld");
        assertThat(others).isNotEmpty().containsOnly(false);
    }

    private void assertWatches(String token) throws Exception {
        Map<String, Object> lesson = lesson(fetch(token, "/api/training/induction"), staffLesson);
        assertThat(lesson.get("videoUrl")).isEqualTo(FILM);
        assertThat(lesson.get("filmHeld")).isEqualTo(false);
    }

    /** The whole lesson is there, the film nowhere in the answer. */
    private void assertHeld(String token) throws Exception {
        String body = fetch(token, "/api/training/induction");
        Map<String, Object> lesson = lesson(body, staffLesson);
        assertThat(lesson.get("videoUrl")).isNull();
        assertThat(lesson.get("filmHeld")).isEqualTo(true);
        assertThat(lesson.get("title")).isEqualTo("Staff film");
        assertThat(lesson.get("paragraphs")).isEqualTo(List.of("Watch the warm up."));
        assertThat(body).doesNotContain(FILM);
    }

    private Map<String, Object> lesson(String body, InductionLesson lesson) {
        return JsonPath.<List<Map<String, Object>>>read(body, "$.lessons[?(@.id == " + lesson.getId() + ")]")
                .getFirst();
    }

    private static InductionLesson approved(InductionLesson lesson) {
        lesson.review(ContentStatus.APPROVED, "team@motormovers.test");
        return lesson;
    }

    private record Dates(LocalDate from, LocalDate to) {
    }

    private School school(Dates... periods) {
        School school = schools.save(new School("Films " + UUID.randomUUID(), null, Region.UK));
        for (Dates d : periods) {
            licences.save(new Licence(school, plans.find("TRUST"), 0, d.from(), d.to(), EnumSet.of(ColourStage.RED)));
        }
        return school;
    }

    private String staffAt(School school, Role role) throws Exception {
        String email = "films-" + UUID.randomUUID() + "@example.test";
        users.save(new AppUser(email, "Films Person", encoder.encode("password"), role, school.getId(), null));
        return login(email);
    }

    private String fetch(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
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
