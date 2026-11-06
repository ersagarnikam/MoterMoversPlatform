package uk.co.motormovers.platform.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

/** Errors are logged by type and place, because their messages can hold anything. */
@ExtendWith(OutputCaptureExtension.class)
class SafeErrorsTest {

    private static final String EMAIL = "someone@example.test";

    @Test
    void namesEachCauseAndWhereItHappenedWithoutTheMessages() {
        Exception error = new IllegalStateException("for " + EMAIL, new java.io.IOException("token=abc123"));

        assertThat(SafeErrors.describe(error))
                .contains("java.lang.IllegalStateException at uk.co.motormovers.platform.common.exception.SafeErrorsTest")
                .contains(" <- java.io.IOException")
                .doesNotContain(EMAIL, "abc123");
    }

    @Test
    void stopsAtACauseThatPointsToItself() {
        Exception a = new RuntimeException("a");
        Exception b = new RuntimeException("b", a);
        a.initCause(b);

        assertThat(SafeErrors.describe(a).split(" <- ")).hasSize(6);
    }

    @Test
    void anUnexpectedErrorLogsTheRouteAndNotTheValues(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/children/42");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/children/{id}");

        new ApiExceptionHandler().unexpected(new RuntimeException("Key (email)=(" + EMAIL + ")"), request);

        assertThat(output.getAll()).contains("Unexpected error on GET /api/children/{id}", "RuntimeException")
                .doesNotContain(EMAIL);
    }

    @Test
    void aRejectedWriteWithoutANamedRuleStillSaysNothingOfItsValues(CapturedOutput output) {
        new ApiExceptionHandler().conflict(new DataIntegrityViolationException("Key (email)=(" + EMAIL + ") exists"));

        assertThat(output.getAll()).contains("Rejected write").doesNotContain(EMAIL);
    }
}
