package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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

import uk.co.motormovers.platform.common.config.Mailer;
import uk.co.motormovers.platform.home.model.HomeMonthlyPrice;
import uk.co.motormovers.platform.home.model.HomeSubscription;
import uk.co.motormovers.platform.home.repository.HomeMonthlyPriceRepository;
import uk.co.motormovers.platform.home.repository.HomePassRepository;
import uk.co.motormovers.platform.home.repository.HomeSubscriptionRepository;
import uk.co.motormovers.platform.home.service.HomePassService;
import uk.co.motormovers.platform.home.service.HomeSubscriptionService;
import uk.co.motormovers.platform.notification.service.PushService;
import uk.co.motormovers.platform.payment.stripe.StripeClient;
import uk.co.motormovers.platform.payment.stripe.StripeSignature;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * The Programme at Home billed monthly (go-live task 68): on sale once priced, subscribed to on Stripe's page, each
 * month recorded from Stripe's signed message as a payment and the days it covers, failed payments, cancelling, the
 * team's cancel, a refund, and who may do what. Its own database with Stripe switched on and Stripe itself a mock.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:homesubscription;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "platform.stripe.secret-key=sk_test_platform", "platform.stripe.webhook-secret=whsec_monthly",
    "platform.login-limit-per-ip=500",
    // No launch price for monthly (task 87 gave every country one): these tests set their own prices and dates.
    "platform.countries.uk.launch-prices.home-monthly=",
})
@AutoConfigureMockMvc
class HomeSubscriptionTest {

    private static final String SECRET = "whsec_monthly";
    private static final int PRICE = 1500;
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    HomePassService passes;

    @Autowired
    HomePassRepository passRows;

    @Autowired
    HomeMonthlyPriceRepository prices;

    @Autowired
    HomeSubscriptionRepository subscriptions;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    StripeClient stripe;

    @MockitoBean
    Mailer mailer;

    @MockitoBean
    PushService push;

    @BeforeEach
    void aPriceAndStripesAnswers() {
        if (prices.count() == 0) {
            prices.save(new HomeMonthlyPrice(PRICE, LocalDate.now(clock).minusDays(1), "test", clock.instant()));
        }
        when(stripe.createSubscriptionCheckout(any(), any(), any(), anyInt(), anyString(), any(), anyString(),
                anyString())).thenAnswer(call -> new StripeClient.Checkout("cs_" + call.getArgument(0),
                        "https://checkout.stripe.com/c/pay/cs_" + call.getArgument(0)));
        when(stripe.createPortal(anyString(), anyString())).thenReturn("https://billing.stripe.com/p/session/1");
        when(stripe.createRefund(anyString(), anyInt(), any(), anyString()))
                .thenReturn(new StripeClient.Refund("re_" + NEXT.getAndIncrement(), "succeeded"));
        when(mailer.link(anyString())).thenAnswer(call -> "https://web.test" + call.getArgument(0));
        clearInvocations(stripe, mailer, push);
    }

    @Test
    void withNoMonthlyPriceItIsNotOnSale() throws Exception {
        List<HomeMonthlyPrice> saved = prices.findAll();
        prices.deleteAll();
        try {
            Family f = family();
            pass(f.token).andExpect(jsonPath("$.onSale").value(false))
                    .andExpect(jsonPath("$.pricePence").doesNotExist())
                    .andExpect(jsonPath("$.canSubscribe").value(false))
                    .andExpect(jsonPath("$.subscription").value("NONE"));
            subscribe(f.token).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("not on sale")));
        } finally {
            saved.forEach(p -> prices.save(new HomeMonthlyPrice(p.getPricePence(), p.getEffectiveFrom(),
                    p.getChangedBy(), p.getChangedAt())));
        }
    }

    @Test
    void theFirstMonthOpensTheGateTheNextCarriesOnAndARepeatedMessageRecordsNothing() throws Exception {
        Family f = family();
        inductionDone(f.email);
        pass(f.token).andExpect(jsonPath("$.onSale").value(true)).andExpect(jsonPath("$.pricePence").value(PRICE))
                .andExpect(jsonPath("$.canSubscribe").value(true)).andExpect(jsonPath("$.cardPayments").value(true));
        session(f.token, f.child).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("subscri")));

        String url = JsonPath.read(subscribe(f.token).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(), "$.url");
        // Pressing Subscribe twice opens the same page: one subscription, one call to Stripe.
        subscribe(f.token).andExpect(status().isOk()).andExpect(jsonPath("$.url").value(url));
        verify(stripe, times(1)).createSubscriptionCheckout(any(), eq(f.familyId), any(), eq(PRICE),
                eq(HomeSubscriptionService.DESCRIPTION), eq(f.email), contains("/home?subscribed=1"), anyString());
        long row = onlyRow(f);

        // Coming back from Stripe proves nothing: only the signed messages do.
        pass(f.token).andExpect(jsonPath("$.running").value(false)).andExpect(jsonPath("$.subscription").value("NONE"));
        webhook(StripeMessages.checkoutCompleted("cs_" + row, row, "sub_" + row, "cus_" + row), "bad-signature")
                .andExpect(status().isBadRequest());
        pass(f.token).andExpect(jsonPath("$.running").value(false));

        ZonedDateTime start = ZonedDateTime.now(clock);
        LocalDate today = start.toLocalDate();
        when(stripe.invoicePaymentIntent("in_" + row + "_1")).thenReturn("pi_" + row + "_1");
        webhook(StripeMessages.checkoutCompleted("cs_" + row, row, "sub_" + row, "cus_" + row));
        String first = StripeMessages.invoicePaid("in_" + row + "_1", row, "sub_" + row, "cus_" + row, PRICE, "gbp",
                start.toInstant(), start.plusMonths(1).toInstant());
        webhook(first);

        // Covered from today to the day Stripe takes the next month, both included.
        LocalDate renews = today.plusMonths(1);
        pass(f.token).andExpect(jsonPath("$.running").value(true)).andExpect(jsonPath("$.subscription").value("ACTIVE"))
                .andExpect(jsonPath("$.endsOn").value(renews.toString()))
                .andExpect(jsonPath("$.renewsOn").value(renews.toString()))
                .andExpect(jsonPath("$.canSubscribe").value(false)).andExpect(jsonPath("$.canManage").value(true));
        session(f.token, f.child).andExpect(status().isOk());

        // The month is the payer's payment, paid, with its tax (20% in the UK: 250 of 1500).
        String mine = fetch(f.token, "/api/payments/mine");
        assertThat(JsonPath.<List<String>>read(mine, "$.payments[*].status")).containsExactly("PAID");
        assertThat(JsonPath.<List<Integer>>read(mine, "$.payments[*].amountPence")).containsExactly(PRICE);
        assertThat(JsonPath.<List<Integer>>read(mine, "$.payments[*].taxPence")).containsExactly(250);
        assertThat(JsonPath.<List<String>>read(mine, "$.payments[*].purposeLabel")).containsExactly("Home programme");
        assertThat(JsonPath.<List<Boolean>>read(mine, "$.payments[*].subscriptionMonth")).containsExactly(true);
        assertThat(JsonPath.<List<Boolean>>read(mine, "$.payments[*].paidOnline")).containsExactly(true);

        // Stripe sends a message again: nothing more is recorded.
        webhook(first);
        webhook(StripeMessages.checkoutCompleted("cs_" + row, row, "sub_" + row, "cus_" + row));
        assertThat(JsonPath.<List<Integer>>read(fetch(f.token, "/api/payments/mine"), "$.payments[*].id")).hasSize(1);
        assertThat(passRows.findByFamilyIdOrderById(f.familyId)).hasSize(1);

        // The next month, as an older version of Stripe says it: cover carries on with no day missing.
        webhook(StripeMessages.invoicePaidOldShape("in_" + row + "_2", row, "sub_" + row, "cus_" + row, PRICE, "gbp",
                start.plusMonths(1).toInstant(), start.plusMonths(2).toInstant(), "pi_" + row + "_2"));
        LocalDate second = today.plusMonths(2);
        pass(f.token).andExpect(jsonPath("$.endsOn").value(second.toString()))
                .andExpect(jsonPath("$.renewsOn").value(second.toString()));
        for (LocalDate day = today; !day.isAfter(second); day = day.plusDays(1)) {
            assertThat(passes.coveredOn(f.familyId, day)).as("covered on %s", day).isTrue();
        }
        assertThat(passes.coveredOn(f.familyId, second.plusDays(1))).isFalse();
        assertThat(JsonPath.<List<Integer>>read(fetch(f.token, "/api/payments/mine"), "$.payments[*].id")).hasSize(2);
        // A running subscription gets no "your pass is ending" reminders, even on its last paid day.
        passRows.findByFamilyIdOrderById(f.familyId).forEach(p -> passRows.save(p.runs(p.getStartsOn(), today)));
        clearInvocations(mailer);
        passes.remindEnding();
        verify(mailer, never()).send(eq(f.email), anyString(), anyString());
    }

    @Test
    void aFailedPaymentTellsThePayerOnceAndAccessEndsWithThePaidMonth() throws Exception {
        Family f = subscribed();
        long row = f.row;
        ZonedDateTime start = ZonedDateTime.now(clock);
        LocalDate paidTo = start.toLocalDate().plusMonths(1);

        String failed = StripeMessages.paymentFailed("in_" + row + "_2", row, "sub_" + row, "cus_" + row, PRICE, "gbp",
                start.plusMonths(1).toInstant(), start.plusMonths(2).toInstant());
        webhook(failed);
        verify(mailer).send(eq(f.email), contains("could not take this month's payment"), contains("Manage"));
        verify(push).notify(eq(List.of(f.userId)), contains("did not go through"), anyString(), anyString());
        webhook(StripeMessages.subscriptionUpdated("sub_" + row, row, "cus_" + row, "past_due", false, ""));
        // Stripe tries again and fails again on the same invoice: the payer is not told twice.
        webhook(failed);
        verify(mailer, times(1)).send(eq(f.email), contains("could not take"), anyString());

        // No extra days: cover is what was paid for.
        pass(f.token).andExpect(jsonPath("$.subscription").value("PAST_DUE"))
                .andExpect(jsonPath("$.running").value(true)).andExpect(jsonPath("$.endsOn").value(paidTo.toString()))
                .andExpect(jsonPath("$.renewsOn").doesNotExist()).andExpect(jsonPath("$.canManage").value(true));
        assertThat(passes.coveredOn(f.familyId, paidTo.plusDays(1))).isFalse();
        assertThat(JsonPath.<List<Integer>>read(fetch(f.token, "/api/payments/mine"), "$.payments[*].id")).hasSize(1);

        // The retry goes through: paid up again, covered for the month it pays for.
        webhook(StripeMessages.invoicePaid("in_" + row + "_2", row, "sub_" + row, "cus_" + row, PRICE, "gbp",
                start.plusMonths(1).toInstant(), start.plusMonths(2).toInstant()));
        pass(f.token).andExpect(jsonPath("$.subscription").value("ACTIVE"))
                .andExpect(jsonPath("$.endsOn").value(start.toLocalDate().plusMonths(2).toString()));

        // Another month fails and Stripe gives up: the subscription is over, and the family is told once.
        webhook(StripeMessages.paymentFailed("in_" + row + "_3", row, "sub_" + row, "cus_" + row, PRICE, "gbp",
                start.plusMonths(2).toInstant(), start.plusMonths(3).toInstant()));
        verify(mailer, times(2)).send(eq(f.email), contains("could not take"), anyString());
        webhook(StripeMessages.subscriptionDeleted("sub_" + row, row, "cus_" + row));
        webhook(StripeMessages.subscriptionDeleted("sub_" + row, row, "cus_" + row));
        verify(mailer, times(1)).send(eq(f.email), contains("has been cancelled"), anyString());
        pass(f.token).andExpect(jsonPath("$.subscription").value("NONE")).andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.canManage").value(false));
    }

    @Test
    void cancellingKeepsThePaidMonthAndTheFamilyIsToldTheLastDayOnce() throws Exception {
        Family f = subscribed();
        long row = f.row;
        LocalDate paidTo = LocalDate.now(clock).plusMonths(1);

        // The payer opens Stripe's own page.
        mvc.perform(post("/api/home/subscription/portal").header("Authorization", bearer(f.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://billing.stripe.com/p/session/1"));
        verify(stripe).createPortal(eq("cus_" + row), contains("/home"));

        webhook(StripeMessages.subscriptionUpdated("sub_" + row, row, "cus_" + row, "active", true, ""));
        webhook(StripeMessages.subscriptionUpdated("sub_" + row, row, "cus_" + row, "active", true, ""));
        verify(mailer, times(1)).send(eq(f.email), contains("has been cancelled"),
                contains("Sessions stay open until"));
        pass(f.token).andExpect(jsonPath("$.subscription").value("CANCELLING"))
                .andExpect(jsonPath("$.running").value(true)).andExpect(jsonPath("$.endsOn").value(paidTo.toString()))
                .andExpect(jsonPath("$.renewsOn").doesNotExist()).andExpect(jsonPath("$.canSubscribe").value(false));
        session(f.token, f.child).andExpect(status().isOk());

        // They change their mind: it renews again. Cancelled again, as Stripe's newer versions say it (cancel_at).
        webhook(StripeMessages.subscriptionUpdated("sub_" + row, row, "cus_" + row, "active", false, ""));
        pass(f.token).andExpect(jsonPath("$.subscription").value("ACTIVE"))
                .andExpect(jsonPath("$.renewsOn").value(paidTo.toString()));
        webhook(StripeMessages.subscriptionUpdated("sub_" + row, row, "cus_" + row, "active", false,
                ",\"cancel_at\":" + Instant.now(clock).plusSeconds(86_400 * 30L).getEpochSecond()));
        pass(f.token).andExpect(jsonPath("$.subscription").value("CANCELLING"));
        verify(mailer, times(2)).send(eq(f.email), contains("has been cancelled"), anyString());

        // The paid month ends at Stripe: the cover already paid for stays, and nothing more is sent.
        webhook(StripeMessages.subscriptionDeleted("sub_" + row, row, "cus_" + row));
        verify(mailer, times(2)).send(eq(f.email), contains("has been cancelled"), anyString());
        pass(f.token).andExpect(jsonPath("$.subscription").value("NONE")).andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.endsOn").value(paidTo.toString()));
        assertThat(passes.coveredOn(f.familyId, paidTo)).isTrue();
        assertThat(passes.coveredOn(f.familyId, paidTo.plusDays(1))).isFalse();
        // On its last day the family can subscribe again; before that it is still covered.
        subscribe(f.token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("covered until")));
    }

    @Test
    void aMonthRefundedInFullEndsThatCoverOnlyAndTheSubscriptionCarriesOn() throws Exception {
        Family f = subscribed();
        String admin = login("admin@motormovers.test");
        LocalDate today = LocalDate.now(clock);
        Integer payment = JsonPath.<List<Integer>>read(fetch(f.token, "/api/payments/mine"), "$.payments[*].id")
                .getFirst();

        mvc.perform(post("/api/admin/payments/" + payment + "/refund").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPence\":" + PRICE + ",\"reason\":\"Changed their mind\",\"clientId\":\""
                        + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        // Through Stripe, against the payment that paid the invoice.
        verify(stripe).createRefund(eq("pi_" + f.row + "_1"), eq(PRICE), any(), anyString());
        verify(stripe, never()).cancelSubscription(anyString(), anyBoolean());
        pass(f.token).andExpect(jsonPath("$.endsOn").value(today.toString()))
                .andExpect(jsonPath("$.subscription").value("ACTIVE"));
        assertThat(passes.coveredOn(f.familyId, today.plusDays(1))).isFalse();

        // The next month Stripe takes covers its own days as usual.
        ZonedDateTime start = ZonedDateTime.now(clock);
        webhook(StripeMessages.invoicePaid("in_" + f.row + "_2", f.row, "sub_" + f.row, "cus_" + f.row, PRICE, "gbp",
                start.plusMonths(1).toInstant(), start.plusMonths(2).toInstant()));
        assertThat(passes.coveredOn(f.familyId, today.plusMonths(1).plusDays(3))).isTrue();
    }

    @Test
    void oneSubscriptionForAFamilyAndOnlyThePayerManagesIt() throws Exception {
        Family f = family();
        subscribe(f.token).andExpect(status().isOk());
        // While the payer's page is open, a carer cannot start a second subscription.
        Account carer = joins(f);
        subscribe(carer.token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Another parent or carer has started")));

        long row = onlyRow(f);
        paysFirstMonth(row);
        subscribe(carer.token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("already subscribes")));
        subscribe(f.token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("already subscribes")));
        // The carer is covered by the family's subscription, but does not manage it.
        pass(carer.token).andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.subscription").value("ACTIVE")).andExpect(jsonPath("$.canManage").value(false));
        mvc.perform(post("/api/home/subscription/portal").header("Authorization", bearer(carer.token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("who subscribed")));
        verify(stripe, times(1)).createSubscriptionCheckout(any(), any(), any(), anyInt(), anyString(), any(),
                anyString(), anyString());
        assertThat(subscriptions.findByFamilyIdOrderById(f.familyId)).hasSize(1);
    }

    @Test
    void anotherFamilyAndTheOtherRolesAreRefused() throws Exception {
        Family f = subscribed();
        // Another family's parent sees and manages only their own.
        Family other = family();
        pass(other.token).andExpect(jsonPath("$.subscription").value("NONE"))
                .andExpect(jsonPath("$.running").value(false));
        mvc.perform(post("/api/home/subscription/portal").header("Authorization", bearer(other.token)))
                .andExpect(status().isBadRequest());
        for (String who : List.of(f.email, "home@example.test")) {
            String token = login(who);
            mvc.perform(get("/api/admin/home-pass/monthly-prices").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            price(token, 1, LocalDate.now(clock).plusDays(3)).andExpect(status().isForbidden());
            cancel(token, f.email, "Not theirs to cancel").andExpect(status().isForbidden());
        }
        // Subscribing and managing are for parents only.
        for (String who : List.of("teacher@oakfield.test", "head@oakfield.test", "admin@motormovers.test",
                "trainer@motormovers.test")) {
            String token = login(who);
            subscribe(token).andExpect(status().isForbidden());
            mvc.perform(post("/api/home/subscription/portal").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/home/pass").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/home/subscription/checkout")).andExpect(status().isUnauthorized());
        verify(stripe, never()).cancelSubscription(anyString(), anyBoolean());
        pass(f.token).andExpect(jsonPath("$.subscription").value("ACTIVE"));
    }

    @Test
    void theTeamCancelsAFamilysSubscriptionAndItIsRecorded() throws Exception {
        Family f = subscribed();
        String admin = login("admin@motormovers.test");
        mvc.perform(get("/api/admin/home-pass/family").param("email", f.email).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.subscription.state").value("ACTIVE"))
                .andExpect(jsonPath("$.subscription.pricePence").value(PRICE))
                .andExpect(jsonPath("$.subscription.payerEmail").value(f.email))
                .andExpect(jsonPath("$.history[0].how").value("Monthly payment"));
        cancel(admin, f.email, " ").andExpect(status().isBadRequest());
        verify(stripe, never()).cancelSubscription(anyString(), anyBoolean());

        cancel(admin, f.email, "Asked by phone").andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.state").value("CANCELLING"))
                .andExpect(jsonPath("$.subscription.cancelledBy").value("admin@motormovers.test"))
                .andExpect(jsonPath("$.subscription.cancelNote").value("Asked by phone"))
                .andExpect(jsonPath("$.running").value(true));
        // At the end of the month paid for: the family keeps what it paid for.
        verify(stripe).cancelSubscription("sub_" + f.row, true);
        verify(mailer, times(1)).send(eq(f.email), contains("has been cancelled"), anyString());
        cancel(admin, f.email, "Again").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("already cancelled")));
        // Stripe's own message about it changes nothing more and sends nothing more.
        webhook(StripeMessages.subscriptionUpdated("sub_" + f.row, f.row, "cus_" + f.row, "active", true, ""));
        verify(mailer, times(1)).send(eq(f.email), contains("has been cancelled"), anyString());
        pass(f.token).andExpect(jsonPath("$.subscription").value("CANCELLING"));

        Family none = family();
        cancel(admin, none.email, "Nothing to cancel").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no running subscription")));
    }

    @Test
    void nobodyIsChargedForAFamilyTheyHaveLeftOrOneThatIsGone() throws Exception {
        // The payer leaves a family that carries on: cancelled at the end of the paid month.
        Family f = subscribed();
        Account carer = joins(f);
        mvc.perform(delete("/api/home/members/" + f.userId).header("Authorization", bearer(f.token)))
                .andExpect(status().isNoContent());
        verify(stripe).cancelSubscription("sub_" + f.row, true);
        pass(carer.token).andExpect(jsonPath("$.subscription").value("CANCELLING"))
                .andExpect(jsonPath("$.running").value(true)).andExpect(jsonPath("$.canManage").value(false));

        // The last member leaves: the family and its records go, and the subscription ends at once.
        Family gone = subscribed();
        mvc.perform(delete("/api/home/members/" + gone.userId).header("Authorization", bearer(gone.token)))
                .andExpect(status().isNoContent());
        verify(stripe).cancelSubscription("sub_" + gone.row, false);
        assertThat(subscriptions.findById(gone.row)).isEmpty();

        // Stripe bills a subscription whose family is gone (its page was left open): it is stopped.
        ZonedDateTime start = ZonedDateTime.now(clock);
        webhook(StripeMessages.invoicePaid("in_orphan", 987_654, "sub_orphan", "cus_orphan", PRICE, "gbp",
                start.toInstant(), start.plusMonths(1).toInstant()));
        verify(stripe).cancelSubscription("sub_orphan", false);
    }

    @Test
    void aFamilyStillCoveredByAPassSubscribesFromItsLastDay() throws Exception {
        Family f = family();
        String admin = login("admin@motormovers.test");
        LocalDate today = LocalDate.now(clock);
        extend(admin, f.email, today.plusDays(10)).andExpect(status().isOk());
        pass(f.token).andExpect(jsonPath("$.running").value(true)).andExpect(jsonPath("$.canSubscribe").value(false))
                .andExpect(jsonPath("$.subscription").value("NONE"));
        subscribe(f.token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("covered until")));

        Family last = family();
        extend(admin, last.email, today).andExpect(status().isOk());
        pass(last.token).andExpect(jsonPath("$.canSubscribe").value(true));
        subscribe(last.token).andExpect(status().isOk());
    }

    @Test
    void theMonthlyPriceIsDatedAndAFamilyKeepsThePriceItSubscribedAt() throws Exception {
        Family f = subscribed();
        String admin = login("admin@motormovers.test");
        LocalDate today = LocalDate.now(clock);
        mvc.perform(get("/api/admin/home-pass/monthly-prices").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.currentPence").value(PRICE));
        price(admin, 1800, today.minusDays(1)).andExpect(status().isBadRequest());
        price(admin, 1800, today.plusDays(30)).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPence").value(PRICE))
                .andExpect(jsonPath("$.prices[0].pricePence").value(1800));
        price(admin, 1900, today.plusDays(30)).andExpect(status().isOk())
                .andExpect(jsonPath("$.prices[0].pricePence").value(1900));
        try {
            // In force today: a new family sees the new price, the subscriber keeps theirs.
            prices.save(new HomeMonthlyPrice(1700, today, "test", clock.instant()));
            pass(family().token).andExpect(jsonPath("$.pricePence").value(1700));
            pass(f.token).andExpect(jsonPath("$.pricePence").value(PRICE));
            price(admin, 1750, today).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("already in force")));
        } finally {
            prices.findByEffectiveFrom(today).ifPresent(prices::delete);
            prices.findByEffectiveFrom(today.plusDays(30)).ifPresent(prices::delete);
        }
    }

    @Test
    void aMonthInAnotherCurrencyOrWithNoPeriodIsNotRecorded() throws Exception {
        Family f = family();
        subscribe(f.token).andExpect(status().isOk());
        long row = onlyRow(f);
        ZonedDateTime start = ZonedDateTime.now(clock);
        webhook(StripeMessages.invoicePaid("in_" + row + "_usd", row, "sub_" + row, "cus_" + row, PRICE, "usd",
                start.toInstant(), start.plusMonths(1).toInstant()));
        webhook(StripeMessages.invoicePaid("in_" + row + "_none", row, "sub_" + row, "cus_" + row, PRICE, "gbp",
                Instant.EPOCH, Instant.EPOCH));
        pass(f.token).andExpect(jsonPath("$.running").value(false)).andExpect(jsonPath("$.subscription").value("NONE"));
        assertThat(JsonPath.<List<Integer>>read(fetch(f.token, "/api/payments/mine"), "$.payments[*].id")).isEmpty();
        assertThat(subscriptions.findById(row).orElseThrow().getStatus()).isEqualTo(HomeSubscription.Status.PENDING);
    }

    // --- Helpers ------------------------------------------------------------------------------------------------------

    private record Account(String email, String token, Long userId) {
    }

    private record Family(String email, String token, Long userId, Integer child, Long familyId, long row) {
    }

    /** A new parent with a child at home, the induction not done, and no cover. */
    private Family family() throws Exception {
        Account a = account();
        Integer child = JsonPath.read(mvc.perform(post("/api/home/children").header("Authorization", bearer(a.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ada\",\"ageMonths\":50}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/home/children/" + child + "/stage").header("Authorization", bearer(a.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"GREEN\"}")).andExpect(status().isOk());
        Number family = JsonPath.read(fetch(login("admin@motormovers.test"),
                "/api/admin/home-pass/family?email=" + a.email), "$.familyId");
        return new Family(a.email, a.token, a.userId, child, family.longValue(), 0);
    }

    /** A family past the induction whose first month Stripe has taken today. */
    private Family subscribed() throws Exception {
        Family f = family();
        inductionDone(f.email);
        subscribe(f.token).andExpect(status().isOk());
        long row = onlyRow(f);
        paysFirstMonth(row);
        clearInvocations(mailer, push);
        return new Family(f.email, f.token, f.userId, f.child, f.familyId, row);
    }

    private void paysFirstMonth(long row) throws Exception {
        ZonedDateTime start = ZonedDateTime.now(clock);
        when(stripe.invoicePaymentIntent("in_" + row + "_1")).thenReturn("pi_" + row + "_1");
        webhook(StripeMessages.checkoutCompleted("cs_" + row, row, "sub_" + row, "cus_" + row));
        webhook(StripeMessages.invoicePaid("in_" + row + "_1", row, "sub_" + row, "cus_" + row, PRICE, "gbp",
                start.toInstant(), start.plusMonths(1).toInstant()));
    }

    private long onlyRow(Family f) {
        List<HomeSubscription> rows = subscriptions.findByFamilyIdOrderById(f.familyId);
        assertThat(rows).hasSize(1);
        return rows.getFirst().getId();
    }

    /** Another parent joins the family as a carer, with the family's code. */
    private Account joins(Family f) throws Exception {
        String code = JsonPath.read(mvc.perform(post("/api/home/invites").header("Authorization", bearer(f.token)))
                .andReturn().getResponse().getContentAsString(), "$.code");
        Account carer = account();
        mvc.perform(post("/api/home/join").header("Authorization", bearer(carer.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());
        return carer;
    }

    /** A new parent account, saved directly: sign-up allows ten an hour from one address, and these tests need more. */
    private Account account() throws Exception {
        String email = "monthly-" + UUID.randomUUID() + "@example.test";
        AppUser user = users.save(new AppUser(email, "A Parent", passwordEncoder.encode("password"), Role.PARENT,
                null, null));
        return new Account(email, login(email), user.getId());
    }

    private void inductionDone(String email) {
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        user.completeInduction(LocalDate.now(clock));
        users.save(user);
    }

    private ResultActions pass(String token) throws Exception {
        return mvc.perform(get("/api/home/pass").header("Authorization", bearer(token))).andExpect(status().isOk());
    }

    private ResultActions subscribe(String token) throws Exception {
        return mvc.perform(post("/api/home/subscription/checkout").header("Authorization", bearer(token)));
    }

    private ResultActions session(String token, Integer child) throws Exception {
        return mvc.perform(post("/api/home/sessions").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"clientId\":\"" + UUID.randomUUID() + "\",\"childId\":" + child + ",\"doneOn\":\""
                        + LocalDate.now(clock) + "\"}]"));
    }

    private ResultActions extend(String admin, String email, LocalDate endsOn) throws Exception {
        return mvc.perform(post("/api/admin/home-pass/extend").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"endsOn\":\"" + endsOn + "\",\"note\":\"Support\"}"));
    }

    private ResultActions cancel(String token, String email, String note) throws Exception {
        return mvc.perform(post("/api/admin/home-pass/cancel-subscription").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"note\":\"" + note + "\"}"));
    }

    private ResultActions price(String token, int pence, LocalDate from) throws Exception {
        return mvc.perform(post("/api/admin/home-pass/monthly-prices").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pricePence\":" + pence + ",\"effectiveFrom\":\"" + from + "\"}"));
    }

    /** One of Stripe's messages, signed as Stripe signs it. */
    private void webhook(String payload) throws Exception {
        long t = clock.instant().getEpochSecond();
        webhook(payload, "t=" + t + ",v1=" + StripeSignature.sign(SECRET, t + "." + payload))
                .andExpect(status().isOk());
    }

    private ResultActions webhook(String payload, String signature) throws Exception {
        return mvc.perform(post("/api/stripe/webhook").header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON).content(payload));
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
