package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.notification.repository.PushTokenRepository;
import uk.co.motormovers.platform.notification.service.ExpoPushSender;
import uk.co.motormovers.platform.notification.service.PushSender;
import uk.co.motormovers.platform.training.service.SessionReminderJob;

/** Phone notifications to the parent app: registering a phone, and what is sent when. Expo itself is stubbed. */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:pushtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
class PushTest {

    private static final String JO_PHONE = "ExponentPushToken[jo-phone]";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PushSender sender;

    @Autowired
    PushTokenRepository tokens;

    @Autowired
    SessionReminderJob reminders;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void aPhoneHearsWhenItsSessionOpensAndWhenAPaymentIsDue() throws Exception {
        when(sender.send(anyList())).thenReturn(List.of());
        String jo = login("parent@example.test");
        send(jo, "/api/me/push-tokens", "{\"token\":\"not-a-token\",\"platform\":\"ios\"}")
                .andExpect(status().isBadRequest());
        send(jo, "/api/me/push-tokens", "{\"token\":\"" + JO_PHONE + "\",\"platform\":\"ios\"}")
                .andExpect(status().isNoContent());

        // Jo's demo session starts ten minutes after start-up: it is open, so her phone hears, once. (The job also
        // runs on its own schedule, which may have announced it before her phone was registered.)
        jdbc.update("UPDATE cohort_session SET opening_push_sent_at = NULL");
        assertThat(reminders.announceOpening()).isPositive();
        assertThat(sent()).filteredOn(m -> m.token().equals(JO_PHONE)).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("Your live session is open");
            assertThat(m.body()).contains("Motor Movers parent course, session 1", "Tap to join");
            assertThat(m.data()).containsEntry("url", "/course");
        });
        clearInvocations(sender);
        assertThat(reminders.announceOpening()).isZero();

        // The team records a payment Jo owes.
        send(login("admin@motormovers.test"), "/api/admin/payments", "{\"payerEmail\":\"parent@example.test\","
                + "\"purpose\":\"COURSE\",\"description\":\"Parent course\",\"amountPence\":4500,"
                + "\"invoicedOn\":\"2026-10-01\",\"paidOn\":null,\"reference\":null}")
                .andExpect(status().isOk());
        assertThat(sent()).filteredOn(m -> m.token().equals(JO_PHONE)).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("A payment is due");
            assertThat(m.body()).isEqualTo("£45 for Parent course. Tap to see how to pay.");
            assertThat(m.data()).containsEntry("url", "/payments");
        });

        // Signing out of the app: the phone hears nothing more.
        send(jo, "/api/me/push-tokens/remove", "{\"token\":\"" + JO_PHONE + "\"}").andExpect(status().isNoContent());
        assertThat(tokens.findByToken(JO_PHONE)).isEmpty();
    }

    @Test
    void aPhoneWithTheAppRemovedIsForgotten() throws Exception {
        String gone = "ExponentPushToken[uninstalled]";
        send(login("parent@example.test"), "/api/me/push-tokens", "{\"token\":\"" + gone + "\",\"platform\":\"android\"}");
        when(sender.send(anyList())).thenReturn(List.of(gone));
        send(login("admin@motormovers.test"), "/api/admin/payments", "{\"payerEmail\":\"parent@example.test\","
                + "\"purpose\":\"OTHER\",\"description\":\"Printed cards\",\"amountPence\":1250,"
                + "\"invoicedOn\":\"2026-10-01\",\"paidOn\":null,\"reference\":null}");
        assertThat(tokens.findByToken(gone)).isEmpty();
    }

    @Test
    void aPaymentAlreadyPaidSendsNothing() throws Exception {
        send(login("parent@example.test"), "/api/me/push-tokens", "{\"token\":\"ExponentPushToken[paid]\"}");
        clearInvocations(sender);
        send(login("admin@motormovers.test"), "/api/admin/payments", "{\"payerEmail\":\"parent@example.test\","
                + "\"purpose\":\"OTHER\",\"description\":\"Paid at once\",\"amountPence\":500,"
                + "\"invoicedOn\":\"2026-10-01\",\"paidOn\":\"2026-10-01\",\"reference\":null}")
                .andExpect(status().isOk());
        verify(sender, never()).send(anyList());
    }

    @Test
    void expoGetsTheMessagesAndSaysWhichPhonesAreGone() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer expo = MockRestServiceServer.bindTo(builder).build();
        expo.expect(requestTo("https://exp.host/--/api/v2/push/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer expo-key"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"to\":\"ExponentPushToken[a]\""),
                        org.hamcrest.Matchers.containsString("\"data\":{\"url\":\"/course\"}"))))
                .andRespond(withSuccess("{\"data\":[{\"status\":\"ok\",\"id\":\"1\"},{\"status\":\"error\","
                        + "\"message\":\"gone\",\"details\":{\"error\":\"DeviceNotRegistered\"}}]}",
                        MediaType.APPLICATION_JSON));
        List<String> gone = new ExpoPushSender(builder, "expo-key").send(List.of(
                new PushSender.Message("ExponentPushToken[a]", "T", "B", Map.of("url", "/course")),
                new PushSender.Message("ExponentPushToken[b]", "T", "B", Map.of("url", "/course"))));
        assertThat(gone).containsExactly("ExponentPushToken[b]");
        expo.verify();
    }

    @SuppressWarnings("unchecked")
    private List<PushSender.Message> sent() {
        ArgumentCaptor<List<PushSender.Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(sender, org.mockito.Mockito.atLeastOnce()).send(messages.capture());
        return messages.getValue();
    }

    private org.springframework.test.web.servlet.ResultActions send(String token, String path, String json)
            throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").with(r -> {
                    r.setRemoteAddr("10.0.0.26");
                    return r;
                }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
