package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.common.config.Mailer;
import uk.co.motormovers.platform.training.repository.CohortRepository;
import uk.co.motormovers.platform.training.service.SessionReminderJob;

/**
 * Recorded courses (go-live task 89), as a deployment runs them: live courses switched off. A learner has a place on
 * the course itself, does its lessons in order, takes the final test and gets the certificate; staff ask their head
 * for a place; only the learner and the team see a place's lessons and films; and nothing live or for trainers answers.
 */
// Its own database: the demo here is the recorded one, with no cohorts, which the live-session tests do not expect.
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:recordedcourses;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "platform.live-courses=false", "platform.login-limit-per-ip=500",
})
@AutoConfigureMockMvc
class RecordedCourseTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CohortRepository cohorts;

    @Autowired
    SessionReminderJob reminders;

    @MockitoSpyBean
    Mailer mailer;

    @Test
    void aPractitionerDoesTheLessonsInOrderPassesTheFinalTestAndGetsTheCertificate() throws Exception {
        // Sam, in the demo, has a place and has done the first of three lessons.
        String sam = login("practitioner@example.test");
        String catalogue = fetch(sam, "/api/training/courses");
        assertThat(JsonPath.<List<String>>read(catalogue, "$.courses[*].title")).containsExactly("Practitioner course");
        assertThat(JsonPath.<Integer>read(catalogue, "$.courses[0].lessons")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(catalogue, "$.courses[0].quizzes")).isEqualTo(1);
        assertThat(JsonPath.<String>read(catalogue, "$.courses[0].place")).isEqualTo("BOOKED");
        assertThat(JsonPath.<List<Object>>read(catalogue, "$.courses[0].cohorts")).isEmpty();

        String mine = fetch(sam, "/api/training/enrolments/mine");
        Integer place = JsonPath.read(mine, "$[0].id");
        assertThat(JsonPath.<Boolean>read(mine, "$[0].recorded")).isTrue();
        assertThat(JsonPath.<Integer>read(mine, "$[0].lessonsDone")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(mine, "$[0].lessons")).isEqualTo(3);
        assertThat(JsonPath.<Object>read(mine, "$[0].cohortId")).isNull();
        // Booking again gives the same place.
        mvc.perform(auth(post("/api/training/courses/" + JsonPath.<Integer>read(mine, "$[0].courseId") + "/enrol"),
                sam)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(place));

        String lessons = "/api/training/enrolments/" + place + "/lessons";
        String player = fetch(sam, lessons);
        List<Integer> ids = JsonPath.read(player, "$.lessons[*].id");
        assertThat(JsonPath.<List<Boolean>>read(player, "$.lessons[*].done")).containsExactly(true, false, false);
        assertThat(JsonPath.<List<Boolean>>read(player, "$.lessons[*].open")).containsExactly(true, true, false);
        assertThat(JsonPath.<Boolean>read(player, "$.quizzesOpen")).isFalse();

        // In order: the third lesson waits for the second, and the test for every lesson.
        mvc.perform(auth(post(lessons + "/" + ids.get(2) + "/complete"), sam)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Finish lesson 2 first")));
        String quizzes = fetch(sam, "/api/training/enrolments/" + place + "/quizzes");
        Integer test = JsonPath.read(quizzes, "$[0].id");
        assertThat(JsonPath.<Boolean>read(quizzes, "$[0].open")).isFalse();
        assertThat(JsonPath.<Integer>read(quizzes, "$[0].questions")).isEqualTo(10);
        assertThat(JsonPath.<Integer>read(quizzes, "$[0].passPercent")).isEqualTo(80);
        String attempts = "/api/training/enrolments/" + place + "/quizzes/" + test + "/attempts";
        attempt(sam, attempts, 10).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("once you have done every lesson")));

        mvc.perform(auth(post(lessons + "/" + ids.get(1) + "/complete"), sam)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonsDone").value(2)).andExpect(jsonPath("$.quizzesOpen").value(false));
        // Marking a lesson done twice changes nothing.
        mvc.perform(auth(post(lessons + "/" + ids.get(1) + "/complete"), sam)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonsDone").value(2));
        mvc.perform(auth(post(lessons + "/" + ids.get(2) + "/complete"), sam)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonsDone").value(3)).andExpect(jsonPath("$.quizzesOpen").value(true))
                .andExpect(jsonPath("$.certificateIssuedOn").doesNotExist());

        // Eight of ten is the pass mark: seven is not enough, eight is, and the certificate follows at once.
        attempt(sam, attempts, 7).andExpect(status().isOk()).andExpect(jsonPath("$.scorePercent").value(70))
                .andExpect(jsonPath("$.passed").value(false)).andExpect(jsonPath("$.certificateIssued").value(false));
        attempt(sam, attempts, 8).andExpect(status().isOk()).andExpect(jsonPath("$.scorePercent").value(80))
                .andExpect(jsonPath("$.passed").value(true)).andExpect(jsonPath("$.certificateIssued").value(true));
        verify(mailer).send(eq("practitioner@example.test"), contains("certificate"),
                contains("You have completed the Practitioner course"), eq("certificate.pdf"), any(),
                eq("application/pdf"));
        byte[] pdf = mvc.perform(auth(get("/api/training/enrolments/" + place + "/certificate.pdf"), sam))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        mvc.perform(auth(get(lessons), sam)).andExpect(jsonPath("$.certificateIssuedOn").exists());
        // With the certificate, the place stands.
        mvc.perform(auth(post("/api/training/enrolments/" + place + "/cancel"), sam))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aParentBooksStartsAtOnceAndCanCancelOnlyUntilTheFirstLessonIsDone() throws Exception {
        String priya = login("home@example.test");
        String catalogue = fetch(priya, "/api/training/courses");
        assertThat(JsonPath.<List<String>>read(catalogue, "$.courses[*].title")).containsExactly("Parent course");
        assertThat(JsonPath.<String>read(catalogue, "$.courses[0].place")).isEqualTo("NONE");
        Integer course = JsonPath.read(catalogue, "$.courses[0].id");

        Integer place = JsonPath.read(mvc.perform(auth(post("/api/training/courses/" + course + "/enrol"), priya))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recorded").value(true))
                .andExpect(jsonPath("$.canCancel").value(true)).andReturn().getResponse().getContentAsString(),
                "$.id");
        // Not started yet: the place can be cancelled, and booked again.
        mvc.perform(auth(post("/api/training/enrolments/" + place + "/cancel"), priya)).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("place on the Parent course is cancelled")));
        place = JsonPath.read(mvc.perform(auth(post("/api/training/courses/" + course + "/enrol"), priya))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");

        String lessons = "/api/training/enrolments/" + place + "/lessons";
        List<Integer> ids = JsonPath.read(fetch(priya, lessons), "$.lessons[*].id");
        assertThat(ids).hasSize(2);
        mvc.perform(auth(post(lessons + "/" + ids.get(0) + "/complete"), priya)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/training/enrolments/mine"), priya)).andExpect(jsonPath("$[0].canCancel").value(false));
        mvc.perform(auth(post("/api/training/enrolments/" + place + "/cancel"), priya))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("The course has started")));
        // The parent course gives no certificate, even with every lesson done.
        mvc.perform(auth(post(lessons + "/" + ids.get(1) + "/complete"), priya)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonsDone").value(2)).andExpect(jsonPath("$.certificateIssuedOn").doesNotExist());
        // The team can still cancel it.
        mvc.perform(auth(post("/api/training/enrolments/" + place + "/cancel"), login("admin@motormovers.test")))
                .andExpect(status().isOk());
        // A parent cannot book the professional course.
        Integer professional = JsonPath.<List<Integer>>read(fetch(login("admin@motormovers.test"),
                "/api/training/courses"), "$.courses[?(@.track=='PROFESSIONAL')].id").getFirst();
        mvc.perform(auth(post("/api/training/courses/" + professional + "/enrol"), priya))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffAskTheirHeadForAPlaceAndApprovingBooksItAndInvoicesTheSchool() throws Exception {
        String hardy = login("teacher@oakfield.test");
        String catalogue = fetch(hardy, "/api/training/courses");
        Integer course = JsonPath.read(catalogue, "$.courses[0].id");
        Integer price = JsonPath.read(catalogue, "$.courses[0].pricePence");
        assertThat(JsonPath.<Boolean>read(catalogue, "$.courses[0].needsApproval")).isTrue();

        mvc.perform(auth(post("/api/training/courses/" + course + "/enrol"), hardy)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Ask for a place instead")));
        Integer request = JsonPath.read(mvc.perform(auth(post("/api/training/courses/" + course + "/request"), hardy))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.courseTitle").value("Practitioner course"))
                .andExpect(jsonPath("$.cohortId").doesNotExist()).andReturn().getResponse().getContentAsString(),
                "$.id");
        verify(mailer).send(eq("head@oakfield.test"), eq("A course place to approve"),
                contains("Ms Hardy has asked for a place on the Practitioner course"));
        // Asking twice gives the same request.
        mvc.perform(auth(post("/api/training/courses/" + course + "/request"), hardy)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(request));
        mvc.perform(auth(get("/api/training/courses"), hardy)).andExpect(jsonPath("$.courses[0].place").value("REQUESTED"));

        // Another teacher cannot approve it; the head can, which books the place and invoices the school.
        mvc.perform(auth(post("/api/course-requests/" + request + "/approve"), login("newteacher@oakfield.test")))
                .andExpect(status().isForbidden());
        String head = login("head@oakfield.test");
        mvc.perform(auth(post("/api/course-requests/" + request + "/approve"), head)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        verify(mailer).send(eq("teacher@oakfield.test"), eq("Your course place is booked"),
                contains("approved your place on the Practitioner course"));
        mvc.perform(auth(get("/api/training/enrolments/mine"), hardy)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recorded").value(true)).andExpect(jsonPath("$[0].lessons").value(3));
        Integer school = JsonPath.read(fetch(head, "/api/auth/me"), "$.schoolId");
        String payments = fetch(head, "/api/schools/" + school + "/payments");
        assertThat(JsonPath.<List<Integer>>read(payments,
                "$..[?(@.description=='Practitioner course · Ms Hardy')].amountPence")).containsExactly(price);
    }

    @Test
    void onlyTheLearnerAndTheTeamSeeAPlacesLessonsAndFilmsAndTheTeamWritesThem() throws Exception {
        String admin = login("admin@motormovers.test");
        Integer course = JsonPath.read(mvc.perform(auth(post("/api/admin/courses"), admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"track\":\"PARENT\",\"title\":\"Moving together\",\"description\":\"A short recorded course.\","
                        + "\"liveSessions\":0,\"sessionMinutes\":0,\"pricePence\":0,\"renewalPence\":null,"
                        + "\"awardsCertificate\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        String tom = login("lapsed@example.test");
        // No approved lessons yet: nothing to book.
        mvc.perform(auth(post("/api/training/courses/" + course + "/enrol"), tom)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("being prepared")));

        String lessonsUrl = "/api/admin/courses/" + course + "/lessons";
        Integer first = JsonPath.read(lesson(admin, post(lessonsUrl), 1, "Getting ready", "Clear a space.",
                "\"videoUrl\":\"https://films.example.test/one.mp4\",\"slidesUrl\":\"https://films.example.test/one.pdf\"")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn().getResponse()
                .getContentAsString(), "$.id");
        Integer second = JsonPath.read(lesson(admin, post(lessonsUrl), 2, "Warming up", "", "\"videoUrl\":null")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        // Refused: a link that is not https, a link and an uploaded file together, and a key the store never gave out.
        lesson(admin, post(lessonsUrl), 3, "Bad link", "", "\"videoUrl\":\"http://films.example.test/x.mp4\"")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("https://")));
        lesson(admin, post(lessonsUrl), 3, "Both", "",
                "\"videoUrl\":\"https://films.example.test/x.mp4\",\"videoKey\":\"video/abc.mp4\"")
                .andExpect(status().isBadRequest());
        lesson(admin, post(lessonsUrl), 3, "Made-up key", "", "\"videoKey\":\"video/not-a-real-key.mp4\"")
                .andExpect(status().isBadRequest());
        // The wording check runs on approval.
        Integer worded = JsonPath.read(lesson(admin, post(lessonsUrl), 3, "Spotting ADHD", "", "\"videoUrl\":null")
                .andExpect(status().isOk()).andExpect(jsonPath("$.violations", hasSize(1))).andReturn().getResponse()
                .getContentAsString(), "$.id");
        mvc.perform(auth(post("/api/admin/lessons/" + worded + "/approve"), admin)).andExpect(status().isBadRequest());
        mvc.perform(auth(delete("/api/admin/lessons/" + worded), admin)).andExpect(status().isNoContent());
        mvc.perform(auth(post("/api/admin/lessons/" + first + "/approve"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Tom books: one approved lesson, the draft is not his to see.
        Integer place = JsonPath.read(mvc.perform(auth(post("/api/training/courses/" + course + "/enrol"), tom))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        String lessons = "/api/training/enrolments/" + place + "/lessons";
        mvc.perform(auth(get(lessons), tom)).andExpect(status().isOk()).andExpect(jsonPath("$.lessons", hasSize(1)))
                .andExpect(jsonPath("$.lessons[0].hasFilm").value(true))
                .andExpect(jsonPath("$.lessons[0].videoUrl").value("https://films.example.test/one.mp4"))
                .andExpect(jsonPath("$.lessons[0].slidesUrl").value("https://films.example.test/one.pdf"));
        mvc.perform(auth(post("/api/admin/lessons/" + second + "/approve"), admin)).andExpect(status().isOk());
        // The second lesson's film is not sent until the first is done.
        mvc.perform(auth(put("/api/admin/lessons/" + second), admin).contentType(MediaType.APPLICATION_JSON)
                .content(lessonJson(2, "Warming up", "", "\"videoUrl\":\"https://films.example.test/two.mp4\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(auth(post("/api/admin/lessons/" + second + "/approve"), admin)).andExpect(status().isOk());
        mvc.perform(auth(get(lessons), tom)).andExpect(jsonPath("$.lessons", hasSize(2)))
                .andExpect(jsonPath("$.lessons[1].open").value(false))
                .andExpect(jsonPath("$.lessons[1].hasFilm").value(true))
                .andExpect(jsonPath("$.lessons[1].videoUrl").doesNotExist());

        // Nobody else: another parent, a head, a teacher, a practitioner and a trainer get nothing of Tom's place.
        for (String other : List.of("parent@example.test", "head@oakfield.test", "teacher@oakfield.test",
                "practitioner@example.test", "trainer@motormovers.test", "trust@northbridge.test")) {
            String token = login(other);
            mvc.perform(auth(get(lessons), token)).andExpect(status().isForbidden());
            mvc.perform(auth(post(lessons + "/" + first + "/complete"), token)).andExpect(status().isForbidden());
            mvc.perform(auth(get(lessonsUrl), token)).andExpect(status().isForbidden());
            lesson(token, post(lessonsUrl), 9, "Not mine to write", "", "\"videoUrl\":null")
                    .andExpect(status().isForbidden());
            mvc.perform(auth(post("/api/admin/lessons/" + first + "/approve"), token)).andExpect(status().isForbidden());
            mvc.perform(auth(delete("/api/admin/lessons/" + first), token)).andExpect(status().isForbidden());
            mvc.perform(auth(get("/api/admin/courses/" + course + "/enrolments"), token))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(lessons)).andExpect(status().isUnauthorized());
        // The team sees the place, with its links, but does not do a learner's lessons for them.
        mvc.perform(auth(get(lessons), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lessons[0].videoUrl").value("https://films.example.test/one.mp4"));
        mvc.perform(auth(post(lessons + "/" + first + "/complete"), admin)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/admin/courses/" + course + "/enrolments"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].learnerEmail").value("lapsed@example.test"));

        // A lesson a learner has done is kept.
        mvc.perform(auth(post(lessons + "/" + first + "/complete"), tom)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lessons[1].videoUrl").value("https://films.example.test/two.mp4"));
        mvc.perform(auth(delete("/api/admin/lessons/" + first), admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Learners have done this lesson")));
    }

    @Test
    void withLiveCoursesOffNothingLiveOrForTrainersAnswers() throws Exception {
        mvc.perform(get("/api/settings")).andExpect(status().isOk()).andExpect(jsonPath("$.liveCourses").value(false));
        // The demo has recorded courses only.
        assertThat(cohorts.count()).isZero();
        String admin = login("admin@motormovers.test");
        String trainer = login("trainer@motormovers.test");
        String jo = login("parent@example.test");
        for (MockHttpServletRequestBuilder live : List.of(get("/api/training/live"), post("/api/training/live/1/join"),
                post("/api/training/cohorts/1/enrol"), post("/api/training/cohorts/1/request"))) {
            mvc.perform(auth(live, jo)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value(containsString("Live courses are switched off")));
        }
        mvc.perform(auth(get("/api/trainer/cohorts"), trainer)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/admin/live/1/register"), trainer)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/training/live"), trainer)).andExpect(status().isNotFound());
        for (MockHttpServletRequestBuilder team : List.of(get("/api/admin/trainers"),
                get("/api/admin/cohorts/1/enrolments"), get("/api/admin/live/1/register"))) {
            mvc.perform(auth(team, admin)).andExpect(status().isNotFound());
        }
        mvc.perform(auth(post("/api/admin/trainers"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new.trainer@example.test\",\"displayName\":\"New Trainer\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/admin/courses/1/cohorts"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isNotFound());
        // The reminder jobs send nothing.
        assertThat(reminders.remind()).isZero();
        assertThat(reminders.announceOpening()).isZero();
        verify(mailer, never()).send(anyString(), contains("live session"), anyString());
        // The demo's places: Jo part-way through the parent course, Mr Patel with the certificate.
        mvc.perform(auth(get("/api/training/enrolments/mine"), jo)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].courseTitle").value("Parent course"))
                .andExpect(jsonPath("$[0].lessonsDone").value(1)).andExpect(jsonPath("$[0].lessons").value(2));
        mvc.perform(auth(get("/api/training/enrolments/mine"), login("head@oakfield.test")))
                .andExpect(jsonPath("$[0].certificateIssuedOn").exists())
                .andExpect(jsonPath("$[0].lessonsDone").value(3)).andExpect(jsonPath("$[0].quizzesPassed").value(1));
    }

    /** Answers the ten-question test with {@code right} right answers (the first option is the right one). */
    private ResultActions attempt(String token, String url, int right) throws Exception {
        StringBuilder answers = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            answers.append(i == 0 ? "" : ",").append(i < right ? 0 : 1);
        }
        return mvc.perform(auth(post(url), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[" + answers + "]}"));
    }

    private ResultActions lesson(String token, MockHttpServletRequestBuilder request, int position, String title,
            String body, String extra) throws Exception {
        return mvc.perform(auth(request, token).contentType(MediaType.APPLICATION_JSON)
                .content(lessonJson(position, title, body, extra)));
    }

    private static String lessonJson(int position, String title, String body, String extra) {
        return "{\"position\":" + position + ",\"title\":\"" + title + "\",\"body\":\"" + body + "\",\"minutes\":15,"
                + extra + "}";
    }

    private String fetch(String token, String path) throws Exception {
        return mvc.perform(auth(get(path), token)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").with(r -> {
                    r.setRemoteAddr("10.0.0.89");
                    return r;
                }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
