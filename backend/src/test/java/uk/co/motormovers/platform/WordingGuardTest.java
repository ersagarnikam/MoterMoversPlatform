package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import uk.co.motormovers.platform.content.WordingGuard;

class WordingGuardTest {

    private final WordingGuard guard = new WordingGuard();

    @Test
    void flagsDiagnosticWords() {
        assertThat(guard.check(List.of("Helps children with ADHD", "Signs of Dyspraxia")))
                .extracting(WordingGuard.Violation::term)
                .containsExactly("adhd", "dyspraxia");
        assertThat(guard.check(List.of("Scored in the 10th percentile"))).isNotEmpty();
    }

    @Test
    void allowsProgrammeTerms() {
        assertThat(guard.check(List.of("Balance and cross-lateral movement", "Developing", "Secure",
                "Add a slow spin to the warm up"))).isEmpty();
    }

    @Test
    void matchesWholeWordsOnly() {
        assertThat(guard.check(List.of("Paddle like a duck"))).isEmpty();
    }

    @Test
    void requireCleanRejects() {
        assertThatThrownBy(() -> guard.requireClean(List.of("A diagnosis of autism")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
