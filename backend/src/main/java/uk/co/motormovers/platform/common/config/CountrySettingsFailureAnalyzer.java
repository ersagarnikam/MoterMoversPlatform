package uk.co.motormovers.platform.common.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Whoever deploys sees a bad country setting in one line, instead of inside a long stack trace. */
public class CountrySettingsFailureAnalyzer extends AbstractFailureAnalyzer<CountrySettingsException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, CountrySettingsException cause) {
        return new FailureAnalysis("Invalid country setting: " + cause.getMessage(),
                "Correct the value in deploy/country.env, or in application.yml (RUNBOOK, Country settings).", cause);
    }
}
