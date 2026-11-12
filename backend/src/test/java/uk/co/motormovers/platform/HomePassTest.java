package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.common.config.Mailer;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.home.model.HomePass;
import uk.co.motormovers.platform.home.model.HomePassPrice;
import uk.co.motormovers.platform.home.repository.HomePassPriceRepository;
import uk.co.motormovers.platform.home.repository.HomePassRepository;
import uk.co.motormovers.platform.home.service.HomePassService;
import uk.co.motormovers.platform.payment.stripe.StripeClient;
import uk.co.motormovers.platform.payment.stripe.StripeSignature;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * The Motor Movers at Home pass (go-live task 26): on sale once priced, bought by card and marked paid only by Stripe's
 * signed webhook, twelve months for the whole family, the gate on sessions, the check and ratings, reminders, the
 * team's extension and a refund. Its own database with Stripe switched on.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:homepass;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "platform.stripe.secret-key=sk_test_platform", "platform.stripe.webhook-secret=whsec_pass",
    "platform.login-limit-per-ip=500",
})
@AutoConfigureMockMvc
class HomePassTest {

    private static final String SECRET = "whsec_pass";

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    HomePassService passes;

    @Autowired
    HomePassPriceRepository prices;

    @Autowired
    HomePassRepository passRows;

    @Autowired
    AppUserRepository users;

    @Autowired
    ExerciseRepository exercises;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    StripeClient stripe;

    @MockitoBean
    Mailer mailer;

    @BeforeEach
    void stripeAnswers() {
        when(stripe.createCheckout(any(), anyInt(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new StripeClient.Checkout("cs_pass", "https://checkout.stripe.com/c/pay/cs_pass"));
    }

    @Test
    void withNoPriceThePassIsNotOnSale() throws Exception {
        List<HomePassPrice> saved = prices.findAll();
        prices.deleteAll();
        try {
            Family f = family();
            pass(f.token).andExpect(jsonPath("$.onSale").value(false))
                    .andExpect(jsonPath("$.pricePence").doesNotExist());
            buy(f.token).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("not on sale")));
        } finally {
            saved.forEach(p -> prices.save(new HomePassPrice(p.getPricePence(), p.getEffectiveFrom(),
                    p.getChangedBy(), p.getChangedAt())));
        }
    }

    @Test
    void aPassIsBoughtByCardAndOnlyStripeMarksItPaidThenBuyingAgainCarriesOnFromTheEnd() throws Exception {
        Family f = family();
        pass(f.token).andExpect(jsonPath("$.hasFamily").value(true)).andExpect(jsonPath("$.onSale").value(true))
                .andExpect(jsonPath("$.pricePence").value(3500)).andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.cardPayments").value(true));
        buy(f.token).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/c/pay/cs_pass"));
        // Pressing Buy twice makes one payment, sent back to the home page.
        buy(f.token).andExpect(status().isOk());
        verify(stripe, times(2)).createCheckout(any(), eq(3500), eq(HomePassService.DESCRIPTION), eq(f.email),
                contains("/home?paid=1"), anyString());
        List<Integer> due = JsonPath.read(fetch(f.token, "/api/payments/mine"),
                "$.payments[?(@.status == 'DUE')].id");
        assertThat(due).hasSize(1);
        assertThat(JsonPath.<List<String>>read(fetch(f.token, "/api/payments/mine"),
                "$.payments[*].purposeLabel")).containsExactly("Home programme");

        // Coming back from Stripe proves nothing: only the signed webhook does.
        pass(f.token).andExpect(jsonPath("$.running").value(false));
        paid(due.getFirst());
        LocalDate today = LocalDate.now(clock);
        LocalDate end = today.plusYears(1).minusDays(1);
        pass(f.token).andExpect(jsonPath("$.running").value(true)).andExpect(jsonPath("$.endsOn").value(end.toString()));

        // Bought again while it runs: twelve more months from its end, not from today.
        buy(f.token).andExpect(status().isOk());
        List<Integer> second = JsonPath.read(fetch(f.token, "/api/payments/mine"),
                "$.payments[?(@.status == 'DUE')].id");
        paid(second.getFirst());
        pass(f.token).andExpect(jsonPath("$.endsOn").value(end.plusYears(1).toString()));
    }

    @Test
    void sessionsTheCheckAndRatingsNeedTheInductionAndARunningPass() throws Exception {
        Family f = family();
        Integer child = f.child;
        Long exercise = exercises.findByStageOrderByNumber(ColourStage.GREEN).getFirst().getId();

        // Neither: the induction is named first, and today carries no session.
        mvc.perform(get("/api/home/children/" + child + "/today").header("Authorization", bearer(f.token)))
                .andExpect(jsonPath("$.passRunning").value(false)).andExpect(jsonPath("$.exercises.length()").value(0))
                .andExpect(jsonPath("$.coolDown").doesNotExist());
        check(f.token, child).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("parent induction")));
        session(f.token, child).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("parent induction")));

        // The induction but no pass.
        inductionDone(f.email);
        check(f.token, child).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("pass")));
        session(f.token, child).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("pass")));
        rate(f.token, child, exercise).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("pass")));
        mvc.perform(get("/api/home/children/" + child + "/today").header("Authorization", bearer(f.token)))
                .andExpect(jsonPath("$.exercises.length()").value(0));

        // What stays open without a pass: the family can always see, change and remove its own data.
        mvc.perform(get("/api/home").header("Authorization", bearer(f.token))).andExpect(status().isOk());
        mvc.perform(get("/api/home/children/" + child).header("Authorization", bearer(f.token)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/home/children/" + child).header("Authorization", bearer(f.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ada\",\"ageMonths\":51}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/home/invites").header("Authorization", bearer(f.token))).andExpect(status().isOk());
        Integer second = addChild(f.token, "Bo");
        mvc.perform(delete("/api/home/children/" + second).header("Authorization", bearer(f.token)))
                .andExpect(status().isNoContent());

        // Both: everything opens.
        extend(f.email, LocalDate.now(clock).plusMonths(12)).andExpect(status().isOk());
        check(f.token, child).andExpect(status().isOk());
        session(f.token, child).andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(1));
        rate(f.token, child, exercise).andExpect(status().isOk());
        mvc.perform(get("/api/home/children/" + child + "/today").header("Authorization", bearer(f.token)))
                .andExpect(jsonPath("$.passRunning").value(true))
                .andExpect(jsonPath("$.exercises.length()").value(greaterThan(0)));
    }

    @Test
    void aCarerUsesTheFamilysPassAndAnotherParentCannotSeeOrUseIt() throws Exception {
        Family f = family();
        inductionDone(f.email);
        extend(f.email, LocalDate.now(clock).plusMonths(6)).andExpect(status().isOk());
        String code = JsonPath.read(mvc.perform(post("/api/home/invites").header("Authorization", bearer(f.token)))
                .andReturn().getResponse().getContentAsString(), "$.code");
        Account carer = account();
        mvc.perform(post("/api/home/join").header("Authorization", bearer(carer.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());
        inductionDone(carer.email);
        pass(carer.token).andExpect(jsonPath("$.running").value(true));
        session(carer.token, f.child).andExpect(status().isOk());

        Account stranger = account();
        pass(stranger.token).andExpect(jsonPath("$.hasFamily").value(false)).andExpect(jsonPath("$.running").value(false));
        session(stranger.token, f.child).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/home-pass/family").param("email", f.email)
                .header("Authorization", bearer(stranger.token))).andExpect(status().isForbidden());
        extend(f.email, LocalDate.now(clock).plusYears(2), stranger.token).andExpect(status().isForbidden());
    }

    @Test
    void theTeamExtendsAPassByHandAndItIsRecorded() throws Exception {
        Family f = family();
        LocalDate today = LocalDate.now(clock);
        extend(f.email, today.plusDays(20)).andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.endsOn").value(today.plusDays(20).toString()))
                .andExpect(jsonPath("$.history[0].how").value("Extended by the team"))
                .andExpect(jsonPath("$.history[0].addedBy").value("admin@motormovers.test"))
                .andExpect(jsonPath("$.members[0].email").value(f.email));
        // Only later: an end already covered is refused.
        extend(f.email, today.plusDays(10)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("already runs to")));
        extend(f.email, today.plusDays(40)).andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].startsOn").value(today.plusDays(21).toString()));
    }

    @Test
    void thePriceIsDatedAndAPriceInForceIsNeverRewritten() throws Exception {
        String admin = login("admin@motormovers.test");
        LocalDate today = LocalDate.now(clock);
        mvc.perform(get("/api/admin/home-pass/prices").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.currentPence").value(3500));
        price(admin, 4000, today.minusDays(1)).andExpect(status().isBadRequest());
        price(admin, 4000, today.plusDays(30)).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPence").value(3500))
                .andExpect(jsonPath("$.prices[0].pricePence").value(4000));
        price(admin, 4200, today.plusDays(30)).andExpect(status().isOk())
                .andExpect(jsonPath("$.prices[0].pricePence").value(4200));
        price(login("home@example.test"), 1, today.plusDays(31)).andExpect(status().isForbidden());
    }

    @Test
    void remindersGoOnceAt30And7DaysAndOnTheDay() throws Exception {
        LocalDate today = LocalDate.now(clock);
        Family month = family();
        Family week = family();
        Family day = family();
        Family later = family();
        extend(month.email, today.plusDays(30)).andExpect(status().isOk());
        extend(week.email, today.plusDays(7)).andExpect(status().isOk());
        extend(day.email, today).andExpect(status().isOk());
        extend(later.email, today.plusDays(31)).andExpect(status().isOk());
        clearInvocations(mailer);

        passes.remindEnding();
        verify(mailer).send(eq(month.email), contains("ends on"), anyString());
        verify(mailer).send(eq(week.email), contains("ends on"), anyString());
        verify(mailer).send(eq(day.email), contains("ends today"), anyString());
        verify(mailer, never()).send(eq(later.email), anyString(), anyString());

        // A restart, or tomorrow's run within the same window, sends nothing again.
        passes.remindEnding();
        verify(mailer, times(1)).send(eq(month.email), anyString(), anyString());
        verify(mailer, times(1)).send(eq(day.email), anyString(), anyString());
    }

    @Test
    void aPassRefundedInFullEndsOnTheDayOfTheRefund() throws Exception {
        Family f = family();
        String admin = login("admin@motormovers.test");
        LocalDate today = LocalDate.now(clock);
        // Paid by hand to the team: it counts for the payer's family too.
        String body = mvc.perform(post("/api/admin/payments").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payerEmail\":\"" + f.email + "\",\"purpose\":\"HOME_PASS\",\"description\":\"Pass\","
                        + "\"amountPence\":3500,\"invoicedOn\":\"" + today + "\",\"paidOn\":\"" + today + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer payment = JsonPath.read(body, "$.id");
        pass(f.token).andExpect(jsonPath("$.endsOn").value(today.plusYears(1).minusDays(1).toString()));

        mvc.perform(post("/api/admin/payments/" + payment + "/refund").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPence\":3500,\"reason\":\"Changed their mind\",\"clientId\":\"" + UUID.randomUUID()
                        + "\"}"))
                .andExpect(status().isOk());
        pass(f.token).andExpect(jsonPath("$.endsOn").value(today.toString()));
    }

    @Test
    void aFullRefundWithARenewalQueuedLeavesNoDayWithoutAPass() throws Exception {
        Family f = family();
        String admin = login("admin@motormovers.test");
        LocalDate today = LocalDate.now(clock);
        // This year's pass, and next year's bought early: it is queued to start when this one ends.
        Integer first = paidByHand(admin, f.email, today);
        paidByHand(admin, f.email, today);
        LocalDate renewalEnds = today.plusYears(2).minusDays(1);
        pass(f.token).andExpect(jsonPath("$.endsOn").value(renewalEnds.toString()));

        refundInFull(admin, first);

        // The refunded pass ends today, and the renewal moves forward to start tomorrow, keeping its length.
        Long familyId = familyId(admin, f.email);
        LocalDate renewalStarts = today.plusDays(1);
        LocalDate movedEnd = renewalStarts.plusDays(java.time.temporal.ChronoUnit.DAYS
                .between(today.plusYears(1), renewalEnds));
        pass(f.token).andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.endsOn").value(movedEnd.toString()))
                .andExpect(jsonPath("$.ended").value(false));
        assertThat(passes.coveredOn(familyId, today)).isTrue();
        assertThat(passes.coveredOn(familyId, renewalStarts)).isTrue();
        assertThat(passes.coveredOn(familyId, movedEnd)).isTrue();
        assertThat(passes.coveredOn(familyId, movedEnd.plusDays(1))).isFalse();
    }

    @Test
    void sessionsAndRatingsSavedOfflineWhileThePassRanAreAcceptedAfterItEnds() throws Exception {
        Family f = family();
        String admin = login("admin@motormovers.test");
        LocalDate today = LocalDate.now(clock);
        Long exercise = exercises.findByStageOrderByNumber(ColourStage.GREEN).getFirst().getId();
        inductionDone(f.email);
        // A pass that ran from 20 days ago to 6 days ago.
        Long familyId = familyId(admin, f.email);
        passRows.save(new HomePass(familyId, null, "test", clock.instant(), null)
                .runs(today.minusDays(20), today.minusDays(6)));
        pass(f.token).andExpect(jsonPath("$.running").value(false)).andExpect(jsonPath("$.ended").value(true));

        // Done while it ran: accepted.
        session(f.token, f.child, today.minusDays(10)).andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(1));
        rate(f.token, f.child, exercise, today.minusDays(10)).andExpect(status().isOk());
        // Before the first pass, after it ended, today or tomorrow: refused.
        session(f.token, f.child, today.minusDays(25)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("pass")));
        session(f.token, f.child, today.minusDays(3)).andExpect(status().isBadRequest());
        session(f.token, f.child, today).andExpect(status().isBadRequest());
        session(f.token, f.child, today.plusDays(1)).andExpect(status().isBadRequest());
        rate(f.token, f.child, exercise, today.minusDays(3)).andExpect(status().isBadRequest());
        // The check (today) stays locked.
        check(f.token, f.child).andExpect(status().isBadRequest());
    }

    @Test
    void theInductionIsStillNeededForASessionSavedWhileThePassRan() throws Exception {
        Family f = family();
        LocalDate today = LocalDate.now(clock);
        passRows.save(new HomePass(familyId(login("admin@motormovers.test"), f.email), null, "test", clock.instant(),
                null).runs(today.minusDays(20), today.minusDays(6)));
        session(f.token, f.child, today.minusDays(10)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("parent induction")));
        pass(f.token).andExpect(jsonPath("$.inductionDone").value(false));
        inductionDone(f.email);
        pass(f.token).andExpect(jsonPath("$.inductionDone").value(true));
    }

    @Test
    void theContactDetailsAreLeftOutWhileTheyArePlaceholders() throws Exception {
        Family f = family();
        pass(f.token).andExpect(jsonPath("$.contactEmail").doesNotExist())
                .andExpect(jsonPath("$.contactPhone").doesNotExist());
        mvc.perform(get("/api/settings")).andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").doesNotExist())
                .andExpect(jsonPath("$.contactPhone").doesNotExist());
    }

    // --- Helpers ------------------------------------------------------------------------------------------------------

    /** A pass payment the team records as paid today for the parent. */
    private Integer paidByHand(String admin, String email, LocalDate today) throws Exception {
        String body = mvc.perform(post("/api/admin/payments").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payerEmail\":\"" + email + "\",\"purpose\":\"HOME_PASS\",\"description\":\"Pass\","
                        + "\"amountPence\":3500,\"invoicedOn\":\"" + today + "\",\"paidOn\":\"" + today + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void refundInFull(String admin, Integer payment) throws Exception {
        mvc.perform(post("/api/admin/payments/" + payment + "/refund").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPence\":3500,\"reason\":\"Changed their mind\",\"clientId\":\""
                        + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
    }

    private Long familyId(String admin, String email) throws Exception {
        Number id = JsonPath.read(fetch(admin, "/api/admin/home-pass/family?email=" + email), "$.familyId");
        return id.longValue();
    }

    private record Account(String email, String token) {
    }

    private record Family(String email, String token, Integer child) {
    }

    /** A new parent with a child at home (Green), no induction and no pass. */
    private Family family() throws Exception {
        Account a = account();
        Integer child = addChild(a.token, "Ada");
        mvc.perform(put("/api/home/children/" + child + "/stage").header("Authorization", bearer(a.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"GREEN\"}")).andExpect(status().isOk());
        return new Family(a.email, a.token, child);
    }

    /** A new parent account, saved directly: sign-up allows ten an hour from one address, and these tests need more. */
    private Account account() throws Exception {
        String email = "pass-" + UUID.randomUUID() + "@example.test";
        users.save(new AppUser(email, "A Parent", passwordEncoder.encode("password"), Role.PARENT, null, null));
        return new Account(email, login(email));
    }

    private Integer addChild(String token, String name) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/home/children").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\",\"ageMonths\":50}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void inductionDone(String email) {
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        user.completeInduction(LocalDate.now(clock));
        users.save(user);
    }

    private ResultActions pass(String token) throws Exception {
        return mvc.perform(get("/api/home/pass").header("Authorization", bearer(token))).andExpect(status().isOk());
    }

    private ResultActions buy(String token) throws Exception {
        return mvc.perform(post("/api/home/pass/checkout").header("Authorization", bearer(token)));
    }

    private ResultActions check(String token, Integer child) throws Exception {
        return mvc.perform(get("/api/home/children/" + child + "/check").header("Authorization", bearer(token)));
    }

    private ResultActions session(String token, Integer child) throws Exception {
        return session(token, child, LocalDate.now(clock));
    }

    private ResultActions session(String token, Integer child, LocalDate doneOn) throws Exception {
        return mvc.perform(post("/api/home/sessions").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"clientId\":\"" + UUID.randomUUID() + "\",\"childId\":" + child + ",\"doneOn\":\""
                        + doneOn + "\"}]"));
    }

    private ResultActions rate(String token, Integer child, Long exercise) throws Exception {
        return rate(token, child, exercise, LocalDate.now(clock));
    }

    private ResultActions rate(String token, Integer child, Long exercise, LocalDate on) throws Exception {
        return mvc.perform(post("/api/assessment/exercise-scores").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"clientId\":\"" + UUID.randomUUID() + "\",\"childId\":" + child + ",\"exerciseId\":"
                        + exercise + ",\"point\":\"START\",\"score\":2,\"assessedOn\":\"" + on + "\"}]"));
    }

    private ResultActions extend(String email, LocalDate endsOn) throws Exception {
        return extend(email, endsOn, login("admin@motormovers.test"));
    }

    private ResultActions extend(String email, LocalDate endsOn, String token) throws Exception {
        return mvc.perform(post("/api/admin/home-pass/extend").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"endsOn\":\"" + endsOn + "\",\"note\":\"Support\"}"));
    }

    private ResultActions price(String token, int pence, LocalDate from) throws Exception {
        return mvc.perform(post("/api/admin/home-pass/prices").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pricePence\":" + pence + ",\"effectiveFrom\":\"" + from + "\"}"));
    }

    private void paid(Integer paymentId) throws Exception {
        String payload = "{\"id\":\"evt_" + paymentId + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":"
                + "{\"id\":\"cs_pass\",\"payment_status\":\"paid\",\"payment_intent\":\"pi_pass_" + paymentId
                + "\",\"client_reference_id\":\"" + paymentId + "\",\"metadata\":{\"paymentId\":\"" + paymentId
                + "\"}}}}";
        long t = clock.instant().getEpochSecond();
        mvc.perform(post("/api/stripe/webhook")
                .header("Stripe-Signature", "t=" + t + ",v1=" + StripeSignature.sign(SECRET, t + "." + payload))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
    }

    private String fetch(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", bearer(token))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
