import { Stack, useLocalSearchParams } from 'expo-router'
import { View } from 'react-native'
import { Body, ErrorState, H1, Loading, Muted, Screen, StageBadge } from '@/components/ui'
import { api } from '@/lib/api'
import { space } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

export default function ArticleScreen() {
  const { id } = useLocalSearchParams<{ id: string }>()
  const { data: article, error, reload } = useLoad(() => api.article(Number(id)), [id])

  if (error && !article) return <ErrorState message={error} onRetry={() => reload()} />
  if (!article) return <Loading />

  return (
    <Screen>
      <Stack.Screen options={{ title: 'Guidance' }} />
      <View style={{ gap: space.sm }}>
        <H1>{article.title}</H1>
        <Muted>
          {article.stageName ?? 'All stages'} · {article.minutesToRead} min read
        </Muted>
        {article.stage && <StageBadge stage={article.stage} label={article.stageName} />}
      </View>
      <Body style={{ fontSize: 18, lineHeight: 26, fontWeight: '500' }}>{article.summary}</Body>
      {article.paragraphs.map((text, i) => (
        <Body key={i} style={{ fontSize: 17, lineHeight: 26 }}>
          {text}
        </Body>
      ))}
    </Screen>
  )
}
