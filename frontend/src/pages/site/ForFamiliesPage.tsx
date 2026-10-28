import { Link } from 'react-router-dom'
import NotADiagnosis from '../../components/NotADiagnosis'
import { useHomeAvailable } from '../../homeProgramme'
import { mailto } from '../../site'

export default function ForFamiliesPage() {
  const home = useHomeAvailable()
  return (
    <>
      <section className="site-page-head">
        <p className="eyebrow eyebrow-light">For parents and carers</p>
        <h1>Keep the movement going at home</h1>
        <p className="site-lead">
          A few minutes of play most days helps the same skills grow that your child practises at school.
        </p>
      </section>

      <section className="site-section">
        <div className="site-cards site-cards-3">
          <div className="card">
            <h3>The parent app</h3>
            <p>
              Home activities matched to your child’s colour stage, with simple guidance for each one. Your child’s school
              gives you a code to link your child in the app.
            </p>
          </div>
          <div className="card">
            <h3>The parent course</h3>
            <p>
              A short online course explaining why movement matters for learning and how to help at home. Sign in on
              this website to take it.
            </p>
            <Link to="/login?as=parent">Sign in as a parent or carer</Link>
          </div>
          <div className="card">
            <h3>The CD and book</h3>
            <p>The Motor Movers CD and book are available to buy for families as well as schools.</p>
            <a href={mailto('Motor Movers CD and book')}>Ask us about the CD and book</a>
          </div>
        </div>
      </section>

      {home && (
        <section className="site-section">
          <div className="site-split">
            <div>
              <h2>Your child’s school is not on Motor Movers?</h2>
              <p>
                <strong>Motor Movers at Home</strong> brings the programme to you: a short check of what your child can
                do now, then a few minutes of the same exercises schools use, most days, and how your child is getting
                on in plain words.
              </p>
              <p>
                We ask only for a first name or nickname and an age, never a date of birth. Only you, and anyone you
                invite, see your child. If the school joins later, you can share what you recorded with the teacher.
              </p>
            </div>
            <div className="card">
              <h3>Start at home</h3>
              <p>Create a parent account on this website, or in the parent app.</p>
              <Link to="/join" className="btn btn-primary">
                Create a parent account
              </Link>
            </div>
          </div>
        </section>
      )}

      <section className="site-section">
        <div className="site-split">
          <div>
            <h2>What you will see</h2>
            <p>
              The app shows your child’s stage and the activities for it. You only ever see the children linked to your account.
            </p>
            <p>
              If you have questions about how your child is getting on, your child’s class teacher is the best person to
              talk to.
            </p>
          </div>
          <NotADiagnosis text="Motor Movers shows readiness for the programme stages. It never diagnoses." />
        </div>
      </section>
    </>
  )
}
