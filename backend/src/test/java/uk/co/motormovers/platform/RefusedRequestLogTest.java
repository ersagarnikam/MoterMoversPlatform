package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Tomcat refuses a broken request before the API sees it, and must not log the address it was given. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class RefusedRequestLogTest {

    @LocalServerPort
    int port;

    @Test
    void aRefusedRequestsAddressStaysOutOfTheLog(CapturedOutput output) throws IOException {
        int start = output.length();
        try (Socket socket = new Socket("localhost", port)) {
            // A character Tomcat will not accept in an address, after a token and an email.
            socket.getOutputStream().write("GET /reset-password?token=SECRETTOKEN&email=someone@example.test|x HTTP/1.1\r\nHost: localhost\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String reply = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            assertThat(reply).startsWith("HTTP/1.1 400");
        }

        assertThat(output.subSequence(start, output.length()).toString())
                .doesNotContain("SECRETTOKEN", "someone@example.test");
    }
}
