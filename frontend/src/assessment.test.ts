import { describe, expect, it } from 'vitest'
import type { AssessmentExercise } from './api'
import { bandOpen, pointColumns, pointOrder, suggestedPoint } from './assessment'

const starfish: AssessmentExercise = {
  id: 3,
  stage: 'GREEN',
  number: 3,
  title: 'Starfish',
  instructions: '',
  levels: {},
  points: [
    { point: 'START', label: 'Start' },
    { point: 'MODULE_1', label: 'End of module 1' },
    { point: 'END', label: 'End of Green' },
  ],
}

const child = (...points: string[]) => ({ scores: points.map((point) => ({ exerciseId: 3, point })) })

describe('assessment points', () => {
  it('puts the start first, the modules in order and the end of the colour last', () => {
    expect(['END', 'MODULE_10', 'START', 'MODULE_2'].sort((a, b) => pointOrder(a) - pointOrder(b))).toEqual([
      'START',
      'MODULE_2',
      'MODULE_10',
      'END',
    ])
  })

  it('stays on a point until every child has a score there', () => {
    expect(suggestedPoint(starfish, [child(), child()])).toBe('START')
    // The last child still needs a start score: not skipped (the demo's Yellow class, 25 of 28 done).
    expect(suggestedPoint(starfish, [child('START'), child('START'), child()])).toBe('START')
    expect(suggestedPoint(starfish, [child('START'), child('START'), child('START')])).toBe('MODULE_1')
    expect(suggestedPoint(starfish, [child('START', 'MODULE_1', 'END')])).toBe('END')
  })

  it('moves on once half the class has a score at a later point', () => {
    // A child who missed the start does not hold the class back: the class is at module 1 (one child still to do).
    const moved = [child('START', 'MODULE_1'), child('START', 'MODULE_1'), child()]
    expect(suggestedPoint(starfish, moved)).toBe('MODULE_1')
    expect(suggestedPoint(starfish, [child('START', 'MODULE_1'), child('START'), child('START')])).toBe('MODULE_1')
  })

  it('makes one column per point any exercise is assessed at', () => {
    const rows = [
      { exerciseId: 1, number: 1, title: 'A', points: [{ point: 'END', label: 'End of Green', score: null, assessedOn: null }] },
      {
        exerciseId: 2,
        number: 2,
        title: 'B',
        points: [
          { point: 'MODULE_2', label: 'End of module 2', score: 3, assessedOn: '2026-10-20' },
          { point: 'START', label: 'Start', score: 2, assessedOn: '2026-10-01' },
        ],
      },
    ]
    expect(pointColumns(rows).map((c) => c.point)).toEqual(['START', 'MODULE_2', 'END'])
  })
})

describe('chart bands', () => {
  it('opens the bands around the child’s age', () => {
    expect(bandOpen(72, 60, 72)).toBe(true)
    expect(bandOpen(72, 0, 6)).toBe(false)
    expect(bandOpen(72, 72, 84)).toBe(true)
    expect(bandOpen(null, 0, 6)).toBe(true)
  })
})
