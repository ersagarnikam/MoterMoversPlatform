package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** Recorded payments and who may see which spending figures, against the demo data. */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentTest {

    /** The demo trust licence (LicencePlan.TRUST), in pence. */
    private static final int TRUST_LICENCE = 148_750;

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Test
    void theTeamSeesSpendBySchoolTrustPersonAndMonth() throws Exception {
        String summary = fetch(login("admin@motormovers.test"), "/api/admin/payments/summary");
        assertThat((List<?>) JsonPath.read(summary, "$.byMonth")).hasSize(12);
        assertThat((List<Integer>) JsonPath.read(summary, "$.byPerson[?(@.name == 'Jo Bennett')].paidPence"))
                .containsExactly(4_500);
        assertThat((List<String>) JsonPath.read(summary, "$.byPerson[?(@.name == 'Jo Bennett')].payerType"))
                .containsExactly("PARENT");
        assertThat((List<Integer>) JsonPath.read(summary, "$.byTrust[?(@.trustName == 'Northbridge Academy Trust')].schools"))
                .containsExactly(4);
        assertThat((List<Integer>) JsonPath.read(summary, "$.byPurpose[?(@.key == 'COURSE')].paidPence"))
                .containsExactly(22_500);
    }

    @Test
    void theTeamRecordsAnInvoiceThenMarksItPaid() throws Exception {
        String admin = login("admin@motormovers.test");
        Integer ferrers = schoolId(admin, "Ferrers Park");
        long outstandingBefore = outstanding(admin);

        String payment = send(admin, "/api/admin/payments", "{\"schoolId\":" + ferrers + ",\"purpose\":\"LICENCE\","
                + "\"description\":\"Early years licence\",\"amountPence\":45000,\"invoicedOn\":\""
                + LocalDate.now(clock) + "\",\"reference\":\"MM-2001\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUE"))
                .andExpect(jsonPath("$.payerName").value("Ferrers Park"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(payment, "$.id");
        assertThat(outstanding(admin)).isEqualTo(outstandingBefore + 45_000);

        send(admin, "/api/admin/payments/" + id + "/paid", "{\"paidOn\":\"" + LocalDate.now(clock).plusDays(1) + "\"}")
                .andExpect(status().isBadRequest());
        send(admin, "/api/admin/payments/" + id + "/paid", "{\"paidOn\":\"" + LocalDate.now(clock) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        assertThat(outstanding(admin)).isEqualTo(outstandingBefore);

        // Paid is final: it can be neither paid again nor cancelled.
        send(admin, "/api/admin/payments/" + id + "/paid", "{\"paidOn\":\"" + LocalDate.now(clock) + "\"}")
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/payments/" + id + "/cancel").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPaymentHasExactlyOnePayerAndTheTeamNeverPays() throws Exception {
        String admin = login("admin@motormovers.test");
        Integer ferrers = schoolId(admin, "Ferrers Park");
        String rest = ",\"purpose\":\"OTHER\",\"description\":\"Cards\",\"amountPence\":100,\"invoicedOn\":\""
                + LocalDate.now(clock) + "\"}";
        send(admin, "/api/admin/payments", "{\"schoolId\":" + ferrers + ",\"payerEmail\":\"parent@example.test\"" + rest)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("either a school or a person")));
        send(admin, "/api/admin/payments", "{\"payerEmail\":\"nobody@example.test\"" + rest)
                .andExpect(status().isBadRequest());
        send(admin, "/api/admin/payments", "{\"payerEmail\":\"admin@motormovers.test\"" + rest)
                .andExpect(status().isBadRequest());
        send(admin, "/api/admin/payments", "{\"payerEmail\":\"parent@example.test\",\"purpose\":\"OTHER\","
                + "\"description\":\"Cards\",\"amountPence\":0,\"invoicedOn\":\"" + LocalDate.now(clock) + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aHeadSeesTheirOwnSchoolsPaymentsOnly() throws Exception {
        String head = login("head@oakfield.test");
        Integer oakfield = JsonPath.read(fetch(head, "/api/auth/me"), "$.schoolId");
        String own = fetch(head, "/api/schools/" + oakfield + "/payments");
        assertThat((List<String>) JsonPath.read(own, "$.payments[*].description"))
                .contains("Trust licence, year 1", "Printed session cards")
                .doesNotContain("Motor Movers parent course");
        assertThat((Integer) JsonPath.read(own, "$.paidPence")).isEqualTo(TRUST_LICENCE + 6_000);

        Integer marlow = schoolId(login("admin@motormovers.test"), "Marlow Infants");
        mvc.perform(get("/api/schools/" + marlow + "/payments").header("Authorization", bearer(head)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/schools/" + oakfield + "/payments")
                .header("Authorization", bearer(login("teacher@oakfield.test"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/payments/summary").header("Authorization", bearer(head)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTrustLeadSeesTotalsForTheirSchools() throws Exception {
        String lead = login("trust@northbridge.test");
        Integer trustId = JsonPath.read(fetch(lead, "/api/auth/me"), "$.trustId");
        String trust = fetch(lead, "/api/trusts/" + trustId + "/payments");
        assertThat((List<?>) JsonPath.read(trust, "$.schools")).hasSize(4);
        assertThat((List<Integer>) JsonPath.read(trust, "$.schools[?(@.schoolName == 'Brookhouse')].outstandingPence"))
                .containsExactly(TRUST_LICENCE);
        // Totals only: no list of individual payments.
        assertThat(trust).doesNotContain("MM-1031");

        mvc.perform(get("/api/trusts/" + (trustId + 100) + "/payments").header("Authorization", bearer(lead)))
                .andExpect(status().isForbidden());
    }

    @Test
    void peopleSeeOnlyWhatTheyPaidThemselves() throws Exception {
        String parent = login("parent@example.test");
        String mine = fetch(parent, "/api/payments/mine");
        assertThat((Integer) JsonPath.read(mine, "$.paidPence")).isEqualTo(4_500);
        assertThat((List<String>) JsonPath.read(mine, "$.payments[*].description"))
                .containsExactly("Motor Movers parent course");

        mvc.perform(get("/api/payments/mine").header("Authorization", bearer(login("practitioner@example.test"))))
                .andExpect(jsonPath("$.paidPence").value(18_000));
        mvc.perform(get("/api/admin/payments").header("Authorization", bearer(parent)))
                .andExpect(status().isForbidden());
    }

    private long outstanding(String admin) throws Exception {
        return ((Number) JsonPath.read(fetch(admin, "/api/admin/payments/summary"), "$.outstandingPence")).longValue();
    }

    private Integer schoolId(String admin, String name) throws Exception {
        List<Integer> ids = JsonPath.read(fetch(admin, "/api/admin/schools"), "$[?(@.name == '" + name + "')].id");
        return ids.getFirst();
    }

    private ResultActions send(String token, String path, String json) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String fetch(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
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
