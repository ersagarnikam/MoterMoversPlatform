import { router } from 'expo-router'
import { useEffect, useState } from 'react'
import { Linking, Pressable, View } from 'react-native'
import { Banner, Body, Button, Card, Chip, CounterGrid, ErrorState, H1, H2, Loading, Muted, Row, Screen, StageBadge } from '@/components/ui'
import { api, money, type ColourStage, type Home, type HomeChild, type HomeToday, type LinkedChild } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { flushHome, homeRatings, homeSessions, unsentWords } from '@/lib/homeOutbox'
import { useNicknames } from '@/lib/nicknames'
import { flush, pending } from '@/lib/outbox'
import { getLocal, setLocal } from '@/lib/storage'
import { space, STAGES, stageInfo, usePalette } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

const STAGE_KEY = 'mm.home.stage'

/** "Please contact us at … or … to buy the pass", leaving out details not yet confirmed (task 82). */
function contactUs(p: { contactEmail: string | null; contactPhone: string | null }): string {
  const ways = [p.contactEmail, p.contactPhone].filter((v): v is string => !!v)
  return `Please contact us${ways.length ? ` at ${ways.join(' or ')}` : ''} to buy the pass.`
}

/**
 * Who today is for: a child linked through their school (their stage), a family's own child on Motor Movers at Home
 * (homeId), or a stage the parent picked.
 */
type Choice = { childId: number } | { homeId: number } | { stage: ColourStage } | null

/** What is still on this phone: home activities and home sessions are both sessions. */
async function countUnsent() {
  const [activities, sessions, ratings] = await Promise.all([pending(), homeSessions.pending(), homeRatings.pending()])
  return { sessions: activities.length + sessions.length, ratings: ratings.length }
}

export default function TodayScreen() {
  const { user } = useAuth()
  const p = usePalette()
  const { nameFor } = useNicknames()
  const [choice, setChoice] = useState<Choice | undefined>(undefined)
  const [unsent, setUnsent] = useState({ sessions: 0, ratings: 0 })

  useEffect(() => {
    getLocal<Choice>(STAGE_KEY, null).then(setChoice)
  }, [])

  const { data, error, refreshing, reload } = useLoad(async () => {
    if (choice === undefined) return null
    await flush()
    await flushHome()
    setUnsent(await countUnsent())
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
    // The check needs what sessions need (task 82): offer it only when the API would take it.
    const pass = kid ? await api.homePass().catch(() => null) : null
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
      pass,
      home,
      due,
      liveNow,
      forChild: kid ? null : ('childId' in params ? params.childId : null) ?? null,
    }
  }, [choice])

  useEffect(() => {
    void countUnsent().then(setUnsent)
  }, [data])

  function choose(next: Choice) {
    setChoice(next)
    void setLocal(STAGE_KEY, next)
  }

  if (error && !data) return <ErrorState message={error} onRetry={() => reload()} />
  if (!data) return <Loading />
  const { children, homeEnabled, homeKids, kid, homeToday, pass, home, due, liveNow, forChild } = data
  const checkOpen = !!pass && pass.running && pass.inductionDone
  // Pat's exercises for this home child's colour; otherwise the colour's home activities, as for everyone else.
  const exercisesToday = kid && homeToday?.hasExercises ? homeToday : null

  return (
    <Screen refreshing={refreshing} onRefresh={() => reload(true)}>
      <View style={{ gap: 2 }}>
        <Muted>{greeting()}</Muted>
        <H1>{user?.displayName ? `Hello, ${user.displayName.split(' ')[0]}` : 'Hello'}</H1>
      </View>
      {unsent.sessions + unsent.ratings > 0 && (
        <Banner>{unsentWords(unsent.sessions, unsent.ratings)} saved on this phone and will be sent when you are back online.</Banner>
      )}
      {liveNow && (
        <Pressable accessibilityRole="button" onPress={() => router.push('/course')}>
          <Banner tone="ok">Your live session is open to join. Tap to join it in Zoom.</Banner>
        </Pressable>
      )}
      {due > 0 && (
        <Pressable accessibilityRole="button" onPress={() => router.push('/payments')}>
          <Banner>You have {money(due)} to pay. Tap to see what it is for and how to pay.</Banner>
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
        checkOpen ? (
          <CheckCard kid={kid} />
        ) : pass && !pass.running ? (
          <PassCard ended={pass.ended} />
        ) : (
          <InductionCard />
        )
      ) : exercisesToday ? (
        <>
          {kid?.checkDue && checkOpen && (
            <Pressable
              accessibilityRole="button"
              onPress={() => router.push({ pathname: '/check/[id]', params: { id: kid.id } })}
            >
              <Banner tone="ok">A new part of the school year: time to do {kid.name}'s check again. Tap to start.</Banner>
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

/**
 * The family's 12-month pass, needed for sessions (go-live task 26): Buy, or Renew once it has ended, on Stripe's own
 * page in the browser. The pass runs once Stripe confirms the payment; pull down to refresh after paying.
 */
function PassCard({ ended }: { ended: boolean }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const pass = useLoad(() => api.homePass(), [])
  const p = pass.data
  async function buy() {
    setBusy(true)
    setError(null)
    try {
      await Linking.openURL((await api.homePassCheckout()).url)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Card payments are not available just now')
    } finally {
      setBusy(false)
    }
  }
  const price = p?.pricePence != null ? money(p.pricePence) : null
  return (
    <Card>
      <H2>{ended ? 'Your pass has ended' : 'The 12-month pass'}</H2>
      <Body>
        {!p
          ? 'Sessions need your family’s pass.'
          : !p.onSale || !price
            ? 'The Motor Movers at Home pass is not on sale yet. Your children’s details are safe here until it is.'
            : ended
              ? `Your children’s records are kept. Renew for ${price} to carry on with the sessions.`
              : `Sessions need a 12-month pass for your family: ${price}, for all your children and anyone you share with.`}
      </Body>
      {p?.onSale && price && p.cardPayments && (
        <Button label={busy ? 'Opening…' : ended ? `Renew for ${price}` : `Buy the pass for ${price}`} onPress={buy} disabled={busy} />
      )}
      {p?.onSale && !p.cardPayments && <Muted>Paying by card is not switched on yet. {contactUs(p)}</Muted>}
      {error && <Banner tone="warn">{error}</Banner>}
    </Card>
  )
}

/** Before the first session or the check: the parent induction. */
function InductionCard() {
  return (
    <Card>
      <H2>Before your first session</H2>
      <Body>There is a short induction to read first. Sessions and the check open once you have finished it.</Body>
      <Button label="Start the induction" onPress={() => router.push('/home-induction')} />
    </Card>
  )
}

/** Today's session with Pat's exercises, and the ratings due at the end of a module. */
function HomeSessionCard({ today }: { today: HomeToday }) {
  const open = () => router.push({ pathname: '/home-session/[id]', params: { id: today.childId } })
  if (!today.passRunning) {
    return <PassCard ended={today.passEndsOn != null} />
  }
  if (!today.inductionDone) {
    return <InductionCard />
  }
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
          {today.progressLabel}
        </Muted>
        <H1 style={{ fontSize: 22 }}>{today.doneToday ? 'Done today' : `Today's session for ${today.name}`}</H1>
        {/* Once today's is done, the exercises shown are the next session's. */}
        <Body>
          {today.doneToday ? 'Tomorrow: ' : ''}
          {today.exercises.map((e) => e.title).join(' · ')}
        </Body>
        {!today.doneToday && today.newThisWeek.length > 0 && (
          <Muted>
            New this week: {today.exercises.filter((e) => today.newThisWeek.includes(e.id)).map((e) => e.title).join(', ')}
          </Muted>
        )}
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
