package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The log shipper's redaction, applied as Alloy's stage.replace does: each pattern's capture group is replaced. The
 * patterns are read from deploy/config.alloy, so this tests the file that runs.
 */
class ShipperRedactionTest {

    private static final Pattern STAGE = Pattern.compile("expression = \"(.*)\"\\R\\s*replace\\s*= \"(.*)\"");

    @Test
    void removesTokensKeysAndCookies() throws IOException {
        for (String line : List.of(
                "?access_token=SECRETVALUE&x=1",
                "?client_secret=SECRETVALUE&x=1",
                "{\"access_token\":\"SECRETVALUE\"}",
                "{\"refresh_token\":\"SECRETVALUE\"}",
                "{\"client_secret\":\"SECRETVALUE\",\"ok\":1}",
                "{\"message\":\"body {\\\"client_secret\\\":\\\"SECRETVALUE\\\"}\"}",
                "Authorization: Basic SECRETVALUE",
                "{\"Authorization\":[\"Basic SECRETVALUE\"]}",
                "sent Basic U0VDUkVUVkFMVUVTRUNSRVRWQUxVRQ== to the proxy",
                "Cookie: session=SECRETVALUE; other=SECRETVALUE",
                "Set-Cookie: session=SECRETVALUE; Path=/",
                "{\"Cookie\":[\"session=SECRETVALUE\"]}",
                "{\"cookie\":\"session=SECRETVALUE\"}",
                "{\"Cookie\":[\"a=1\",\"b=SECRETVALUE\",\"c=SECRETVALUE\"]}",
                "{\"Cookie\": [ \"a=SECRETVALUE]\", \"b=SECRETVALUE\" ]}",
                "{\"message\":\"body {\\\"Cookie\\\":[\\\"a=1\\\",\\\"b=SECRETVALUE\\\"]}\"}",
                "{\"Cookie\":[\"a=1\",\"b=SECRETVALUE",
                "{\"password\":\"ab\\\"SECRETVALUE\"}",
                "{\"password\":\"SECRET\\\\\\\"VALUE\\\"SECRETVALUE\",\"ok\":1}",
                "{\"message\":\"body {\\\"password\\\":\\\"ab\\\\\\\"SECRETVALUE\\\",\\\"ok\\\":1}\"}",
                "sent basic U0VDUkVUVkFMVUVTRUNSRVRWQUxVRQ== to the proxy",
                "access_token: SECRETVALUE",
                "client_secret: SECRETVALUE, other",
                "X-Api-Key: SECRETVALUE",
                "{\"X-Api-Key\":[\"SECRETVALUE\"]}",
                "{\"api_key\":\"SECRETVALUE\"}",
                "?api_key=SECRETVALUE&x=1")) {
            assertThat(redact(line)).as(line).doesNotContain("SECRETVALUE", "U0VDUkVU").contains("[removed]");
        }
    }

    @Test
    void stillRemovesWhatItDidBefore() throws IOException {
        assertThat(redact("mail person@example.test")).isEqualTo("mail [email]");
        assertThat(redact("?token=SECRETVALUE")).isEqualTo("?token=[removed]");
        assertThat(redact("Bearer SECRETVALUE")).isEqualTo("Bearer [removed]");
        assertThat(redact("bare eyJhbGci.eyJzdWIi.abc_DEF end")).isEqualTo("bare [removed] end");
    }

    @Test
    void leavesOrdinaryWordsAlone() throws IOException {
        for (String line : List.of("Basic programme for the Basic plan", "No cookies were used", "tokens: 3",
                "{\"status\":\"ok\",\"name\":\"keep me\"}", "the basic responsibilities of the team",
                "token: expired", "password: must be 10 characters", "api keys: none", "basic plan",
                "{\"cookies\":[\"a\"],\"list\":[\"keep me\"]}")) {
            assertThat(redact(line)).isEqualTo(line);
        }
    }

    @Test
    void keepsWhatFollowsARemovedValue() throws IOException {
        assertThat(redact("{\"Cookie\":[\"a=1\",\"b=2\"],\"name\":\"keep me\"}"))
                .isEqualTo("{\"Cookie\":[\"[removed]\"],\"name\":\"keep me\"}");
        assertThat(redact("{\"password\":\"ab\\\"cd\",\"name\":\"keep me\"}"))
                .isEqualTo("{\"password\":\"[removed]\",\"name\":\"keep me\"}");
        assertThat(redact("{\"message\":\"{\\\"password\\\":\\\"ab\\\",\\\"name\\\":\\\"keep me\\\"}\"}"))
                .isEqualTo("{\"message\":\"{\\\"password\\\":\\\"[removed]\\\",\\\"name\\\":\\\"keep me\\\"}\"}");
    }

    private static String redact(String line) throws IOException {
        String result = line;
        for (Stage stage : stages()) {
            Matcher m = stage.pattern().matcher(result);
            StringBuilder out = new StringBuilder();
            int from = 0;
            while (m.find()) {
                out.append(result, from, m.start(1)).append(stage.replace());
                from = m.end(1);
            }
            result = out.append(result.substring(from)).toString();
        }
        return result;
    }

    private record Stage(Pattern pattern, String replace) {
    }

    private static List<Stage> stages() throws IOException {
        String config = Files.readString(Path.of("../deploy/config.alloy"));
        List<Stage> stages = new ArrayList<>();
        Matcher m = STAGE.matcher(config);
        while (m.find()) {
            String expression = m.group(1).replace("\\\\", "\u0000").replace("\\\"", "\"").replace("\u0000", "\\");
            stages.add(new Stage(Pattern.compile(expression), m.group(2)));
        }
        assertThat(stages).hasSizeGreaterThan(4);
        return stages;
    }
}
