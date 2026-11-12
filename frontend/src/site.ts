/** Facts the public website repeats on several pages. Change them here, not in the page text. */

// Our contact details are not here: they are the deployment's legal entity's email and phone, from the country
// settings (useContacts in src/country.ts), and are left off while not set (go-live task 28).

/**
 * The credit to the project Motor Movers grew from, on the home page and in the footer. Its wording is waiting for
 * Pat (go-live task 28): Movement for Learning will not be reopened. Change it only here.
 */
export const PROJECT_CREDIT = {
  /** The home page's eyebrow. */
  eyebrow: 'From the Movement for Learning Project',
  /** The website footer. */
  footer: 'A programme from the Movement for Learning Project.',
} as const

/**
 * A mailto link to `address` that opens a new email with a subject and a template body for the sender to fill
 * in; null with no address, so a page leaves the link off rather than writing to no one.
 */
export function mailto(address: string | null | undefined, subject: string, body = ''): string | null {
  if (!address) return null
  const q = new URLSearchParams({ subject, body }).toString().replace(/\+/g, '%20')
  return `mailto:${address}?${q}`
}

export const ENQUIRY_BODY =
  'Name:\nSchool or setting:\nPhone:\nNumber of classes:\nStages we are interested in:\n\nMessage:\n'

/** The kinds of sign-in offered on the website, each pointing at the sign-in page for that role. */
export const SIGN_IN_ROLES = [
  {
    key: 'teacher',
    label: 'Class teacher',
    blurb: "Today's session, assessing children and each child's profile.",
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
    label: 'Trust or group lead',
    blurb: 'Schools, licences and summary results across your trust or school group.',
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
  {
    key: 'trainer',
    label: 'Trainer',
    blurb: 'The live course sessions you run, and their registers.',
    demo: 'trainer@motormovers.test',
  },
] as const

export type SignInRole = (typeof SIGN_IN_ROLES)[number]

export function signInRole(key: string | null): SignInRole | undefined {
  return SIGN_IN_ROLES.find((r) => r.key === key)
}
