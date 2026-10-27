import * as Linking from 'expo-linking'
import { useState } from 'react'
import { View } from 'react-native'
import { Banner, Body, Button, Card, ErrorState, H2, Loading, Muted, Row, Screen } from '@/components/ui'
import { api, formatDate, sessionTime, type LiveSession } from '@/lib/api'
import { space } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

/** The parent course: live online sessions with the Motor Movers team, joined in the Zoom app. */
export default function CourseScreen() {
  const { data, error, refreshing, reload } = useLoad(async () => {
    const [catalogue, mine, live] = await Promise.all([api.courses(), api.myEnrolments(), api.liveSessions()])
    return { ...catalogue, mine, live }
  })
  const [booking, setBooking] = useState<number | null>(null)
  const [bookError, setBookError] = useState<string | null>(null)
  const [joining, setJoining] = useState<number | null>(null)
  const [joinMessage, setJoinMessage] = useState<string | null>(null)

  if (error && !data) return <ErrorState message={error} onRetry={() => reload()} />
  if (!data) return <Loading />

  const booked = new Set(data.mine.map((e) => e.cohortId))

  async function book(cohortId: number) {
    setBooking(cohortId)
    setBookError(null)
    try {
      await api.enrol(cohortId)
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

      {data.mine.length > 0 && (
        <View style={{ gap: space.sm }}>
          <H2>Your bookings</H2>
          {data.mine.map((e) => (
            <Card key={e.id}>
              <Body style={{ fontWeight: '600' }}>{e.courseTitle}</Body>
              <Muted>
                {e.cohortName} · starts {formatDate(e.startsOn)} · {e.timeSlot}
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
            {c.liveSessions} live sessions of {c.sessionMinutes} minutes · £{(c.pricePence / 100).toFixed(2)} per family
          </Muted>
          {c.cohorts.length === 0 && <Muted>New dates coming soon.</Muted>}
          {c.cohorts.map((h) => (
            <View key={h.id} style={{ gap: space.sm, paddingTop: space.sm }}>
              <Row style={{ justifyContent: 'space-between' }}>
                <Body style={{ fontWeight: '600', flexShrink: 1 }}>{h.name}</Body>
                <Muted>{h.seatsLeft > 0 ? `${h.seatsLeft} places left` : 'Full'}</Muted>
              </Row>
              <Muted>
                Starts {formatDate(h.startsOn)} · {h.timeSlot} · with {h.trainerName}
              </Muted>
              {h.recordingAgreed && <Muted>Sessions are recorded so you can catch up.</Muted>}
              {booked.has(h.id) ? (
                <Banner tone="ok">You are booked on this group.</Banner>
              ) : (
                <Button
                  label="Book a place"
                  variant="secondary"
                  disabled={h.seatsLeft <= 0}
                  busy={booking === h.id}
                  onPress={() => book(h.id)}
                />
              )}
            </View>
          ))}
        </Card>
      ))}
      {bookError && <Banner tone="error">{bookError}</Banner>}
      <Muted>{data.rules}</Muted>
    </Screen>
  )
}
