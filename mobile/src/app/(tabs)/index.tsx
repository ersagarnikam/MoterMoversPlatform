import { router } from 'expo-router'
import { useEffect, useState } from 'react'
import { Pressable, View } from 'react-native'
import { Banner, Body, Button, Card, Chip, CounterGrid, ErrorState, H1, H2, Loading, Muted, Row, Screen, StageBadge } from '@/components/ui'
import { api, gbp, type ColourStage, type Home, type LinkedChild } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { useNicknames } from '@/lib/nicknames'
import { flush, pending } from '@/lib/outbox'
import { getLocal, setLocal } from '@/lib/storage'
import { space, STAGES, stageInfo, usePalette } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

const STAGE_KEY = 'mm.home.stage'

/** Who the activity is for: a linked child (their stage), or a stage the parent picked. */
type Choice = { childId: number } | { stage: ColourStage } | null

export default function TodayScreen() {
  const { user } = useAuth()
  const p = usePalette()
  const { nameFor } = useNicknames()
  const [choice, setChoice] = useState<Choice | undefined>(undefined)
  const [unsent, setUnsent] = useState(0)

  useEffect(() => {
    getLocal<Choice>(STAGE_KEY, null).then(setChoice)
  }, [])

  const { data, error, refreshing, reload } = useLoad(async () => {
    if (choice === undefined) return null
    setUnsent(await flush())
    const children = await api.children()
    const params =
      choice && 'childId' in choice && children.some((c) => c.childId === choice.childId)
        ? { childId: choice.childId }
        : choice && 'stage' in choice
          ? { stage: choice.stage }
          : children[0]
            ? { childId: children[0].childId }
            : {}
    const home = await api.home(params)
    // A payment problem must not hide the activities, so it falls back to nothing due.
    const due = await api.myPayments().then((m) => m.outstandingPence, () => 0)
    // Likewise a live session open to join now, from the parent course.
    const liveNow = await api.liveSessions().then((l) => l.some((s) => s.open), () => false)
    return { children, home, due, liveNow, forChild: ('childId' in params ? params.childId : null) ?? null }
  }, [choice])

  useEffect(() => {
    pending().then((q) => setUnsent(q.length))
  }, [data])

  function choose(next: Choice) {
    setChoice(next)
    void setLocal(STAGE_KEY, next)
  }

  if (error && !data) return <ErrorState message={error} onRetry={() => reload()} />
  if (!data) return <Loading />
  const { children, home, due, liveNow, forChild } = data

  return (
    <Screen refreshing={refreshing} onRefresh={() => reload(true)}>
      <View style={{ gap: 2 }}>
        <Muted>{greeting()}</Muted>
        <H1>{user?.displayName ? `Hello, ${user.displayName.split(' ')[0]}` : 'Hello'}</H1>
      </View>

      {unsent > 0 && (
        <Banner>
          {unsent === 1 ? '1 session is' : `${unsent} sessions are`} saved on this phone and will be sent when you
          are back online.
        </Banner>
      )}

      {liveNow && (
        <Pressable accessibilityRole="button" onPress={() => router.push('/course')}>
          <Banner tone="ok">Your live session is open to join. Tap to join it in Zoom.</Banner>
        </Pressable>
      )}

      {due > 0 && (
        <Pressable accessibilityRole="button" onPress={() => router.push('/payments')}>
          <Banner>You have {gbp(due)} to pay. Tap to see what it is for and how to pay.</Banner>
        </Pressable>
      )}

      <WhoFor linked={children} forChild={forChild} stage={home.stage} nameFor={nameFor} onChoose={choose} />

      <TodayCard home={home} childId={forChild} />

      <Card>
        <Row style={{ justifyContent: 'space-between' }}>
          <H2>Last {home.counterDays} days</H2>
          <Muted>
            {home.daysDone} of {home.counterDays}
          </Muted>
        </Row>
        <CounterGrid grid={home.counterGrid} />
        <Muted>
          {home.daysDone >= 10
            ? 'Brilliant. Little and often is exactly right.'
            : home.daysDone >= 5
              ? 'Good going. Most days is the aim.'
              : 'Five minutes most days makes the biggest difference.'}
        </Muted>
      </Card>

      {home.activities.length > 0 && (
        <View style={{ gap: space.sm }}>
          <H2>All activities for {stageInfo(home.stage)?.label ?? 'this stage'}</H2>
          {home.activities.map((a) => (
            <Pressable
              key={a.id}
              accessibilityRole="button"
              onPress={() => router.push({ pathname: '/activity/[id]', params: { id: a.id, childId: forChild ?? '' } })}
            >
              <Card>
                <Row style={{ justifyContent: 'space-between' }}>
                  <Body style={{ fontWeight: '600', flexShrink: 1 }}>{a.title}</Body>
                  <Muted>{a.durationMinutes} min</Muted>
                </Row>
                <Muted numberOfLines={2}>{a.focus}</Muted>
              </Card>
            </Pressable>
          ))}
        </View>
      )}

      {children.length === 0 && (
        <Card accent={p.teal}>
          <H2>Is your child on Motor Movers at school?</H2>
          <Muted>
            Ask the school for a parent code. Linking your child picks the right activities and shows their progress.
          </Muted>
          <Button label="I have a code" variant="secondary" onPress={() => router.push('/link-child')} />
        </Card>
      )}
    </Screen>
  )
}

function WhoFor({
  linked,
  forChild,
  stage,
  nameFor,
  onChoose,
}: {
  linked: LinkedChild[]
  forChild: number | null
  stage: ColourStage
  nameFor: (c: LinkedChild) => string
  onChoose: (c: Choice) => void
}) {
  if (linked.length > 0) {
    if (linked.length === 1 && forChild) return null
    return (
      <Row>
        {linked.map((c) => (
          <Chip
            key={c.childId}
            label={nameFor(c)}
            dot={stageInfo(c.currentStage)?.colour}
            selected={forChild === c.childId}
            onPress={() => onChoose({ childId: c.childId })}
          />
        ))}
      </Row>
    )
  }
  return (
    <View style={{ gap: space.sm }}>
      <Muted>Choose a stage. Your child's school can tell you which one.</Muted>
      <Row>
        {STAGES.map((s) => (
          <Chip
            key={s.key}
            label={s.label}
            dot={s.colour}
            selected={stage === s.key}
            onPress={() => onChoose({ stage: s.key })}
          />
        ))}
      </Row>
    </View>
  )
}

function TodayCard({ home, childId }: { home: Home; childId: number | null }) {
  const info = stageInfo(home.stage)
  if (!home.next) {
    return (
      <Card>
        <StageBadge stage={home.stage} label={home.stageName} />
        <Muted>Home activities for this stage are on their way.</Muted>
      </Card>
    )
  }
  const open = () =>
    router.push({ pathname: '/activity/[id]', params: { id: home.next!.id, childId: childId ?? '' } })
  return (
    <Card accent={info?.colour}>
      <Row style={{ justifyContent: 'space-between' }}>
        <StageBadge stage={home.stage} label={home.stageName} />
        <Muted>{home.next.durationMinutes} min</Muted>
      </Row>
      <Muted>{home.doneToday ? 'Done today' : "Today's activity"}</Muted>
      <H1 style={{ fontSize: 22 }}>{home.next.title}</H1>
      <Body>{home.next.focus}</Body>
      {home.doneToday ? (
        <>
          <Banner tone="ok">Done for today. Nice work, see you tomorrow.</Banner>
          <Button label="Do it again" variant="secondary" onPress={open} />
        </>
      ) : (
        <Button label="Start" onPress={open} />
      )}
    </Card>
  )
}

function greeting(): string {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening'
}
