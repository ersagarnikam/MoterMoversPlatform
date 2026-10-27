import { router, Stack, useLocalSearchParams } from 'expo-router'
import { useEffect, useState } from 'react'
import { View } from 'react-native'
import { Banner, Body, Button, Field, Muted, Row, Screen } from '@/components/ui'
import { api } from '@/lib/api'
import { ageLabel, toMonths } from '@/lib/ages'
import { space } from '@/lib/theme'

/**
 * Motor Movers at Home: adds a child who is not linked to a school, or (with ?id=) changes their name or age. Only a
 * first name or nickname and an age are asked for; no date of birth.
 */
export default function AddChildScreen() {
  const { id } = useLocalSearchParams<{ id?: string }>()
  const editing = id ? Number(id) : null
  const [name, setName] = useState('')
  const [years, setYears] = useState('')
  const [months, setMonths] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!editing) return
    api.homeProgress(editing).then(
      (p) => {
        setName(p.child.name)
        setYears(String(Math.floor(p.child.ageMonths / 12)))
        setMonths(String(p.child.ageMonths % 12))
      },
      (e) => setError(e instanceof Error ? e.message : 'Could not load'),
    )
  }, [editing])

  const age = toMonths(years, months)
  const ready = name.trim().length > 0 && age !== null

  async function save() {
    if (age === null) return
    setBusy(true)
    setError(null)
    try {
      if (editing) {
        await api.updateHomeChild(editing, name.trim(), age)
        router.back()
      } else {
        const child = await api.addHomeChild(name.trim(), age)
        router.replace({ pathname: '/check/[id]', params: { id: child.id } })
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
      setBusy(false)
    }
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: editing ? 'Name and age' : 'Add your child' }} />
      {!editing && (
        <Body>
          Motor Movers at home: a few minutes of movement most days, with the same exercises schools use. Add your
          child, then a short check suggests where to start.
        </Body>
      )}
      <Field
        label="First name or nickname"
        value={name}
        onChangeText={setName}
        maxLength={30}
        autoCapitalize="words"
        hint="Only you, and anyone you invite, see this."
      />
      <View style={{ gap: 6 }}>
        <Row style={{ gap: space.md }}>
          <View style={{ flex: 1 }}>
            <Field label="Age: years" value={years} onChangeText={setYears} keyboardType="number-pad" maxLength={2} />
          </View>
          <View style={{ flex: 1 }}>
            <Field label="and months" value={months} onChangeText={setMonths} keyboardType="number-pad" maxLength={2} />
          </View>
        </Row>
        <Muted>
          {age !== null ? ageLabel(age) : 'Motor Movers is for children from 6 months to 8 years.'} We do not ask for a
          date of birth.
        </Muted>
      </View>
      {error && <Banner tone="error">{error}</Banner>}
      <Button label={editing ? 'Save' : 'Add and start the check'} onPress={save} disabled={!ready} busy={busy} />
    </Screen>
  )
}
