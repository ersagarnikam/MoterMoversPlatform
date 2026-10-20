import { Link } from 'react-router-dom'
import { STAGES } from '../../stages'

const STEPS = [
  {
    title: 'Assess',
    text: 'Staff assess each child one movement at a time. Each child gets a readiness profile showing which colour stage to start at.',
  },
  {
    title: 'Move every day',
    text: 'The whole class follows a short session every school day: a warm-up, a few activities and a cool-down, with rhymes and video.',
  },
  {
    title: 'See progress',
    text: 'Teachers, heads and trusts or school groups see participation and progress, so support goes where it is needed.',
  },
]

const AUDIENCES = [
  { title: 'Class teachers', text: "Today's session ready to play, ongoing assessment, and each child's readiness profile.", to: '/programme' },
  { title: 'Heads and SENCos', text: 'Progress across the school, classes that need support, and summaries for governors.', to: '/for-schools' },
  { title: 'Trusts and school groups', text: 'Licences and summary results for every school in the trust or group.', to: '/for-schools' },
  { title: 'Practitioners', text: 'Courses with live sessions to learn to run the programme well.', to: '/for-schools' },
  { title: 'Parents and carers', text: 'A parent course and an app with home activities at your child’s stage.', to: '/for-families' },
]

const BENEFITS = [
  {
    title: 'Physical',
    items: ['Balance and posture', 'Muscle tone and stamina', 'Body awareness', 'Grip, fine and gross motor skills'],
  },
  {
    title: 'Ready to learn',
    items: ['Sitting still and paying attention', 'Listening and language', 'Self-control and persistence', 'Pencil grip and letter formation'],
  },
  {
    title: 'Social and emotional',
    items: ['Independence', 'Teamwork and leadership', 'Confidence', 'Self-esteem'],
  },
]

export default function HomePage() {
  return (
    <>
      <section className="site-hero">
        <div className="site-hero-inner">
          <p className="eyebrow eyebrow-light">From the Movement for Learning Project</p>
          <h1>Movement that gets children ready to learn</h1>
          <p className="site-lead">
            Motor Movers is a daily movement programme that builds the balance, coordination and hand control children
            need in the classroom. Seven colour stages cover children from 6 months to 8 years, and staff run it in
            class with everyday equipment.
          </p>
          <div className="site-cta">
            <Link to="/for-schools" className="btn btn-big btn-light">
              Bring Motor Movers to your setting
            </Link>
            <Link to="/sign-in" className="btn btn-big btn-ghost-dark">
              Sign in
            </Link>
          </div>
          <ol className="hero-stages" aria-label="The seven colour stages">
            {STAGES.map((s) => (
              <li key={s.key}>
                <span className="swatch" style={{ background: s.colour }} aria-hidden="true" />
                {s.label}
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="site-section">
        <div className="site-split">
          <div>
            <p className="eyebrow">Why movement matters</p>
            <h2>Many children start school without the movement skills learning relies on</h2>
          </div>
          <div>
            <p>
              Sitting at a table, holding a pencil, copying from the board and taking turns all depend on balance,
              muscle tone and coordination. When those are still developing, children work harder to do things their
              classmates find easy.
            </p>
            <p>
              In the Movement for Learning pilot, more than half of the Reception children assessed had significant
              movement difficulties that could affect their learning, in state and independent schools alike. Motor
              Movers builds on that work.
            </p>
            <Link to="/about">Read about the research</Link>
          </div>
        </div>
      </section>

      <section className="site-section site-band" aria-label="The programme in numbers">
        <div className="site-stats">
          <div>
            <strong>7</strong>
            <span>colour stages, from 6 months to 8 years</span>
          </div>
          <div>
            <strong>10 min</strong>
            <span>a day, with the whole class</span>
          </div>
          <div>
            <strong>+18</strong>
            <span>points gained in physical development scores in the first pilot</span>
          </div>
          <div>
            <strong>50</strong>
            <span>schools in the second pilot</span>
          </div>
        </div>
      </section>

      <section className="site-section">
        <p className="eyebrow">How it works</p>
        <h2>Assess, move every day, see progress</h2>
        <ol className="site-steps">
          {STEPS.map((s, i) => (
            <li key={s.title} className="card">
              <span className="step-number" aria-hidden="true">
                {i + 1}
              </span>
              <h3>{s.title}</h3>
              <p>{s.text}</p>
            </li>
          ))}
        </ol>
        <p className="muted">
          Assessment shows readiness for the programme stages. It is not a diagnosis or a clinical assessment.
        </p>
      </section>

      <section className="site-section">
        <div className="site-split">
          <div>
            <p className="eyebrow">Seven colour stages</p>
            <h2>Stages follow development, not age</h2>
          </div>
          <div>
            <p>
              Each stage has its own colour, from Red to Violet, and follows the order in which movement skills develop.
              Children work at the stage that fits them rather than the one their age suggests.
            </p>
            <p>Settings can take all seven stages or only the ones their children need.</p>
            <Link to="/programme">See how the programme works</Link>
          </div>
        </div>
      </section>

      <section className="site-section">
        <p className="eyebrow">What children gain</p>
        <h2>More than physical skills</h2>
        <div className="site-cards site-cards-3">
          {BENEFITS.map((b) => (
            <div key={b.title} className="card">
              <h3>{b.title}</h3>
              <ul className="site-list">
                {b.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </section>

      <section className="site-section">
        <p className="eyebrow">One platform for everyone involved</p>
        <h2>Who Motor Movers is for</h2>
        <div className="site-cards">
          {AUDIENCES.map((a) => (
            <Link key={a.title} to={a.to} className="card site-card-link">
              <h3>{a.title}</h3>
              <p>{a.text}</p>
            </Link>
          ))}
        </div>
      </section>

      <section className="site-section">
        <figure className="site-quote">
          <blockquote>
            Teachers in the pilot schools saw children who were readier to learn, better able to sit still and
            concentrate, and holding their pencils better. Fewer children struggled with letter formation.
          </blockquote>
          <figcaption>What teachers noticed in the Movement for Learning pilot</figcaption>
        </figure>
      </section>

      <section className="site-section site-cta-band">
        <h2>Ready to start?</h2>
        <p>Tell us about your setting and we will send the price guide and help you choose your stages.</p>
        <div className="site-cta">
          <Link to="/for-schools" className="btn btn-big btn-light">
            How to join
          </Link>
          <Link to="/sign-in" className="btn btn-big btn-ghost-dark">
            Already using Motor Movers? Sign in
          </Link>
        </div>
      </section>
    </>
  )
}
