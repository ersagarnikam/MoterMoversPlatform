import { router } from 'expo-router'
import { Pressable } from 'react-native'
import { Body, Button, Card, ErrorState, H2, Loading, Muted, Row, Screen, StageBadge } from '@/components/ui'
import { api } from '@/lib/api'
import { useNicknames } from '@/lib/nicknames'
import { stageInfo } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

export default function ProgressScreen() {
  const { data: children, error, refreshing, reload } = useLoad(() => api.children())
  const { nameFor } = useNicknames()

  if (error && !children) return <ErrorState message={error} onRetry={() => reload()} />
  if (!children) return <Loading />

  return (
    <Screen refreshing={refreshing} onRefresh={() => reload(true)}>
      {children.length === 0 ? (
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
    </Screen>
  )
}
