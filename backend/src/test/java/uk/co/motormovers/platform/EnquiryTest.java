package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import uk.co.motormovers.platform.common.config.Mailer;

/**
 * The public website's price chart and enquiry form: both open without signing in. The form emails the team and turns
 * away bots and floods.
 */
@SpringBootTest(properties = "platform.enquiry-email=team@motormovers.test")
@AutoConfigureMockMvc
class EnquiryTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    Mailer mailer;

    private ResultActions send(String json, String address) throws Exception {
        return mvc.perform(post("/api/enquiries").contentType(MediaType.APPLICATION_JSON).content(json)
                .with(r -> {
                    r.setRemoteAddr(address);
                    return r;
                }));
    }

    @Test
    void thePriceChartListsThePlansOnSaleAtTodaysPrice() throws Exception {
        // The free trial and the paid plans, in the team's order; the "Free" plan is the team's to give, so not shown.
        mvc.perform(get("/api/plans")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(contains("FREE_TRIAL", "EARLY_YEARS",
                        "PRIMARY_CORE", "FULL_PROGRAMME", "TRUST")))
                .andExpect(jsonPath("$[2].label").value("Primary core"))
                .andExpect(jsonPath("$[2].annualPricePence").value(90000))
                .andExpect(jsonPath("$[0].freeTrial").value(true));
    }

    @Test
    void aPriceGuideRequestIsEmailedToTheTeam() throws Exception {
        send("""
                {"kind":"PRICE_GUIDE","name":"Sam Lee","email":"sam@school.test","setting":"Oak Primary",
                 "phone":"01234 567890","classes":"4","stages":"Green, Yellow","plan":"Primary core","message":"Two forms of entry.\\nThanks"}
                """, "10.0.0.1").andExpect(status().isAccepted());
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eq("team@motormovers.test"), subject.capture(), body.capture());
        assertThat(subject.getValue()).isEqualTo("Motor Movers price guide: Oak Primary");
        assertThat(body.getValue()).contains("Name: Sam Lee", "Email: sam@school.test", "Number of classes: 4",
                "Stages they are interested in: Green, Yellow", "Plan they are looking at: Primary core", "Two forms of entry.\nThanks");
    }

    @Test
    void missingDetailsAndBotsAreNotSent() throws Exception {
        send("{\"kind\":\"APPLY\",\"name\":\"Sam\",\"email\":\"not-an-email\",\"setting\":\"Oak\"}", "10.0.0.2")
                .andExpect(status().isBadRequest());
        send("{\"kind\":\"APPLY\",\"name\":\"\",\"email\":\"sam@school.test\",\"setting\":\"Oak\"}", "10.0.0.2")
                .andExpect(status().isBadRequest());
        // The hidden field filled in: accepted, so the bot learns nothing, but not sent.
        send("""
                {"kind":"APPLY","name":"Sam","email":"sam@school.test","setting":"Oak","website":"http://spam.test"}
                """, "10.0.0.2").andExpect(status().isAccepted());
        verify(mailer, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void fiveAnHourFromOneAddress() throws Exception {
        String json = "{\"kind\":\"APPLY\",\"name\":\"Sam\",\"email\":\"sam@school.test\",\"setting\":\"Oak\"}";
        for (int i = 0; i < 5; i++) {
            send(json, "10.0.0.3").andExpect(status().isAccepted());
        }
        send(json, "10.0.0.3").andExpect(status().isTooManyRequests());
        clearInvocations(mailer);
        send(json, "10.0.0.4").andExpect(status().isAccepted());
        verify(mailer).send(eq("team@motormovers.test"), eq("Apply for Motor Movers: Oak"), anyString());
    }
}
