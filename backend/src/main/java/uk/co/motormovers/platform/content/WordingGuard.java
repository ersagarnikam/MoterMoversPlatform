package uk.co.motormovers.platform.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * The platform reports readiness against programme stages, never a diagnosis. Every screen, report
 * and email passes through this check before release, so no diagnostic word reaches a teacher or
 * parent by accident. The term list is a starting point for Pat and Laura to extend.
 */
@Component
public class WordingGuard {

    private static final List<String> BLOCKED_TERMS = List.of(
            "adhd", "autism", "autistic", "asd", "dyspraxia", "dyspraxic", "dcd",
            "developmental coordination disorder", "dyslexia", "dyslexic", "disorder", "diagnosis",
            "diagnose", "diagnosed", "diagnostic", "condition", "syndrome", "symptom", "deficit",
            "impairment", "disability", "delay", "delayed", "abnormal", "at risk", "centile",
            "percentile", "treatment", "therapy", "cure", "clinical");

    private final List<Pattern> patterns = BLOCKED_TERMS.stream()
            .map(t -> Pattern.compile("\\b" + Pattern.quote(t) + "\\b"))
            .toList();

    public record Violation(String text, String term) {
    }

    public List<Violation> check(List<String> texts) {
        List<Violation> violations = new ArrayList<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            for (int i = 0; i < patterns.size(); i++) {
                if (patterns.get(i).matcher(lower).find()) {
                    violations.add(new Violation(text, BLOCKED_TERMS.get(i)));
                }
            }
        }
        return violations;
    }

    public void requireClean(List<String> texts) {
        List<Violation> violations = check(texts);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Wording check failed: \"" + violations.getFirst().term()
                    + "\" is not allowed in programme wording");
        }
    }
}
