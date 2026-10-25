package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.training.repository.CohortSessionRepository;
import uk.co.motormovers.platform.training.service.SessionReminderJob;

/**
 * A school pays for its staff's course places, so staff ask and their head approves before the school is invoiced.
 * Places can be cancelled, with their invoice, and learners are reminded of each session the day before.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingRequestTest {

    private static final String COURSE = "Motor Movers practitioner course";

    @Autowired
    MockMvc mvc;

    @Autowired
    SessionReminderJob reminders;

    @Autowired
    CohortSessionRepository sessions;

    @Test
    void staffAskTheirHeadAndApprovalBooksThePlaceAndInvoicesTheSchool() throws Exception {
        String lewis = login("newteacher@oakfield.test");
        Integer cohort = cohort(lewis, "Cohort A · UK evening");

        // Booking directly is refused: the school pays.
        call(lewis, "/api/training/cohorts/" + cohort + "/enrol")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Ask for a place")));
        String asked = call(lewis, "/api/training/cohorts/" + cohort + "/request")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn().getResponse().getContentAsString();
        Integer request = JsonPath.read(asked, "$.id");
        // Asking again changes nothing.
        call(lewis, "/api/training/cohorts/" + cohort + "/request").andExpect(jsonPath("$.id").value(request));

        // Nothing is booked or invoiced yet.
        String head = login("head@oakfield.test");
        Integer oakfield = JsonPath.read(fetch(head, "/api/auth/me"), "$.schoolId");
        assertThat(lewisInvoices(head, oakfield)).isEmpty();
        assertThat((List<String>) JsonPath.read(fetch(head, "/api/schools/" + oakfield + "/course-requests"),
                "$[?(@.id == " + request + ")].learnerName")).containsExactly("Mr Lewis");

        // Only the school's head (or the team) decides.
        call(login("trust@northbridge.test"), "/api/course-requests/" + request + "/approve")
                .andExpect(status().isForbidden());
        call(lewis, "/api/course-requests/" + request + "/approve").andExpect(status().isForbidden());

        call(head, "/api/course-requests/" + request + "/approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("Mr Patel"));
        call(head, "/api/course-requests/" + request + "/decline")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("already been approved")));
        assertThat(lewisInvoices(head, oakfield)).containsExactly("DUE");
        String mine = fetch(lewis, "/api/training/enrolments/mine");
        assertThat((List<String>) JsonPath.read(mine, "$[*].cohortName")).contains("Cohort A · UK evening");

        // Lewis cancels before the course starts: the place and the school's invoice go.
        List<Integer> place = JsonPath.read(mine, "$[?(@.cohortName == 'Cohort A · UK evening')].id");
        call(lewis, "/api/training/enrolments/" + place.getFirst() + "/cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("invoice for it is cancelled")));
        assertThat(lewisInvoices(head, oakfield)).containsExactly("CANCELLED");
        assertThat((List<String>) JsonPath.read(fetch(lewis, "/api/training/enrolments/mine"), "$[*].cohortName"))
                .doesNotContain("Cohort A · UK evening");
    }

    @Test
    void staffCanWithdrawARequestAndTheHeadCanDeclineOne() throws Exception {
        String hardy = login("teacher@oakfield.test");
        Integer cohort = cohort(hardy, "Cohort B · UK morning / AU evening");
        Integer first = JsonPath.read(call(hardy, "/api/training/cohorts/" + cohort + "/request")
                .andReturn().getResponse().getContentAsString(), "$.id");
        call(hardy, "/api/training/requests/" + first + "/withdraw")
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        Integer second = JsonPath.read(call(hardy, "/api/training/cohorts/" + cohort + "/request")
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(second).isNotEqualTo(first);
        call(login("head@oakfield.test"), "/api/course-requests/" + second + "/decline")
                .andExpect(jsonPath("$.status").value("DECLINED"));
        assertThat((List<String>) JsonPath.read(fetch(hardy, "/api/training/requests/mine"), "$[*].status"))
                .contains("WITHDRAWN", "DECLINED");
        assertThat((List<String>) JsonPath.read(fetch(hardy, "/api/training/enrolments/mine"), "$[*].cohortName"))
                .doesNotContain("Cohort B · UK morning / AU evening");
    }

    @Test
    void onlyTheLearnerTheirHeadOrTheTeamCancelsAPlace() throws Exception {
        String admin = login("admin@motormovers.test");
        List<Integer> now = JsonPath.read(fetch(admin, "/api/admin/courses"),
                "$[*].cohorts[?(@.name == 'Cohort A · starting now')].id");
        Integer jo = JsonPath.read(fetch(admin, "/api/admin/cohorts/" + now.getFirst() + "/enrolments"), "$[0].id");
        call(login("practitioner@example.test"), "/api/training/enrolments/" + jo + "/cancel")
                .andExpect(status().isForbidden());
        call(login("head@oakfield.test"), "/api/training/enrolments/" + jo + "/cancel")
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void learnersAreRemindedOnceOfASessionInTheNextDay() throws Exception {
        // The demo's parent cohort starts ten minutes after start-up, with Jo booked.
        reminders.remind();
        assertThat(sessions.findAll()).filteredOn(s -> s.getCohort().getName().equals("Cohort A · starting now")
                && s.getNumber() == 1).allMatch(s -> s.getReminderSentAt() != null);
        // Next week's session is not due yet, and nothing goes twice.
        assertThat(sessions.findAll()).filteredOn(s -> s.getCohort().getName().equals("Cohort A · starting now")
                && s.getNumber() == 2).allMatch(s -> s.getReminderSentAt() == null);
        assertThat(reminders.remind()).isZero();
    }

    private List<String> lewisInvoices(String head, Integer school) throws Exception {
        return JsonPath.read(fetch(head, "/api/schools/" + school + "/payments"),
                "$.payments[?(@.description =~ /.*Mr Lewis.*/)].status");
    }

    private Integer cohort(String token, String name) throws Exception {
        List<Integer> ids = JsonPath.read(fetch(token, "/api/training/courses"),
                "$.courses[?(@.title == '" + COURSE + "')].cohorts[?(@.name == '" + name + "')].id");
        return ids.getFirst();
    }

    private String fetch(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions call(String token, String path) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token));
    }

    private String login(String email) throws Exception {
        // Signs in from an address of its own, so these sign-ins do not count towards the per-address limit
        // that the other test classes share.
        String body = mvc.perform(post("/api/auth/login").with(r -> {
                    r.setRemoteAddr("10.0.0.24");
                    return r;
                }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
