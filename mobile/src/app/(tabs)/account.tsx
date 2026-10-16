import Constants from 'expo-constants'
import { Body, Button, Card, H2, Muted, Screen, StageStripe } from '@/components/ui'
import { API_URL } from '@/lib/api'
import { useAuth } from '@/lib/auth'

export default function AccountScreen() {
  const { user, signOut } = useAuth()
  return (
    <Screen>
      <Card>
        <H2>{user?.displayName}</H2>
        <Muted>{user?.email}</Muted>
      </Card>

      <Card>
        <H2>Your information</H2>
        <Body>
          Your child's school decides whether you can see their progress, and can remove the link at any time. The
          school can see that you are linked and each time you open your child's progress.
        </Body>
        <Body>The home activities you record are yours. They are not shared with the school.</Body>
        <Body>
          Motor Movers describes where a child is in the programme. It is not a diagnosis or a clinical test. If you
          are worried about your child, talk to their school or your GP.
        </Body>
      </Card>

      <Button label="Sign out" variant="secondary" onPress={() => void signOut()} />

      <StageStripe height={4} />
      <Muted>
        Motor Movers {Constants.expoConfig?.version}
        {__DEV__ ? ` · ${API_URL}` : ''}
      </Muted>
    </Screen>
  )
}
