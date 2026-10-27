import { router } from 'expo-router'
import { Pressable } from 'react-native'
import { Body, Button, Card, ErrorState, H2, Loading, Muted, Row, Screen, StageBadge } from '@/components/ui'
import { api } from '@/lib/api'
import { ageLabel } from '@/lib/ages'
import { useNicknames } from '@/lib/nicknames'
import { stageInfo } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

export default function ProgressScreen() {
  const { data, error, refreshing, reload } = useLoad(async () => ({
    children: await api.children(),
    // Motor Movers at Home may be switched off, or the call may fail: then only school children show.
    home: await api.homeOverview().catch(() => null),
  }))
  const { nameFor } = useNicknames()

  if (error && !data) return <ErrorState message={error} onRetry={() => reload()} />
  if (!data) return <Loading />
  const { children, home } = data
  const homeKids = home?.enabled ? home.children : []

  return (
    <Screen refreshing={refreshing} onRefresh={() => reload(true)}>
      {homeKids.map((k) => (
        <Pressable
          key={`h${k.id}`}
          accessibilityRole="button"
          onPress={() => router.push({ pathname: '/home-child/[id]', params: { id: k.id } })}
        >
          <Card accent={stageInfo(k.stage)?.colour}>
            <Row style={{ justifyContent: 'space-between' }}>
              <H2>{k.name}</H2>
              <Muted>{ageLabel(k.ageMonths)}</Muted>
            </Row>
            <Muted>At home{k.checkDue ? ' · check due' : ''}</Muted>
            {k.stage ? <StageBadge stage={k.stage} label={k.stageName} /> : <Muted>Starting check to do</Muted>}
          </Card>
        </Pressable>
      ))}
      {children.length === 0 && homeKids.length > 0 ? null : children.length === 0 ? (
        <Card>
          <H2>No children linked yet</H2>
          <Body>
            When your child does Motor Movers at school, the school can give you a parent code. Enter it here to see
            their stage and how they are getting on.
          </Body>
          <Muted>Nothing about your child is shared with this app until the school gives you a code.</Muted>
        </Card>
      ) : (
        children.map((c) => (
          <Pressable
            key={c.childId}
            accessibilityRole="button"
            onPress={() => router.push({ pathname: '/child/[id]', params: { id: c.childId } })}
          >
            <Card accent={stageInfo(c.currentStage)?.colour}>
              <Row style={{ justifyContent: 'space-between' }}>
                <H2>{nameFor(c)}</H2>
                <Muted>{c.yearGroup}</Muted>
              </Row>
              <Muted>
                {c.schoolName} · {c.className}
              </Muted>
              <StageBadge stage={c.currentStage} label={c.currentStageName} />
              {c.movedUp && <Muted>Moved up since starting at {c.startingStageName}</Muted>}
            </Card>
          </Pressable>
        ))
      )}
      <Button label="Link a child with a code" variant="secondary" onPress={() => router.push('/link-child')} />
      {home?.enabled && homeKids.length < home.maxChildren && (
        <Button label="Add a child at home" variant="secondary" onPress={() => router.push('/add-child')} />
      )}
    </Screen>
  )
}
