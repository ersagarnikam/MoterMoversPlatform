import { router, Stack } from 'expo-router'
import { useState } from 'react'
import { Linking } from 'react-native'
import { Banner, Body, Button, Card, ErrorState, H2, Loading, Muted, Screen } from '@/components/ui'
import { api, fullUrl } from '@/lib/api'
import { useLoad } from '@/lib/useLoad'

/**
 * The short parent induction, done before the first home session (each carer does their own). Sessions open once the
 * last lesson is done; the API refuses a session until then.
 */
export default function HomeInductionScreen() {
  const { data, error, reload, setData } = useLoad(() => api.homeInduction())
  const [busy, setBusy] = useState<number | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)

  if (error && !data) return <ErrorState message={error} onRetry={() => reload()} />
  if (!data) return <Loading />

  async function complete(lessonId: number) {
    setBusy(lessonId)
    setSaveError(null)
    try {
      setData(await api.completeHomeInductionLesson(lessonId))
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : 'Could not save')
    } finally {
      setBusy(null)
    }
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: 'Before your first session' }} />
      {data.completedOn ? (
        <>
          <Banner tone="ok">You have finished the induction.</Banner>
          <Button label="Back to today" onPress={() => router.back()} />
        </>
      ) : data.lessons.length === 0 ? (
        <Body>The induction is being prepared. Sessions open once it is ready.</Body>
      ) : (
        <Muted>
          {data.lessonsDone} of {data.lessons.length} parts done · {data.totalMinutes} minutes in all. Sessions open
          after the last one.
        </Muted>
      )}
      {saveError && <Banner>{saveError}</Banner>}
      {data.lessons.map((l) => (
        <Card key={l.id}>
          <H2>
            {l.position}. {l.title}
          </H2>
          <Muted>
            {l.minutes} min · {l.summary}
          </Muted>
          {l.paragraphs.map((p) => (
            <Body key={p}>{p}</Body>
          ))}
          {l.videoUrl ? (
            <Button label="Watch the video" variant="secondary" onPress={() => Linking.openURL(fullUrl(l.videoUrl!))} />
          ) : null}
          {l.done ? (
            <Muted>Done</Muted>
          ) : (
            <Button label="Mark as done" busy={busy === l.id} onPress={() => complete(l.id)} />
          )}
        </Card>
      ))}
    </Screen>
  )
}
