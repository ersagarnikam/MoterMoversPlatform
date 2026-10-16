// The sign-in token lives in the phone's secure store (Keychain / Keystore). In a browser, where
// there is no secure store, it falls back to localStorage for development.
import AsyncStorage from '@react-native-async-storage/async-storage'
import * as SecureStore from 'expo-secure-store'
import { Platform } from 'react-native'

const TOKEN_KEY = 'mm.token'

export async function loadToken(): Promise<string | null> {
  try {
    if (Platform.OS === 'web') return globalThis.localStorage?.getItem(TOKEN_KEY) ?? null
    return await SecureStore.getItemAsync(TOKEN_KEY)
  } catch {
    return null
  }
}

export async function saveToken(token: string | null): Promise<void> {
  try {
    if (Platform.OS === 'web') {
      if (token) globalThis.localStorage?.setItem(TOKEN_KEY, token)
      else globalThis.localStorage?.removeItem(TOKEN_KEY)
      return
    }
    if (token) await SecureStore.setItemAsync(TOKEN_KEY, token)
    else await SecureStore.deleteItemAsync(TOKEN_KEY)
  } catch {
    // storage unavailable: the parent signs in again next time
  }
}

/** Small, non-secret values kept on this phone only. */
export async function getLocal<T>(key: string, fallback: T): Promise<T> {
  try {
    const raw = await AsyncStorage.getItem(key)
    return raw == null ? fallback : (JSON.parse(raw) as T)
  } catch {
    return fallback
  }
}

export async function setLocal(key: string, value: unknown): Promise<void> {
  try {
    if (value === null || value === undefined) await AsyncStorage.removeItem(key)
    else await AsyncStorage.setItem(key, JSON.stringify(value))
  } catch {
    // storage full or blocked: the value lasts until the app closes
  }
}

/** Removes everything this app keeps on the phone for the signed-in parent. */
export async function clearLocal(): Promise<void> {
  try {
    const keys = await AsyncStorage.getAllKeys()
    await AsyncStorage.multiRemove(keys.filter((k) => k.startsWith('mm.')))
  } catch {
    // nothing to clear
  }
}
