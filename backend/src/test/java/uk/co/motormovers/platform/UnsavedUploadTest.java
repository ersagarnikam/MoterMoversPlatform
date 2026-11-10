package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.common.storage.FileStorage;
import uk.co.motormovers.platform.common.storage.StorageProperties;
import uk.co.motormovers.platform.common.storage.UnsavedUploadJob;
import uk.co.motormovers.platform.common.storage.UnsavedUploadRepository;

/** Files uploaded for a form that was never saved go after the grace period; nothing else ever does. */
@SpringBootTest(properties = "platform.login-limit-per-ip=500")
@AutoConfigureMockMvc
class UnsavedUploadTest {

    private static final byte[] FILE = "not really a recording".getBytes(StandardCharsets.UTF_8);

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    FileStorage storage;

    @Autowired
    StorageProperties properties;

    @Autowired
    UnsavedUploadRepository unsaved;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void forgetOtherTestsUploads() {
        unsaved.deleteAll();
    }

    @Test
    void anUnusedFilePastTheGracePeriodIsDeleted() throws Exception {
        String key = uploaded();
        assertThat(unsaved.existsById(key)).isTrue();

        assertThat(jobAfter(8, storage).cleanUp()).isEqualTo(1);

        assertThat(storage.exists(key)).isFalse();
        assertThat(unsaved.existsById(key)).isFalse();
    }

    @Test
    void aFileNeverSentStillClearsItsRow() throws Exception {
        String key = started();

        assertThat(jobAfter(8, storage).cleanUp()).isEqualTo(1);

        assertThat(unsaved.existsById(key)).isFalse();
    }

    @Test
    void aFileInsideTheGracePeriodIsKept() throws Exception {
        String key = uploaded();

        assertThat(jobAfter(6, storage).cleanUp()).isZero();

        assertThat(storage.exists(key)).isTrue();
        assertThat(unsaved.existsById(key)).isTrue();
    }

    @Test
    void aFileOnAnyOfTheFourColumnsIsKeptAndItsRowCleared() throws Exception {
        for (String[] column : new String[][] {{"programme_session", "video_key"}, {"home_activity", "video_key"},
                {"induction_lesson", "video_key"}, {"library_resource", "file_key"}}) {
            String key = uploaded();
            String table = column[0];
            String name = column[1];
            Long id = jdbc.queryForObject("SELECT MIN(id) FROM " + table, Long.class);
            jdbc.update("UPDATE " + table + " SET " + name + " = ? WHERE id = ?", key, id);
            try {
                assertThat(jobAfter(8, storage).cleanUp()).as(table).isZero();
                assertThat(storage.exists(key)).as(table).isTrue();
                assertThat(unsaved.existsById(key)).as(table).isFalse();
            } finally {
                jdbc.update("UPDATE " + table + " SET " + name + " = NULL WHERE id = ?", id);
            }
        }
    }

    @Test
    void aFileItDidNotHandOutIsLeftAlone() throws Exception {
        String key = uploaded();
        unsaved.deleteById(key);

        assertThat(jobAfter(8, storage).cleanUp()).isZero();

        assertThat(storage.exists(key)).isTrue();
    }

    @Test
    void aFailedDeleteKeepsTheRowForTheNextDay() throws Exception {
        String key = uploaded();
        FileStorage failing = mock(FileStorage.class);
        when(failing.available()).thenReturn(true);
        doThrow(new IllegalStateException("store down")).when(failing).delete(key);

        assertThat(jobAfter(8, failing).cleanUp()).isZero();
        assertThat(unsaved.existsById(key)).isTrue();

        assertThat(jobAfter(9, storage).cleanUp()).isEqualTo(1);
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    void withNoStoreItDoesNothing() throws Exception {
        String key = uploaded();
        FileStorage off = mock(FileStorage.class);
        when(off.available()).thenReturn(false);

        assertThat(jobAfter(8, off).cleanUp()).isZero();

        verify(off, never()).delete(anyString());
        assertThat(unsaved.existsById(key)).isTrue();
    }

    @Test
    void savingAnItemClearsTheRow() throws Exception {
        String key = uploaded();

        save(key).andExpect(status().isOk());

        assertThat(unsaved.existsById(key)).isFalse();
        assertThat(jobAfter(8, storage).cleanUp()).isZero();
        assertThat(storage.exists(key)).isTrue();
    }

    @Test
    void aSaveThatFailsKeepsTheRow() throws Exception {
        String key = started();

        save(key).andExpect(status().isBadRequest());

        assertThat(unsaved.existsById(key)).isTrue();
    }

    @Test
    void aFormLeftOpenPastTheCleanUpIsToldToUploadAgain() throws Exception {
        String key = uploaded();
        jobAfter(8, storage).cleanUp();

        save(key).andExpect(status().isBadRequest());
    }

    private UnsavedUploadJob jobAfter(int days, FileStorage store) {
        return new UnsavedUploadJob(unsaved, store, properties, transactions,
                Clock.fixed(clock.instant().plus(Duration.ofDays(days)), ZoneOffset.UTC));
    }

    private ResultActions save(String key) throws Exception {
        return mvc.perform(post("/api/admin/resources").header("Authorization", "Bearer " + admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"PRINTABLE\",\"title\":\"Unsaved test\","
                        + "\"description\":\"A printable\",\"fileKey\":\"" + key + "\"}"));
    }

    private String started() throws Exception {
        return JsonPath.read(start(), "$.key");
    }

    /** Uploads a file the way the browser does, and returns its key. */
    private String uploaded() throws Exception {
        String body = start();
        mvc.perform(put(URI.create(JsonPath.read(body, "$.url"))).contentType("audio/mpeg").content(FILE))
                .andExpect(status().isOk());
        return JsonPath.read(body, "$.key");
    }

    private String start() throws Exception {
        return mvc.perform(post("/api/admin/files").header("Authorization", "Bearer " + admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"audio/mpeg\",\"size\":" + FILE.length + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String admin() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@motormovers.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
