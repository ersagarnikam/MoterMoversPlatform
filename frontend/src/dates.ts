const DAY = new Intl.DateTimeFormat('en-GB', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
})

/**
 * "Thu 5 Nov 2026": how every calendar date from the API ("2026-11-05", or the date part of an instant) is shown.
 * Read as a date, not a moment, so no time zone moves it to the day before. Empty for no date.
 */
export function day(iso: string | null | undefined): string {
  if (!iso) return ''
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  const parts = Object.fromEntries(
    DAY.formatToParts(new Date(Date.UTC(y, m - 1, d))).map((p) => [p.type, p.value]),
  )
  // Built from the parts: the formatter's own "Thu, 5 Nov 2026" has a comma the rest of the site does not use.
  return `${parts.weekday} ${parts.day} ${parts.month} ${parts.year}`
}

/** "Today", "Tomorrow", or the day: for sessions and deadlines close at hand. */
export function relativeDay(iso: string, now: Date = new Date()): string {
  const target = new Date(iso)
  const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const days = Math.round((startOf(target) - startOf(now)) / 86_400_000)
  if (days === 0) return 'Today'
  if (days === 1) return 'Tomorrow'
  return ''
}
