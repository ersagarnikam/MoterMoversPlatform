import { router, Stack, useLocalSearchParams } from 'expo-router'
import { useState } from 'react'
import { RateExercises } from '@/components/RateExercises'
import { Body, Button, Card, ErrorState, H1, Loading, Screen } from '@/components/ui'
import { api } from '@/lib/api'
import { useLoad } from '@/lib/useLoad'

/** At the end of a module (or the colour), the parent rates its exercises again, so progress shows. */
export default function ReviewScreen() {
  const { id } = useLocalSearchParams<{ id: string }>()
  const childId = Number(id)
  const { data: today, error, reload } = useLoad(() => api.homeToday(childId), [childId])
  const [saved, setSaved] = useState<'sent' | 'queued' | null>(null)

  if (error && !today) return <ErrorState message={error} onRetry={() => reload()} />
  if (!today) return <Loading />

  if (saved || !today.review) {
    return (
      <Screen>
        <Stack.Screen options={{ title: 'Ratings' }} />
        <Card>
          <H1>Thank you</H1>
          <Body>
            {saved === 'queued'
              ? 'Saved on this phone. It will be sent next time you are online.'
              : `You can see how ${today.name} is getting on in Progress.`}
          </Body>
        </Card>
        <Button label="Back to today" onPress={() => router.back()} />
      </Screen>
    )
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: today.review.label }} />
      <RateExercises
        childId={childId}
        point={today.review.point}
        exercises={today.review.exercises}
        levels={today.levels}
        intro={`${today.name} has finished ${today.review.point === 'END' ? today.stageName : `module ${today.review.point.replace('MODULE_', '')}`}. Do each of these once more and choose how it went.`}
        onDone={(queued) => setSaved(queued ? 'queued' : 'sent')}
      />
    </Screen>
  )
}
