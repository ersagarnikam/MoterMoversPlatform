import * as Linking from 'expo-linking'
import { useState } from 'react'
import { View } from 'react-native'
import { Banner, Body, Button, Card, ErrorState, H2, Loading, Muted, Row, Screen } from '@/components/ui'
import { api, formatDate, fullUrl, money, sessionTime, type CoursePlayer, type LiveSession } from '@/lib/api'
import { space } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

/**
 * The parent course. Courses are recorded (go-live task 89): a parent books a place and its lessons open in order,
 * each with its film to watch. Marking a lesson done and any test are on the website, where the film plays inside
 * the page and the platform can tell it has played to the end; the app opens the film, as home activities do.
 * Live sessions in the Zoom app show only where a deployment has live courses switched on.
 */
export default function CourseScreen() {
  const { data, error, refreshing, reload } = useLoad(async () => {
    const [catalogue, mine, settings] = await Promise.all([api.courses(), api.myEnrolments(), api.settings()])
    const live = settings.liveCourses ? await api.liveSessions().catch(() => []) : []
    const places = await Promise.all(
      mine.filter((e) => e.recorded).map((e) => api.coursePlayer(e.id).catch(() => null)),
    )
    return { ...catalogue, mine, live, liveCourses: settings.liveCourses, places: places.filter((p) => p !== null) }
  })
  const [booking, setBooking] = useState<number | null>(null)
  const [bookError, setBookError] = useState<string | null>(null)
  const [joining, setJoining] = useState<number | null>(null)
  const [joinMessage, setJoinMessage] = useState<string | null>(null)

  if (error && !data) return <ErrorState message={error} onRetry={() => reload()} />
  if (!data) return <Loading />

  const bookedCohorts = new Set(data.mine.map((e) => e.cohortId))

  async function book(id: number, action: (id: number) => Promise<unknown>) {
    setBooking(id)
    setBookError(null)
    try {
      await action(id)
      await reload()
    } catch (e) {
      setBookError(e instanceof Error ? e.message : 'Could not book')
    } finally {
      setBooking(null)
    }
  }

  async function join(s: LiveSession) {
    setJoining(s.id)
    setJoinMessage(null)
    try {
      const joined = await api.joinLive(s.id)
      if (joined.zoomUrl) {
        // Opens the Zoom app if it is installed, or Zoom's page in the browser, which offers it.
        await Linking.openURL(joined.zoomUrl)
      } else {
        setJoinMessage('Video is not connected yet. Your trainer will let you know how to join this session.')
      }
    } catch (e) {
      setJoinMessage(e instanceof Error ? e.message : 'Could not join the session')
    } finally {
      setJoining(null)
    }
  }

  return (
    <Screen refreshing={refreshing} onRefresh={() => reload(true)}>
      {data.live.length > 0 && (
        <View style={{ gap: space.sm }}>
          <H2>Live sessions</H2>
          {data.live.slice(0, 3).map((s) => (
            <Card key={s.id}>
              <Body style={{ fontWeight: '600' }}>
                Session {s.number} of {s.of} · {sessionTime(s.startsAt)}
              </Body>
              <Muted>
                {s.courseTitle} · {s.cohortName} · {s.durationMinutes} minutes with {s.trainerName}
              </Muted>
              {s.open ? (
                <Button label="Join in Zoom" busy={joining === s.id} onPress={() => join(s)} />
              ) : (
                <Muted>You can join from 15 minutes before the start.</Muted>
              )}
            </Card>
          ))}
          {joinMessage && <Banner>{joinMessage}</Banner>}
        </View>
      )}

      {data.places.map((p) => (
        <Place key={p.enrolmentId} place={p} />
      ))}

      {data.liveCourses && data.mine.some((e) => !e.recorded) && (
        <View style={{ gap: space.sm }}>
          <H2>Your bookings</H2>
          {data.mine
            .filter((e) => !e.recorded)
            .map((e) => (
              <Card key={e.id}>
                <Body style={{ fontWeight: '600' }}>{e.courseTitle}</Body>
                <Muted>
                  {e.cohortName} · starts {e.startsOn ? formatDate(e.startsOn) : ''} · {e.timeSlot}
                </Muted>
                <Muted>
                  {e.sessionsAttended} of {e.liveSessions} live sessions attended
                </Muted>
              </Card>
            ))}
        </View>
      )}

      {data.courses.map((c) => (
        <Card key={c.id}>
          <H2>{c.title}</H2>
          <Body>{c.description}</Body>
          <Muted>
            {c.lessons === 0
              ? 'Being prepared: its lessons are on their way.'
              : `${c.lessons} recorded ${c.lessons === 1 ? 'lesson' : 'lessons'}, about ${c.lessonMinutes} minutes in all · ${money(c.pricePence)} per family`}
          </Muted>
          {c.place === 'BOOKED' ? (
            <Banner tone="ok">You have a place on this course. Its lessons are above.</Banner>
          ) : c.lessons > 0 ? (
            <Button
              label="Book a place"
              variant="secondary"
              busy={booking === c.id}
              onPress={() => book(c.id, api.enrolOnCourse)}
            />
          ) : null}
          {data.liveCourses &&
            c.cohorts.map((h) => (
              <View key={h.id} style={{ gap: space.sm, paddingTop: space.sm }}>
                <Row style={{ justifyContent: 'space-between' }}>
                  <Body style={{ fontWeight: '600', flexShrink: 1 }}>{h.name}</Body>
                  <Muted>{h.seatsLeft > 0 ? `${h.seatsLeft} places left` : 'Full'}</Muted>
                </Row>
                <Muted>
                  Starts {formatDate(h.startsOn)} · {h.timeSlot} · with {h.trainerName}
                </Muted>
                {h.recordingAgreed && <Muted>Sessions are recorded so you can catch up.</Muted>}
                {bookedCohorts.has(h.id) ? (
                  <Banner tone="ok">You are booked on this group.</Banner>
                ) : (
                  <Button
                    label="Book a place"
                    variant="secondary"
                    disabled={h.seatsLeft <= 0}
                    busy={booking === h.id}
                    onPress={() => book(h.id, api.enrol)}
                  />
                )}
              </View>
            ))}
        </Card>
      ))}
      {bookError && <Banner tone="error">{bookError}</Banner>}
      {data.liveCourses && <Muted>{data.rules}</Muted>}
    </Screen>
  )
}

/** A place on a recorded course: its lessons in order, each open one with its film to watch. */
function Place({ place }: { place: CoursePlayer }) {
  const allDone = place.lessons.length > 0 && place.lessonsDone === place.lessons.length
  return (
    <View style={{ gap: space.sm }}>
      <H2>{place.courseTitle}</H2>
      <Muted>
        {place.lessonsDone} of {place.lessons.length} {place.lessons.length === 1 ? 'lesson' : 'lessons'} done
      </Muted>
      {place.lessons.map((l) => (
        <Card key={l.id}>
          <Row style={{ justifyContent: 'space-between' }}>
            <Body style={{ fontWeight: '600', flexShrink: 1 }}>
              {l.position}. {l.title}
            </Body>
            <Muted>{l.done ? 'Done' : l.open ? 'Next' : `After lesson ${l.position - 1}`}</Muted>
          </Row>
          <Muted>{l.minutes} minutes</Muted>
          {l.open && l.paragraphs.map((p) => <Body key={p}>{p}</Body>)}
          {l.open && l.videoUrl && (
            <Button
              label="Watch the film"
              variant={l.done ? 'secondary' : 'primary'}
              onPress={() => Linking.openURL(fullUrl(l.videoUrl!))}
            />
          )}
          {l.open && l.slidesUrl && (
            <Button label="Open the slides" variant="secondary" onPress={() => Linking.openURL(fullUrl(l.slidesUrl!))} />
          )}
        </Card>
      ))}
      <Banner>
        {allDone
          ? place.quizzes > place.quizzesPassed
            ? 'Every lesson is done. Take the test on the website.'
            : 'You have finished this course.'
          : 'Mark each lesson as done on the website once you have watched it: the next lesson then opens here too.'}
      </Banner>
    </View>
  )
}
