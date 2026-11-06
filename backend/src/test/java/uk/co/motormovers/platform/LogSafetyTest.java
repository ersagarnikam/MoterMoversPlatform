package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * No email address, token, password or child's initials reach the log. A mail server is configured, as production
 * requires, so the development fallback that logs whole emails is not in play.
 */
@SpringBootTest(properties = "platform.login-limit-per-ip=500")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class LogSafetyTest {

    private static final String TEACHER = "teacher@oakfield.test";
    private static final String PASSWORD = "password";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JavaMailSender mail;

    @Test
    void signingInLogsNothingPrivate(CapturedOutput output) throws Exception {
        int start = output.length();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEACHER + "\",\"password\":\"wrong-password-here\"}"))
                .andExpect(status().isUnauthorized());
        login(TEACHER);

        assertThat(output.subSequence(start, output.length()).toString()).doesNotContain(TEACHER, "wrong-password-here", "eyJ");
    }

    @Test
    void aFailedResetEmailLogsNeitherTheAddressNorTheToken(CapturedOutput output) throws Exception {
        int start = output.length();
        // Mail errors quote the address, as a real server's refusal does.
        doThrow(new MailSendException("550 mailbox not found: <" + TEACHER + ">")).when(mail)
                .send(any(SimpleMailMessage.class));

        mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + TEACHER + "\"}"))
                .andExpect(status().is2xxSuccessful());

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        String token = sent.getValue().getText().replaceAll("(?s).*token=(\\S+).*", "$1");
        assertThat(token).isNotBlank().doesNotContain(" ");

        assertThat(output.subSequence(start, output.length()).toString()).contains("Could not send an email", "MailSendException")
                .doesNotContain(TEACHER, token, "550 mailbox", "Reset your Motor Movers password");
    }

    @Test
    void aRejectedDuplicateNamesTheRuleAndNotTheValues(CapturedOutput output) throws Exception {
        int start = output.length();
        String admin = login("admin@motormovers.test");
        String title = "Private title " + UUID.randomUUID();
        String session = "{\"stage\":\"VIOLET\",\"unit\":97,\"day\":3,\"title\":\"" + title
                + "\",\"focus\":\"Focus\",\"durationMinutes\":10}";

        send(admin, "/api/admin/content", session).andExpect(status().isOk());
        send(admin, "/api/admin/content", session).andExpect(status().isConflict());

        assertThat(output.subSequence(start, output.length()).toString()).contains("Rejected write")
                .doesNotContain(title, "VIOLET", "Unique index", "SQL Error");
    }

    @Test
    void aChildsInitialsStayOutOfTheLog(CapturedOutput output) throws Exception {
        int start = output.length();
        String head = login("head@oakfield.test");
        String setup = read(head, "/api/auth/me");
        Integer school = JsonPath.read(setup, "$.schoolId");
        Integer classId = JsonPath.read(read(head, "/api/schools/" + school + "/setup"), "$.classes[0].id");

        send(head, "/api/schools/" + school + "/classes/" + classId + "/children",
                "{\"children\":[{\"initials\":\"QZX\",\"fullName\":null}]}").andExpect(status().isOk());

        assertThat(output.subSequence(start, output.length()).toString()).doesNotContain("QZX");
    }

    private ResultActions send(String token, String path, String body)
            throws Exception {
        return mvc.perform(post(path)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String read(String token, String path) throws Exception {
        return mvc.perform(get(path)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
