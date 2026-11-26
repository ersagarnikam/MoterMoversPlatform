import { describe, expect, it } from 'vitest'
import { canMarkDone, courseSummary, markDoneHint, nextLesson, placeAction, placeProgress, testLabel } from './courseLessons'

describe('courseSummary', () => {
  it('says what a recorded course offers', () => {
    expect(courseSummary({ lessons: 3, lessonMinutes: 60, quizzes: 1 })).toBe(
      '3 recorded lessons, about 60 minutes in all, to take in your own time, then a test',
    )
    expect(courseSummary({ lessons: 1, lessonMinutes: 20, quizzes: 0 })).toBe(
      '1 recorded lesson, about 20 minutes in all, to take in your own time',
    )
    expect(courseSummary({ lessons: 2, lessonMinutes: 40, quizzes: 2 })).toContain('then 2 tests')
  })

  it('says a course with no lessons yet is being prepared', () => {
    expect(courseSummary({ lessons: 0, lessonMinutes: 0, quizzes: 1 })).toBe(
      'Being prepared: its lessons are on their way',
    )
  })
})

describe('marking a lesson done', () => {
  const withFilm = { open: true, done: false, hasFilm: true }

  it('waits for a lesson’s film to play to the end', () => {
    expect(canMarkDone(withFilm, false)).toBe(false)
    expect(markDoneHint(withFilm, false)).toBe('Watch the film to the end, then mark the lesson as done.')
    expect(canMarkDone(withFilm, true)).toBe(true)
    expect(markDoneHint(withFilm, true)).toBeNull()
  })

  it('lets a lesson with no film be marked at any time', () => {
    expect(canMarkDone({ open: true, done: false, hasFilm: false }, false)).toBe(true)
  })

  it('never offers a lesson that is not open yet, or one already done', () => {
    expect(canMarkDone({ open: false, done: false, hasFilm: false }, true)).toBe(false)
    expect(markDoneHint({ open: false, done: false, hasFilm: true }, true)).toBe(
      'Opens when the lesson before it is done.',
    )
    expect(canMarkDone({ open: true, done: true, hasFilm: true }, true)).toBe(false)
    expect(markDoneHint({ open: true, done: true, hasFilm: true }, false)).toBeNull()
  })
})

describe('where a learner has got to', () => {
  it('finds the lesson to carry on with', () => {
    const lessons = [
      { id: 1, open: true, done: true },
      { id: 2, open: true, done: false },
      { id: 3, open: false, done: false },
    ]
    expect(nextLesson(lessons)?.id).toBe(2)
    expect(nextLesson(lessons.map((l) => ({ ...l, open: true, done: true })))).toBeNull()
  })

  it('says the progress in words', () => {
    expect(placeProgress({ lessonsDone: 1, lessons: 3, quizzes: 1, quizzesPassed: 0 })).toBe(
      '1 of 3 lessons done, then the test',
    )
    expect(placeProgress({ lessonsDone: 3, lessons: 3, quizzes: 1, quizzesPassed: 0 })).toBe(
      '3 of 3 lessons done: the test is open',
    )
    expect(placeProgress({ lessonsDone: 3, lessons: 3, quizzes: 1, quizzesPassed: 1 })).toBe(
      '3 of 3 lessons done, test passed',
    )
    expect(placeProgress({ lessonsDone: 1, lessons: 1, quizzes: 0, quizzesPassed: 0 })).toBe('1 of 1 lesson done')
  })

  it('names the link into the course', () => {
    expect(placeAction({ lessonsDone: 0, lessons: 3 })).toBe('Start the course')
    expect(placeAction({ lessonsDone: 2, lessons: 3 })).toBe('Continue the course')
    expect(placeAction({ lessonsDone: 3, lessons: 3 })).toBe('Look at the lessons again')
  })

  it('names a test without a live session on a recorded course', () => {
    expect(testLabel({ title: 'Final test', afterSession: 1 }, true)).toBe('Final test')
    expect(testLabel({ title: 'The daily session', afterSession: 2 }, false)).toBe(
      'Quiz after session 2: The daily session',
    )
  })
})
