package uk.co.motormovers.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** With HOME_PROGRAMME off (the production default), the app is told so and nothing can be added. */
@SpringBootTest(properties = "platform.home-programme=false")
@AutoConfigureMockMvc
class HomeProgrammeOffTest {

    @Autowired
    MockMvc mvc;

    @Test
    void switchedOffItSaysSoAndRefusesChanges() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"home@example.test\",\"password\":\"password\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + JsonPath.read(body, "$.token");
        mvc.perform(get("/api/home").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.children.length()").value(0));
        mvc.perform(post("/api/home/children").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sam\",\"ageMonths\":40}"))
                .andExpect(status().isForbidden());
    }
}
