import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type Enrolment } from '../api'
import StageStripe from '../components/StageStripe'

/** A learner's practitioner certificate, ready to print or save as a PDF from the browser. */
export default function CertificatePage() {
  const { enrolmentId } = useParams()
  const [enrolment, setEnrolment] = useState<Enrolment | null | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .myEnrolments()
      .then((all) => setEnrolment(all.find((e) => e.id === Number(enrolmentId) && e.certificateIssuedOn) ?? null))
      .catch((e) => setError(e.message))
  }, [enrolmentId])

  if (error) return <p className="error">{error}</p>
  if (enrolment === undefined) return <p className="muted">Loading…</p>
  if (enrolment === null) {
    return (
      <p className="muted">
        There is no certificate here yet. <Link to="/training">Back to training</Link>
      </p>
    )
  }

  return (
    <>
      <div className="page-head no-print">
        <Link className="fine-link" to="/training">
          Back to training
        </Link>
        <button className="btn btn-primary" onClick={() => window.print()}>
          Print or save as PDF
        </button>
      </div>
      <article className="certificate" aria-label="Certificate">
        <StageStripe />
        <p className="eyebrow">Motor Movers</p>
        <h1>Certificate of completion</h1>
        <p>This certifies that</p>
        <p className="certificate-name">{enrolment.learnerName}</p>
        <p>
          has attended all {enrolment.liveSessions} live sessions and passed the assessment for the
        </p>
        <p className="certificate-course">{enrolment.courseTitle}</p>
        <p className="muted">{enrolment.cohortName}</p>
        <dl className="certificate-dates">
          <div>
            <dt>Issued</dt>
            <dd>{enrolment.certificateIssuedOn}</dd>
          </div>
          <div>
            <dt>Valid until</dt>
            <dd>{enrolment.certificateExpiresOn}</dd>
          </div>
        </dl>
      </article>
    </>
  )
}
