import { describe, expect, it } from 'vitest'
import { matches } from './ListFilter'

describe('matches', () => {
  it('finds the search in any of the texts, ignoring case and spaces around it', () => {
    expect(matches(' oak ', 'Oakfield Primary', null)).toBe(true)
    expect(matches('trust', 'Oakfield Primary', 'Northbridge Academy Trust')).toBe(true)
    expect(matches('marlow', 'Oakfield Primary', undefined)).toBe(false)
  })

  it('matches everything when the search is empty', () => {
    expect(matches('', 'anything')).toBe(true)
    expect(matches('   ')).toBe(true)
  })
})
