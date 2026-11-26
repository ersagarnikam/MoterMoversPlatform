import type { Course, CourseLesson, Enrolment, QuizSummary } from './api'

/**
 * The rules of a recorded course on screen (go-live task 89): what a course offers, where a learner has got to, and
 * when a lesson can be marked done. The API enforces the order and who may see a place; these decide the wording
 * and which button shows.
 */

/** "3 recorded lessons, about 60 minutes in all, then a test of 10 questions", for the course's card. */
export function courseSummary(course: Pick<Course, 'lessons' | 'lessonMinutes' | 'quizzes'>): string {
  if (course.lessons === 0) return 'Being prepared: its lessons are on their way'
  const lessons = course.lessons === 1 ? '1 recorded lesson' : `${course.lessons} recorded lessons`
  const test = course.quizzes === 0 ? '' : course.quizzes === 1 ? ', then a test' : `, then ${course.quizzes} tests`
  return `${lessons}, about ${course.lessonMinutes} minutes in all, to take in your own time${test}`
}

/**
 * Whether a lesson can be marked done now. A lesson with a film waits until the film has played to its end; a lesson
 * with no film can be marked at any time. A lesson already done, or one that is not open yet, cannot.
 */
export function canMarkDone(lesson: Pick<CourseLesson, 'open' | 'done' | 'hasFilm'>, filmEnded: boolean): boolean {
  return lesson.open && !lesson.done && (!lesson.hasFilm || filmEnded)
}

/** Why a lesson cannot be marked done yet, to say beside the button; null when it can, or is done. */
export function markDoneHint(lesson: Pick<CourseLesson, 'open' | 'done' | 'hasFilm'>, filmEnded: boolean): string | null {
  if (lesson.done || canMarkDone(lesson, filmEnded)) return null
  return lesson.open ? 'Watch the film to the end, then mark the lesson as done.' : 'Opens when the lesson before it is done.'
}

/** The lesson to carry on with: the first that is open and not done; null when every lesson is done. */
export function nextLesson<L extends Pick<CourseLesson, 'open' | 'done'>>(lessons: L[]): L | null {
  return lessons.find((l) => l.open && !l.done) ?? null
}

/** Where a learner stands on a recorded course, in a few words for their Training page. */
export function placeProgress(e: Pick<Enrolment, 'lessonsDone' | 'lessons' | 'quizzes' | 'quizzesPassed'>): string {
  const lessons = `${e.lessonsDone} of ${e.lessons} ${e.lessons === 1 ? 'lesson' : 'lessons'} done`
  if (e.quizzes === 0) return lessons
  if (e.lessonsDone < e.lessons) return `${lessons}, then the test`
  return e.quizzesPassed === e.quizzes ? `${lessons}, test passed` : `${lessons}: the test is open`
}

/** What the link into a place says: Start, Continue, the test, or looking back. */
export function placeAction(e: Pick<Enrolment, 'lessonsDone' | 'lessons'>): string {
  if (e.lessonsDone === 0) return 'Start the course'
  return e.lessonsDone < e.lessons ? 'Continue the course' : 'Look at the lessons again'
}

/** A quiz's name on a recorded course: its tests come after the lessons, not after a live session. */
export function testLabel(quiz: Pick<QuizSummary, 'title' | 'afterSession'>, recorded: boolean): string {
  return recorded ? quiz.title : `Quiz after session ${quiz.afterSession}: ${quiz.title}`
}
