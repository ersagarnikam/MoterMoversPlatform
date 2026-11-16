import { describe, expect, it } from 'vitest'
import { advance, START, stepAt, ticking, type Clock, type ClockEvent, type Plan } from './playerClock'

const run = (plan: Plan, clock: Clock, ...events: ClockEvent[]) => events.reduce((c, e) => advance(c, e, plan), clock)
const ticks = (n: number): ClockEvent[] => Array.from({ length: n }, () => ({ type: 'tick' }) as ClockEvent)

/** A film that plays to its end reports a pause, then its end. */
const filmEnds: ClockEvent[] = [{ type: 'pause' }, { type: 'ended' }]

describe('the class player clock', () => {
  // Like a real Green session: two exercises with films, one without, one with, then the cool-down. 100 s a step.
  const mixed: Plan = { sources: ['own', 'own', 'session', 'own', 'none'], total: 500 }

  it('waits for a step film, which is never cut by the clock', () => {
    const playing = run(mixed, START, { type: 'play' }, ...ticks(300))
    expect(playing.elapsed).toBe(0)
    expect(stepAt(playing.elapsed, mixed)).toBe(0)
  })

  it('goes on to the next step when a step film ends, with the session still under way', () => {
    const second = run(mixed, START, { type: 'play' }, ...filmEnds)
    expect(stepAt(second.elapsed, mixed)).toBe(1)
    expect(second.running).toBe(true)
    // The second film has only just appeared: nothing counts until it plays, and it too is waited for.
    expect(second.filmPlaying).toBe(false)
    expect(ticking(second, mixed)).toBe(false)
  })

  it('starts the session film by itself after a step film, and counts only while it plays', () => {
    const third = run(mixed, START, { type: 'play' }, ...filmEnds, { type: 'play' }, ...filmEnds)
    expect(stepAt(third.elapsed, mixed)).toBe(2)
    // Under way, so the screen starts the session's film without a press (the Tester's case a).
    expect(third.running).toBe(true)
    // If the browser will not start it, the plan waits for it; it does not run on with nothing playing.
    expect(run(mixed, third, ...ticks(60)).elapsed).toBe(200)
    const counted = run(mixed, third, { type: 'play' }, ...ticks(60))
    expect(counted.elapsed).toBe(260)
  })

  it('does not run on when the session film gives way to a step film (the mixed-session bug)', () => {
    const onSessionFilm = run(mixed, START, { type: 'go', step: 2 }, { type: 'play' })
    // The clock reaches the next step, whose own film takes over. The session's film is taken away without a pause.
    const fourth = run(mixed, onSessionFilm, ...ticks(100))
    expect(stepAt(fourth.elapsed, mixed)).toBe(3)
    expect(fourth.running).toBe(true)
    expect(fourth.filmPlaying).toBe(false)
    expect(run(mixed, fourth, ...ticks(60)).elapsed).toBe(300)
    // That film plays and ends: on to the cool-down, which has no film and counts by itself.
    const coolDown = run(mixed, fourth, { type: 'play' }, ...filmEnds)
    expect(stepAt(coolDown.elapsed, mixed)).toBe(4)
    expect(run(mixed, coolDown, ...ticks(60)).elapsed).toBe(460)
    // And the session stops at its end.
    const done = run(mixed, coolDown, ...ticks(200))
    expect(done).toEqual({ elapsed: 500, running: false, filmPlaying: false })
  })

  it('stays paused when the teacher pauses, on a film or on the timed plan', () => {
    const paused = run(mixed, START, { type: 'go', step: 2 }, { type: 'play' }, ...ticks(10), { type: 'pause' })
    expect(run(mixed, paused, ...ticks(30)).elapsed).toBe(210)
    expect(paused.running).toBe(false)
    const plain: Plan = { sources: ['none', 'none'], total: 600 }
    const toggled = run(plain, START, { type: 'toggle' }, ...ticks(5), { type: 'toggle' }, ...ticks(5))
    expect(toggled.elapsed).toBe(5)
  })

  it('keeps one session film playing across steps, as a rhyme or module film does', () => {
    const rhyme: Plan = { sources: ['session', 'session', 'session'], total: 600 }
    const later = run(rhyme, START, { type: 'play' }, ...ticks(250))
    expect(later.elapsed).toBe(250)
    expect(stepAt(later.elapsed, rhyme)).toBe(1)
    expect(later.filmPlaying).toBe(true)
  })

  it('goes to a chosen step without starting or stopping the session, and skips to the end', () => {
    const chosen = run(mixed, START, { type: 'go', step: 3 })
    expect(stepAt(chosen.elapsed, mixed)).toBe(3)
    expect(chosen.running).toBe(false)
    const whilePlaying = run(mixed, START, { type: 'play' }, { type: 'go', step: 1 })
    expect(whilePlaying.running).toBe(true)
    expect(whilePlaying.filmPlaying).toBe(false)
    expect(run(mixed, whilePlaying, { type: 'skipToEnd' })).toEqual({ elapsed: 500, running: false, filmPlaying: false })
  })

  it('puts the start of every step in that step, whatever the step length', () => {
    const seven: Plan = { sources: Array(7).fill('none'), total: 600 }
    for (let step = 0; step < 7; step++) {
      expect(stepAt(run(seven, START, { type: 'go', step }).elapsed, seven)).toBe(step)
    }
  })
})
