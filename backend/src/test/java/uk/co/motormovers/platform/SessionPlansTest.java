package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;
import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.assessment.service.SessionPlans;
import uk.co.motormovers.platform.classroom.model.DailySessionRecord;
import uk.co.motormovers.platform.classroom.repository.DailySessionRecordRepository;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.ProgrammeSession;
import uk.co.motormovers.platform.content.repository.ProgrammeSessionRepository;
import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.home.service.CoolDowns;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.SchoolClass;
import uk.co.motormovers.platform.school.repository.SchoolClassRepository;
import uk.co.motormovers.platform.school.repository.SchoolRepository;

/** Pat's daily session plans: what is loaded, what a teacher sees, and approving them a colour or module at a time. */
@SpringBootTest(properties = "platform.login-limit-per-ip=500")
@AutoConfigureMockMvc
class SessionPlansTest {

    private static final Set<ColourStage> RHYMES = Set.of(ColourStage.RED, ColourStage.ORANGE, ColourStage.YELLOW);

    @Autowired
    MockMvc mvc;

    @Autowired
    SessionPlans plans;

    @Autowired
    ProgrammeSessionRepository sessions;

    @Autowired
    ExerciseRepository exercises;

    @Autowired
    CoolDowns coolDowns;

    @Autowired
    WordingGuard wordingGuard;

    @Autowired
    SchoolRepository schools;

    @Autowired
    SchoolClassRepository classes;

    @Autowired
    DailySessionRecordRepository records;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JsonMapper json;

    @Autowired
    Clock clock;

    @Test
    void everyColourHasItsSessions() {
        for (ColourStage stage : ColourStage.values()) {
            assertThat(planned(stage)).as(stage.label()).hasSize(RHYMES.contains(stage) ? 90 : 180);
        }
    }

    @Test
    void everyWeekMatchesItsFile() throws Exception {
        for (ColourStage stage : ColourStage.values()) {
            SessionPlans.PlanFile file = file(stage);
            assertThat(file.stage()).isEqualTo(stage);
            for (SessionPlans.PlanUnit unit : file.units()) {
                int day = 0;
                for (int week = 1; week <= unit.weeks().size(); week++) {
                    for (int d = 1; d <= SessionPlans.SESSIONS_A_WEEK; d++) {
                        ProgrammeSession session = session(stage, unit.number(), ++day);
                        assertThat(session.getActivities()).isEqualTo(unit.weeks().get(week - 1));
                        assertThat(session.getDurationMinutes()).isEqualTo(10);
                        assertThat(session.getTitle()).isEqualTo(RHYMES.contains(stage)
                                ? stage.label() + " Rhyme " + unit.number() + ", Day " + d
                                : stage.label() + " Module " + unit.number() + ", Week " + week + ", Day " + d);
                    }
                }
            }
        }
    }

    @Test
    void patsAnswersOfTheTenthOfNovemberAreIn() throws Exception {
        assertThat(file(ColourStage.BLUE).units().get(2).weeks().get(4)).contains("Hot Air Balloon");
        assertThat(file(ColourStage.BLUE).units().get(2).weeks().get(3)).doesNotContain("Hot Air Balloon");
        // Green Module 6 says "Repeat" for weeks two to six.
        List<List<String>> greenSix = file(ColourStage.GREEN).units().get(5).weeks();
        assertThat(greenSix).hasSize(6).allMatch(w -> w.equals(greenSix.getFirst()));
        for (int module = 1; module <= 6; module++) {
            assertThat(coolDowns.forModule(ColourStage.BLUE, module).title()).isEqualTo("Cool Down " + module);
            assertThat(coolDowns.forModule(ColourStage.VIOLET, module)).isNotNull();
        }
        String indigo = coolDowns.forModule(ColourStage.INDIGO, 1).text();
        assertThat(indigo).endsWith("5, 4, 3, 2, 1");
        assertThat(indigo.split("Look at the other thumb nail", -1)).hasSize(2);
    }

    @Test
    void everyNameIsInItsColoursGridAndPassesTheWordingCheck() {
        for (ColourStage stage : ColourStage.values()) {
            Set<String> grid = exercises.findByStageOrderByNumber(stage).stream().map(Exercise::getTitle)
                    .collect(Collectors.toSet());
            for (ProgrammeSession session : planned(stage)) {
                assertThat(grid).as(session.getTitle()).containsAll(session.getActivities());
                assertThat(wordingGuard.check(session.wording())).as(session.getTitle()).isEmpty();
            }
        }
        assertThat(wordingGuard.check(coolDowns.texts())).isEmpty();
    }

    @Test
    void aNameNotInTheGridStopsTheLoad() {
        SessionPlans.PlanFile wrong = new SessionPlans.PlanFile("test", ColourStage.GREEN, "Module",
                List.of(new SessionPlans.PlanUnit(1, List.of(List.of("Yes and No", "Star Fish")))));
        assertThatThrownBy(() -> plans.add(wrong)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Star Fish");
    }

    @Test
    void startingAgainAddsNothing() {
        long before = sessions.count();
        plans.loadMissing();
        assertThat(sessions.count()).isEqualTo(before);
    }

    @Test
    void onlyWhatIsMissingIsAddedAndWhatIsThereIsLeftAlone() throws Exception {
        ProgrammeSession last = session(ColourStage.VIOLET, 6, 30);
        sessions.delete(last);
        ProgrammeSession neighbour = session(ColourStage.VIOLET, 6, 29);

        plans.loadMissing();

        ProgrammeSession back = session(ColourStage.VIOLET, 6, 30);
        assertThat(back.getStatus()).isEqualTo(ContentStatus.DRAFT);
        ProgrammeSession unchanged = session(ColourStage.VIOLET, 6, 29);
        assertThat(unchanged.getId()).isEqualTo(neighbour.getId());
        assertThat(unchanged.getStatus()).isEqualTo(ContentStatus.APPROVED);
        back.review(ContentStatus.APPROVED, "test");
        sessions.save(back);

        // A plan revised to seven weeks adds that week's five sessions and no others.
        SessionPlans.PlanUnit first = file(ColourStage.GREEN).units().getFirst();
        List<List<String>> weeks = new ArrayList<>(first.weeks());
        weeks.add(first.weeks().getLast());
        assertThat(plans.add(new SessionPlans.PlanFile("test", ColourStage.GREEN, "Module",
                List.of(new SessionPlans.PlanUnit(1, weeks))))).isEqualTo(5);
        ProgrammeSession added = session(ColourStage.GREEN, 1, 35);
        assertThat(added.getTitle()).isEqualTo("Green Module 1, Week 7, Day 5");
        sessions.deleteAll(sessions.findByStage(ColourStage.GREEN).stream()
                .filter(s -> s.getUnit() == 1 && s.getDay() > 30).toList());
    }

    @Test
    void aTeacherSeesTheGridsInstructionsThenTheModulesCoolDown() throws Exception {
        withClass(ColourStage.GREEN, 30, id -> today(id)
                .andExpect(jsonPath("$.nextSession.title").value("Green Module 2, Week 1, Day 1"))
                .andExpect(jsonPath("$.nextSession.steps[0].title").value("Yes and No"))
                .andExpect(jsonPath("$.nextSession.steps[0].instructions").value(containsString("nod head")))
                .andExpect(jsonPath("$.nextSession.coolDown.title").value("Cool Down 2")));
    }

    @Test
    void aRhymeSessionEndsInTheRhymeAndCarriesItsLibraryRhyme() throws Exception {
        // Yellow's eleventh rhyme is Jack and Jill, which the starter library has.
        withClass(ColourStage.YELLOW, 50, id -> today(id)
                .andExpect(jsonPath("$.nextSession.title").value("Yellow Rhyme 11, Day 1"))
                .andExpect(jsonPath("$.nextSession.steps[0].title").value("Jack and Jill"))
                .andExpect(jsonPath("$.nextSession.coolDown").doesNotExist())
                .andExpect(jsonPath("$.nextSession.rhyme.title").value("Jack and Jill"))
                .andExpect(jsonPath("$.nextSession.rhyme.lines.length()").value(greaterThan(2))));
    }

    @Test
    void aClassMovesThroughTheSequence() throws Exception {
        withClass(ColourStage.GREEN, 0,
                id -> today(id).andExpect(jsonPath("$.nextSession.title").value("Green Module 1, Week 1, Day 1")));
        withClass(ColourStage.GREEN, 7,
                id -> today(id).andExpect(jsonPath("$.nextSession.title").value("Green Module 1, Week 2, Day 3")));
        withClass(ColourStage.RED, 5,
                id -> today(id).andExpect(jsonPath("$.nextSession.title").value("Red Rhyme 2, Day 1")));
    }

    @Test
    void theTeamApprovesAModuleAndADraftFailingTheWordingCheckStaysAndIsNamed() throws Exception {
        ProgrammeSession clean = draft(ColourStage.YELLOW, 501, 1, "Clean draft", "Balance");
        ProgrammeSession failing = draft(ColourStage.YELLOW, 501, 2, "Failing draft", "For children with ADHD");
        ProgrammeSession otherModule = draft(ColourStage.YELLOW, 502, 1, "Another module", "Balance");
        try {
            approveAll(admin(), "{\"stage\":\"YELLOW\",\"unit\":501}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.approved").value(1))
                    .andExpect(jsonPath("$.left.length()").value(1))
                    .andExpect(jsonPath("$.left[0].title").value("Failing draft"))
                    .andExpect(jsonPath("$.left[0].wordingIssues[0].term").isNotEmpty());

            assertThat(statusOf(clean)).isEqualTo(ContentStatus.APPROVED);
            assertThat(statusOf(failing)).isEqualTo(ContentStatus.DRAFT);
            assertThat(statusOf(otherModule)).isEqualTo(ContentStatus.DRAFT);
        } finally {
            sessions.deleteAll(List.of(clean, failing, otherModule));
        }
    }

    @Test
    void theTeamApprovesAWholeColourAndARejectedSessionStaysRejected() throws Exception {
        ProgrammeSession draft = draft(ColourStage.ORANGE, 503, 1, "Orange draft", "Balance");
        ProgrammeSession rejected = draft(ColourStage.ORANGE, 503, 2, "Sent back", "Balance");
        rejected.review(ContentStatus.REJECTED, "test");
        sessions.save(rejected);
        try {
            approveAll(admin(), "{\"stage\":\"ORANGE\"}").andExpect(status().isOk())
                    .andExpect(jsonPath("$.approved").value(1));
            assertThat(statusOf(draft)).isEqualTo(ContentStatus.APPROVED);
            assertThat(statusOf(rejected)).isEqualTo(ContentStatus.REJECTED);
        } finally {
            sessions.deleteAll(List.of(draft, rejected));
        }
    }

    @Test
    void onlyTheTeamCanApproveManyAtOnce() throws Exception {
        ProgrammeSession draft = draft(ColourStage.BLUE, 504, 1, "Blue draft", "Balance");
        try {
            for (String email : List.of("head@oakfield.test", "teacher@oakfield.test", "trust@northbridge.test",
                    "parent@example.test")) {
                approveAll(login(email), "{\"stage\":\"BLUE\",\"unit\":504}").andExpect(status().isForbidden());
            }
            assertThat(statusOf(draft)).isEqualTo(ContentStatus.DRAFT);
        } finally {
            sessions.delete(draft);
        }
    }

    @FunctionalInterface
    private interface ClassCheck {
        void on(Long classId) throws Exception;
    }

    /** A class at Oakfield with this many sessions already done, removed afterwards. */
    private void withClass(ColourStage stage, int done, ClassCheck check) throws Exception {
        School oakfield = schools.findAll().stream().filter(s -> s.getName().equals("Oakfield Primary")).findFirst()
                .orElseThrow();
        SchoolClass schoolClass = classes.save(new SchoolClass(oakfield, "Sequence " + stage, "Year 1", null, stage));
        try {
            ProgrammeSession any = session(stage, 1, 1);
            LocalDate today = LocalDate.now(clock);
            for (int i = 1; i <= done; i++) {
                records.save(new DailySessionRecord(schoolClass, any, today.minusDays(i), 20, 0, "test"));
            }
            check.on(schoolClass.getId());
        } finally {
            jdbc.update("DELETE FROM daily_session_record WHERE school_class_id = ?", schoolClass.getId());
            classes.delete(schoolClass);
        }
    }

    private ResultActions today(Long classId) throws Exception {
        return mvc.perform(get("/api/classes/" + classId + "/today").header("Authorization",
                "Bearer " + login("head@oakfield.test"))).andExpect(status().isOk());
    }

    private ResultActions approveAll(String token, String body) throws Exception {
        return mvc.perform(post("/api/admin/content/approve-all").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ProgrammeSession draft(ColourStage stage, int unit, int day, String title, String focus) {
        return sessions.save(new ProgrammeSession(stage, unit, day, title, focus, 10, null, List.of("Step")));
    }

    private ContentStatus statusOf(ProgrammeSession session) {
        return sessions.findById(session.getId()).orElseThrow().getStatus();
    }

    private ProgrammeSession session(ColourStage stage, int unit, int day) {
        return sessions.findByStage(stage).stream().filter(s -> s.getUnit() == unit && s.getDay() == day).findFirst()
                .orElseThrow();
    }

    /** Pat's sessions of a colour, leaving out any a test or the demo wrote by hand. */
    private List<ProgrammeSession> planned(ColourStage stage) {
        Pattern title = Pattern.compile("^" + stage.label() + " (Module \\d+, Week \\d+|Rhyme \\d+), Day \\d+$");
        return sessions.findByStage(stage).stream().filter(s -> title.matcher(s.getTitle()).matches()).toList();
    }

    private SessionPlans.PlanFile file(ColourStage stage) throws IOException {
        String path = "assessment/session-plan-" + stage.name().toLowerCase(Locale.ROOT) + ".json";
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readValue(in, SessionPlans.PlanFile.class);
        }
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
}
