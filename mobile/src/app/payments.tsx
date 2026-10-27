import { useState } from 'react'
import { Linking, View } from 'react-native'
import { Banner, Body, Button, Card, ErrorState, H2, Loading, Muted, Row, Screen } from '@/components/ui'
import { api, formatDate, gbp, type Payment } from '@/lib/api'
import { CONTACT_EMAIL, CONTACT_PHONE } from '@/lib/contact'
import { space, usePalette } from '@/lib/theme'
import { useLoad } from '@/lib/useLoad'

const STATUS = { DUE: 'Due', PAID: 'Paid', CANCELLED: 'Cancelled' }

/**
 * The parent's own payments: what is due now and how to pay it (by card on the payment provider's page when that is
 * switched on), then everything so far.
 */
export default function PaymentsScreen() {
  const p = usePalette()
  const { data, error, refreshing, reload } = useLoad(async () => {
    const [mine, online] = await Promise.all([api.myPayments(), api.onlinePayments().catch(() => ({ enabled: false }))])
    return { ...mine, cards: online.enabled }
  })
  const [paying, setPaying] = useState<number | null>(null)
  const [payError, setPayError] = useState<string | null>(null)

  async function pay(payment: Payment) {
    setPaying(payment.id)
    setPayError(null)
    try {
      // The card is entered on the provider's own page in the browser; pull down here afterwards to see it paid.
      await Linking.openURL((await api.checkout(payment.id)).url)
    } catch (e) {
      setPayError(e instanceof Error ? e.message : 'Card payments are not available just now')
    } finally {
      setPaying(null)
    }
  }

  if (error && !data) return <ErrorState message={error} onRetry={() => reload()} />
  if (!data) return <Loading />
  const due = data.payments.filter((x) => x.status === 'DUE')

  return (
    <Screen refreshing={refreshing} onRefresh={() => reload(true)}>
      <Row>
        <Card style={{ flex: 1, minWidth: 140 }}>
          <Muted>Paid in total</Muted>
          <H2>{gbp(data.paidPence)}</H2>
        </Card>
        <Card style={{ flex: 1, minWidth: 140 }} accent={data.outstandingPence > 0 ? p.warnInk : undefined}>
          <Muted>Due now</Muted>
          <H2>{gbp(data.outstandingPence)}</H2>
        </Card>
      </Row>

      {due.length > 0 && (
        <Card accent={p.warnInk}>
          <H2>To pay</H2>
          {due.map((x) => (
            <View key={x.id} style={{ gap: space.sm }}>
              <PaymentRow payment={x} />
              {data.cards && <Button label="Pay by card" busy={paying === x.id} onPress={() => pay(x)} />}
            </View>
          ))}
          {payError && <Banner tone="error">{payError}</Banner>}
          <Muted>
            {data.cards ? 'Or pay' : 'Pay'} as set out on your invoice, quoting the reference so the Motor Movers team can match your payment. It
            shows here as paid once they have received it.
          </Muted>
          <Button
            label="Email about a payment"
            variant="secondary"
            onPress={() => Linking.openURL(`mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent('A Motor Movers payment')}`)}
          />
          <Muted>Or call {CONTACT_PHONE}.</Muted>
        </Card>
      )}

      <Card>
        <H2>Payment history</H2>
        {data.payments.length === 0 ? (
          <Body>No payments yet. When you book a course, its invoice and payment show here.</Body>
        ) : (
          data.payments.map((x) => <PaymentRow key={x.id} payment={x} showStatus />)
        )}
      </Card>
    </Screen>
  )
}

function PaymentRow({ payment, showStatus = false }: { payment: Payment; showStatus?: boolean }) {
  const p = usePalette()
  const status =
    payment.status === 'PAID' && payment.paidOn
      ? `Paid ${formatDate(payment.paidOn)}${payment.paidOnline ? ' by card' : ''}`
      : STATUS[payment.status]
  return (
    <View
      style={{ gap: 2, paddingVertical: space.sm, borderTopWidth: 1, borderTopColor: p.line }}
      accessible
      accessibilityLabel={`${payment.description}, ${gbp(payment.amountPence)}, ${status}`}
    >
      <Row style={{ justifyContent: 'space-between', flexWrap: 'nowrap' }}>
        <Body style={{ fontWeight: '600', flexShrink: 1 }}>{payment.description}</Body>
        <Body style={{ fontWeight: '600' }}>{gbp(payment.amountPence)}</Body>
      </Row>
      <Muted>
        {payment.purposeLabel} · invoiced {formatDate(payment.invoicedOn)}
        {payment.reference ? ` · reference ${payment.reference}` : ''}
      </Muted>
      {showStatus && <Muted style={payment.status === 'DUE' ? { color: p.warnInk } : undefined}>{status}</Muted>}
    </View>
  )
}
