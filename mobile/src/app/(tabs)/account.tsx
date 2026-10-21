import Constants from 'expo-constants'
import { router } from 'expo-router'
import { Body, Button, Card, H2, Muted, Screen, StageStripe } from '@/components/ui'
import { API_URL, api, gbp } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { useLoad } from '@/lib/useLoad'

export default function AccountScreen() {
  const { user, signOut } = useAuth()
  const { data: payments, refreshing, reload } = useLoad(() => api.myPayments())
  return (
    <Screen refreshing={refreshing} onRefresh={() => reload(true)}>
      <Card>
        <H2>{user?.displayName}</H2>
        <Muted>{user?.email}</Muted>
      </Card>

      <Card>
        <H2>Payments</H2>
        {payments ? (
          <Body>
            {gbp(payments.paidPence)} paid
            {payments.outstandingPence > 0 ? ` · ${gbp(payments.outstandingPence)} to pay` : ''}
          </Body>
        ) : (
          <Muted>Your payments for courses and resources.</Muted>
        )}
        <Button label="Payment details and history" variant="secondary" onPress={() => router.push('/payments')} />
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
