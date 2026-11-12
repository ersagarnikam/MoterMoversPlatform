import { describe, expect, it } from 'vitest'
import type { HomePass } from './api'
import { passState } from './homePass'

const pass = (p: Partial<HomePass>): HomePass => ({
  hasFamily: true,
  onSale: true,
  pricePence: 3500,
  cardPayments: true,
  running: false,
  endsOn: null,
  ended: false,
  ...p,
})

describe('passState', () => {
  it('offers the first pass at its price', () => {
    const s = passState(pass({}))
    expect(s.kind).toBe('buy')
    expect(s.action).toBe('Buy the pass for £35')
  })

  it('says when a running pass ends, with nothing to buy', () => {
    const s = passState(pass({ running: true, endsOn: '2027-11-11' }))
    expect(s.kind).toBe('running')
    expect(s.text).toContain('Thu 11 Nov 2027')
    expect(s.action).toBeNull()
  })

  it('offers to renew a pass that ended, and says the records are kept', () => {
    const s = passState(pass({ ended: true, endsOn: '2026-11-01' }))
    expect(s.kind).toBe('renew')
    expect(s.text).toContain('ended on Sun 1 Nov 2026')
    expect(s.text).toContain('records are kept')
    expect(s.action).toBe('Renew for £35')
  })

  it('offers nothing while there is no price', () => {
    const s = passState(pass({ onSale: false, pricePence: null }))
    expect(s.kind).toBe('not-on-sale')
    expect(s.action).toBeNull()
  })

  it('shows nothing to a parent with no children at home', () => {
    expect(passState(pass({ hasFamily: false })).kind).toBe('no-family')
  })
})
