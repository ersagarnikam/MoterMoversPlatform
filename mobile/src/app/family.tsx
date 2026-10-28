import { Stack } from 'expo-router'
import { useState } from 'react'
import { View } from 'react-native'
import { Banner, Body, Button, Card, ErrorState, Field, H2, Loading, Muted, Row, Screen } from '@/components/ui'
import { api, formatDate } from '@/lib/api'
import { space } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

/**
 * Who shares a family's home children: invite a second parent or carer with a code, join someone else's family, or
 * remove someone. The person who added the children can remove others; anyone can leave.
 */
export default function FamilyScreen() {
  const { data, error, reload } = useLoad(() => api.homeOverview())
  const [invite, setInvite] = useState<{ code: string; expiresOn: string; sentTo: string | null } | null>(null)
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ tone: 'ok' | 'error'; text: string } | null>(null)
  const [confirm, setConfirm] = useState<number | null>(null)

  if (error && !data) return <ErrorState message={error} onRetry={() => reload()} />
  if (!data) return <Loading />

  async function run(action: () => Promise<unknown>, ok: string) {
    setBusy(true)
    setMessage(null)
    try {
      await action()
      setMessage({ tone: 'ok', text: ok })
      setConfirm(null)
      await reload()
    } catch (e) {
      setMessage({ tone: 'error', text: e instanceof Error ? e.message : 'Something went wrong' })
    } finally {
      setBusy(false)
    }
  }

  const ready = code.replace(/[^a-z0-9]/gi, '').length === 8
  const address = email.trim()
  const emailOk = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address)

  return (
    <Screen>
      <Stack.Screen options={{ title: 'Your family' }} />
      {message && <Banner tone={message.tone}>{message.text}</Banner>}
      {data.members.length > 0 && (
        <Card>
          <H2>Sharing your children</H2>
          {data.members.map((m) => (
            <View key={m.userId} style={{ gap: space.xs }}>
              <Row style={{ justifyContent: 'space-between' }}>
                <Body>
                  {m.name}
                  {m.you ? ' (you)' : ''}
                </Body>
                <Muted>{m.owner ? 'Added the children' : 'Carer'}</Muted>
              </Row>
              {(m.you || data.owner) &&
                (confirm === m.userId ? (
                  <View style={{ gap: space.sm }}>
                    <Muted>
                      {m.you
                        ? data.members.length === 1
                          ? 'You are the only one: leaving deletes your children and everything recorded.'
                          : 'You will no longer see the children.'
                        : `${m.name} will no longer see the children.`}
                    </Muted>
                    <Button
                      label={m.you ? 'Leave' : `Remove ${m.name}`}
                      variant="danger"
                      busy={busy}
                      onPress={() => run(() => api.removeFamilyMember(m.userId), m.you ? 'You have left' : 'Removed')}
                    />
                    <Button label="Cancel" variant="secondary" onPress={() => setConfirm(null)} />
                  </View>
                ) : (
                  data.members.length > 1 && (
                    <Button
                      label={m.you ? 'Leave' : 'Remove'}
                      variant="secondary"
                      onPress={() => setConfirm(m.userId)}
                    />
                  )
                ))}
            </View>
          ))}
        </Card>
      )}

      {data.children.length > 0 && (
        <Card>
          <H2>Invite someone</H2>
          <Body>A second parent, carer or grandparent can share the sessions and see progress on their own phone.</Body>
          {invite ? (
            <>
              <Body style={{ fontSize: 28, fontWeight: '700', letterSpacing: 2 }}>{invite.code}</Body>
              <Muted>
                {invite.sentTo ? `Emailed to ${invite.sentTo}. ` : ''}Works once, until {formatDate(invite.expiresOn)}.
                They enter it under Account, Your family.
              </Muted>
              <Button label="Get another code" variant="secondary" onPress={() => setInvite(null)} />
            </>
          ) : (
            <>
              <Field
                label="Their email (optional)"
                hint="We email them the code and how to use it. Or leave it empty and pass the code on yourself."
                value={email}
                onChangeText={setEmail}
                placeholder="name@example.com"
                keyboardType="email-address"
                autoCapitalize="none"
                autoCorrect={false}
                maxLength={200}
              />
              <Button
                label={address ? 'Email a code' : 'Get a code'}
                disabled={address !== '' && !emailOk}
                busy={busy}
                onPress={() =>
                  run(
                    async () => {
                      setInvite(await api.inviteCarer(address || undefined))
                      setEmail('')
                    },
                    address ? `Code emailed to ${address}` : 'Code ready',
                  )
                }
              />
            </>
          )}
        </Card>
      )}

      <Card>
        <H2>Join a family</H2>
        <Body>Has someone given you a code to share their children? Enter it here.</Body>
        <Field
          label="Family code"
          value={code}
          onChangeText={setCode}
          placeholder="ABCD-EFGH"
          autoCapitalize="characters"
          autoCorrect={false}
          maxLength={12}
        />
        <Button
          label="Join"
          variant="secondary"
          disabled={!ready}
          busy={busy}
          onPress={() => run(() => api.joinFamily(code), 'You have joined')}
        />
      </Card>
    </Screen>
  )
}
