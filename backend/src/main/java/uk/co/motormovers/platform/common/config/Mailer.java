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

/**
 * Sends plain-text email through the SMTP server in {@code spring.mail.*}. With no server configured
 * (local development) the message is written to the log instead, so links can be copied from there.
 */
@Component
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final PlatformProperties properties;

    public Mailer(ObjectProvider<JavaMailSender> sender, PlatformProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    /** A link into the web app, e.g. link("/reset-password?token=abc"). */
    public String link(String path) {
        String base = properties.mail().webUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }

    /** Sends a message with one attached file, e.g. a certificate PDF. */
    public void send(String to, String subject, String body, String fileName, byte[] file, String contentType) {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null) {
            log.info("Email not sent (no mail server configured)\nTo: {}\nSubject: {}\nAttached: {} ({} bytes)\n\n{}",
                    to, subject, fileName, file.length, body);
            return;
        }
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(fileName, new ByteArrayResource(file), contentType);
            mail.send(message);
        } catch (MailException | MessagingException e) {
            // The caller's change stands; the file can be downloaded from the platform instead.
            log.error("Could not send \"{}\" to {}", subject, to, e);
        }
    }

    public void send(String to, String subject, String body) {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null) {
            log.info("Email not sent (no mail server configured)\nTo: {}\nSubject: {}\n\n{}", to, subject, body);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mail.send(message);
        } catch (MailException e) {
            // The caller's change stands; the person can ask for the email again.
            log.error("Could not send \"{}\" to {}", subject, to, e);
        }
    }
}
