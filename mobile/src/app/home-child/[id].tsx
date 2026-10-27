import { router, Stack, useLocalSearchParams } from 'expo-router'
import { useState } from 'react'
import { View } from 'react-native'
import {
  Banner,
  Body,
  Button,
  Card,
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
import { api } from '@/lib/api'
import { ageLabel } from '@/lib/ages'
import { space, stageInfo } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

/**
 * A home child's progress in the parent's words: movements now secure on Pat's charts, and each exercise's first and
 * latest rating. Never a score against other children. Also: the check again, colour, name and age, linking a
 * school, and deleting the child.
 */
export default function HomeChildScreen() {
  const { id } = useLocalSearchParams<{ id: string }>()
  const childId = Number(id)
  const { data, error, refreshing, reload } = useLoad(() => api.homeProgress(childId), [childId])
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState<string | null>(null)

  if (error && !data) return <ErrorState message={error} onRetry={() => reload()} />
  if (!data) return <Loading />
  const { child } = data

  async function remove() {
    setBusy(true)
    try {
      await api.deleteHomeChild(childId)
      router.back()
    } catch (e) {
      setFailed(e instanceof Error ? e.message : 'Could not delete')
      setBusy(false)
    }
  }

  return (
    <Screen refreshing={refreshing} onRefresh={() => reload(true)}>
      <Stack.Screen options={{ title: child.name }} />
      <Card accent={stageInfo(child.stage)?.colour}>
        <H1>{child.name}</H1>
        <Muted>{ageLabel(child.ageMonths)} · at home</Muted>
        <StageBadge stage={child.stage} label={child.stageName} />
        <Muted>{data.sessionsDone === 1 ? '1 session done' : `${data.sessionsDone} sessions done`}</Muted>
      </Card>
      <NotADiagnosis text={data.banner} />
      {data.support.show && data.support.message && <Banner>{data.support.message}</Banner>}

      <H2>Movements</H2>
      {data.charts.map((c) => (
        <Card key={c.chart}>
          <Row style={{ justifyContent: 'space-between' }}>
            <Body style={{ fontWeight: '600' }}>{c.chart === 'FINE' ? 'Hands and fingers' : 'Whole body'}</Body>
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
      <Button
        label={child.checkDue ? 'Time for the check again' : 'Do the check again'}
        variant={child.checkDue ? 'primary' : 'secondary'}
        onPress={() => router.push({ pathname: '/check/[id]', params: { id: childId } })}
      />

      {data.exercises.length > 0 && (
        <>
          <H2>Exercises{data.stageName ? ` · ${data.stageName}` : ''}</H2>
          <Card>
            {data.exercises.map((e) => (
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
      )}

      <H2>Settings</H2>
      <Button
        label="Change name or age"
        variant="secondary"
        onPress={() => router.push({ pathname: '/add-child', params: { id: childId } })}
      />
      <Button
        label={`${child.name}'s school has joined Motor Movers`}
        variant="secondary"
        onPress={() => router.push({ pathname: '/join-school/[id]', params: { id: childId } })}
      />
      {failed && <Banner tone="error">{failed}</Banner>}
      {confirmDelete ? (
        <Card>
          <Body>
            Delete {child.name} and everything recorded? This cannot be undone, and anyone you share with loses it too.
          </Body>
          <View style={{ gap: space.sm }}>
            <Button label={`Delete ${child.name}`} variant="danger" onPress={remove} busy={busy} />
            <Button label="Keep" variant="secondary" onPress={() => setConfirmDelete(false)} />
          </View>
        </Card>
      ) : (
        <Button label={`Delete ${child.name}`} variant="danger" onPress={() => setConfirmDelete(true)} />
      )}
    </Screen>
  )
}
