package uk.co.motormovers.platform.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
