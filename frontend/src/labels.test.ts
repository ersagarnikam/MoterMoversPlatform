import { describe, expect, it } from 'vitest'
import { approveAllLabel, coverEntryLine, draftCount, licenceStatusLabel, planPriceLabel } from './labels'

describe('licenceStatusLabel', () => {
  it('says when a licence that has not started will start, never Active', () => {
    expect(licenceStatusLabel('UPCOMING', '2027-01-04')).toBe('Starts Mon 4 Jan 2027')
    expect(licenceStatusLabel('UPCOMING', null)).toBe('Not started')
  })

  it('names the other statuses as before', () => {
    expect(licenceStatusLabel('ACTIVE', '2026-01-04')).toBe('Active')
    expect(licenceStatusLabel('RENEWAL_DUE', null)).toBe('Renewal due')
    expect(licenceStatusLabel('FREE_TRIAL', null)).toBe('Free trial')
    expect(licenceStatusLabel('EXPIRED', null)).toBe('Expired')
  })
})

describe('approving every draft', () => {
  it('uses the right plural', () => {
    expect(draftCount(1)).toBe('1 draft')
    expect(draftCount(0)).toBe('0 drafts')
    expect(draftCount(180)).toBe('180 drafts')
  })

  it('names what it approves, not what the page shows', () => {
    expect(approveAllLabel(1, 'Yellow module 19')).toBe('Approve the 1 draft in Yellow module 19')
    expect(approveAllLabel(180, 'Green')).toBe('Approve all 180 drafts in Green')
    expect(approveAllLabel(180, 'Green')).not.toContain('shown')
  })
})

describe('planPriceLabel', () => {
  it('shows a plan on sale with its yearly price', () => {
    expect(planPriceLabel({ active: true, currentPricePence: 63500 })).toBe('£635 a year')
  })

  it('shows a retired plan with no price', () => {
    expect(planPriceLabel({ active: false, currentPricePence: 175000 })).toBe('No longer on sale')
  })
})

describe('coverEntryLine', () => {
  it('shows a part that was paid for with its dates', () => {
    expect(coverEntryLine({ startsOn: '2026-11-21', endsOn: '2026-12-21', how: 'Monthly payment' })).toBe(
      'Sat 21 Nov 2026 to Mon 21 Dec 2026 · Monthly payment',
    )
  })

  it('says a refunded month was refunded, with the day', () => {
    expect(
      coverEntryLine({ startsOn: '2026-11-21', endsOn: '2026-11-26', how: 'Monthly payment', refundedOn: '2026-11-26' }),
    ).toBe('Sat 21 Nov 2026 to Thu 26 Nov 2026 · Monthly payment · Refunded on Thu 26 Nov 2026')
  })

  it('never shows an end before the start for a part refunded before it began', () => {
    const line = coverEntryLine({ startsOn: '2026-12-21', endsOn: null, how: 'Pass', refundedOn: '2026-11-26' })
    expect(line).toBe('Pass · Refunded on Thu 26 Nov 2026, before it started (Mon 21 Dec 2026)')
    expect(line).not.toContain(' to ')
    expect(line).not.toContain('Paid')
  })
})
