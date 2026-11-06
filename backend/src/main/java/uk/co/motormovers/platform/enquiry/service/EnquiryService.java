package uk.co.motormovers.platform.enquiry.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import uk.co.motormovers.platform.common.config.Mailer;
import uk.co.motormovers.platform.common.exception.EnquiryNotSentException;
import uk.co.motormovers.platform.enquiry.dto.EnquiryRequest;

/**
 * Passes website enquiries to the Motor Movers team by email ({@code platform.enquiry-email}). Nothing is stored,
 * and the sender is not emailed, so the form cannot be used to send mail to someone else.
 */
@Service
public class EnquiryService {

    private static final Logger log = LoggerFactory.getLogger(EnquiryService.class);

    private final Mailer mailer;
    private final String to;

    public EnquiryService(Mailer mailer, @Value("${platform.enquiry-email}") String to) {
        this.mailer = mailer;
        this.to = to;
    }

    public void send(EnquiryRequest in) {
        if (in.website() != null && !in.website().isBlank()) {
            log.info("Website enquiry dropped: the hidden field was filled in");
            return;
        }
        String body = """
                A new enquiry from the Motor Movers website.

                Name: %s
                Email: %s
                School or setting: %s
                Phone: %s
                Number of classes: %s
                Stages they are interested in: %s
                Plan they are looking at: %s

                Message:
                %s

                Reply to %s.
                """.formatted(line(in.name()), line(in.email()), line(in.setting()), line(in.phone()),
                line(in.classes()), line(in.stages()), line(in.plan()), text(in.message()), line(in.email()));
        if (!mailer.send(to, in.kind().subject() + ": " + line(in.setting()), body)) {
            // Enquiries are not stored, so this email was the only copy: the visitor must know it did not go.
            throw new EnquiryNotSentException();
        }
    }

    /** One line of what was typed, or a dash; line breaks are taken out so a value cannot add header-like lines. */
    private static String line(String value) {
        return value == null || value.isBlank() ? "-" : value.strip().replaceAll("[\\r\\n]+", " ");
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "-" : value.strip();
    }
}
