import { useState } from 'react'
import { View } from 'react-native'
import { Banner, Body, Button, Card, Chip, Muted, Row } from '@/components/ui'
import { localDate, newClientId, type HomeExercise, type HomeToday } from '@/lib/api'
import { homeRatings } from '@/lib/homeOutbox'
import { space } from '@/lib/theme'

/**
 * Rates exercises with the four levels in the parent's words ("Not yet" to "Got it"). Choosing one shows Pat's own
 * description of that level, so parents can check what it looks like. Ratings are saved on the phone first.
 */
export function RateExercises({
  childId,
  point,
  exercises,
  levels,
  intro,
  onDone,
}: {
  childId: number
  point: string
  exercises: HomeExercise[]
  levels: HomeToday['levels']
  intro: string
  onDone: (queued: boolean) => void
}) {
  const [scores, setScores] = useState<Record<number, number>>({})
  const [busy, setBusy] = useState(false)
  const done = exercises.every((e) => scores[e.id])

  async function save() {
    setBusy(true)
    const today = localDate()
    await homeRatings.enqueue(
      exercises.map((e) => ({
        clientId: newClientId(),
        childId,
        exerciseId: e.id,
        point,
        score: scores[e.id],
        assessedOn: today,
      })),
    )
    const left = await homeRatings.flush()
    setBusy(false)
    onDone(left > 0)
  }

  return (
    <View style={{ gap: space.md }}>
      <Body>{intro}</Body>
      {exercises.map((e) => {
        const chosen = scores[e.id]
        return (
          <Card key={e.id}>
            <Body style={{ fontWeight: '600' }}>{e.title}</Body>
            <Row>
              {levels.map((l) => (
                <Chip
                  key={l.score}
                  label={l.label}
                  selected={chosen === l.score}
                  onPress={() => setScores((all) => ({ ...all, [e.id]: l.score }))}
                />
              ))}
            </Row>
            {chosen ? <Muted>{e.levels[String(chosen)] ?? 'Pat’s description for this one is on its way.'}</Muted> : null}
          </Card>
        )
      })}
      {!done && <Banner>Choose one for each exercise. Go with what you saw today.</Banner>}
      <Button label="Save" onPress={save} disabled={!done} busy={busy} />
    </View>
  )
}
