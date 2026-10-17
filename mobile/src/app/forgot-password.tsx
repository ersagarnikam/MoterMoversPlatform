import { useState } from 'react'
import { Banner, Body, Button, Field, Muted, Screen } from '@/components/ui'
import { api } from '@/lib/api'

/** Emails a link to choose a new password. The answer is the same whether or not the email has an account. */
export default function ForgotPasswordScreen() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit() {
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) {
      setError('Enter a valid email address')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await api.requestPasswordReset(email.trim())
      setSent(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not send the link')
    } finally {
      setBusy(false)
    }
  }

  if (sent) {
    return (
      <Screen>
        <Banner tone="ok">
          If {email.trim()} has a Motor Movers account, we have emailed it a link to choose a new password.
        </Banner>
        <Muted>The link works for one hour. Open it on this phone or any computer, then sign in here.</Muted>
      </Screen>
    )
  }

  return (
    <Screen>
      <Body>Enter the email you signed up with and we will send you a link to choose a new password.</Body>
      <Field
        label="Email"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        autoComplete="email"
        keyboardType="email-address"
        textContentType="emailAddress"
        returnKeyType="send"
        onSubmitEditing={submit}
      />
      {error && <Banner tone="error">{error}</Banner>}
      <Button label="Email me a link" onPress={submit} busy={busy} />
    </Screen>
  )
}
