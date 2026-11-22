import { describe, expect, it } from 'vitest'
import type { HomePass } from './api'
import { contactUs, needsAttention, passState, sessionsOpen } from './homePass'

const pass = (p: Partial<HomePass>): HomePass => ({
  hasFamily: true,
  onSale: true,
  pricePence: 1500,
  cardPayments: true,
  running: false,
  endsOn: null,
  ended: false,
  inductionDone: true,
  contactEmail: null,
  contactPhone: null,
  subscription: 'NONE',
  renewsOn: null,
  canSubscribe: true,
  canManage: false,
  ...p,
})

describe('contactUs', () => {
  it('gives the email and phone when both are confirmed', () => {
    expect(contactUs(pass({ contactEmail: 'hello@example.test', contactPhone: '0118 000 0000' }))).toBe(
      'Please contact us at hello@example.test or 0118 000 0000 to subscribe.',
    )
  })

  it('leaves out what is not confirmed yet', () => {
    expect(contactUs(pass({ contactPhone: '0118 000 0000' }))).toBe('Please contact us at 0118 000 0000 to subscribe.')
    expect(contactUs(pass({}))).toBe('Please contact us to subscribe.')
  })
})

describe('sessionsOpen', () => {
  it('needs cover today and the induction, so the check is not offered otherwise', () => {
    expect(sessionsOpen(pass({ running: true }))).toBe(true)
    expect(sessionsOpen(pass({ running: false, ended: true, endsOn: '2026-11-01' }))).toBe(false)
    expect(sessionsOpen(pass({ running: true, inductionDone: false }))).toBe(false)
    expect(sessionsOpen(null)).toBe(false)
  })
})

describe('passState', () => {
  it('offers the monthly subscription at its price', () => {
    const s = passState(pass({}))
    expect(s.kind).toBe('subscribe')
    expect(s.action).toBe('subscribe')
    expect(s.actionLabel).toBe('Subscribe for £15 a month')
    expect(s.text).toContain('cancel at any time')
  })

  it('says when the next payment is taken, and lets the payer manage it', () => {
    const s = passState(
      pass({ running: true, subscription: 'ACTIVE', endsOn: '2026-12-21', renewsOn: '2026-12-21', canSubscribe: false, canManage: true }),
    )
    expect(s.kind).toBe('active')
    expect(s.text).toContain('£15 a month')
    expect(s.text).toContain('next payment is taken on Mon 21 Dec 2026')
    expect(s.action).toBe('manage')
  })

  it('tells a carer who does not pay that the subscriber manages it, with no button', () => {
    const s = passState(pass({ running: true, subscription: 'ACTIVE', renewsOn: '2026-12-21', canSubscribe: false }))
    expect(s.action).toBeNull()
    expect(s.text).toContain('The parent or carer who subscribed manages it.')
  })

  it('says the last day of a cancelled subscription', () => {
    const s = passState(
      pass({ running: true, subscription: 'CANCELLING', endsOn: '2026-12-21', canSubscribe: false, canManage: true }),
    )
    expect(s.kind).toBe('cancelling')
    expect(s.text).toContain('Sessions stay open until Mon 21 Dec 2026')
    expect(s.text).toContain('records are kept')
    expect(s.action).toBe('manage')
  })

  it('says a payment failed, how long sessions stay open, and where to change the card', () => {
    const open = passState(
      pass({ running: true, subscription: 'PAST_DUE', endsOn: '2026-12-21', canSubscribe: false, canManage: true }),
    )
    expect(open.kind).toBe('past-due')
    expect(open.heading).toBe('This month’s payment did not go through')
    expect(open.text).toContain('Sessions stay open until Mon 21 Dec 2026')
    expect(open.action).toBe('manage')
    const stopped = passState(pass({ subscription: 'PAST_DUE', ended: true, endsOn: '2026-11-20', canSubscribe: false, canManage: true }))
    expect(stopped.text).toContain('Sessions have stopped until a payment goes through')
  })

  it('shows a pass that still runs, and when the family can subscribe', () => {
    const later = passState(pass({ running: true, endsOn: '2027-11-11', canSubscribe: false }))
    expect(later.kind).toBe('covered')
    expect(later.text).toContain('Sessions are open until Thu 11 Nov 2027')
    expect(later.text).toContain('you can subscribe from that day, for £15 a month')
    expect(later.action).toBeNull()
    const lastDay = passState(pass({ running: true, endsOn: '2026-11-21' }))
    expect(lastDay.action).toBe('subscribe')
  })

  it('offers to subscribe again once cover has ended, and says the records are kept', () => {
    const s = passState(pass({ ended: true, endsOn: '2026-11-01' }))
    expect(s.kind).toBe('ended')
    expect(s.text).toContain('covered until Sun 1 Nov 2026')
    expect(s.text).toContain('records are kept')
    expect(s.actionLabel).toBe('Subscribe for £15 a month')
  })

  it('offers nothing while there is no price', () => {
    const s = passState(pass({ onSale: false, pricePence: null, canSubscribe: false }))
    expect(s.kind).toBe('not-on-sale')
    expect(s.action).toBeNull()
  })

  it('shows nothing to a parent with no children at home', () => {
    expect(passState(pass({ hasFamily: false })).kind).toBe('no-family')
  })
})

describe('needsAttention', () => {
  it('is for a family with no cover, or whose payment failed', () => {
    expect(needsAttention(pass({}))).toBe(true)
    expect(needsAttention(pass({ running: true, subscription: 'PAST_DUE' }))).toBe(true)
    expect(needsAttention(pass({ running: true, subscription: 'ACTIVE' }))).toBe(false)
    expect(needsAttention(pass({ hasFamily: false }))).toBe(false)
  })
})
