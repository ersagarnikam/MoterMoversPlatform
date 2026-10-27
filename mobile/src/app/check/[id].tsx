import { router, Stack, useLocalSearchParams } from 'expo-router'
import { useEffect, useState } from 'react'
import { View } from 'react-native'
import {
  Banner,
  Body,
  Button,
  Card,
  Chip,
  ErrorState,
  H1,
  H2,
  Loading,
  Muted,
  NotADiagnosis,
  Row,
  Screen,
  StageBadge,
} from '@/components/ui'
import { api, type CheckAnswer, type CheckResult, type ColourStage } from '@/lib/api'
import { ageLabel, CHECK_ANSWERS } from '@/lib/ages'
import { space, STAGES, stageInfo } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

/**
 * The starting check: movements from Pat's charts that children usually manage around this age. The parent answers
 * Yes, Not yet or Not sure for each, and the check suggests a colour to start at. Done again every few months.
 */
export default function CheckScreen() {
  const { id } = useLocalSearchParams<{ id: string }>()
  const childId = Number(id)
  const { data: check, error, reload } = useLoad(() => api.homeCheck(childId), [childId])
  const [answers, setAnswers] = useState<Record<number, CheckAnswer>>({})
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState<string | null>(null)
  const [result, setResult] = useState<CheckResult | null>(null)

  // Movements already secure start as Yes.
  useEffect(() => {
    if (!check) return
    const start: Record<number, CheckAnswer> = {}
    check.questions.filter((q) => q.secure).forEach((q) => (start[q.milestoneId] = 'YES'))
    setAnswers(start)
  }, [check])

  if (error && !check) return <ErrorState message={error} onRetry={() => reload()} />
  if (!check) return <Loading />

  if (result) return <Result childId={childId} name={check.name} result={result} />

  const answered = Object.keys(answers).length
  const charts = [...new Set(check.questions.map((q) => q.chartLabel))]

  async function submit() {
    setBusy(true)
    setFailed(null)
    try {
      setResult(
        await api.submitHomeCheck(
          childId,
          Object.entries(answers).map(([milestoneId, answer]) => ({ milestoneId: Number(milestoneId), answer })),
        ),
      )
    } catch (e) {
      setFailed(e instanceof Error ? e.message : 'Could not save')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: `${check.name}'s check` }} />
      <View style={{ gap: space.sm }}>
        <H1>Can {check.name} do these?</H1>
        <Body>
          These are movements children often manage by {ageLabel(check.ageMonths)}. Answer from what you have seen.
          If you are not sure, try it together, or choose Not sure.
        </Body>
      </View>
      <NotADiagnosis text={check.banner} />
      {charts.map((chart) => (
        <View key={chart} style={{ gap: space.sm }}>
          <H2>{chart === 'Fine motor' ? 'Hands and fingers (fine motor)' : 'Whole body (gross motor)'}</H2>
          {check.questions
            .filter((q) => q.chartLabel === chart)
            .map((q) => (
              <Card key={q.milestoneId}>
                <Body>{q.description}</Body>
                <Muted>Usually by {q.band}</Muted>
                <Row>
                  {CHECK_ANSWERS.map((a) => (
                    <Chip
                      key={a.value}
                      label={a.label}
                      selected={answers[q.milestoneId] === a.value}
                      onPress={() => setAnswers((all) => ({ ...all, [q.milestoneId]: a.value }))}
                    />
                  ))}
                </Row>
              </Card>
            ))}
        </View>
      ))}
      {failed && <Banner tone="error">{failed}</Banner>}
      <Muted>
        {answered} of {check.questions.length} answered. You can leave some out.
      </Muted>
      <Button label="See where to start" onPress={submit} busy={busy} disabled={answered === 0} />
    </Screen>
  )
}

/** The suggested colour, with the choice to start there or pick another. */
function Result({ childId, name, result }: { childId: number; name: string; result: CheckResult }) {
  const [stage, setStage] = useState<ColourStage>(result.suggested)
  const [choosing, setChoosing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function start() {
    setBusy(true)
    try {
      await api.setHomeStage(childId, stage)
      router.dismissTo('/')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save')
      setBusy(false)
    }
  }

  return (
    <Screen>
      <Stack.Screen options={{ title: 'Where to start' }} />
      <Card accent={stageInfo(result.suggested)?.colour}>
        <Muted>Suggested for {name}</Muted>
        <StageBadge stage={result.suggested} label={result.suggestedName} />
        <Body>{result.explanation}</Body>
        <Muted>
          {result.yes} yes · {result.notYet} not yet · {result.notSure} not sure
        </Muted>
      </Card>
      <Body>
        Each colour builds on the one before, and there is no rush. Every few months we will suggest doing the check
        again.
      </Body>
      {choosing && (
        <View style={{ gap: space.sm }}>
          <Muted>Choose a colour</Muted>
          <Row>
            {STAGES.map((s) => (
              <Chip key={s.key} label={s.label} dot={s.colour} selected={stage === s.key} onPress={() => setStage(s.key)} />
            ))}
          </Row>
        </View>
      )}
      {error && <Banner tone="error">{error}</Banner>}
      <Button label={`Start with ${stageInfo(stage)?.label ?? 'this colour'}`} onPress={start} busy={busy} />
      {!choosing && <Button label="Choose another colour" variant="secondary" onPress={() => setChoosing(true)} />}
    </Screen>
  )
}
