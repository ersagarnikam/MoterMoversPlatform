import { router, Stack, useLocalSearchParams } from 'expo-router'
import { useEffect, useState } from 'react'
import { Text, View } from 'react-native'
import { ExerciseList, MovementCards } from '@/components/AssessmentSummary'
import {
  Banner,
  Body,
  Button,
  Card,
  ErrorState,
  Field,
  H1,
  H2,
  Loading,
  Muted,
  NotADiagnosis,
  Row,
  Screen,
  StageBadge,
} from '@/components/ui'
import { api, formatDate } from '@/lib/api'
import { useNicknames } from '@/lib/nicknames'
import { radius, space, STAGES, stageInfo, usePalette } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

/** A child's progress in programme terms: stage, the six areas as bands, and how often the class moves. */
export default function ChildScreen() {
  const { id } = useLocalSearchParams<{ id: string }>()
  const childId = Number(id)
  const p = usePalette()
  const { data, error, refreshing, reload } = useLoad(() => api.progress(childId), [childId])
  const { names, nameFor, setNickname } = useNicknames()
  const [nickname, setNicknameDraft] = useState('')
  const [confirmRemove, setConfirmRemove] = useState(false)
  const [removing, setRemoving] = useState(false)
  const [removeError, setRemoveError] = useState<string | null>(null)

  useEffect(() => {
    setNicknameDraft(names[childId] ?? '')
  }, [names, childId])

  if (error && !data) return <ErrorState message={error} onRetry={() => reload()} />
  if (!data) return <Loading />
  const { child } = data
  const name = nameFor(child)

  async function remove() {
    setRemoving(true)
    setRemoveError(null)
    try {
      await api.unlinkChild(childId)
      await setNickname(childId, '')
      router.back()
    } catch (e) {
      setRemoveError(e instanceof Error ? e.message : 'Could not remove')
      setRemoving(false)
    }
  }

  return (
    <Screen refreshing={refreshing} onRefresh={() => reload(true)}>
      <Stack.Screen options={{ title: name }} />
      <View style={{ gap: 2 }}>
        <H1>{name}</H1>
        <Muted>
          {child.schoolName} · {child.className}
        </Muted>
      </View>

      <NotADiagnosis text={data.banner} />

      <Card accent={stageInfo(child.currentStage)?.colour}>
        <Muted>Working at</Muted>
        <StageBadge stage={child.currentStage} label={child.currentStageName} />
        <StageLadder current={child.currentStage} start={child.startingStage} />
        {child.movedUp ? (
          <Body>Moved up since starting at {child.startingStageName}.</Body>
        ) : child.startingStageName ? (
          <Muted>Started at {child.startingStageName}.</Muted>
        ) : null}
      </Card>

      <H2>Movements</H2>
      {data.charts.some((c) => c.secure > 0) ? (
        <>
          <Muted>Movements from the programme's charts that the school has seen {name} do securely.</Muted>
          <MovementCards charts={data.charts} />
        </>
      ) : (
        <Muted>The school has not recorded these for {name} yet.</Muted>
      )}
      <ExerciseList
        stageName={data.exerciseStageName}
        exercises={data.exercises}
        note={`How ${name} is getting on with each exercise at school, from the first time to the latest.`}
      />

      <Card>
        <H2>Six areas</H2>
        {!data.screened ? (
          <Muted>The school has not done the movement check with {name} yet.</Muted>
        ) : (
          <>
            <Muted>From the latest movement check at school.</Muted>
            {data.areas.map((a) => {
              const c = p.band[a.band]
              return (
                <Row key={a.area} style={{ justifyContent: 'space-between', paddingVertical: 4 }}>
                  <Body style={{ flexShrink: 1 }}>{a.label}</Body>
                  <Text
                    style={{
                      backgroundColor: c.bg,
                      color: c.ink,
                      borderRadius: 999,
                      overflow: 'hidden',
                      paddingHorizontal: 10,
                      paddingVertical: 3,
                      fontSize: 13,
                      fontWeight: '600',
                    }}
                  >
                    {a.bandLabel}
                  </Text>
                </Row>
              )
            })}
            <Muted>
              Emerging, Developing and Secure describe where {name} is in the programme. Every child moves through
              them.
            </Muted>
            {data.screenings.length > 0 && (
              <Muted>Movement checks: {data.screenings.map(formatDate).join(', ')}</Muted>
            )}
          </>
        )}
      </Card>

      <Card>
        <H2>At school</H2>
        <Body>
          {child.className} did the daily session on {data.classSessionsDone} of the last {data.counterDays} school
          days.
        </Body>
      </Card>

      <Card>
        <Field
          label="What do you call them?"
          hint="Shown in this app instead of initials. Kept on this phone only; never sent to Motor Movers or the school."
          value={nickname}
          onChangeText={setNicknameDraft}
          placeholder={child.name}
          autoCapitalize="words"
          returnKeyType="done"
          onSubmitEditing={() => setNickname(childId, nickname)}
        />
        <Button label="Save name" variant="secondary" onPress={() => setNickname(childId, nickname)} />
      </Card>

      {removeError && <Banner tone="error">{removeError}</Banner>}
      {confirmRemove ? (
        <Card>
          <Body>Remove {name} from this app? You will need a new code from the school to add them again.</Body>
          <Button label="Yes, remove" variant="danger" onPress={remove} busy={removing} />
          <Button label="Cancel" variant="secondary" onPress={() => setConfirmRemove(false)} />
        </Card>
      ) : (
        <Button label="Remove from my app" variant="danger" onPress={() => setConfirmRemove(true)} />
      )}
      <Muted>The school can see that you are linked and when you open this page.</Muted>
    </Screen>
  )
}

/** The seven stages in a row, with the current one marked. */
function StageLadder({ current, start }: { current: string | null; start: string | null }) {
  const p = usePalette()
  return (
    <View style={{ flexDirection: 'row', gap: 4, marginVertical: space.xs }}>
      {STAGES.map((s) => {
        const here = s.key === current
        return (
          <View key={s.key} style={{ flex: 1, alignItems: 'center', gap: 4 }}>
            <View
              style={{
                height: here ? 14 : 8,
                alignSelf: 'stretch',
                borderRadius: radius / 2,
                backgroundColor: s.colour,
                opacity: here || s.key === start ? 1 : 0.35,
              }}
            />
            {here && <Text style={{ fontSize: 11, color: p.ink, fontWeight: '700' }}>now</Text>}
          </View>
        )
      })}
    </View>
  )
}
