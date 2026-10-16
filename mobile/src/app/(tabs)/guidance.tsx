import { router } from 'expo-router'
import { Pressable } from 'react-native'
import { Body, Card, ErrorState, H2, Loading, Muted, Row, Screen, StageBadge } from '@/components/ui'
import { api } from '@/lib/api'
import { useLoad } from '@/lib/useLoad'

/** Short reads approved by the Motor Movers team. */
export default function GuidanceScreen() {
  const { data: articles, error, refreshing, reload } = useLoad(() => api.guidance())

  if (error && !articles) return <ErrorState message={error} onRetry={() => reload()} />
  if (!articles) return <Loading />

  return (
    <Screen refreshing={refreshing} onRefresh={() => reload(true)}>
      {articles.length === 0 && <Muted>New guidance is on its way.</Muted>}
      {articles.map((a) => (
        <Pressable
          key={a.id}
          accessibilityRole="button"
          onPress={() => router.push({ pathname: '/guidance/[id]', params: { id: a.id } })}
        >
          <Card>
            <H2>{a.title}</H2>
            <Body>{a.summary}</Body>
            <Row style={{ justifyContent: 'space-between' }}>
              {a.stage ? <StageBadge stage={a.stage} label={a.stageName} /> : <Muted>All stages</Muted>}
              <Muted>{a.minutesToRead} min read</Muted>
            </Row>
          </Card>
        </Pressable>
      ))}
    </Screen>
  )
}
