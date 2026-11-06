package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.common.config.Mailer;
import uk.co.motormovers.platform.payment.stripe.StripeClient;
import uk.co.motormovers.platform.payment.stripe.StripeSignature;

/** Refunds and credit notes: who can refund, Stripe and by hand, webhooks, totals, numbers and who sees what. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:refundtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "platform.stripe.secret-key=sk_test_platform", "platform.stripe.webhook-secret=whsec_refund",
})
@AutoConfigureMockMvc
class RefundTest {

    private static final String SECRET = "whsec_refund";

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @MockitoBean
    StripeClient stripe;

    @MockitoBean
    Mailer mailer;

    @Test
    void onlyTheTeamCanRefundAndNeverMoreThanWasPaid() throws Exception {
        Integer id = paidByHand("parent@example.test", 6_000);
        refund(login("parent@example.test"), id, 1_000).andExpect(status().isForbidden());
        refund(login("head@oakfield.test"), id, 1_000).andExpect(status().isForbidden());

        String admin = login("admin@motormovers.test");
        refund(admin, id, 6_001).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("left to refund")));
        refund(admin, id, 0).andExpect(status().isBadRequest());
        // Only a payment that has been paid.
        Integer due = invoice("parent@example.test", 2_000, null);
        refund(admin, due, 1_000).andExpect(status().isBadRequest());
    }

    @Test
    void twoPartRefundsAddUpAndTheStatusSaysSo() throws Exception {
        String admin = login("admin@motormovers.test");
        Integer id = paidByHand("practitioner@example.test", 6_000);
        // Paid another way: recorded, for the money to go back by hand. VAT at 20% in £20 is £3.33.
        refund(admin, id, 2_000).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.refundLabel").value("Part refunded"))
                .andExpect(jsonPath("$.refundedPence").value(2_000))
                .andExpect(jsonPath("$.refunds[0].byHand").value(true))
                .andExpect(jsonPath("$.refunds[0].taxPence").value(333));
        verify(stripe, never()).createRefund(anyString(), anyInt(), any(), anyString());

        refund(admin, id, 4_001).andExpect(status().isBadRequest());
        // The last refund takes the rest of the tax, so the two add up to the £10 VAT on the payment.
        refund(admin, id, 4_000).andExpect(status().isOk())
                .andExpect(jsonPath("$.refundLabel").value("Refunded"))
                .andExpect(jsonPath("$.refunds[1].taxPence").value(667));
        refund(admin, id, 1).andExpect(status().isBadRequest());
    }

    @Test
    void aCardPaymentGoesBackThroughStripeAndAFailureSavesNothing() throws Exception {
        String admin = login("admin@motormovers.test");
        String intent = "pi_" + UUID.randomUUID();
        Integer id = paidByCard("parent@example.test", 4_500, intent);
        List<Integer> before = creditNoteNumbers(admin);

        when(stripe.createRefund(eq(intent), eq(1_500), any(), anyString()))
                .thenThrow(new IllegalStateException("Stripe did not make the refund. Nothing has been recorded."));
        refund(admin, id, 1_500).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Nothing has been recorded")));
        assertThat(paymentField(admin, id, "refundedPence")).isEqualTo(List.of(0));

        when(stripe.createRefund(eq(intent), eq(2_000), any(), anyString()))
                .thenReturn(new StripeClient.Refund("re_" + UUID.randomUUID(), "succeeded"));
        String clientId = UUID.randomUUID().toString();
        refund(admin, id, 2_000, clientId).andExpect(status().isOk())
                .andExpect(jsonPath("$.refunds[0].byHand").value(false));
        // Sent twice (a double click, say): one refund, one call to Stripe.
        refund(admin, id, 2_000, clientId).andExpect(status().isOk())
                .andExpect(jsonPath("$.refundedPence").value(2_000));
        verify(stripe, times(1)).createRefund(eq(intent), eq(2_000), eq(id.longValue()), anyString());

        // The failure took no number: credit notes have no gaps.
        List<Integer> after = creditNoteNumbers(admin);
        assertThat(after).hasSize(before.size() + 1);
        for (int i = 1; i < after.size(); i++) {
            assertThat(after.get(i)).isEqualTo(after.get(i - 1) + 1);
        }
    }

    @Test
    void aRefundInStripesDashboardIsRecordedOnceAndARepeatedWebhookChangesNothing() throws Exception {
        String admin = login("admin@motormovers.test");
        String intent = "pi_" + UUID.randomUUID();
        Integer id = paidByCard("parent@example.test", 4_500, intent);
        String refundId = "re_" + UUID.randomUUID();

        // Stripe sends both events, and may repeat them.
        String created = refundEvent("refund.created", refundId, intent, 1_000, "succeeded");
        webhook(created).andExpect(status().isOk());
        webhook(created).andExpect(status().isOk());
        webhook(refundEvent("refund.updated", refundId, intent, 1_000, "succeeded")).andExpect(status().isOk());
        assertThat(paymentField(admin, id, "refundedPence")).isEqualTo(List.of(1_000));
        assertThat(paymentField(admin, id, "refundLabel")).isEqualTo(List.of("Part refunded"));

        // A team refund and its own webhook agree too.
        String teamRefund = "re_" + UUID.randomUUID();
        when(stripe.createRefund(eq(intent), eq(500), any(), anyString()))
                .thenReturn(new StripeClient.Refund(teamRefund, "succeeded"));
        refund(admin, id, 500).andExpect(status().isOk());
        webhook(refundEvent("refund.created", teamRefund, intent, 500, "succeeded")).andExpect(status().isOk());
        assertThat(paymentField(admin, id, "refundedPence")).isEqualTo(List.of(1_500));

        // An unsigned call is refused, and one for no payment here is ignored.
        mvc.perform(post("/api/stripe/webhook").header("Stripe-Signature", "t=1,v1=forged")
                .contentType(MediaType.APPLICATION_JSON).content(created)).andExpect(status().isBadRequest());
        webhook(refundEvent("refund.created", "re_other", "pi_unknown", 100, "succeeded")).andExpect(status().isOk());

        // In the rare case Stripe cannot return the money, the refund stops counting.
        webhook(refundEvent("refund.failed", refundId, intent, 1_000, "failed")).andExpect(status().isOk());
        assertThat(paymentField(admin, id, "refundedPence")).isEqualTo(List.of(500));
    }

    @Test
    void totalsAreNetOfRefunds() throws Exception {
        String admin = login("admin@motormovers.test");
        String jo = login("parent@example.test");
        Integer paid = JsonPath.read(fetch(admin, "/api/admin/payments/summary"), "$.paidPence");
        Integer mine = JsonPath.read(fetch(jo, "/api/payments/mine"), "$.paidPence");
        Integer lastYear = JsonPath.read(fetch(admin, "/api/admin/payments/summary"), "$.paidLast12MonthsPence");

        Integer id = paidByHand("parent@example.test", 3_000);
        refund(admin, id, 1_200).andExpect(status().isOk());

        String summary = fetch(admin, "/api/admin/payments/summary");
        assertThat((Integer) JsonPath.read(summary, "$.paidPence")).isEqualTo(paid + 1_800);
        assertThat((Integer) JsonPath.read(summary, "$.paidLast12MonthsPence")).isEqualTo(lastYear + 1_800);
        assertThat((Integer) JsonPath.read(fetch(jo, "/api/payments/mine"), "$.paidPence")).isEqualTo(mine + 1_800);

        // A school's refund shows in the head's and the trust lead's totals.
        String head = login("head@oakfield.test");
        Integer oakfield = JsonPath.read(fetch(head, "/api/auth/me"), "$.schoolId");
        Integer schoolBefore = JsonPath.read(fetch(head, "/api/schools/" + oakfield + "/payments"), "$.paidPence");
        Integer school = schoolInvoicePaid(oakfield, 10_000);
        refund(admin, school, 4_000).andExpect(status().isOk());
        assertThat((Integer) JsonPath.read(fetch(head, "/api/schools/" + oakfield + "/payments"), "$.paidPence"))
                .isEqualTo(schoolBefore + 6_000);
    }

    @Test
    void theCreditNoteIsEmailedAndOnlyThosePayingCanSeeIt() throws Exception {
        String admin = login("admin@motormovers.test");
        clearInvocations(mailer);
        Integer id = paidByHand("parent@example.test", 6_000);
        String refunded = refund(admin, id, 6_000).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer refundId = JsonPath.read(refunded, "$.refunds[0].id");
        String number = JsonPath.read(refunded, "$.refunds[0].creditNote");
        verify(mailer).send(eq("parent@example.test"), eq("Credit note " + number), anyString(), eq(number + ".pdf"),
                any(), eq("application/pdf"));

        String path = "/api/payments/refunds/" + refundId + "/credit-note.pdf";
        byte[] pdf = mvc.perform(get(path).header("Authorization", bearer(login("parent@example.test"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Credit note").contains(number).contains("Jo Bennett")
                    .contains("Amount refunded: £60").contains("Includes VAT £10").contains("Original payment: MM-");
        }
        mvc.perform(get(path).header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", bearer(login("practitioner@example.test"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", bearer(login("head@oakfield.test"))))
                .andExpect(status().isForbidden());

        // A payer sees only their own refunds, and the app can ask for the note by email.
        assertThat((List<?>) JsonPath.read(fetch(login("practitioner@example.test"), "/api/payments/mine"),
                "$.payments[?(@.id == " + id + ")]")).isEmpty();
        clearInvocations(mailer);
        mvc.perform(post("/api/payments/refunds/" + refundId + "/credit-note/email")
                .header("Authorization", bearer(login("parent@example.test"))))
                .andExpect(status().isNoContent());
        verify(mailer).send(eq("parent@example.test"), anyString(), anyString(), anyString(), any(), anyString());
        mvc.perform(post("/api/payments/refunds/" + refundId + "/credit-note/email")
                .header("Authorization", bearer(login("practitioner@example.test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPaymentWithNoSavedTaxRateShowsNoTaxOnItsRefund() throws Exception {
        // The demo's older payments have no rate, like any from before tax was kept.
        String admin = login("admin@motormovers.test");
        List<Integer> old = JsonPath.read(fetch(admin, "/api/admin/payments"),
                "$[?(@.status == 'PAID' && @.taxPence == null && @.refundedPence == 0 && @.payerEmail != null)].id");
        String refunded = refund(admin, old.getFirst(), 100).andExpect(status().isOk())
                .andExpect(jsonPath("$.refunds[0].taxPence").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        byte[] pdf = mvc.perform(get("/api/payments/refunds/" + JsonPath.read(refunded, "$.refunds[0].id")
                + "/credit-note.pdf").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(doc)).doesNotContain("Includes");
        }
    }

    private ResultActions refund(String token, Integer paymentId, int amount) throws Exception {
        return refund(token, paymentId, amount, UUID.randomUUID().toString());
    }

    private ResultActions refund(String token, Integer paymentId, int amount, String clientId) throws Exception {
        return mvc.perform(post("/api/admin/payments/" + paymentId + "/refund").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPence\":" + amount + ",\"reason\":\"Place cancelled\",\"clientId\":\"" + clientId
                        + "\"}"));
    }

    private Integer paidByHand(String email, int amount) throws Exception {
        return invoice(email, amount, LocalDate.now(clock));
    }

    private Integer invoice(String email, int amount, LocalDate paidOn) throws Exception {
        return recordPayment("\"payerEmail\":\"" + email + "\"", amount, paidOn);
    }

    private Integer schoolInvoicePaid(Integer schoolId, int amount) throws Exception {
        return recordPayment("\"schoolId\":" + schoolId, amount, LocalDate.now(clock));
    }

    private Integer recordPayment(String payer, int amount, LocalDate paidOn) throws Exception {
        String body = mvc.perform(post("/api/admin/payments")
                .header("Authorization", bearer(login("admin@motormovers.test")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + payer + ",\"purpose\":\"OTHER\",\"description\":\"Session cards\",\"amountPence\":"
                        + amount + ",\"invoicedOn\":\"" + LocalDate.now(clock) + "\",\"reference\":\"MM-" + amount
                        + "\",\"paidOn\":" + (paidOn == null ? "null" : "\"" + paidOn + "\"") + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private Integer paidByCard(String email, int amount, String intent) throws Exception {
        Integer id = invoice(email, amount, null);
        webhook("{\"id\":\"evt_paid\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_1\","
                + "\"payment_status\":\"paid\",\"payment_intent\":\"" + intent + "\",\"client_reference_id\":\"" + id
                + "\",\"metadata\":{\"paymentId\":\"" + id + "\"}}}}").andExpect(status().isOk());
        return id;
    }

    /** A dashboard refund has no metadata: the payment is found by its payment intent. */
    private static String refundEvent(String type, String refundId, String intent, int amount, String refundStatus) {
        return "{\"id\":\"evt_" + UUID.randomUUID() + "\",\"type\":\"" + type + "\",\"data\":{\"object\":{\"id\":\""
                + refundId + "\",\"object\":\"refund\",\"amount\":" + amount + ",\"payment_intent\":\"" + intent
                + "\",\"status\":\"" + refundStatus + "\"}}}";
    }

    private ResultActions webhook(String payload) throws Exception {
        long t = clock.instant().getEpochSecond();
        return mvc.perform(post("/api/stripe/webhook")
                .header("Stripe-Signature", "t=" + t + ",v1=" + StripeSignature.sign(SECRET, t + "." + payload))
                .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private List<Object> paymentField(String admin, Integer id, String field) throws Exception {
        return JsonPath.read(fetch(admin, "/api/admin/payments"), "$[?(@.id == " + id + ")]." + field);
    }

    /** Every credit note number so far, in order. */
    private List<Integer> creditNoteNumbers(String admin) throws Exception {
        List<String> labels = JsonPath.read(fetch(admin, "/api/admin/payments"), "$[*].refunds[*].creditNote");
        List<Integer> numbers = new ArrayList<>();
        labels.forEach(l -> numbers.add(Integer.valueOf(l.substring(3))));
        numbers.sort(Integer::compare);
        return numbers;
    }

    private String fetch(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").with(r -> {
                    r.setRemoteAddr("10.0.0.28");
                    return r;
                }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
