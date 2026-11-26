import { useCallback, useEffect, useState } from 'react'
import { api, type AdminCourse, type AdminLesson, type Enrolment } from '../api'
import { placeProgress } from '../courseLessons'
import { day } from '../dates'
import { useConfirm, useToast } from './Feedback'

/**
 * A recorded course's lessons on its own page (go-live task 89): what learners see, in order, and what still waits
 * for sign-off. Lessons are written and approved on Content approval, the one page that can send files to the store.
 */
export function CourseLessonsSummary({ course }: { course: AdminCourse }) {
  const [lessons, setLessons] = useState<AdminLesson[] | null>(null)

  useEffect(() => {
    api
      .courseLessons(course.id)
      .then(setLessons)
      .catch(() => setLessons([]))
  }, [course.id])

  return (
    <>
      <h4>Recorded lessons</h4>
      {lessons && lessons.length === 0 && (
        <p className="muted">No lessons yet. Learners can book once a lesson is approved.</p>
      )}
      {lessons && lessons.length > 0 && (
        <ul className="quiz-list">
          {lessons.map((l) => (
            <li key={l.id}>
              <span>
                {l.position}. <strong>{l.title}</strong>
                <span className="muted">
                  {' '}
                  · {l.minutes} min · {l.videoUrl ? 'film' : 'no film'}
                  {l.slidesUrl ? ' · slides' : ''} · done by {l.learnersDone}
                </span>
              </span>
              <span className={`badge ${l.status === 'APPROVED' ? 'badge-ok' : ''}`}>
                {l.status === 'APPROVED' ? 'Approved' : l.status === 'REJECTED' ? 'Rejected' : 'Draft'}
              </span>
            </li>
          ))}
        </ul>
      )}
      <div className="actions">
        {/* A full page load: only Content approval's own headers let the browser send a file to the store. */}
        <a className="btn" href={`/admin/content?tab=course-lessons&course=${course.id}`}>
          Write and approve lessons
        </a>
      </div>
    </>
  )
}

/** Who has a place on a recorded course, and how far each has got. The team can cancel a place. */
export function CourseLearners({ course }: { course: AdminCourse }) {
  const [learners, setLearners] = useState<Enrolment[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const toast = useToast()
  const confirm = useConfirm()

  const load = useCallback(() => {
    api
      .courseLearners(course.id)
      .then(setLearners)
      .catch((e) => setError(e.message))
  }, [course.id])

  useEffect(load, [load])

  async function cancel(e: Enrolment) {
    const sure = await confirm({
      title: `Cancel ${e.learnerName}’s place?`,
      body: `On ${e.courseTitle}. Their progress goes with it.`,
      confirmLabel: 'Cancel place',
      cancelLabel: 'Keep the place',
      danger: true,
    })
    if (!sure) return
    try {
      toast((await api.cancelPlace(e.id)).message)
      load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not cancel the place')
    }
  }

  return (
    <>
      <h4>Learners</h4>
      {error && <p className="error">{error}</p>}
      {learners && learners.length === 0 && <p className="muted">Nobody has a place yet.</p>}
      {learners && learners.length > 0 && (
        <div className="table-wrap">
          <table className="cards">
            <thead>
              <tr>
                <th>Learner</th>
                <th>Progress</th>
                <th>Certificate</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {learners.map((e) => (
                <tr key={e.id}>
                  <td data-label="Learner">
                    {e.learnerName} <span className="muted">· {e.learnerEmail}</span>
                  </td>
                  <td data-label="Progress">{placeProgress(e)}</td>
                  <td data-label="Certificate">
                    {e.certificateIssuedOn ? `Issued ${day(e.certificateIssuedOn)}` : '-'}
                  </td>
                  <td>
                    {!e.certificateIssuedOn && (
                      <button className="btn" onClick={() => cancel(e)}>
                        Cancel place
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
