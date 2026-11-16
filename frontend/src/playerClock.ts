/**
 * The class player's clock. A session is a list of steps of equal length, and each step shows one of three things: its
 * exercise's own film, the session's film (its own, or its rhyme's or module's), or nothing but the timed plan. The
 * rules that keep the plan and the picture together:
 *
 * - A step with its own film waits for that film: the film's end moves the plan on, never the clock, so a film is not cut.
 * - On the session's film, the clock runs only while that film is really playing.
 * - With no film, the clock runs while the session is running.
 * - `running` is what the teacher last asked for. It carries from step to step, so whatever the next step shows starts
 *   by itself once the session is under way, and a pause stays a pause.
 */
export type Source = 'own' | 'session' | 'none'

export interface Plan {
  /** What each step shows, the cool-down included. */
  sources: Source[]
  /** The session's length in seconds. */
  total: number
}

export interface Clock {
  elapsed: number
  /** The session is under way: the teacher pressed play and has not paused. */
  running: boolean
  /** The film on screen is playing now. A film that has only just appeared is not, until it says so. */
  filmPlaying: boolean
}

export type ClockEvent =
  | { type: 'tick' }
  /** The film on screen started, or stopped part-way. */
  | { type: 'play' }
  | { type: 'pause' }
  /** A step's own film played to its end. */
  | { type: 'ended' }
  /** Play or pause on the timed plan, where there is no film. */
  | { type: 'toggle' }
  /** The teacher chose a step in the plan. */
  | { type: 'go'; step: number }
  | { type: 'skipToEnd' }

export const START: Clock = { elapsed: 0, running: false, filmPlaying: false }

export function stepAt(elapsed: number, plan: Plan): number {
  const steps = plan.sources.length
  // The small allowance keeps the start of a step in that step when the step length is not a whole number.
  return Math.max(0, Math.min(steps - 1, Math.floor((elapsed * steps) / plan.total + 1e-9)))
}

export const startOf = (step: number, plan: Plan): number =>
  Math.min(plan.total, (plan.total * step) / plan.sources.length)

/** Whether a second on the clock should count now. */
export function ticking(clock: Clock, plan: Plan): boolean {
  if (clock.elapsed >= plan.total) return false
  const source = plan.sources[stepAt(clock.elapsed, plan)]
  return source === 'none' ? clock.running : source === 'session' ? clock.filmPlaying : false
}

export function advance(clock: Clock, event: ClockEvent, plan: Plan): Clock {
  switch (event.type) {
    case 'tick': {
      if (!ticking(clock, plan)) return clock
      return moveTo(clock, Math.min(plan.total, clock.elapsed + 1), plan)
    }
    case 'play':
      return { ...clock, running: true, filmPlaying: true }
    case 'pause':
      return { ...clock, running: false, filmPlaying: false }
    case 'ended':
      // A film's end is reported after its pause, so this puts the session under way again for the next step.
      return moveTo({ ...clock, running: true }, startOf(stepAt(clock.elapsed, plan) + 1, plan), plan)
    case 'toggle':
      return { ...clock, running: !clock.running }
    case 'go':
      return moveTo(clock, startOf(event.step, plan), plan)
    case 'skipToEnd':
      return { elapsed: plan.total, running: false, filmPlaying: false }
  }
}

/** A new step may show another film, which is not playing until it reports that it is; the end stops the session. */
function moveTo(clock: Clock, elapsed: number, plan: Plan): Clock {
  if (elapsed >= plan.total) return { elapsed: plan.total, running: false, filmPlaying: false }
  const from = stepAt(clock.elapsed, plan)
  const to = stepAt(elapsed, plan)
  const sameFilm = from === to || (plan.sources[from] === 'session' && plan.sources[to] === 'session')
  return { ...clock, elapsed, filmPlaying: sameFilm ? clock.filmPlaying : false }
}
