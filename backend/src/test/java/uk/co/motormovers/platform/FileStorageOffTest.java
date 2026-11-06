package uk.co.motormovers.platform;

import static org.hamcrest.Matchers.containsString;
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

/** With no store configured, nothing can be uploaded and the team pastes links as before. */
@SpringBootTest(properties = "platform.storage.local-dir=")
@AutoConfigureMockMvc
class FileStorageOffTest {

    @Autowired
    MockMvc mvc;

    @Test
    void uploadsAreOffAndPastedLinksStillWork() throws Exception {
        String admin = bearer();
        mvc.perform(get("/api/admin/files").header("Authorization", admin))
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(post("/api/admin/files").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"audio/mpeg\",\"size\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Paste a link")));
        mvc.perform(post("/api/admin/resources").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"RHYME\",\"title\":\"A rhyme\",\"description\":\"A recording\","
                                + "\"fileUrl\":\"https://example.org/rhyme.mp3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileLink").value("https://example.org/rhyme.mp3"));
    }

    @Test
    void theLocalPathsAreRefused() throws Exception {
        mvc.perform(get("/api/files/local/uploads/11111111-1111-1111-1111-111111111111.mp3")
                        .param("expires", "99999999999").param("sig", "00"))
                .andExpect(status().isForbidden());
    }

    private String bearer() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@motormovers.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }
}
