import Constants from 'expo-constants'
import { router } from 'expo-router'
import { useState } from 'react'
import { Banner, Body, Button, Card, H2, Muted, Screen, StageStripe } from '@/components/ui'
import { api, apiUrl, money } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { useChosenCountry } from '@/lib/country'
import { flushHome } from '@/lib/homeOutbox'
import { flush } from '@/lib/outbox'
import { useLoad } from '@/lib/useLoad'

export default function AccountScreen() {
  const { user, signOut } = useAuth()
  const { data: payments, refreshing, reload } = useLoad(() => api.myPayments())
  const { data: home } = useLoad(() => api.homeOverview().catch(() => null))
  const { country, forgetCountry } = useChosenCountry()
  const [confirming, setConfirming] = useState(false)
  const [changing, setChanging] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  /** Nothing unsent may be left behind: it belongs on this country's server. Signing out also drops the push token. */
  async function changeCountry() {
    setChanging(true)
    setRefusal(null)
    const waiting = (await flushHome()) + (await flush())
    if (waiting > 0) {
      setRefusal(`Waiting to send ${waiting} ${waiting === 1 ? 'item' : 'items'}. Connect to the internet first.`)
      setChanging(false)
      return
    }
    await signOut()
    await forgetCountry()
  }

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
            {money(payments.paidPence)} paid
            {payments.outstandingPence > 0 ? ` · ${money(payments.outstandingPence)} to pay` : ''}
          </Body>
        ) : (
          <Muted>Your payments for courses and resources.</Muted>
        )}
        <Button label="Payment details and history" variant="secondary" onPress={() => router.push('/payments')} />
      </Card>

      {home?.enabled && (
        <Card>
          <H2>Your family</H2>
          <Body>
            {home.members.length > 1
              ? `Shared with ${home.members.filter((m) => !m.you).map((m) => m.name).join(', ')}.`
              : 'Invite a second parent or carer, or join a family with their code.'}
          </Body>
          <Button label="Family and sharing" variant="secondary" onPress={() => router.push('/family')} />
        </Card>
      )}

      <Card>
        <H2>Your information</H2>
        <Body>
          Your child's school decides whether you can see their progress, and can remove the link at any time. The
          school can see that you are linked and each time you open your child's progress.
        </Body>
        <Body>The home activities you record are yours. They are not shared with the school.</Body>
        <Body>
          Children you add yourself (Motor Movers at home) are seen only by you and anyone you invite. A school sees
          them only if you link the school. Deleting a child deletes everything recorded.
        </Body>
        <Body>
          Motor Movers describes where a child is in the programme. It is not a diagnosis or a clinical test. If you
          are worried about your child, talk to their school or your GP.
        </Body>
      </Card>

      <Card>
        <H2>Country</H2>
        <Body>{country.name}</Body>
        {refusal && <Banner>{refusal}</Banner>}
        {confirming ? (
          <>
            <Muted>You will be signed out, then you can choose your country and sign in there.</Muted>
            <Button label="Change country" variant="danger" busy={changing} onPress={() => void changeCountry()} />
            <Button label="Cancel" variant="secondary" onPress={() => setConfirming(false)} />
          </>
        ) : (
          <Button label="Change country" variant="secondary" onPress={() => setConfirming(true)} />
        )}
      </Card>

      <Button label="Sign out" variant="secondary" onPress={() => void signOut()} />

      <StageStripe height={4} />
      <Muted>
        Motor Movers {Constants.expoConfig?.version}
        {__DEV__ ? ` · ${apiUrl()}` : ''}
      </Muted>
    </Screen>
  )
}
