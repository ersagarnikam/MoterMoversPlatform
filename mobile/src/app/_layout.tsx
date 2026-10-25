import { Stack } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
import { useEffect } from 'react'
import { AppState } from 'react-native'
import { Loading } from '@/components/ui'
import { AuthProvider, useAuth } from '@/lib/auth'
import { flush } from '@/lib/outbox'
import { openNotificationsOnTap, registerForPush } from '@/lib/push'
import { usePalette } from '@/lib/theme'

function RootStack() {
  const { user, loading } = useAuth()
  const p = usePalette()

  // Notifications for live sessions and payments, once signed in; a tap opens the screen it is about.
  useEffect(() => {
    if (!user) return
    void registerForPush()
    return openNotificationsOnTap()
  }, [user])

  // Send any home sessions saved while offline whenever the app comes back to the foreground.
  useEffect(() => {
    if (!user) return
    void flush()
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') void flush()
    })
    return () => sub.remove()
  }, [user])

  if (loading) return <Loading />

  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: p.header },
        headerTintColor: p.headerInk,
        headerTitleStyle: { fontWeight: '700' },
        headerShadowVisible: false,
        contentStyle: { backgroundColor: p.page },
        headerBackButtonDisplayMode: 'minimal',
      }}
    >
      <Stack.Protected guard={!!user}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="activity/[id]" options={{ title: 'Home activity' }} />
        <Stack.Screen name="child/[id]" options={{ title: 'Progress' }} />
        <Stack.Screen name="guidance/[id]" options={{ title: 'Guidance' }} />
        <Stack.Screen name="payments" options={{ title: 'Payments' }} />
        <Stack.Screen name="link-child" options={{ title: 'Link your child', presentation: 'modal' }} />
      </Stack.Protected>
      <Stack.Protected guard={!user}>
        <Stack.Screen name="sign-in" options={{ headerShown: false }} />
        <Stack.Screen name="register" options={{ title: 'Create an account' }} />
        <Stack.Screen name="forgot-password" options={{ title: 'Reset your password' }} />
      </Stack.Protected>
    </Stack>
  )
}

export default function RootLayout() {
  return (
    <AuthProvider>
      <StatusBar style="light" />
      <RootStack />
    </AuthProvider>
  )
}
