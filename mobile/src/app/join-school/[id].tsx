import { router, Stack, useLocalSearchParams } from 'expo-router'
import { useState } from 'react'
import { Banner, Body, Button, Field, Muted, Screen } from '@/components/ui'
import { api } from '@/lib/api'
import { useLoad } from '@/lib/useLoad'

/**
 * The child's school has joined Motor Movers and given the parent a code. Linking shares what was recorded at home
 * with the class teacher, and the school's record carries on from there. The parent agrees to this here.
 */
export default function JoinSchoolScreen() {
  const { id } = useLocalSearchParams<{ id: string }>()
  const childId = Number(id)
  const { data } = useLoad(() => api.homeProgress(childId), [childId])
  const name = data?.child.name ?? 'your child'
  const [code, setCode] = useState('')
  const [agreed, setAgreed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const ready = code.replace(/[^a-z0-9]/gi, '').length === 8

  async function submit() {
    setBusy(true)
    setError(null)
    try {
      const linked = await api.joinSchool(childId, code)
      router.dismissTo({ pathname: '/child/[id]', params: { id: linked.childId } })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not link')
      setBusy(false)
    }
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: 'Link a school' }} />
      <Body>
        If {name}'s school now does Motor Movers, ask them for a parent code. It looks like ABCD-EFGH and works once.
      </Body>
      <Field
        label="Parent code from the school"
        value={code}
        onChangeText={setCode}
        placeholder="ABCD-EFGH"
        autoCapitalize="characters"
        autoCorrect={false}
        maxLength={12}
      />
      <Body>When you link {name}:</Body>
      <Muted>• The class teacher sees the movements and ratings you recorded at home.</Muted>
      <Muted>• The school's record takes over: the teacher does the assessing from now on.</Muted>
      <Muted>• You can carry on with home sessions and see {name}'s progress, as other parents do.</Muted>
      <Button
        label={agreed ? 'I agree to share' : 'I agree to share what I recorded at home'}
        variant={agreed ? 'primary' : 'secondary'}
        onPress={() => setAgreed(!agreed)}
      />
      {error && <Banner tone="error">{error}</Banner>}
      <Button label="Link the school" onPress={submit} disabled={!ready || !agreed} busy={busy} />
    </Screen>
  )
}
