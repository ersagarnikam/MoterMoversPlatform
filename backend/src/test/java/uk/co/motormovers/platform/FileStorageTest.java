package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
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

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.common.model.Region;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.HomeActivity;
import uk.co.motormovers.platform.content.model.ProgrammeSession;
import uk.co.motormovers.platform.content.repository.HomeActivityRepository;
import uk.co.motormovers.platform.content.repository.ProgrammeSessionRepository;
import uk.co.motormovers.platform.school.model.Licence;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.SchoolClass;
import uk.co.motormovers.platform.school.repository.LicenceRepository;
import uk.co.motormovers.platform.school.repository.SchoolClassRepository;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.school.service.PlanService;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * The team uploads through the local store, which behaves as the bucket does. Everyone else gets a signed link only
 * after the access checks; pasted links stay as they were.
 */
@SpringBootTest(properties = "platform.login-limit-per-ip=500")
@AutoConfigureMockMvc
class FileStorageTest {

    private static final byte[] FILE = "not really a recording".getBytes(StandardCharsets.UTF_8);
    private static final String LOCAL = "/api/files/local/uploads/";

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    SchoolRepository schools;

    @Autowired
    SchoolClassRepository classes;

    @Autowired
    LicenceRepository licences;

    @Autowired
    PlanService plans;

    @Autowired
    ProgrammeSessionRepository sessions;

    @Autowired
    HomeActivityRepository activities;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder encoder;

    @Test
    void onlyTheTeamCanUpload() throws Exception {
        String teacher = login("teacher@oakfield.test");
        mvc.perform(get("/api/admin/files").header("Authorization", bearer(teacher))).andExpect(status().isForbidden());
        start(teacher, "audio/mpeg", 100).andExpectForbidden();
        mvc.perform(post("/api/admin/files").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"audio/mpeg\",\"size\":100}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/files").header("Authorization", bearer(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.maxMegabytes").value(2048));
    }

    @Test
    void onlyAllowedTypesAndSizesAreAccepted() throws Exception {
        String admin = admin();
        for (String type : List.of("text/html", "image/svg+xml", "application/zip")) {
            start(admin, type, 100).result().andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("MP4 or WebM video")));
        }
        start(admin, "video/quicktime", 100).result().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("A MOV film needs saving as MP4")));
        start(admin, "", 100).result().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(startsWith("We could not tell what kind of file")));
        start(admin, "audio/mpeg", 0).result().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("That file is empty. Please choose another."));
        start(admin, "video/mp4", 2049L * 1024 * 1024).result().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("too large")));
    }

    @Test
    void anUploadedFileIsSavedAndReadBackWithRanges() throws Exception {
        String[] upload = upload();
        String link = fileLink(upload[0]);

        mvc.perform(get(link)).andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().bytes(FILE));
        // A video element seeks with ranges.
        mvc.perform(get(link).header("Range", "bytes=4-9")).andExpect(status().isPartialContent())
                .andExpect(content().bytes("really".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aLinkOnlyWorksAsSigned() throws Exception {
        String[] upload = upload();
        String link = fileLink(upload[0]);
        mvc.perform(get(link.replaceAll("sig=[0-9a-f]{4}", "sig=0000"))).andExpect(status().isForbidden());
        mvc.perform(get(link.replaceAll("expires=\\d+", "expires=99999999999"))).andExpect(status().isForbidden());
        // A read link cannot be used to write, and an upload link takes only the agreed type and size.
        String agreed = JsonPath.read(start(admin(), "audio/mpeg", FILE.length).body(), "$.url");
        mvc.perform(put(URI.create(agreed)).contentType("video/mp4").content(FILE)).andExpect(status().isForbidden());
        mvc.perform(put(URI.create(agreed)).contentType("audio/mpeg").content(new byte[3])).andExpect(status().isForbidden());
        mvc.perform(put(URI.create(link.replace("sig=", "type=audio%2Fmpeg&size=22&sig="))).contentType("audio/mpeg").content(FILE))
                .andExpect(status().isForbidden());
    }

    @Test
    void aKeyMustBeOneTheApiGaveOut() throws Exception {
        mvc.perform(post("/api/admin/resources").header("Authorization", bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(resource("../../secrets.txt", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aKeyOfTheRightShapeMustBeAFileThatExists() throws Exception {
        String madeUp = "uploads/" + UUID.randomUUID() + ".mp3";
        mvc.perform(post("/api/admin/resources").header("Authorization", bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(resource(madeUp, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Please upload it again")));
    }

    @Test
    void aDownloadSavesUnderTheResourcesTitle() throws Exception {
        String admin = admin();
        String body = "{\"kind\":\"PRINTABLE\",\"title\":\"Rhyme book: Green\",\"description\":\"A printable\","
                + "\"fileKey\":\"" + uploadedKey() + "\"}";
        Integer id = JsonPath.read(mvc.perform(post("/api/admin/resources").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/admin/resources/" + id + "/approve").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        String link = JsonPath.<List<String>>read(library(admin), "$[?(@.id == " + id + ")].fileUrl").getFirst();
        mvc.perform(get(URI.create(link))).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("Rhyme book Green.mp3")));
        // The name is signed with the link.
        mvc.perform(get(URI.create(link.replace("Rhyme%20book", "Other")))).andExpect(status().isForbidden());
    }

    @Test
    void aPastedLinkStaysAsItWas() throws Exception {
        String admin = admin();
        String link = "https://example.org/rhyme-" + UUID.randomUUID() + ".mp3";
        Integer id = JsonPath.read(mvc.perform(post("/api/admin/resources").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(resource(null, link)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileLink").value(link))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/admin/resources/" + id + "/approve").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/resources").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].fileUrl").value(link));
    }

    @Test
    void aSessionsFilmIsSignedOnlyForALicensedSchool() throws Exception {
        ProgrammeSession session = firstSession(ColourStage.RED);
        session.setVideoKey(uploadedKey());
        sessions.save(session);
        try {
            LocalDate today = LocalDate.now(clock);
            String licensed = today(classWith(today.minusMonths(1), today.plusMonths(1)));
            assertThat((String) JsonPath.read(licensed, "$.nextSession.videoUrl")).startsWith(LOCAL);

            String expired = today(classWith(today.minusYears(1), today.minusDays(1)));
            assertThat(expired).doesNotContain(LOCAL);
        } finally {
            session.setVideoKey(null);
            sessions.save(session);
        }
    }

    @Test
    void aFileForAllStagesNeedsALicence() throws Exception {
        String key = uploadedKey();
        String admin = admin();
        Integer id = JsonPath.read(mvc.perform(post("/api/admin/resources").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(resource(key, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileLink").value(startsWith(LOCAL)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String json = "$[?(@.id == " + id + ")].fileUrl";
        // A draft reaches nobody yet.
        assertThat(library(admin)).doesNotContain("\"id\":" + id + ",");
        mvc.perform(post("/api/admin/resources/" + id + "/approve").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        assertThat(JsonPath.<List<String>>read(library(admin), json).getFirst()).startsWith(LOCAL);
        LocalDate today = LocalDate.now(clock);
        String licensedTeacher = teacherAt(classWith(today.minusMonths(1), today.plusMonths(1)));
        assertThat(JsonPath.<List<String>>read(library(licensedTeacher), json).getFirst()).startsWith(LOCAL);

        String unlicensed = library(teacherAt(classWith(today.minusYears(1), today.minusDays(1))));
        assertThat(JsonPath.<List<Object>>read(unlicensed, json).getFirst()).isNull();
        assertThat(unlicensed).doesNotContain(LOCAL);
    }

    @Test
    void aHomeFilmIsSignedOnlyForAFamilyWithAChild() throws Exception {
        HomeActivity activity = activities.findByStageAndStatusOrderByPosition(ColourStage.RED, ContentStatus.APPROVED)
                .getFirst();
        activity.setVideoKey(uploadedKey());
        activities.save(activity);
        String path = "/api/family/activities/" + activity.getId();
        try {
            mvc.perform(get(path).header("Authorization", bearer(login("parent@example.test"))))
                    .andExpect(jsonPath("$.videoUrl").value(startsWith(LOCAL)));
            mvc.perform(get(path).header("Authorization", bearer(login("home@example.test"))))
                    .andExpect(jsonPath("$.videoUrl").value(startsWith(LOCAL)));

            String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"parent-" + UUID.randomUUID()
                                    + "@example.test\",\"password\":\"password123\",\"displayName\":\"Alex Carer\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String stranger = JsonPath.read(body, "$.token");
            mvc.perform(get(path).header("Authorization", bearer(stranger)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.videoUrl").doesNotExist());
            mvc.perform(get("/api/family/home?stage=RED").header("Authorization", bearer(stranger)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString(LOCAL))));
        } finally {
            activity.setVideoKey(null);
            activities.save(activity);
        }
    }

    private ProgrammeSession firstSession(ColourStage stage) {
        return sessions.findByStageAndStatusOrderByUnitAscDayAsc(stage, ContentStatus.APPROVED).getFirst();
    }

    private SchoolClass classWith(LocalDate from, LocalDate to) {
        School school = schools.save(new School("Files " + UUID.randomUUID(), null, Region.UK));
        licences.save(new Licence(school, plans.find("TRUST"), 0, from, to, EnumSet.of(ColourStage.RED)));
        return classes.save(new SchoolClass(school, "Files F", "Year 1", null, ColourStage.RED));
    }

    private String teacherAt(SchoolClass schoolClass) throws Exception {
        String email = "files-" + UUID.randomUUID() + "@example.test";
        users.save(new AppUser(email, "Files Teacher", encoder.encode("password"), Role.TEACHER,
                schoolClass.getSchool().getId(), null));
        return login(email);
    }

    private String today(SchoolClass schoolClass) throws Exception {
        return mvc.perform(get("/api/classes/" + schoolClass.getId() + "/today").header("Authorization", bearer(admin())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String library(String token) throws Exception {
        return mvc.perform(get("/api/resources").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String uploadedKey() throws Exception {
        return upload()[0];
    }

    /** Uploads a file the way the browser does, and returns its key. */
    private String[] upload() throws Exception {
        String body = start(admin(), "audio/mpeg", FILE.length).body();
        String url = JsonPath.read(body, "$.url");
        mvc.perform(put(URI.create(url)).contentType("audio/mpeg").content(FILE)).andExpect(status().isOk());
        return new String[] {JsonPath.read(body, "$.key")};
    }

    /** A signed link for a key, from the draft the team reviews. */
    private String fileLink(String key) throws Exception {
        String body = mvc.perform(post("/api/admin/resources").header("Authorization", bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON).content(resource(key, null)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.fileLink");
    }

    private static String resource(String key, String link) {
        return "{\"kind\":\"RHYME\",\"title\":\"A rhyme\",\"description\":\"A recording\""
                + (key == null ? "" : ",\"fileKey\":\"" + key + "\"")
                + (link == null ? "" : ",\"fileUrl\":\"" + link + "\"") + "}";
    }

    private Started start(String token, String type, long size) throws Exception {
        return new Started(mvc.perform(post("/api/admin/files").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"" + type + "\",\"size\":" + size + "}")));
    }

    private record Started(org.springframework.test.web.servlet.ResultActions result) {

        String body() throws Exception {
            return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        }

        void andExpectForbidden() throws Exception {
            result.andExpect(status().isForbidden());
        }
    }

    private String admin() throws Exception {
        return login("admin@motormovers.test");
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
