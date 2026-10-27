import { describe, expect, it } from 'vitest'
import { day, relativeDay } from './dates'

describe('day', () => {
  it('shows a calendar date with its weekday and year', () => {
    expect(day('2026-11-05')).toBe('Thu 5 Nov 2026')
    expect(day('2027-01-01')).toBe('Fri 1 Jan 2027')
  })

  it('takes the date part of an instant, without moving it to another day', () => {
    expect(day('2026-10-26T23:30:00Z')).toBe('Mon 26 Oct 2026')
  })

  it('shows nothing for no date', () => {
    expect(day(null)).toBe('')
    expect(day(undefined)).toBe('')
  })
})

describe('relativeDay', () => {
  const now = new Date(2026, 9, 26, 9, 0)

  it('says Today and Tomorrow, and nothing further off', () => {
    expect(relativeDay(new Date(2026, 9, 26, 19, 0).toISOString(), now)).toBe('Today')
    expect(relativeDay(new Date(2026, 9, 27, 8, 0).toISOString(), now)).toBe('Tomorrow')
    expect(relativeDay(new Date(2026, 9, 29, 8, 0).toISOString(), now)).toBe('')
  })
})
