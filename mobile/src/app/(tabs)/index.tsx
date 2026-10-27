import { router } from 'expo-router'
import { useEffect, useState } from 'react'
import { Pressable, View } from 'react-native'
import { Banner, Body, Button, Card, Chip, CounterGrid, ErrorState, H1, H2, Loading, Muted, Row, Screen, StageBadge } from '@/components/ui'
import { api, gbp, type ColourStage, type Home, type HomeChild, type HomeToday, type LinkedChild } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { flushHome } from '@/lib/homeOutbox'
import { useNicknames } from '@/lib/nicknames'
import { flush, pending } from '@/lib/outbox'
import { getLocal, setLocal } from '@/lib/storage'
import { space, STAGES, stageInfo, usePalette } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

const STAGE_KEY = 'mm.home.stage'

/**
 * Who today is for: a child linked through their school (their stage), a family's own child on Motor Movers at Home
 * (homeId), or a stage the parent picked.
 */
type Choice = { childId: number } | { homeId: number } | { stage: ColourStage } | null

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
    setUnsent((await flush()) + (await flushHome()))
    const children = await api.children()
    // Motor Movers at Home may be switched off, or the call may fail: then the app is as before.
    const overview = await api.homeOverview().catch(() => null)
    const homeKids = overview?.enabled ? overview.children : []
    let homeId = choice && 'homeId' in choice && homeKids.some((k) => k.id === choice.homeId) ? choice.homeId : null
    if (homeId === null && !(choice && ('childId' in choice || 'stage' in choice)) && children.length === 0) {
      homeId = homeKids[0]?.id ?? null
    }
    const kid = homeKids.find((k) => k.id === homeId) ?? null
    const homeToday = kid?.stage ? await api.homeToday(kid.id) : null
    const params = kid
      ? { childId: kid.id }
      : choice && 'childId' in choice && children.some((c) => c.childId === choice.childId)
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
    return {
      children,
      homeEnabled: !!overview?.enabled,
      homeKids,
      kid,
      homeToday,
      home,
      due,
      liveNow,
      forChild: kid ? null : ('childId' in params ? params.childId : null) ?? null,
    }
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
  const { children, homeEnabled, homeKids, kid, homeToday, home, due, liveNow, forChild } = data
  // Pat's exercises for this home child's colour; otherwise the colour's home activities, as for everyone else.
  const exercisesToday = kid && homeToday?.hasExercises ? homeToday : null

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
      <WhoFor
        linked={children}
        homeKids={homeKids}
        forChild={forChild}
        homeId={kid?.id ?? null}
        stage={home.stage}
        nameFor={nameFor}
        onChoose={choose}
      />
      {kid && !kid.stage ? (
        <CheckCard kid={kid} />
      ) : exercisesToday ? (
        <>
          {kid?.checkDue && (
            <Pressable
              accessibilityRole="button"
              onPress={() => router.push({ pathname: '/check/[id]', params: { id: kid.id } })}
            >
              <Banner tone="ok">It has been a few months: time to do {kid.name}'s check again. Tap to start.</Banner>
            </Pressable>
          )}
          <HomeSessionCard today={exercisesToday} />
          <Counter grid={exercisesToday.counterGrid} done={exercisesToday.daysDone} days={exercisesToday.counterDays} />
        </>
      ) : (
        <>
          <TodayCard home={home} childId={kid?.id ?? forChild} />
          <Counter grid={home.counterGrid} done={home.daysDone} days={home.counterDays} />
          {home.activities.length > 0 && (
            <View style={{ gap: space.sm }}>
              <H2>All activities for {stageInfo(home.stage)?.label ?? 'this stage'}</H2>
              {home.activities.map((a) => (
                <Pressable
                  key={a.id}
                  accessibilityRole="button"
                  onPress={() =>
                    router.push({ pathname: '/activity/[id]', params: { id: a.id, childId: kid?.id ?? forChild ?? '' } })
                  }
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
        </>
      )}
      {children.length === 0 && homeKids.length === 0 && (
        <Card accent={p.teal}>
          <H2>Is your child on Motor Movers at school?</H2>
          <Muted>
            Ask the school for a parent code. Linking your child picks the right activities and shows their progress.
          </Muted>
          <Button label="I have a code" variant="secondary" onPress={() => router.push('/link-child')} />
        </Card>
      )}
      {homeEnabled && homeKids.length === 0 && (
        <Card accent={stageInfo('GREEN')?.colour}>
          <H2>Doing Motor Movers at home?</H2>
          <Muted>
            No school needed. Add your child, do a short check, and get a few minutes of movement for most days,
            with the exercises schools use.
          </Muted>
          <Button label="Add your child" variant="secondary" onPress={() => router.push('/add-child')} />
        </Card>
      )}
    </Screen>
  )
}

function WhoFor({
  linked,
  homeKids,
  forChild,
  homeId,
  stage,
  nameFor,
  onChoose,
}: {
  linked: LinkedChild[]
  homeKids: HomeChild[]
  forChild: number | null
  homeId: number | null
  stage: ColourStage
  nameFor: (c: LinkedChild) => string
  onChoose: (c: Choice) => void
}) {
  if (linked.length + homeKids.length > 0) {
    if (linked.length + homeKids.length === 1 && (forChild || homeId)) return null
    return (
      <Row>
        {linked.map((c) => (
          <Chip
            key={`s${c.childId}`}
            label={nameFor(c)}
            dot={stageInfo(c.currentStage)?.colour}
            selected={forChild === c.childId}
            onPress={() => onChoose({ childId: c.childId })}
          />
        ))}
        {homeKids.map((k) => (
          <Chip
            key={`h${k.id}`}
            label={k.name}
            dot={stageInfo(k.stage)?.colour}
            selected={homeId === k.id}
            onPress={() => onChoose({ homeId: k.id })}
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

/** A home child who has not done the starting check yet. */
function CheckCard({ kid }: { kid: HomeChild }) {
  return (
    <Card accent={stageInfo('GREEN')?.colour}>
      <H2>Start with a short check</H2>
      <Body>
        A few questions about what {kid.name} can do now. It takes about five minutes and suggests the colour to start
        at.
      </Body>
      <Button label="Start the check" onPress={() => router.push({ pathname: '/check/[id]', params: { id: kid.id } })} />
    </Card>
  )
}

/** Today's session with Pat's exercises, and the ratings due at the end of a module. */
function HomeSessionCard({ today }: { today: HomeToday }) {
  const open = () => router.push({ pathname: '/home-session/[id]', params: { id: today.childId } })
  return (
    <>
      {today.review && (
        <Pressable
          accessibilityRole="button"
          onPress={() => router.push({ pathname: '/review/[id]', params: { id: today.childId } })}
        >
          <Banner tone="ok">
            {today.name} has finished {today.review.point === 'END' ? today.stageName : `module ${today.review.point.replace('MODULE_', '')}`}. Tap to
            rate {today.review.exercises.length} exercises.
          </Banner>
        </Pressable>
      )}
      <Card accent={stageInfo(today.stage)?.colour}>
        <Row style={{ justifyContent: 'space-between' }}>
          <StageBadge stage={today.stage} label={today.stageName} />
          <Muted>about 10 min</Muted>
        </Row>
        <Muted>
          {today.colourComplete
            ? `All ${today.modules} modules done: keep practising`
            : `Module ${today.module} of ${today.modules} · session ${today.sessionInModule} of ${today.sessionsPerModule}`}
        </Muted>
        <H1 style={{ fontSize: 22 }}>{today.doneToday ? 'Done today' : `Today's session for ${today.name}`}</H1>
        {/* Once today's is done, the exercises shown are the next session's. */}
        <Body>
          {today.doneToday ? 'Tomorrow: ' : ''}
          {today.exercises.map((e) => e.title).join(' · ')}
        </Body>
        {today.doneToday ? (
          <>
            <Banner tone="ok">Done for today. Nice work, see you tomorrow.</Banner>
            <Button label="Do it again" variant="secondary" onPress={open} />
          </>
        ) : (
          <Button label="Start" onPress={open} />
        )}
      </Card>
    </>
  )
}

function Counter({ grid, done, days }: { grid: boolean[]; done: number; days: number }) {
  return (
    <Card>
      <Row style={{ justifyContent: 'space-between' }}>
        <H2>Last {days} days</H2>
        <Muted>
          {done} of {days}
        </Muted>
      </Row>
      <CounterGrid grid={grid} />
      <Muted>
        {done >= days / 1.5
          ? 'Brilliant. Little and often is exactly right.'
          : done >= days / 3
            ? 'Good going. Most days is the aim.'
            : 'Five minutes most days makes the biggest difference.'}
      </Muted>
    </Card>
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
