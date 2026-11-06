package uk.co.motormovers.platform.common.config;

/** A country setting that cannot work. It stops start-up, with one clear message (CountrySettingsFailureAnalyzer). */
public class CountrySettingsException extends IllegalStateException {

    public CountrySettingsException(String message) {
        super(message);
    }
}
