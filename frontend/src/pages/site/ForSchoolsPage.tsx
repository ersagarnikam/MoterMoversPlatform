import { useState } from 'react'
import EnquiryForm from '../../components/EnquiryForm'
import type { EnquiryKind } from '../../api'
import { CONTACT_EMAIL, CONTACT_PHONE, ENQUIRY_BODY, mailto } from '../../site'

const INCLUDED = [
  'An annual licence to run your chosen colour stages in your setting',
  'The platform for all your staff: daily sessions, assessment, profiles and dashboards',
  'A one-hour staff induction before the first session',
  'Session videos, rhymes and guidance for each stage',
  'An annual conference for participating schools',
  'Two Motor Movers Meet and Greets',
  'The parent app and parent course for your families',
]

const STEPS = [
  {
    title: 'Get in touch',
    text: 'Email us with your setting, how many classes you have and the stages you are interested in.',
  },
  {
    title: 'Choose your stages',
    text: 'We send the price guide and help you pick all seven stages or just the ones your children need.',
  },
  {
    title: 'Agree the licence',
    text: 'With your head teacher’s or manager’s agreement, we set up your annual licence.',
  },
  {
    title: 'Start moving',
    text: 'We set up your school on the platform and invite your staff. After the induction, they run their first session.',
  },
]

export default function ForSchoolsPage() {
  // The enquiry form opens in place under the button that asked for it.
  const [open, setOpen] = useState<EnquiryKind | null>(null)
  return (
    <>
      <section className="site-page-head">
        <p className="eyebrow eyebrow-light">For schools and settings</p>
        <h1>Bring Motor Movers to your setting</h1>
        <p className="site-lead">
          For primary schools, nurseries and other early years settings. Low cost, run by your own staff, and built to
          fit into the school day.
        </p>
      </section>

      <section className="site-section">
        <div className="site-split">
          <div>
            <h2>The licence</h2>
            <p>
              Joining gives your setting an annual licence to use the Motor Movers colour programmes. You can take all
              seven stages or only the ones you need, so you pay for what your children will use.
            </p>
            <p>Ask us for the programme price guide and we will help you work out the right stages.</p>
            {open !== 'PRICE_GUIDE' && (
              <button type="button" className="btn btn-primary" onClick={() => setOpen('PRICE_GUIDE')}>
                Ask for the price guide
              </button>
            )}
          </div>
          <div className="card">
            <h3>What is included</h3>
            <ul className="site-list">
              {INCLUDED.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        </div>
        {/* Full width under the licence, so the price chart has room. */}
        {open === 'PRICE_GUIDE' && <EnquiryForm kind="PRICE_GUIDE" onClose={() => setOpen(null)} />}
      </section>

      <section className="site-section">
        <h2>How to join</h2>
        <ol className="site-steps site-steps-4">
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
      </section>

      <section className="site-section">
        <div className="site-cards site-cards-2">
          <div className="card">
            <h3>Trusts and school groups</h3>
            <p>
              Academy trusts, other groups of schools and independent groups: the group lead sees every school’s licence
              and summary results in one place. Schools choose whether to share
              more than the summary.
            </p>
          </div>
          <div className="card">
            <h3>Practitioners</h3>
            <p>
              Courses with weekly live sessions teach practitioners to run the programme well. Book a place once your
              account is set up.
            </p>
          </div>
        </div>
      </section>

      <section className="site-section site-cta-band" id="contact">
        <h2>Apply for Motor Movers</h2>
        <p>
          Email <a href={mailto('Apply for Motor Movers', ENQUIRY_BODY)}>{CONTACT_EMAIL}</a> or call {CONTACT_PHONE}.
          We reply by email, usually within a week.
        </p>
        {open === 'APPLY' ? (
          <EnquiryForm kind="APPLY" onClose={() => setOpen(null)} />
        ) : (
          <div className="site-cta">
            <button type="button" className="btn btn-big btn-light" onClick={() => setOpen('APPLY')}>
              Apply online
            </button>
            <a className="btn btn-big btn-ghost-dark" href={mailto('Apply for Motor Movers', ENQUIRY_BODY)}>
              Email us to apply
            </a>
          </div>
        )}
      </section>
    </>
  )
}
