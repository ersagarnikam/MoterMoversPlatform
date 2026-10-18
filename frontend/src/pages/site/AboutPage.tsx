import { Link } from 'react-router-dom'

const PILOT_SCHOOLS = [
  "Queen Ethelburga's Collegiate, York",
  "St Bartholomew's Primary School, Loughborough",
  'Leicester Grammar School, Leicester',
  "St Peter's and St Paul's Primary School, Leicester",
]

const PEOPLE = [
  {
    name: 'Professor Pat Preedy',
    role: 'Co-developer of Motor Movers, lead of the Movement for Learning Project',
    text: 'Adjunct Professor at Curtin University and a member of the Institute for Neuro-Physiological Psychology (INPP).',
  },
  {
    name: 'Laura Preedy-Maher',
    role: 'Co-developer of Motor Movers',
    text: 'Developed Motor Movers with Professor Preedy, extending the Movement for Learning work to seven colour stages.',
  },
  {
    name: 'Dr Rebecca Duncombe',
    role: 'Research partner, Movement for Learning',
    text: 'Teaching fellow in the School of Sport, Exercise and Health Sciences at Loughborough University.',
  },
]

export default function AboutPage() {
  return (
    <>
      <section className="site-page-head">
        <p className="eyebrow eyebrow-light">About</p>
        <h1>Built on the Movement for Learning research</h1>
        <p className="site-lead">
          Motor Movers grew out of the Movement for Learning Project, a daily movement programme for Reception and
          Year 1 developed with Loughborough University and tested in schools across England.
        </p>
      </section>

      <section className="site-section site-prose">
        <h2>The story</h2>
        <p>
          The Movement for Learning Project set out to answer a simple question: if children practise the right
          movements every day, do they become more ready to learn? The programme ran in Reception classes as short daily
          sessions using everyday equipment such as bats, balls, hoops and bean bags.
        </p>
        <p>
          Motor Movers takes what the project learned and extends it. Instead of a single year group, it covers
          children from 6 months to 8 years in seven colour stages that follow how movement develops. It works in
          nurseries, schools and other early years settings, and now comes with a platform that supports staff and
          parents along the way.
        </p>
      </section>

      <section className="site-section">
        <h2>The research</h2>
        <div className="site-cards site-cards-2">
          <div className="card">
            <p className="eyebrow">Pilot 1 · 2015 to 2016</p>
            <h3>Four schools, 120 children</h3>
            <p>
              Developed with Loughborough University. Children were assessed at the start and end of Reception with the
              Movement ABC test battery and the INPP developmental test battery.
            </p>
            <ul className="site-list">
              {PILOT_SCHOOLS.map((s) => (
                <li key={s}>{s}</li>
              ))}
            </ul>
          </div>
          <div className="card">
            <p className="eyebrow">What the pilot found</p>
            <h3>Clear gains for children on the programme</h3>
            <ul className="site-list">
              <li>Over half of the children had significant movement difficulties at the start of Reception.</li>
              <li>
                Children on the programme improved their physical development scores by 18 points. The
                comparison group made no progress.
              </li>
              <li>Primitive reflex scores fell for the programme group and rose for the comparison group.</li>
              <li>Balance, manual dexterity, aiming and catching all improved more than in the comparison class.</li>
            </ul>
          </div>
          <div className="card">
            <p className="eyebrow">Pilot 2</p>
            <h3>Fifty schools across England</h3>
            <p>
              The second pilot widened the programme to 50 schools. Their feedback shaped the sessions, and research
              continues.
            </p>
          </div>
          <div className="card">
            <p className="eyebrow">In the classroom</p>
            <h3>What teachers noticed</h3>
            <ul className="site-list">
              <li>Fewer children struggling with letter formation, and better writing than in earlier years.</li>
              <li>Children concentrating in sessions and carrying that focus into independent work.</li>
              <li>Children skipping with a rope on sports day for the first time.</li>
            </ul>
          </div>
        </div>
      </section>

      <section className="site-section">
        <h2>The people behind it</h2>
        <div className="site-cards">
          {PEOPLE.map((p) => (
            <div key={p.name} className="card">
              <h3>{p.name}</h3>
              <p className="strong">{p.role}</p>
              <p className="muted">{p.text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="site-section site-cta-band">
        <h2>See how a day of Motor Movers works</h2>
        <div className="site-cta">
          <Link to="/programme" className="btn btn-big btn-light">
            The programme
          </Link>
        </div>
      </section>
    </>
  )
}
