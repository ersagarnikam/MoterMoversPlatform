import { router } from 'expo-router'
import { useState } from 'react'
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { Banner, Button, Field, Muted, StageStripe } from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { space, usePalette } from '@/lib/theme'

export default function SignInScreen() {
  const { signIn } = useAuth()
  const p = usePalette()
  const insets = useSafeAreaInsets()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit() {
    setBusy(true)
    setError(null)
    try {
      await signIn(email, password)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not sign in')
      setBusy(false)
    }
  }

  return (
    <KeyboardAvoidingView style={{ flex: 1, backgroundColor: p.page }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{ flexGrow: 1 }}>
        <View style={{ backgroundColor: p.navy, paddingTop: insets.top + space.xl, paddingHorizontal: space.xl, paddingBottom: space.xl, gap: space.md }}>
          <StageStripe />
          <Text style={{ color: '#ffffff', fontSize: 30, fontWeight: '800' }}>Motor Movers</Text>
          <Text style={{ color: '#c8d3de', fontSize: 16, lineHeight: 23 }}>
            Short movement activities to do at home, and your child's progress on the programme.
          </Text>
        </View>
        <View style={{ padding: space.xl, gap: space.md, width: '100%', maxWidth: 480, alignSelf: 'center' }}>
          <Field
            label="Email"
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            autoComplete="email"
            keyboardType="email-address"
            textContentType="username"
          />
          <Field
            label="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            autoComplete="current-password"
            textContentType="password"
            returnKeyType="go"
            onSubmitEditing={submit}
          />
          {error && <Banner tone="error">{error}</Banner>}
          <Button label="Sign in" onPress={submit} busy={busy} disabled={!email || !password} />
          <Button label="Create an account" variant="secondary" onPress={() => router.push('/register')} />
          <Button label="Forgot your password?" variant="secondary" onPress={() => router.push('/forgot-password')} />
          {__DEV__ && <Muted>Demo: parent@example.test / password</Muted>}
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  )
}
