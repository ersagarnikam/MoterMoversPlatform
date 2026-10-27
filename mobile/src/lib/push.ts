import Constants from 'expo-constants'
import * as Notifications from 'expo-notifications'
import { router } from 'expo-router'
import { Platform } from 'react-native'
import { api } from './api'
import { getLocal, setLocal } from './storage'

const TOKEN_KEY = 'mm.pushToken'

// Shown while the app is open too, e.g. "Your live session is open". Phones only.
if (Platform.OS !== 'web') {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: false,
      shouldSetBadge: false,
    }),
  })
}

/**
 * Asks for permission and registers this phone for notifications: live sessions opening, payments due. Quietly does
 * nothing where push is not available (Expo Go on Android, a build without an EAS project, permission refused), so
 * the app works the same without it. Returns the token, or null.
 */
export async function registerForPush(): Promise<string | null> {
  try {
    if (Platform.OS === 'web') return null
    if (Platform.OS === 'android') {
      // Android 13+ only asks for permission once a channel exists.
      await Notifications.setNotificationChannelAsync('default', {
        name: 'Live sessions and payments',
        importance: Notifications.AndroidImportance.DEFAULT,
      })
    }
    const existing = await Notifications.getPermissionsAsync()
    const status = existing.status === 'granted' ? existing.status : (await Notifications.requestPermissionsAsync()).status
    if (status !== 'granted') return null
    const projectId = Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId
    if (!projectId) return null
    const token = (await Notifications.getExpoPushTokenAsync({ projectId })).data
    await api.registerPushToken(token, Platform.OS)
    await setLocal(TOKEN_KEY, token)
    return token
  } catch {
    // Offline, or push not available in this build: try again next launch.
    return null
  }
}

/** On signing out: this phone stops getting the account's notifications. */
export async function unregisterPush(): Promise<void> {
  const token = await getLocal<string | null>(TOKEN_KEY, null)
  if (!token) return
  try {
    await api.removePushToken(token)
  } catch {
    // The server drops tokens Expo reports as no longer registered, so a missed removal heals itself.
  }
  await setLocal(TOKEN_KEY, null)
}

/** Tapping a notification opens the screen it is about (its data.url, e.g. "/course"). Phones only. */
export function openNotificationsOnTap(): () => void {
  // On the web these calls throw ("not available on web"), so the app there skips notifications entirely.
  if (Platform.OS === 'web') return () => {}
  const open = (response: Notifications.NotificationResponse | null | undefined) => {
    if (!response || response.actionIdentifier !== Notifications.DEFAULT_ACTION_IDENTIFIER) return
    const url = response.notification.request.content.data?.url
    if (typeof url === 'string' && url.startsWith('/')) router.push(url as never)
  }
  try {
    // A tap that launched the app from closed.
    void Notifications.getLastNotificationResponseAsync().then(open, () => undefined)
    const subscription = Notifications.addNotificationResponseReceivedListener(open)
    return () => subscription.remove()
  } catch {
    return () => {}
  }
}
