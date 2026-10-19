package uk.co.motormovers.platform;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** The team sets licence plans and dated prices; each school's licence keeps an agreed price. */
@SpringBootTest
@AutoConfigureMockMvc
class PlanTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Test
    void aPriceChangeAppliesFromItsDateAndCanBeReplacedBeforeThen() throws Exception {
        String admin = login("admin@motormovers.test");
        LocalDate later = today().plusDays(30);
        send(admin, post("/api/admin/plans/PRIMARY_CORE/prices"), price(95_000, later))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPricePence").value(90_000))
                .andExpect(jsonPath("$.nextPrice.pricePence").value(95_000))
                .andExpect(jsonPath("$.nextPrice.effectiveFrom").value(later.toString()))
                .andExpect(jsonPath("$.nextPrice.changedBy").value("admin@motormovers.test"));

        // Not yet in force, so it can be replaced; the history gains no extra row.
        send(admin, post("/api/admin/plans/PRIMARY_CORE/prices"), price(96_000, later))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextPrice.pricePence").value(96_000))
                .andExpect(jsonPath("$.history.length()").value(2));

        send(admin, post("/api/admin/plans/PRIMARY_CORE/prices"), price(80_000, today().minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("past")));
    }

    @Test
    void aNewSchoolPaysThePriceInForceWhenItsLicenceStarts() throws Exception {
        String admin = login("admin@motormovers.test");
        send(admin, post("/api/admin/plans/EARLY_YEARS/prices"), price(50_000, today().plusDays(10)))
                .andExpect(status().isOk());

        send(admin, post("/api/admin/schools"), school("Starts Now Nursery", "EARLY_YEARS", null, today()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreedPricePence").value(45_000));
        send(admin, post("/api/admin/schools"), school("Starts Later Nursery", "EARLY_YEARS", null, today().plusDays(20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreedPricePence").value(50_000));
    }

    @Test
    void theTeamAgreesADiscountAndCanChangeItLater() throws Exception {
        String admin = login("admin@motormovers.test");
        String row = send(admin, post("/api/admin/schools"), school("Discount Primary", "FULL_PROGRAMME", 150_000, today()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreedPricePence").value(150_000))
                .andExpect(jsonPath("$.listPricePence").value(175_000))
                .andReturn().getResponse().getContentAsString();
        send(admin, post("/api/admin/schools/" + JsonPath.read(row, "$.id") + "/agreed-price"), "{\"pricePence\":140000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreedPricePence").value(140_000));

        String trial = send(admin, post("/api/admin/schools"), school("Trial Infants", "FREE_TRIAL", null, today()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreedPricePence").value(0))
                .andReturn().getResponse().getContentAsString();
        send(admin, post("/api/admin/schools/" + JsonPath.read(trial, "$.id") + "/agreed-price"), "{\"pricePence\":100}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void theTeamAddsAPlanAndRetiresIt() throws Exception {
        String admin = login("admin@motormovers.test");
        send(admin, post("/api/admin/plans"), "{\"label\":\"Secondary pilot\",\"freeTrial\":false,\"pricePence\":60000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SECONDARY_PILOT"))
                .andExpect(jsonPath("$.currentPricePence").value(60_000));
        send(admin, post("/api/admin/schools"), school("Pilot Academy", "SECONDARY_PILOT", null, today()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("Secondary pilot"))
                .andExpect(jsonPath("$.agreedPricePence").value(60_000));

        // Priced today, so a same-day change would rewrite a price already used: it must start tomorrow.
        send(admin, post("/api/admin/plans/SECONDARY_PILOT/prices"), price(65_000, today()))
                .andExpect(status().isBadRequest());

        send(admin, put("/api/admin/plans/SECONDARY_PILOT"), "{\"label\":\"Secondary pilot\",\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.licences").value(1));
        send(admin, post("/api/admin/schools"), school("Too Late Academy", "SECONDARY_PILOT", null, today()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no longer on sale")));

        send(admin, post("/api/admin/plans"), "{\"label\":\"Paid trial\",\"freeTrial\":true,\"pricePence\":100}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheTeamManagesPlans() throws Exception {
        String head = login("head@oakfield.test");
        mvc.perform(get("/api/admin/plans").header("Authorization", bearer(head))).andExpect(status().isForbidden());
        send(head, post("/api/admin/plans/TRUST/prices"), price(1, today().plusDays(1)))
                .andExpect(status().isForbidden());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String price(int pence, LocalDate from) {
        return "{\"pricePence\":" + pence + ",\"effectiveFrom\":\"" + from + "\"}";
    }

    private static String school(String name, String plan, Integer agreedPence, LocalDate startsOn) {
        return "{\"name\":\"" + name + "\",\"trustId\":null,\"plan\":\"" + plan + "\",\"agreedPricePence\":"
                + agreedPence + ",\"stages\":[\"RED\"],\"startsOn\":\"" + startsOn + "\",\"endsOn\":\""
                + startsOn.plusYears(1) + "\"}";
    }

    private ResultActions send(String token, MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
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
