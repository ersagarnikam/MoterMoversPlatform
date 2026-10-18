/** Facts the public website repeats on several pages. Change them here, not in the page text. */
export const CONTACT_EMAIL = 'movementforlearningproject@gmail.com'
export const CONTACT_PHONE = '01202 826 718'

/** A mailto link that opens a new email with a subject and a template body for the sender to fill in. */
export function mailto(subject: string, body = ''): string {
  const q = new URLSearchParams({ subject, body }).toString().replace(/\+/g, '%20')
  return `mailto:${CONTACT_EMAIL}?${q}`
}

export const ENQUIRY_BODY =
  'Name:\nSchool or setting:\nPhone:\nNumber of classes:\nStages we are interested in:\n\nMessage:\n'

/** The kinds of sign-in offered on the website, each pointing at the sign-in page for that role. */
export const SIGN_IN_ROLES = [
  {
    key: 'teacher',
    label: 'Class teacher',
    blurb: "Today's session, screening and your class's readiness profiles.",
    demo: 'teacher@oakfield.test',
  },
  {
    key: 'head',
    label: 'Head teacher or SENCo',
    blurb: 'School progress, flagged classes, governor summaries and school setup.',
    demo: 'head@oakfield.test',
  },
  {
    key: 'trust',
    label: 'Trust lead',
    blurb: 'Schools, licences and summary results across your trust.',
    demo: 'trust@northbridge.test',
  },
  {
    key: 'practitioner',
    label: 'Practitioner',
    blurb: 'Your Motor Movers course, live sessions and bookings.',
    demo: 'practitioner@example.test',
  },
  {
    key: 'parent',
    label: 'Parent or carer',
    blurb: 'The parent course. Home activities are in the parent app.',
    demo: 'parent@example.test',
  },
  {
    key: 'team',
    label: 'Motor Movers team',
    blurb: 'Content approval, schools, licences and trusts.',
    demo: 'admin@motormovers.test',
  },
] as const

export type SignInRole = (typeof SIGN_IN_ROLES)[number]

export function signInRole(key: string | null): SignInRole | undefined {
  return SIGN_IN_ROLES.find((r) => r.key === key)
}
