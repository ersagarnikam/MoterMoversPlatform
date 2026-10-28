import { describe, expect, it } from 'vitest'
import { ageLabel, reviewName, toMonths } from './homeProgramme'

describe('ages for Motor Movers at Home', () => {
  it('reads months as years and months', () => {
    expect(ageLabel(44)).toBe('3 years 8 months')
    expect(ageLabel(11)).toBe('11 months')
    expect(ageLabel(12)).toBe('1 year')
    expect(ageLabel(13)).toBe('1 year 1 month')
  })

  it('turns typed years and months into months the programme can use', () => {
    expect(toMonths('3', '8')).toBe(44)
    expect(toMonths('', '9')).toBe(9)
    expect(toMonths('2', '')).toBe(24)
    expect(toMonths('', '')).toBeNull()
    expect(toMonths('1', '12')).toBeNull()
    expect(toMonths('11', '0')).toBeNull()
    expect(toMonths('two', '1')).toBeNull()
  })

  it('names a review by its module or, at the end, the colour', () => {
    expect(reviewName('MODULE_2', 'Stage 4 · Green')).toBe('module 2')
    expect(reviewName('END', 'Stage 4 · Green')).toBe('Stage 4 · Green')
  })
})
