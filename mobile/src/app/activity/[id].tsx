import { router, Stack, useLocalSearchParams } from 'expo-router'
import { useEffect, useRef, useState } from 'react'
import { Linking, Pressable, Text, View } from 'react-native'
import { Banner, Body, Button, Card, ErrorState, H1, Loading, Muted, Row, Screen, StageBadge } from '@/components/ui'
import { api, localDate, newClientId } from '@/lib/api'
import { enqueue, flush } from '@/lib/outbox'
import { radius, space, stageInfo, usePalette } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

/** Steps one at a time with a gentle timer. The timer is a guide: "We did it" works at any point. */
export default function ActivityScreen() {
  const { id, childId } = useLocalSearchParams<{ id: string; childId?: string }>()
  const p = usePalette()
  const { data: activity, error, reload } = useLoad(() => api.activity(Number(id)), [id])
  const [step, setStep] = useState(0)
  const [running, setRunning] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState<'sent' | 'queued' | null>(null)
  const timer = useRef<ReturnType<typeof setInterval> | null>(null)

  const stepSeconds = activity ? Math.max(30, Math.round((activity.durationMinutes * 60) / activity.steps.length)) : 60

  useEffect(() => {
    if (!running) return
    timer.current = setInterval(() => setElapsed((s) => s + 1), 1000)
    return () => {
      if (timer.current) clearInterval(timer.current)
    }
  }, [running])

  useEffect(() => {
    if (!activity || elapsed < stepSeconds) return
    if (step < activity.steps.length - 1) {
      setStep((s) => s + 1)
      setElapsed(0)
    } else {
      setRunning(false)
    }
  }, [elapsed, stepSeconds, step, activity])

  async function finish() {
    if (!activity) return
    setSaving(true)
    setRunning(false)
    await enqueue({
      clientId: newClientId(),
      activityId: activity.id,
      childId: childId ? Number(childId) : null,
      doneOn: localDate(),
    })
    const left = await flush()
    setSaved(left === 0 ? 'sent' : 'queued')
    setSaving(false)
  }

  if (error && !activity) return <ErrorState message={error} onRetry={() => reload()} />
  if (!activity) return <Loading />

  const colour = stageInfo(activity.stage)?.colour ?? p.teal
  const last = step === activity.steps.length - 1
  const remaining = Math.max(0, stepSeconds - elapsed)

  if (saved) {
    return (
      <Screen>
        <Stack.Screen options={{ title: activity.title }} />
        <Card accent={colour}>
          <H1>Well done!</H1>
          <Body>
            {saved === 'sent'
              ? 'That counts towards your last 14 days.'
              : 'Saved on this phone. It will be sent next time you are online.'}
          </Body>
        </Card>
        <Button label="Back to today" onPress={() => router.back()} />
      </Screen>
    )
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: activity.title }} />
      <View style={{ gap: space.sm }}>
        <StageBadge stage={activity.stage} label={activity.stageName} />
        <H1>{activity.title}</H1>
        <Muted>
          {activity.focus} · about {activity.durationMinutes} minutes
        </Muted>
      </View>

      {activity.videoUrl ? (
        <Button label="Watch the video" variant="secondary" onPress={() => Linking.openURL(activity.videoUrl!)} />
      ) : (
        <Muted>Video coming soon. Follow the steps below together.</Muted>
      )}

      <Card accent={colour} style={{ gap: space.md }}>
        <Row style={{ justifyContent: 'space-between' }}>
          <Muted>
            Step {step + 1} of {activity.steps.length}
          </Muted>
          <Text style={{ color: p.muted, fontVariant: ['tabular-nums'], fontSize: 14 }}>
            {Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, '0')}
          </Text>
        </Row>
        <Text style={{ color: p.ink, fontSize: 22, lineHeight: 30, fontWeight: '600' }}>{activity.steps[step]}</Text>
        <View style={{ height: 6, backgroundColor: p.track, borderRadius: 3, overflow: 'hidden' }}>
          <View style={{ height: 6, width: `${(elapsed / stepSeconds) * 100}%`, backgroundColor: colour }} />
        </View>
        <Row>
          <View style={{ flex: 1 }}>
            <Button
              label={running ? 'Pause' : elapsed > 0 ? 'Carry on' : 'Start timer'}
              variant="secondary"
              onPress={() => setRunning((r) => !r)}
            />
          </View>
          {!last && (
            <View style={{ flex: 1 }}>
              <Button
                label="Next step"
                variant="secondary"
                onPress={() => {
                  setStep((s) => s + 1)
                  setElapsed(0)
                }}
              />
            </View>
          )}
        </Row>
      </Card>

      <View style={{ gap: space.xs }}>
        {activity.steps.map((s, i) => (
          <Pressable
            key={i}
            accessibilityRole="button"
            accessibilityState={{ selected: i === step }}
            onPress={() => {
              setStep(i)
              setElapsed(0)
            }}
            style={{
              flexDirection: 'row',
              gap: space.md,
              padding: space.md,
              borderRadius: radius,
              backgroundColor: i === step ? p.cream : 'transparent',
            }}
          >
            <Text style={{ color: i < step ? p.teal : p.muted, fontWeight: '700', width: 18 }}>
              {i < step ? '✓' : i + 1}
            </Text>
            <Text style={{ color: p.ink, fontSize: 15, lineHeight: 21, flex: 1 }}>{s}</Text>
          </Pressable>
        ))}
      </View>

      {last && elapsed >= stepSeconds && <Banner tone="ok">All steps done. Tap below to count today.</Banner>}
      <Button label="We did it" onPress={finish} busy={saving} />
    </Screen>
  )
}
