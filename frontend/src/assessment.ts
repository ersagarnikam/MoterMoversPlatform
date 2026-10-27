// Small rules for Pat's assessments, kept apart from the pages so they can be tested.
import type { AssessmentExercise, AssessmentProfile } from './api'

/** START first, then the modules in order, then the end of the colour. */
export function pointOrder(point: string): number {
  if (point === 'START') return 0
  if (point === 'END') return 1000
  const m = /^MODULE_(\d+)$/.exec(point)
  return m ? Number(m[1]) : 999
}

/**
 * The point to score next: the first one (in the exercise's order) that some child still has no score for, so the
 * last few children of a point are not skipped. A point is passed over once the class has moved on, that is once half
 * the class or more has a score at a later point. When every point is done, the last one, so a late score can still be
 * put right.
 */
export function suggestedPoint(
  exercise: AssessmentExercise,
  children: { scores: { exerciseId: number; point: string }[] }[],
): string {
  const points = exercise.points.map((p) => p.point)
  const done = points.map(
    (p) => children.filter((c) => c.scores.some((s) => s.exerciseId === exercise.id && s.point === p)).length,
  )
  for (let i = 0; i < points.length; i++) {
    const movedOn = done.slice(i + 1).some((d) => d * 2 >= children.length)
    if (done[i] < children.length && !movedOn) return points[i]
  }
  return points[points.length - 1]
}

/** The columns of Pat's table for a colour: every point any exercise is assessed at, in order. */
export function pointColumns(exercises: AssessmentProfile['exercises']): { point: string; label: string }[] {
  const seen = new Map<string, string>()
  exercises.forEach((e) => e.points.forEach((p) => seen.set(p.point, p.label)))
  return [...seen.entries()]
    .sort(([a], [b]) => pointOrder(a) - pointOrder(b))
    .map(([point, label]) => ({ point, label }))
}

/**
 * Which age bands of a chart open by default: those from two years below the child's age to one year above, so the
 * teacher starts with the movements that matter now. Every band can still be opened.
 */
export function bandOpen(ageMonths: number | null, fromMonths: number, toMonths: number): boolean {
  if (ageMonths === null) return true
  return toMonths >= ageMonths - 24 && fromMonths <= ageMonths + 12
}
