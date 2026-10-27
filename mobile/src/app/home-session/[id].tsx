import { router, Stack, useLocalSearchParams } from 'expo-router'
import { useState } from 'react'
import { Text, View } from 'react-native'
import { RateExercises } from '@/components/RateExercises'
import { Body, Button, Card, ErrorState, H1, Loading, Muted, Row, Screen, StageBadge } from '@/components/ui'
import { api, localDate, newClientId } from '@/lib/api'
import { homeSessions } from '@/lib/homeOutbox'
import { space, stageInfo, usePalette } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

/**
 * Today's home session: a few of Pat's exercises from the child's current module, one at a time. Afterwards the
 * parent rates any exercise done for the first time (not for a child linked to a school, whose teacher rates).
 */
export default function HomeSessionScreen() {
  const { id } = useLocalSearchParams<{ id: string }>()
  const childId = Number(id)
  const p = usePalette()
  // Loaded once: the session must not change under the parent while they do it.
  const { data: today, error, reload } = useLoad(() => api.homeToday(childId), [childId])
  const [step, setStep] = useState(0)
  const [phase, setPhase] = useState<'doing' | 'rating' | 'done'>('doing')
  const [queued, setQueued] = useState(false)
  const [busy, setBusy] = useState(false)

  if (error && !today) return <ErrorState message={error} onRetry={() => reload()} />
  if (!today) return <Loading />

  const colour = stageInfo(today.stage)?.colour ?? p.teal
  const firstTime = today.exercises.filter((e) => today.firstTime.includes(e.id))

  async function finish() {
    setBusy(true)
    await homeSessions.enqueue([{ clientId: newClientId(), childId, doneOn: localDate() }])
    setQueued((await homeSessions.flush()) > 0)
    setBusy(false)
    setPhase(today!.canRate && firstTime.length > 0 ? 'rating' : 'done')
  }

  if (phase === 'rating') {
    return (
      <Screen>
        <Stack.Screen options={{ title: 'How did it go?' }} />
        <RateExercises
          childId={childId}
          point="START"
          exercises={firstTime}
          levels={today.levels}
          intro={`This was the first time ${today.name} did ${firstTime.length === 1 ? 'this one' : 'these'}. How did it go?`}
          onDone={(q) => {
            setQueued(queued || q)
            setPhase('done')
          }}
        />
      </Screen>
    )
  }

  if (phase === 'done') {
    return (
      <Screen>
        <Stack.Screen options={{ title: 'Well done' }} />
        <Card accent={colour}>
          <H1>Well done, {today.name}!</H1>
          <Body>
            {queued
              ? 'Saved on this phone. It will be sent next time you are online.'
              : `That is session ${Math.min(today.sessionInModule, today.sessionsPerModule)} of ${today.sessionsPerModule} in module ${today.module}.`}
          </Body>
        </Card>
        <Button label="Back to today" onPress={() => router.back()} />
      </Screen>
    )
  }

  const exercise = today.exercises[step]
  const last = step === today.exercises.length - 1

  return (
    <Screen>
      <Stack.Screen options={{ title: `Module ${today.module} · session ${today.sessionInModule}` }} />
      <Row style={{ justifyContent: 'space-between' }}>
        <StageBadge stage={today.stage} label={today.stageName} />
        <Muted>
          {step + 1} of {today.exercises.length}
        </Muted>
      </Row>
      <View style={{ height: 6, backgroundColor: p.track, borderRadius: 3, overflow: 'hidden' }}>
        <View
          style={{ height: 6, width: `${((step + 1) / today.exercises.length) * 100}%`, backgroundColor: colour }}
        />
      </View>
      <Card accent={colour} style={{ gap: space.md }}>
        <H1 style={{ fontSize: 24 }}>{exercise.title}</H1>
        <Text style={{ color: p.ink, fontSize: 17, lineHeight: 26 }}>{exercise.instructions}</Text>
      </Card>
      <Muted>Video coming soon. Read each step aloud and do it together.</Muted>
      <Row>
        {step > 0 && (
          <View style={{ flex: 1 }}>
            <Button label="Back" variant="secondary" onPress={() => setStep(step - 1)} />
          </View>
        )}
        <View style={{ flex: 1 }}>
          {last ? (
            <Button label="We did it" onPress={finish} busy={busy} />
          ) : (
            <Button label="Next exercise" onPress={() => setStep(step + 1)} />
          )}
        </View>
      </Row>
    </Screen>
  )
}
