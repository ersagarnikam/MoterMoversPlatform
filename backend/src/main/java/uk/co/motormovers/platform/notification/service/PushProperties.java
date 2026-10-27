package uk.co.motormovers.platform.notification.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Notifications to the parent app through Expo's push service. Off unless {@code enabled} (PUSH_ENABLED=true), so
 * development and tests never call Expo: what would be sent is logged instead. {@code accessToken} is only needed if
 * the Expo account turns on "enhanced push security" (EXPO_ACCESS_TOKEN).
 */
@ConfigurationProperties(prefix = "platform.push")
public record PushProperties(boolean enabled, String accessToken) {
}
