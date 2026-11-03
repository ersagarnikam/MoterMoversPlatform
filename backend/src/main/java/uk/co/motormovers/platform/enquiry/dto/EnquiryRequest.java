package uk.co.motormovers.platform.enquiry.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An enquiry from the public website: a school asking for the price guide or to apply. {@code website} is a
 * field people never see; anything in it means a bot filled the form in.
 */
public record EnquiryRequest(
        @NotNull Kind kind,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Size(max = 200) String setting,
        @Size(max = 40) String phone,
        @Size(max = 40) String classes,
        @Size(max = 200) String stages,
        @Size(max = 100) String plan,
        @Size(max = 2000) String message,
        @Size(max = 200) String website) {

    public enum Kind {
        PRICE_GUIDE("Motor Movers price guide"),
        APPLY("Apply for Motor Movers");

        private final String subject;

        Kind(String subject) {
            this.subject = subject;
        }

        public String subject() {
            return subject;
        }
    }
}
