import type { RosterEntry } from '../api'

/**
 * How a child appears in a class roster: their initials, always. When the school shows full names, the full
 * name is the hover text and what screen readers announce; when it does not, the API never sends the name.
 * Returns the props for the roster button or link and its contents.
 */
export function rosterChild(c: RosterEntry, action: string, label?: string) {
  const fullName = c.name !== c.initials ? c.name : null
  return {
    props: {
      title: fullName ?? undefined,
      'aria-label': `${fullName ?? c.initials}, ${action}`,
    },
    content: (
      <>
        <span className="avatar" aria-hidden="true">
          {c.initials.replace(/\./g, '').slice(0, 3)}
        </span>
        <span aria-hidden="true">{label ?? c.initials}</span>
      </>
    ),
  }
}
