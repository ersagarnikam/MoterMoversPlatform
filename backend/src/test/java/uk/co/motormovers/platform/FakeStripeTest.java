package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.home.model.HomeMonthlyPrice;
import uk.co.motormovers.platform.home.repository.HomeMonthlyPriceRepository;
import uk.co.motormovers.platform.payment.stripe.StripeClient;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * The stand-in for Stripe (STRIPE_FAKE=true), which the demo and the browser tests use: subscribing, cancelling, the
 * next month and a refused card all reach the platform as Stripe's own messages would, with no keys. Also a monthly
 * launch price from the settings (home-monthly), which this context is given in place of the UK's own.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:fakestripe;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "platform.stripe.fake=true", "platform.countries.uk.launch-prices.home-monthly=14.50",
    "platform.login-limit-per-ip=500",
})
@AutoConfigureMockMvc
class FakeStripeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    StripeClient stripe;

    @Autowired
    HomeMonthlyPriceRepository prices;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void theMonthlyLaunchPriceIsAppliedOnceWhenTheSettingsGiveOne() {
        List<HomeMonthlyPrice> all = prices.findAll();
        assertThat(all).extracting(HomeMonthlyPrice::getPricePence).containsExactly(1450);
        assertThat(all).extracting(HomeMonthlyPrice::getChangedBy).containsExactly("launch prices");
    }

    @Test
    void aFamilySubscribesCancelsAndTheMonthEndsWithNoStripeKeys() throws Exception {
        assertThat(stripe.getClass().getSimpleName()).isEqualTo("FakeStripe");
        String email = "fake-" + UUID.randomUUID() + "@example.test";
        users.save(new AppUser(email, "A Parent", passwordEncoder.encode("password"), Role.PARENT, null, null));
        String token = login(email);
        mvc.perform(post("/api/home/children").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ada\",\"ageMonths\":50}"))
                .andExpect(status().isOk());
        LocalDate today = LocalDate.now(clock);

        mvc.perform(get("/api/home/pass").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.cardPayments").value(true)).andExpect(jsonPath("$.pricePence").value(1450))
                .andExpect(jsonPath("$.canSubscribe").value(true));
        // The "page" is an address on this server, which goes back to the web app as Stripe does.
        String page = JsonPath.read(mvc.perform(post("/api/home/subscription/checkout")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("/api/dev/stripe/checkout/cs_fake_")))
                .andReturn().getResponse().getContentAsString(), "$.url");
        mvc.perform(get(page)).andExpect(status().isFound())
                .andExpect(header().string("Location", "/home?subscribed=1"));
        // Paid once: the page is gone.
        mvc.perform(get(page)).andExpect(status().isNotFound());
        mvc.perform(get("/api/home/pass").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.subscription").value("ACTIVE")).andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.renewsOn").value(today.plusMonths(1).toString()))
                .andExpect(jsonPath("$.canManage").value(true));
        String mine = mvc.perform(get("/api/payments/mine").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(mine, "$.payments[*].amountPence")).containsExactly(1450);

        String portal = JsonPath.read(mvc.perform(post("/api/home/subscription/portal")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.url");
        assertThat(portal).startsWith("/api/dev/stripe/portal/cus_fake_");
        mvc.perform(get(portal)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Development only")));

        // The next month is taken.
        mvc.perform(get(portal + "/next-month")).andExpect(status().isFound());
        mvc.perform(get("/api/home/pass").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.renewsOn").value(today.plusMonths(2).toString()));
        // The card is refused, then a payment goes through.
        mvc.perform(get(portal + "/next-month-fails")).andExpect(status().isFound());
        mvc.perform(get("/api/home/pass").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.subscription").value("PAST_DUE"));
        mvc.perform(get(portal + "/next-month")).andExpect(status().isFound());
        mvc.perform(get("/api/home/pass").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.subscription").value("ACTIVE"))
                .andExpect(jsonPath("$.endsOn").value(today.plusMonths(3).toString()));
        // Cancelled on the page, and the paid month ends.
        mvc.perform(get(portal + "/cancel")).andExpect(status().isFound());
        mvc.perform(get("/api/home/pass").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.subscription").value("CANCELLING"));
        mvc.perform(get(portal + "/next-month")).andExpect(status().isFound());
        mvc.perform(get("/api/home/pass").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.subscription").value("NONE")).andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.endsOn").value(today.plusMonths(3).toString()));
        mvc.perform(get(portal + "/no-such-action")).andExpect(status().isNotFound());
        mvc.perform(get("/api/dev/stripe/portal/cus_unknown")).andExpect(status().isNotFound());
    }

    @Test
    void aPaymentThatIsDueIsPaidByCardOnTheStandIn() throws Exception {
        String admin = login("admin@motormovers.test");
        String body = mvc.perform(post("/api/admin/payments").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payerEmail\":\"parent@example.test\",\"purpose\":\"OTHER\",\"description\":\"Cards\","
                        + "\"amountPence\":1250,\"invoicedOn\":\"" + LocalDate.now(clock) + "\",\"paidOn\":null}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer payment = JsonPath.read(body, "$.id");
        String jo = login("parent@example.test");
        String page = JsonPath.read(mvc.perform(post("/api/payments/" + payment + "/checkout")
                .header("Authorization", "Bearer " + jo)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.url");
        mvc.perform(get(page)).andExpect(status().isFound()).andExpect(header().string("Location", "/payments?paid=1"));
        String mine = mvc.perform(get("/api/payments/mine").header("Authorization", "Bearer " + jo))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(mine, "$.payments[?(@.id == " + payment + ")].status"))
                .containsExactly("PAID");
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
