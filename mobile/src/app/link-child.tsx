import { router } from 'expo-router'
import { useState } from 'react'
import { Banner, Body, Button, Field, Muted, Screen } from '@/components/ui'
import { api } from '@/lib/api'

/** Redeems the one-time code the school gives a parent or carer. */
export default function LinkChildScreen() {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit() {
    setBusy(true)
    setError(null)
    try {
      const child = await api.linkChild(code)
      router.replace({ pathname: '/child/[id]', params: { id: child.childId } })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not link')
      setBusy(false)
    }
  }

  const ready = code.replace(/[^a-z0-9]/gi, '').length === 8

  return (
    <Screen>
      <Body>Your child's school gives each parent or carer a code. It looks like ABCD-EFGH and works once.</Body>
      <Field
        label="Parent code"
        value={code}
        onChangeText={setCode}
        placeholder="ABCD-EFGH"
        autoCapitalize="characters"
        autoCorrect={false}
        autoFocus
        maxLength={12}
        returnKeyType="go"
        onSubmitEditing={() => ready && submit()}
      />
      {error && <Banner tone="error">{error}</Banner>}
      <Button label="Link my child" onPress={submit} disabled={!ready} busy={busy} />
      <Muted>
        Once linked, you can see your child's colour stage and progress in programme terms. The school can remove the
        link at any time.
      </Muted>
    </Screen>
  )
}
