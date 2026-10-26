package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClient;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.payment.stripe.StripeApiClient;
import uk.co.motormovers.platform.payment.stripe.StripeClient;
import uk.co.motormovers.platform.payment.stripe.StripeSignature;

/** Paying by card on Stripe Checkout: who can start a payment, and Stripe's signed confirmation marking it paid. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:stripetest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "platform.stripe.secret-key=sk_test_platform", "platform.stripe.webhook-secret=whsec_test",
})
@AutoConfigureMockMvc
class OnlinePaymentTest {

    private static final String SECRET = "whsec_test";

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @MockitoBean
    StripeClient stripe;

    @Test
    void aParentPaysTheirOwnPaymentByCardAndStripeMarksItPaid() throws Exception {
        when(stripe.createCheckout(any(), anyInt(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new StripeClient.Checkout("cs_test_1", "https://checkout.stripe.com/c/pay/cs_test_1"));
        String jo = login("parent@example.test");
        mvc.perform(get("/api/payments/online").header("Authorization", bearer(jo)))
                .andExpect(jsonPath("$.enabled").value(true));
        Integer due = dueOf(jo);

        call(jo, "/api/payments/" + due + "/checkout")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/c/pay/cs_test_1"));
        verify(stripe).createCheckout(eq(due.longValue()), anyInt(), anyString(), eq("parent@example.test"),
                eq("http://localhost:5173/payments?paid=1"), eq("http://localhost:5173/payments?cancelled=1"));

        // Someone else cannot pay Jo's payment, and coming back from Stripe alone proves nothing.
        call(login("practitioner@example.test"), "/api/payments/" + due + "/checkout").andExpect(status().isForbidden());
        assertThat(statusOf(jo, due)).isEqualTo("DUE");

        // A forged confirmation is refused.
        String event = paidEvent(due);
        webhook(event, "t=" + clock.instant().getEpochSecond() + ",v1=forged").andExpect(status().isBadRequest());
        webhook(event, signed(event, clock.instant().minusSeconds(3600))).andExpect(status().isBadRequest());
        assertThat(statusOf(jo, due)).isEqualTo("DUE");

        // Stripe's signed confirmation marks it paid, once.
        webhook(event, signed(event, clock.instant())).andExpect(status().isOk());
        webhook(event, signed(event, clock.instant())).andExpect(status().isOk());
        String mine = fetch(jo, "/api/payments/mine");
        assertThat((List<String>) JsonPath.read(mine, "$.payments[?(@.id == " + due + ")].status")).containsExactly("PAID");
        assertThat((List<Boolean>) JsonPath.read(mine, "$.payments[?(@.id == " + due + ")].paidOnline"))
                .containsExactly(true);
        call(jo, "/api/payments/" + due + "/checkout")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("not due")));
    }

    @Test
    void aHeadPaysTheirSchoolsInvoice() throws Exception {
        when(stripe.createCheckout(any(), anyInt(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new StripeClient.Checkout("cs_test_2", "https://checkout.stripe.com/c/pay/cs_test_2"));
        String head = login("head@oakfield.test");
        Integer oakfield = JsonPath.read(fetch(head, "/api/auth/me"), "$.schoolId");
        String invoice = mvc.perform(post("/api/admin/payments").header("Authorization", bearer(login("admin@motormovers.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolId\":" + oakfield + ",\"purpose\":\"OTHER\",\"description\":\"Session cards\","
                                + "\"amountPence\":2400,\"invoicedOn\":\"2026-10-01\",\"paidOn\":null,\"reference\":\"MM-2001\"}"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(invoice, "$.id");
        call(head, "/api/payments/" + id + "/checkout").andExpect(status().isOk());
        verify(stripe).createCheckout(eq(id.longValue()), eq(2400), eq("Session cards"), eq("head@oakfield.test"),
                eq("http://localhost:5173/school/payments?paid=1"), anyString());
        // A teacher at the school cannot.
        call(login("teacher@oakfield.test"), "/api/payments/" + id + "/checkout").andExpect(status().isForbidden());
    }

    @Test
    void stripeIsAskedForACheckoutPageInPounds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer api = MockRestServiceServer.bindTo(builder).build();
        api.expect(requestTo("https://api.stripe.com/v1/checkout/sessions"))
                .andExpect(header("Authorization", "Bearer sk_test_x"))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "mode", "payment",
                        "line_items[0][price_data][currency]", "gbp",
                        "line_items[0][price_data][unit_amount]", "4500",
                        "metadata[paymentId]", "7",
                        "customer_email", "jo@example.test")))
                .andRespond(withSuccess("{\"id\":\"cs_1\",\"url\":\"https://checkout.stripe.com/c/pay/cs_1\"}",
                        MediaType.APPLICATION_JSON));
        StripeClient.Checkout checkout = new StripeApiClient(builder, "sk_test_x").createCheckout(7L, 4500,
                "Parent course", "jo@example.test", "https://app/payments?paid=1", "https://app/payments");
        assertThat(checkout.url()).isEqualTo("https://checkout.stripe.com/c/pay/cs_1");
        api.verify();
    }

    private static String paidEvent(Integer paymentId) {
        return "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_1\","
                + "\"payment_status\":\"paid\",\"payment_intent\":\"pi_123\",\"client_reference_id\":\"" + paymentId
                + "\",\"metadata\":{\"paymentId\":\"" + paymentId + "\"}}}}";
    }

    private static String signed(String payload, Instant at) {
        long t = at.getEpochSecond();
        return "t=" + t + ",v1=" + StripeSignature.sign(SECRET, t + "." + payload);
    }

    private ResultActions webhook(String payload, String signature) throws Exception {
        return mvc.perform(post("/api/stripe/webhook").header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private Integer dueOf(String token) throws Exception {
        List<Integer> due = JsonPath.read(fetch(token, "/api/payments/mine"), "$.payments[?(@.status == 'DUE')].id");
        return due.getFirst();
    }

    private String statusOf(String token, Integer id) throws Exception {
        List<String> s = JsonPath.read(fetch(token, "/api/payments/mine"), "$.payments[?(@.id == " + id + ")].status");
        return s.getFirst();
    }

    private String fetch(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions call(String token, String path) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer(token)));
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").with(r -> {
                    r.setRemoteAddr("10.0.0.27");
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
