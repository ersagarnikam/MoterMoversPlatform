package uk.co.motormovers.platform.notification.service;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.notification.model.PushToken;
import uk.co.motormovers.platform.notification.repository.PushTokenRepository;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;

/**
 * Notifications to the parent app: a live session open to join, a session tomorrow, a payment due. They only reach
 * phones that registered (signed in to the app and allowed notifications); emails carry the same news.
 */
@Service
public class PushService {

    private final PushTokenRepository tokens;
    private final PushSender sender;
    private final CurrentUserResolver currentUser;
    private final Clock clock;

    public PushService(PushTokenRepository tokens, PushSender sender, CurrentUserResolver currentUser, Clock clock) {
        this.tokens = tokens;
        this.sender = sender;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** This phone gets the signed-in account's notifications (a phone signed in to another account moves over). */
    @Transactional
    public void register(String token, String platform) {
        if (!token.startsWith("ExponentPushToken[") && !token.startsWith("ExpoPushToken[")) {
            throw new IllegalArgumentException("That is not a push token from the Motor Movers app");
        }
        Long me = currentUser.current().id();
        tokens.findByToken(token).ifPresentOrElse(t -> t.moveTo(me),
                () -> tokens.save(new PushToken(me, token, platform, clock.instant())));
    }

    @Transactional
    public void remove(String token) {
        tokens.deleteByTokenAndUserId(token, currentUser.current().id());
    }

    /**
     * Sends to every phone of these people. {@code url} is the app screen a tap opens, e.g. "/course". Returns how
     * many phones it went to.
     */
    @Transactional
    public int notify(Collection<Long> userIds, String title, String body, String url) {
        if (userIds.isEmpty()) {
            return 0;
        }
        List<PushSender.Message> messages = tokens.findByUserIdIn(userIds).stream()
                .map(t -> new PushSender.Message(t.getToken(), title, body, Map.of("url", url)))
                .toList();
        if (messages.isEmpty()) {
            return 0;
        }
        List<String> gone = sender.send(messages);
        if (!gone.isEmpty()) {
            tokens.deleteByTokenIn(gone);
        }
        return messages.size();
    }
}
