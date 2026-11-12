import type { Contacts } from '../country'
import { mailto } from '../site'

/**
 * "email hello@… or call 0118 …", "email hello@…", "call 0118 …", or nothing while neither is set, for a sentence
 * the caller writes around it ({@link hasContacts} says whether there is one). Never the old project's details.
 */
export default function ContactWays({
  contacts,
  subject = 'Motor Movers',
  body,
  capital = false,
}: {
  contacts: Contacts
  subject?: string
  body?: string
  capital?: boolean
}) {
  const href = mailto(contacts.email, subject, body)
  const email = capital ? 'Email' : 'email'
  const call = capital && !href ? 'Call' : 'call'
  return (
    <>
      {href && (
        <>
          {email} <a href={href}>{contacts.email}</a>
        </>
      )}
      {href && contacts.phone && ' or '}
      {contacts.phone && (
        <>
          {call} <span className="nowrap">{contacts.phone}</span>
        </>
      )}
    </>
  )
}

export function hasContacts(contacts: Contacts): boolean {
  return !!(contacts.email || contacts.phone)
}
