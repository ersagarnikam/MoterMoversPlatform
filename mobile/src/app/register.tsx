import { useState } from 'react'
import { Banner, Body, Button, Field, Muted, Screen } from '@/components/ui'
import { useAuth } from '@/lib/auth'

export default function RegisterScreen() {
  const { register } = useAuth()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const problem = !name.trim()
    ? 'Enter your name'
    : !/^\S+@\S+\.\S+$/.test(email.trim())
      ? 'Enter a valid email address'
      : password.length < 10
        ? 'Use at least 10 characters for your password'
        : null

  async function submit() {
    if (problem) {
      setError(problem)
      return
    }
    setBusy(true)
    setError(null)
    try {
      await register(name, email, password)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create the account')
      setBusy(false)
    }
  }

  return (
    <Screen>
      <Body>For parents and carers. You can use the home activities and book the parent course straight away.</Body>
      <Field label="Your name" value={name} onChangeText={setName} autoComplete="name" textContentType="name" />
      <Field
        label="Email"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        autoComplete="email"
        keyboardType="email-address"
        textContentType="emailAddress"
      />
      <Field
        label="Password"
        hint="At least 10 characters."
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        autoComplete="new-password"
        textContentType="newPassword"
        returnKeyType="go"
        onSubmitEditing={submit}
      />
      {error && <Banner tone="error">{error}</Banner>}
      <Button label="Create account" onPress={submit} busy={busy} />
      <Muted>
        To see your child's progress, you will also need a parent code from their school. We never ask for your
        child's date of birth or full name.
      </Muted>
    </Screen>
  )
}
