import { View } from 'react-native'
import { Body, Card, H2, Muted, Row } from '@/components/ui'
import type { ChartProgress, ExerciseProgress } from '@/lib/api'

/** The programme's two movement charts, in the parent's words. */
const CHART_NAMES = { FINE: 'Hands and fingers', GROSS: 'Whole body' } as const

/**
 * Movements secure on each of Pat's charts, with those new in the last few months. Used for a home child and for a
 * school child (where the teacher records them).
 */
export function MovementCards({ charts }: { charts: ChartProgress[] }) {
  return (
    <>
      {charts.map((c) => (
        <Card key={c.chart}>
          <Row style={{ justifyContent: 'space-between' }}>
            <Body style={{ fontWeight: '600' }}>{CHART_NAMES[c.chart]}</Body>
            <Muted>
              {c.secure} of {c.total}
            </Muted>
          </Row>
          {c.recentlySecure.length > 0 && (
            <View style={{ gap: 2 }}>
              <Muted>New in the last few months:</Muted>
              {c.recentlySecure.slice(0, 5).map((m) => (
                <Body key={m}>• {m}</Body>
              ))}
              {c.recentlySecure.length > 5 && <Muted>and {c.recentlySecure.length - 5} more</Muted>}
            </View>
          )}
        </Card>
      ))}
    </>
  )
}

/** Each exercise's first and latest rating, in the parent's words (Not yet, Getting there, Nearly, Got it). */
export function ExerciseList({
  stageName,
  exercises,
  note,
}: {
  stageName: string | null
  exercises: ExerciseProgress[]
  note?: string
}) {
  if (exercises.length === 0) return null
  return (
    <>
      <H2>Exercises{stageName ? ` · ${stageName}` : ''}</H2>
      {note && <Muted>{note}</Muted>}
      <Card>
        {exercises.map((e) => (
          <Row key={e.exerciseId} style={{ justifyContent: 'space-between' }}>
            <Body style={{ flexShrink: 1 }}>{e.title}</Body>
            <Muted>
              {e.first === e.latest ? e.latest : `${e.first} → ${e.latest}`}
              {e.improved ? ' ✓' : ''}
            </Muted>
          </Row>
        ))}
      </Card>
    </>
  )
}
