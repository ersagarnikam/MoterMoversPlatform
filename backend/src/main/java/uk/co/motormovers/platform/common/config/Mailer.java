package uk.co.motormovers.platform.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import uk.co.motormovers.platform.common.exception.SafeErrors;

/**
 * Sends plain-text email through the SMTP server in {@code spring.mail.*}. With no server configured
 * (local development) the message is written to the log instead, so links can be copied from there.
 */
@Component
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final PlatformProperties properties;
    private final String legalLine;

    public Mailer(ObjectProvider<JavaMailSender> sender, PlatformProperties properties, CountrySettings country) {
        this.sender = sender;
        this.properties = properties;
        this.legalLine = country.legalEntity().line();
    }

    /** The email's text, with the legal entity at the foot once it is known. */
    private String withFooter(String body) {
        return legalLine == null ? body : body + "\n\n-- \n" + legalLine;
    }

    /** A link into the web app, e.g. link("/reset-password?token=abc"). */
    public String link(String path) {
        String base = properties.mail().webUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }

    /** Sends a message with one attached file, e.g. a certificate PDF. False if it could not be sent. */
    public boolean send(String to, String subject, String body, String fileName, byte[] file, String contentType) {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null) {
            log.info("Email not sent (no mail server configured)\nTo: {}\nSubject: {}\nAttached: {} ({} bytes)\n\n{}",
                    to, subject, fileName, file.length, withFooter(body));
            return true;
        }
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(withFooter(body));
            helper.addAttachment(fileName, new ByteArrayResource(file), contentType);
            mail.send(message);
            return true;
        } catch (MailException | MessagingException e) {
            // The caller's change stands; the file can be downloaded from the platform instead.
            logFailure(e);
            return false;
        }
    }

    /** False if it could not be sent. Most callers go on regardless: the person can ask for the email again. */
    public boolean send(String to, String subject, String body) {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null) {
            log.info("Email not sent (no mail server configured)\nTo: {}\nSubject: {}\n\n{}", to, subject,
                    withFooter(body));
            return true;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(withFooter(body));
        try {
            mail.send(message);
            return true;
        } catch (MailException e) {
            // The caller's change stands; the person can ask for the email again.
            logFailure(e);
            return false;
        }
    }

    /** Not the address, nor the subject, which can name a person; a mail error can quote the address too. */
    private static void logFailure(Exception e) {
        log.error("Could not send an email: {} (SMTP code {})", SafeErrors.describe(e), SafeErrors.smtpCode(e));
    }
}
