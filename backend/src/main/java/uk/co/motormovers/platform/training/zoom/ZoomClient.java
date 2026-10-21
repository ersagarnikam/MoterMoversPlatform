package uk.co.motormovers.platform.training.zoom;

import java.time.Instant;
import java.time.ZoneId;

/** What the platform needs from Zoom's API. */
public interface ZoomClient {

    record Meeting(String id, String passcode) {
    }

    /** Schedules a meeting hosted by {@code hostUser} (a Zoom user's email or id). */
    Meeting createMeeting(String hostUser, String topic, Instant startsAt, int minutes, ZoneId zone, boolean record);

    /** The host's ZAK, which lets the platform start the meeting as that host. */
    String hostToken(String hostUser);
}
