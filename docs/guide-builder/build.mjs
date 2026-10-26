// Builds the guides as HTML and prints each to an A4 PDF with Chrome, from the screenshots in shots/ (capture.mjs).
//   docs/flow-guides  for partners, investors and school leaders: the overview and one guide per role (flow-content.mjs)
//   docs/user-guide   for the people who use the platform: how to do each task (user-guide-content.mjs)
// Usage: node docs/guide-builder/build.mjs [flow|user|all]   (default all; needs frontend/node_modules and Chrome)
import { createRequire } from 'node:module'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { DEMO_NOTE, OVERVIEW, ROLES } from './flow-content.mjs'
import { GETTING_STARTED, GUIDES } from './user-guide-content.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = join(HERE, '..', '..')
const { chromium } = createRequire(join(ROOT, 'frontend', 'package.json'))('@playwright/test')
const WHICH = process.argv[2] ?? 'all'
const HTML_DIR = join(HERE, 'html')
mkdirSync(HTML_DIR, { recursive: true })

const FONTS = join(ROOT, 'frontend', 'node_modules', '@fontsource')
const shot = (name) => pathToFileURL(join(HERE, 'shots', `${name}.png`)).href
const font = (family, weight) => pathToFileURL(join(FONTS, family, 'files', `${family}-latin-${weight}-normal.woff2`)).href
const DATE = 'October 2026'
const STAGES = ['#d9534f', '#e8913a', '#e6c13d', '#5aa05a', '#4a7fc1', '#5a5fa8', '#9b6bb8']
const STAGE_NAMES = ['Red', 'Orange', 'Yellow', 'Green', 'Blue', 'Indigo', 'Violet']
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const CSS = `
@font-face { font-family: Inter; src: url(${font('inter', 400)}); font-weight: 400; }
@font-face { font-family: Inter; src: url(${font('inter', 500)}); font-weight: 500; }
@font-face { font-family: Inter; src: url(${font('inter', 600)}); font-weight: 600; }
@font-face { font-family: Inter; src: url(${font('inter', 700)}); font-weight: 700; }
@font-face { font-family: Lora; src: url(${font('lora', 600)}); font-weight: 600; }
@font-face { font-family: Lora; src: url(${font('lora', 700)}); font-weight: 700; }
:root { --navy: #1f2a3c; --ink: #1f2a3c; --muted: #5b6676; --line: #e3e0d8; --paper: #fff; --cream: #f8f6f1; --teal: #2e7d6f; --teal-soft: #e3f1ee; --warn: #fdf3dc; --blue: #4a7fc1; }
@page { size: A4; margin: 16mm 16mm 18mm; }
* { box-sizing: border-box; }
html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body { margin: 0; font-family: Inter, sans-serif; font-size: 10.2pt; line-height: 1.5; color: var(--ink); background: #fff; }
h1, h2, h3 { font-family: Lora, Georgia, serif; line-height: 1.2; margin: 0; }
h2 { font-size: 17pt; margin-bottom: 3mm; }
h3 { font-size: 12.5pt; }
p { margin: 0 0 2.5mm; }
.muted { color: var(--muted); }
.stripe { display: flex; gap: 1.6mm; }
.stripe span { width: 9mm; height: 2mm; border-radius: 1mm; }

/* Cover */
.cover { height: 263mm; display: flex; flex-direction: column; break-after: page; }
.cover-band { background: var(--navy); color: #fff; border-radius: 4mm; padding: 16mm 14mm 14mm; }
.brand { display: flex; align-items: center; gap: 4mm; font-family: Lora, serif; font-weight: 700; font-size: 15pt; }
.cover h1 { font-size: 32pt; margin: 22mm 0 5mm; }
.cover .strap { font-size: 13pt; color: #d4dce6; max-width: 150mm; }
.cover-meta { margin-top: auto; display: grid; grid-template-columns: 1fr 1fr; gap: 6mm; }
.meta-box { border: 1px solid var(--line); border-radius: 3mm; padding: 5mm 6mm; }
.meta-box strong { display: block; font-size: 8.5pt; text-transform: uppercase; letter-spacing: 0.08em; color: var(--teal); margin-bottom: 1.5mm; }
.cover-hero { margin: 10mm 0; flex: 1; min-height: 0; display: flex; align-items: center; justify-content: center; }
.cover-hero img { max-width: 100%; max-height: 100%; border-radius: 3mm; border: 1px solid var(--line); box-shadow: 0 2mm 6mm rgba(31,42,60,0.12); }

/* At a glance */
.glance { break-after: page; }
.eyebrow { font-size: 8.5pt; text-transform: uppercase; letter-spacing: 0.1em; color: var(--teal); font-weight: 600; margin-bottom: 1.5mm; }
.cards { display: grid; grid-template-columns: 1fr 1fr; gap: 4mm; margin: 4mm 0 6mm; }
.card { border: 1px solid var(--line); border-radius: 3mm; padding: 4.5mm 5mm; background: var(--paper); }
.card h3 { font-size: 11pt; margin-bottom: 2mm; }
.card ul { margin: 0; padding-left: 4.5mm; }
.card li { margin-bottom: 1.2mm; }
.access { display: grid; grid-template-columns: 1fr 1fr; gap: 4mm; }
.access .yes { background: var(--teal-soft); border-color: #c4e2db; }
.access .no { background: var(--cream); }
.flow { display: flex; flex-wrap: wrap; align-items: stretch; gap: 2mm 0; margin: 3mm 0 2mm; }
.flow .node { display: flex; align-items: center; gap: 2mm; background: var(--navy); color: #fff; border-radius: 2.5mm; padding: 2.5mm 3.5mm; font-weight: 600; font-size: 9pt; }
.flow .node b { display: inline-grid; place-items: center; width: 5mm; height: 5mm; border-radius: 50%; background: #fff; color: var(--navy); font-size: 7.5pt; }
.flow .arrow { display: flex; align-items: center; padding: 0 1.6mm; color: var(--muted); font-size: 11pt; }
.note { font-size: 8.5pt; color: var(--muted); border-left: 2px solid var(--line); padding-left: 3mm; margin-top: 5mm; }

/* Steps */
.step { break-inside: avoid; padding-bottom: 6mm; }
.step-head { display: flex; align-items: baseline; gap: 3mm; margin-bottom: 2mm; }
.step-num { flex: none; display: inline-grid; place-items: center; width: 8mm; height: 8mm; border-radius: 50%; background: var(--teal); color: #fff; font-weight: 700; font-size: 10pt; }
.why { background: var(--teal-soft); border-radius: 2mm; padding: 2.2mm 3.5mm; font-size: 9.4pt; margin: 2mm 0 3mm; }
.why strong { color: var(--teal); }
figure { margin: 0 0 2mm; text-align: center; }
figure img { display: block; margin: 0 auto; max-width: 100%; max-height: 66mm; border-radius: 2.5mm; border: 1px solid var(--line); }
figure img.tall { max-height: 92mm; }
figcaption { font-size: 8.6pt; color: var(--muted); margin-top: 1.8mm; }
.pair { display: grid; grid-template-columns: 1fr 1fr; gap: 4mm; align-items: start; }
.pair figure img { max-height: 62mm; }
.phones { display: flex; gap: 8mm; justify-content: center; }
.phones figure { width: 58mm; }
.phones figure img { max-height: 112mm; border-radius: 5mm; }
.mixed { display: grid; grid-template-columns: 50mm 1fr; gap: 6mm; align-items: start; }
.mixed figure:first-child img { border-radius: 5mm; max-height: 100mm; }

/* Overview */
.section { margin-bottom: 7mm; }
.section.page { break-before: page; }
.roles-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4mm; }
.role-card { border: 1px solid var(--line); border-radius: 3mm; overflow: hidden; break-inside: avoid; }
.role-card img { display: block; width: 100%; height: 44mm; object-fit: cover; object-position: top left; border-bottom: 1px solid var(--line); }
.role-card div { padding: 3mm 4mm 3.5mm; }
.role-card h3 { font-size: 10.5pt; margin-bottom: 1mm; }
.role-card p { font-size: 9pt; margin: 0; }
.loop { display: grid; grid-template-columns: repeat(3, 1fr); gap: 3mm; margin: 3mm 0; }
.loop div { border: 1px solid var(--line); border-radius: 3mm; padding: 3.5mm 4mm; position: relative; background: var(--paper); }
.loop div b { display: block; font-size: 8.3pt; color: var(--teal); text-transform: uppercase; letter-spacing: 0.06em; margin-bottom: 1mm; }
.loop div span { font-size: 9.2pt; }
table { width: 100%; border-collapse: collapse; font-size: 9.2pt; margin: 2mm 0 3mm; }
th, td { text-align: left; padding: 2mm 2.5mm; border-bottom: 1px solid var(--line); vertical-align: top; }
th { font-weight: 600; background: var(--cream); }
td.num, th.num { text-align: right; white-space: nowrap; }
.stages { display: flex; gap: 2mm; margin: 2mm 0 3mm; }
.stages span { flex: 1; text-align: center; color: #fff; font-weight: 600; font-size: 8.5pt; padding: 2mm 0; border-radius: 2mm; }
.ticks { columns: 2; column-gap: 6mm; padding-left: 4.5mm; margin: 2mm 0; }
.ticks li { margin-bottom: 1.5mm; break-inside: avoid; }
.status-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4mm; }
.toc { margin: 3mm 0; padding: 0; list-style: none; }
.toc li { display: flex; justify-content: space-between; gap: 6mm; border-bottom: 1px solid var(--line); padding: 1.3mm 0; font-size: 9pt; }
.toc li > span:first-child { flex: none; width: 42mm; font-weight: 600; }
.part-title { break-before: page; }

/* User guide */
.intro { break-after: page; }
.task { break-inside: avoid; margin-bottom: 7mm; }
.task h3 { font-size: 12.5pt; margin-bottom: 2.5mm; }
ol.steps { margin: 0 0 3mm; padding: 0; list-style: none; counter-reset: s; }
ol.steps li { counter-increment: s; position: relative; padding-left: 9mm; margin-bottom: 1.8mm; }
ol.steps li::before { content: counter(s); position: absolute; left: 0; top: 0.2mm; width: 6mm; height: 6mm; border-radius: 50%; background: var(--teal); color: #fff; font-weight: 700; font-size: 8.5pt; display: grid; place-items: center; }
.tip { background: var(--teal-soft); border-radius: 2mm; padding: 2.2mm 3.5mm; font-size: 9.4pt; margin: 0 0 3mm; }
.tip strong { color: var(--teal); }
.faq { break-inside: avoid; border-top: 1px solid var(--line); padding: 3mm 0; }
.faq h3 { font-size: 10.5pt; margin-bottom: 1mm; font-family: Inter, sans-serif; font-weight: 600; }
.help { break-inside: avoid; margin-top: 6mm; }
`

const stripe = () => `<div class="stripe">${STAGES.map((c) => `<span style="background:${c}"></span>`).join('')}</div>`

function cover({ title, strap, hero, audience }) {
  return `<section class="cover">
    <div class="cover-band">
      <div class="brand">${stripe()} Motor Movers</div>
      <h1>${esc(title)}</h1>
      <p class="strap">${esc(strap)}</p>
    </div>
    <div class="cover-hero">${hero ? `<img src="${shot(hero)}" alt="">` : ''}</div>
    <div class="cover-meta">
      <div class="meta-box"><strong>Who this is for</strong>${esc(audience)}</div>
      <div class="meta-box"><strong>About these screens</strong>${esc(DEMO_NOTE)} Prepared ${DATE}.</div>
    </div>
  </section>`
}

function flow(nodes) {
  return `<div class="flow">${nodes
    .map((n, i) => `<div class="node"><b>${i + 1}</b>${esc(n)}</div>${i < nodes.length - 1 ? '<div class="arrow">→</div>' : ''}`)
    .join('')}</div>`
}

/** Desktop screenshots taller than they are wide get more room on the page. */
function isTall(name) {
  const png = readFileSync(join(HERE, 'shots', `${name}.png`))
  return png.readUInt32BE(20) / png.readUInt32BE(16) > 0.7
}

function figures(step) {
  const figs = step.shots.map(
    (s, i) =>
      `<figure><img src="${shot(s)}" alt=""${!step.phone && !step.mixed && isTall(s) ? ' class="tall"' : ''}><figcaption>${esc(step.captions?.[i] ?? '')}</figcaption></figure>`,
  )
  if (figs.length === 0) return ''
  if (step.mixed) return `<div class="mixed">${figs.join('')}</div>`
  if (step.phone) return `<div class="phones">${figs.join('')}</div>`
  if (figs.length === 2) return `<div class="pair">${figs.join('')}</div>`
  return figs.join('')
}

function roleBody(role) {
  return `
  <section class="glance">
    <div class="eyebrow">At a glance</div>
    <h2>${esc(role.title)}: the journey</h2>
    ${flow(role.flow)}
    <div class="cards">
      <div class="card"><h3>Who they are</h3><p>${esc(role.who)}</p></div>
      <div class="card"><h3>What they do</h3><ul>${role.does.map((d) => `<li>${esc(d)}</li>`).join('')}</ul></div>
    </div>
    <div class="access">
      <div class="card yes"><h3>What they can see</h3><p>${esc(role.sees)}</p></div>
      <div class="card no"><h3>What stays out of view</h3><p>${esc(role.cannot)}</p></div>
    </div>
    <p class="note">Who sees what is enforced by the platform itself, not only by hiding things on screen. The same rules apply on the website and in the parent app.</p>
  </section>
  <section>
    <div class="eyebrow">Step by step</div>
    <h2>How it works</h2>
    ${role.steps
      .map(
        (s, i) => `<div class="step">
        <div class="step-head"><span class="step-num">${i + 1}</span><h3>${esc(s.title)}</h3></div>
        <p>${esc(s.body)}</p>
        <div class="why"><strong>Why it matters:</strong> ${esc(s.why)}</div>
        ${figures(s)}
      </div>`,
      )
      .join('')}
  </section>`
}

const HERO = {
  'class-teacher': 'teacher-today',
  'head-teacher': 'head-dashboard',
  'trust-lead': 'trust-dashboard',
  practitioner: 'practitioner-training',
  'parent-or-carer': 'app-today',
  trainer: 'trainer-live',
  'motor-movers-team': 'admin-payments',
}
const AUDIENCE = 'Partners, investors and school leaders. No technical knowledge needed.'

function overviewBody() {
  const stageBar = `<div class="stages">${STAGES.map((c, i) => `<span style="background:${c}">${i + 1} · ${STAGE_NAMES[i]}</span>`).join('')}</div>`
  const roleCards = [
    ['Class teacher', 'teacher-today', 'Runs the 10-minute session every school day, assesses children and sees each child’s readiness profile.'],
    ['Head teacher and SENCo', 'head-dashboard', 'Sees progress and participation across the school, sets the school up and checks payments.'],
    ['Trust or group lead', 'trust-dashboard', 'Compares licences, results and spending across a group of schools, without seeing individual children.'],
    ['Practitioner', 'practitioner-training', 'Books the live practitioner course and works towards the yearly-renewed certificate.'],
    ['Parent or carer', 'app-today', 'Follows their child’s stage in the parent app, does short home activities and takes the parent course.'],
    ['Trainer', 'trainer-live', 'Runs the live course sessions given to them on Zoom, keeps the register and completes their cohorts.'],
    ['Motor Movers team', 'admin-content-wording', 'Publishes approved content, signs schools up, runs courses and trainers, and tracks income.'],
  ]
    .map(([t, s, d]) => `<div class="role-card"><img src="${shot(s)}" alt=""><div><h3>${t}</h3><p>${d}</p></div></div>`)
    .join('')

  return `
  <section class="section">
    <div class="eyebrow">The programme</div>
    <h2>What Motor Movers is</h2>
    <p>Motor Movers is a movement programme for primary schools and early-years settings. Many children start school without the movement skills that learning relies on, such as balance, coordination and control of their hands. Motor Movers gives them a few minutes of the right movement every school day, built on the Movement for Learning research.</p>
    <p>The programme runs in seven colour stages. Each class works through the stage that suits it, and schools can license all seven or only the ones their children need.</p>
    ${stageBar}
    <p>The platform is the single place where the programme happens: teachers run sessions and assess children, leaders see progress, families continue at home, practitioners train, and the Motor Movers team runs the whole service.</p>
  </section>

  <section class="section">
    <div class="eyebrow">How it fits together</div>
    <h2>One platform, seven kinds of user</h2>
    <div class="loop">
      <div><b>1 · The Motor Movers team</b><span>publishes approved sessions and activities, and licenses each school for its stages.</span></div>
      <div><b>2 · Teachers</b><span>run a guided 10-minute session daily, with ongoing assessment and start and end developmental assessments.</span></div>
      <div><b>3 · Head teachers</b><span>see participation and progress for every class, approve staff training and act where support is needed.</span></div>
      <div><b>4 · Trusts and school groups</b><span>see summaries across their schools to plan renewals and budgets.</span></div>
      <div><b>5 · Families</b><span>link their child with a school code and keep the movement going at home.</span></div>
      <div><b>6 · Practitioners</b><span>take the live course, its quizzes and the certificate, growing the number of trained adults.</span></div>
      <div><b>7 · Trainers</b><span>run the live sessions on Zoom inside the platform, so training grows beyond one person.</span></div>
    </div>
    <p class="muted">Each of these people has a step-by-step guide in this pack, with the screens they see.</p>
  </section>

  <section class="section page">
    <div class="eyebrow">Who uses it</div>
    <h2>What each person sees</h2>
    <div class="roles-grid">${roleCards}</div>
  </section>

  <section class="section page">
    <div class="eyebrow">Trust and safety</div>
    <h2>Built to protect children</h2>
    <p>Schools trust Motor Movers with records about young children. These protections are built into how the platform works, not left to good intentions:</p>
    <ul class="ticks">
      <li><strong>Never a diagnosis.</strong> The platform assesses readiness to learn. Every readiness profile says it is not a diagnosis, and the team cannot approve wording that sounds like one.</li>
      <li><strong>Initials by default.</strong> Children are shown by initials unless the school chooses to show full names.</li>
      <li><strong>Everyone sees only their part.</strong> Teachers see their own class, heads their school, trusts and school groups summaries only, parents only the children linked to them.</li>
      <li><strong>Parents are invited by the school.</strong> A one-time code links a parent to a child, and the school can remove the link at any time.</li>
      <li><strong>Every view is logged.</strong> Each time someone opens a child’s record it is written to an access log the head can review.</li>
      <li><strong>Records do not linger.</strong> Children who leave are deleted automatically a year after their leaving date.</li>
      <li><strong>Video is off by default.</strong> Assessment video needs the school to opt in and parents to consent.</li>
      <li><strong>Data stays in the region.</strong> Each deployment serves one data region, so UK schools’ data is hosted in the UK.</li>
      <li><strong>Secure accounts.</strong> Staff are invited by email, passwords are at least 10 characters, repeated sign-in attempts are slowed down, and switched-off accounts stop working within a minute.</li>
      <li><strong>Safe live training.</strong> Adults only on camera, and no discussion of an individual child’s condition.</li>
      <li><strong>Card details never touch the platform.</strong> Card payments are taken on the payment provider’s own secure page.</li>
    </ul>
  </section>

  <section class="section page">
    <div class="eyebrow">The business</div>
    <h2>How the platform earns</h2>
    <p>Income comes from two sources, both managed in the platform: yearly school licences and live training courses. Schools, families and practitioners can pay by card online; staff course places are invoiced to the school once the head approves them. The team sees income by month, school, trust or group, family and type.</p>
    <table>
      <thead><tr><th>Source</th><th>Who pays</th><th>What it is</th><th class="num">Price</th></tr></thead>
      <tbody>
        <tr><td>Early years plan</td><td>Early-years setting</td><td>Yearly programme licence</td><td class="num">£450 / year</td></tr>
        <tr><td>Primary core plan</td><td>Primary school</td><td>Yearly programme licence</td><td class="num">£900 / year</td></tr>
        <tr><td>Full programme plan</td><td>Primary school</td><td>Yearly programme licence</td><td class="num">£1,750 / year</td></tr>
        <tr><td>Trust plan</td><td>Each school in a trust or group</td><td>Yearly licence at trust pricing</td><td class="num">£1,487.50 / year</td></tr>
        <tr><td>Practitioner course</td><td>Practitioner or employer</td><td>4 live sessions, assessment, certificate</td><td class="num">£180, then £60 / year</td></tr>
        <tr><td>Parent course</td><td>Family</td><td>3 live sessions and home activities</td><td class="num">£45 per family</td></tr>
      </tbody>
    </table>
    <p class="muted">Licence prices are the proposed plans built into the platform, and each licence covers the stages the team sets for that school. Course prices are those in the demonstration; the team can change them in the platform at any time. A free trial lets a school start before it buys.</p>
    <figure><img src="${shot('admin-payments')}" alt="" style="max-height:88mm"><figcaption>The team’s Payments page: money received, outstanding invoices and income month by month.</figcaption></figure>
  </section>

  <section class="section page">
    <div class="eyebrow">Where it stands</div>
    <h2>Status in ${DATE}</h2>
    <div class="status-grid">
      <div class="card yes"><h3>Built and tested</h3><ul>
        <li>Public website, and the web platform for all seven kinds of user.</li>
        <li>Parent phone app for iPhone and Android, with phone notifications.</li>
        <li>Live course sessions on Zoom inside the platform, with a training team, registers, quizzes and certificates emailed as PDFs.</li>
        <li>Content approval with the wording check, school onboarding, staff training approved by heads, and payments by card online.</li>
        <li>Checked automatically on every change: 109 checks of the system behind the screens, 34 of the web app’s building blocks, 61 that click through the screens as each kind of user, and a load test of 50 learners joining a live session at once.</li>
        <li>Production set-up tried in full: secure web address (HTTPS), nightly backups with copies off the server, and a refusal to start with unsafe settings.</li>
      </ul></div>
      <div class="card no"><h3>Next steps</h3><ul>
        <li>Go live: a UK server, the web address and an email account for invitations.</li>
        <li>Publish the parent app in the Apple and Google app stores, which also switches on phone notifications.</li>
        <li>Connect the Motor Movers card payment account, and confirm VAT.</li>
        <li>Upload the filmed session videos and rhyme recordings.</li>
        <li>The new developmental and exercise assessments, from Pat’s milestone charts.</li>
      </ul></div>
    </div>
    <h3 style="margin-top:7mm">The guides in this pack</h3>
    <ul class="toc">
      ${ROLES.map((r) => `<li><span>${esc(r.title)}</span><span class="muted">${esc(r.strap)}</span></li>`).join('')}
    </ul>
  </section>`
}

function page(title, body) {
  return `<!doctype html><html lang="en-GB"><head><meta charset="utf-8"><title>${esc(title)}</title><style>${CSS}</style></head><body>${body}</body></html>`
}

// The user guide: for each role, what they can do, getting started, then "how do I…?" tasks and answers.
const USER_AUDIENCE = 'Everyone who uses the platform in this role. No technical knowledge needed.'

function task(t) {
  return `<div class="task">
    <h3>${esc(t.title)}</h3>
    <ol class="steps">${t.steps.map((s) => `<li>${esc(s)}</li>`).join('')}</ol>
    ${t.tip ? `<div class="tip"><strong>Good to know:</strong> ${esc(t.tip)}</div>` : ''}
    ${t.shots ? figures(t) : ''}
  </div>`
}

function userBody(g) {
  return `
  <section class="intro">
    <div class="eyebrow">${esc(g.title)}</div>
    <h2>What you can do</h2>
    <div class="card"><ul>${g.can.map((c) => `<li>${esc(c)}</li>`).join('')}</ul></div>
    <h2 style="margin-top:8mm">${esc(GETTING_STARTED.title)}</h2>
    ${GETTING_STARTED.tasks.map(task).join('')}
  </section>
  <section>
    <div class="eyebrow">Step by step</div>
    <h2>How do I…?</h2>
    ${g.tasks.map(task).join('')}
  </section>
  <section class="help">
    <div class="eyebrow">Help</div>
    <h2>Questions and answers</h2>
    ${g.faq.map((f) => `<div class="faq"><h3>${esc(f.q)}</h3><p>${esc(f.a)}</p></div>`).join('')}
    <p class="note">Something else? Contact the Motor Movers team: the contact details are on the Motor Movers website.</p>
  </section>`
}

const USER_HERO = { ...HERO, practitioner: 'practitioner-quiz' }

const userDocs = [
  ...GUIDES.map((g, i) => ({
    out: 'user-guide',
    file: `${i + 1} Motor Movers user guide - ${g.title}`,
    title: `Motor Movers user guide: ${g.title}`,
    body: cover({ title: `${g.title} user guide`, strap: g.strap, hero: USER_HERO[g.slug], audience: USER_AUDIENCE }) + userBody(g),
  })),
  {
    out: 'user-guide',
    file: 'Motor Movers user guide - all roles',
    title: 'Motor Movers user guide',
    body:
      cover({
        title: 'User guide',
        strap: 'How to use the Motor Movers platform, role by role: getting started, everyday tasks step by step, and answers to common questions.',
        hero: 'teacher-today',
        audience: 'Everyone who uses the platform. No technical knowledge needed.',
      }) +
      `<section class="intro"><div class="eyebrow">Contents</div><h2>The guides in this pack</h2><ul class="toc">${GUIDES.map(
        (g) => `<li><span>${esc(g.title)}</span><span class="muted">${esc(g.strap)}</span></li>`,
      ).join('')}</ul></section>` +
      GUIDES.map(
        (g) =>
          `<div class="part-title"></div>${cover({ title: `${g.title} user guide`, strap: g.strap, hero: USER_HERO[g.slug], audience: USER_AUDIENCE })}${userBody(g)}`,
      ).join(''),
  },
]

const flowDocs = [
  {
    file: '0 Motor Movers platform overview',
    title: 'Motor Movers platform overview',
    body: cover({
      title: 'Platform overview',
      strap: OVERVIEW.strap,
      hero: 'head-dashboard',
      audience: 'Investors, partners and school leaders. No technical knowledge needed.',
    }) + overviewBody(),
  },
  ...ROLES.map((r, i) => ({
    file: `${i + 1} Motor Movers guide - ${r.title}`,
    title: `Motor Movers guide: ${r.title}`,
    body: cover({ title: `${r.title} guide`, strap: r.strap, hero: HERO[r.slug], audience: AUDIENCE }) + roleBody(r),
  })),
]

// The complete pack: the overview, then every guide, each starting on a new page.
flowDocs.push({
  file: 'Motor Movers platform guides - complete pack',
  title: 'Motor Movers platform guides',
  body:
    cover({
      title: 'Platform guides',
      strap: 'How each person uses the Motor Movers platform, step by step, with the screens they see.',
      hero: 'teacher-player',
      audience: 'Investors, partners and school leaders. No technical knowledge needed.',
    }) +
    overviewBody() +
    ROLES.map((r) => `<div class="part-title"></div>${cover({ title: `${r.title} guide`, strap: r.strap, hero: HERO[r.slug], audience: AUDIENCE })}${roleBody(r)}`).join(''),
})

const docs = [
  ...(WHICH === 'user' ? [] : flowDocs.map((d) => ({ out: 'flow-guides', ...d }))),
  ...(WHICH === 'flow' ? [] : userDocs),
]
const browser = await chromium.launch({ channel: process.env.PW_CHANNEL || 'chrome' })
const ctx = await browser.newContext()
for (const d of docs) {
  const OUT = join(ROOT, 'docs', d.out)
  mkdirSync(OUT, { recursive: true })
  const htmlPath = join(HTML_DIR, `${d.file}.html`)
  writeFileSync(htmlPath, page(d.title, d.body))
  const p = await ctx.newPage()
  await p.goto(pathToFileURL(htmlPath).href, { waitUntil: 'load' })
  await p.evaluate(() => document.fonts.ready)
  await p.pdf({
    path: join(OUT, `${d.file}.pdf`),
    format: 'A4',
    printBackground: true,
    preferCSSPageSize: true,
    displayHeaderFooter: true,
    headerTemplate: '<span></span>',
    footerTemplate: `<div style="width:100%;font-family:Inter,Arial,sans-serif;font-size:7.5pt;color:#8a93a0;padding:0 16mm;display:flex;justify-content:space-between"><span>${esc(d.title)} · ${DATE}</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>`,
  })
  await p.close()
  console.log('built', d.file)
}
await browser.close()
