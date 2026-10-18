import { Link } from 'react-router-dom'
import NotADiagnosis from '../../components/NotADiagnosis'
import { STAGES } from '../../stages'

const SESSION = [
  { title: 'Warm up', text: 'Gentle movement and a rhyme to get bodies and minds ready.' },
  { title: 'Activities', text: 'A short circuit of movements with bats, balls, hoops and bean bags.' },
  { title: 'Cool down', text: 'Calm, slow movement so children return to class settled and ready to learn.' },
]

const PLATFORM = [
  { title: "Today's session", text: 'The session video, the activity plan and the rhyme, ready to play to the class.' },
  { title: 'Participation counter', text: 'A 20-day counter so the class can see its daily habit building.' },
  { title: 'Screening', text: 'One item at a time, on a tablet or laptop, saved even if the connection drops.' },
  { title: 'Readiness profiles', text: "Each child's stage and the areas that are secure or still emerging." },
  { title: 'Staff induction', text: 'A one-hour induction in short lessons before staff run their first session.' },
  { title: 'Resource library', text: 'Rhymes and guidance for every stage your setting holds.' },
  { title: 'Dashboards', text: 'School and trust progress, flagged classes and a governor summary to download.' },
  { title: 'Parent app', text: 'Home activities matched to each child’s stage, linked with a code from school.' },
]

export default function ProgrammePage() {
  return (
    <>
      <section className="site-page-head">
        <p className="eyebrow eyebrow-light">The programme</p>
        <h1>A few minutes of the right movement, every day</h1>
        <p className="site-lead">
          Motor Movers works through repetition. Children practise the same movements daily, and the quality of each
          movement improves week by week.
        </p>
      </section>

      <section className="site-section">
        <h2>Seven colour stages</h2>
        <p className="site-narrow">
          The stages follow the order in which movement skills develop, for children from 6 months to 8 years. Screening
          shows where each child starts, and children move on when they are ready.
        </p>
        <ol className="stage-ladder">
          {STAGES.map((s, i) => (
            <li key={s.key}>
              <span className="stage-ladder-bar" style={{ background: s.colour }} aria-hidden="true" />
              <span className="stage-ladder-label">
                <strong>{s.label}</strong>
                <span className="muted">Stage {i + 1}</span>
              </span>
            </li>
          ))}
        </ol>
      </section>

      <section className="site-section">
        <h2>The daily session</h2>
        <p className="site-narrow">
          About ten minutes, with the whole class, every school day. Children take part in uniform with bare feet, and
          the equipment is what most settings already have.
        </p>
        <ol className="site-steps">
          {SESSION.map((s, i) => (
            <li key={s.title} className="card">
              <span className="step-number" aria-hidden="true">
                {i + 1}
              </span>
              <h3>{s.title}</h3>
              <p>{s.text}</p>
            </li>
          ))}
        </ol>
        <p className="muted site-narrow">
          Motor Movers supports the Physical Development area of the EYFS. It does not replace PE or other physical
          activity.
        </p>
      </section>

      <section className="site-section">
        <h2>Screening and readiness</h2>
        <div className="site-split">
          <div>
            <p>
              Staff screen each child against the programme stages, one item at a time. The result is a readiness
              profile: the stage to work at, and which areas are secure or still emerging. Re-screening shows progress
              over the year.
            </p>
            <p>
              Children are shown by their initials unless the school chooses full names, and only the people who work
              with a child can see their record.
            </p>
          </div>
          <NotADiagnosis text="Screening shows readiness for the programme stages. It is not a clinical assessment, and the platform never diagnoses." />
        </div>
      </section>

      <section className="site-section">
        <h2>What the platform gives you</h2>
        <div className="site-cards site-cards-4">
          {PLATFORM.map((p) => (
            <div key={p.title} className="card">
              <h3>{p.title}</h3>
              <p>{p.text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="site-section">
        <h2>Looking after children’s data</h2>
        <ul className="site-list site-narrow">
          <li>Data is held in the UK.</li>
          <li>Teachers see their own class, heads their school, and trusts see summaries unless a school grants more.</li>
          <li>Parents see only the children linked to them.</li>
          <li>Records of children who have left are deleted a year after they leave.</li>
        </ul>
      </section>

      <section className="site-section site-cta-band">
        <h2>Bring Motor Movers to your setting</h2>
        <div className="site-cta">
          <Link to="/for-schools" className="btn btn-big btn-light">
            How to join
          </Link>
        </div>
      </section>
    </>
  )
}
