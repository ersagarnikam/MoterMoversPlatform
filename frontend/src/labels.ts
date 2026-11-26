import { day } from './dates'
import { money } from './money'

/**
 * Small pieces of wording that depend on a count, a date or a state, kept here so their rules are tested
 * (go-live task 88).
 */

/** A school's licence status. One that has not started yet says when it starts, never "Active". */
export function licenceStatusLabel(status: string, startsOn: string | null | undefined): string {
  switch (status) {
    case 'UPCOMING':
      return startsOn ? `Starts ${day(startsOn)}` : 'Not started'
    case 'ACTIVE':
      return 'Active'
    case 'RENEWAL_DUE':
      return 'Renewal due'
    case 'FREE_TRIAL':
      return 'Free trial'
    case 'EXPIRED':
      return 'Expired'
    default:
      return status
  }
}

/** "1 draft", "180 drafts". */
export function draftCount(count: number): string {
  return count === 1 ? '1 draft' : `${count} drafts`
}

/**
 * The button that approves every draft of a colour or module. It names the scope, not "shown": the page shows 100
 * cards at a time, and the button approves them all.
 */
export function approveAllLabel(count: number, scope: string): string {
  return count === 1 ? `Approve the 1 draft in ${scope}` : `Approve all ${count} drafts in ${scope}`
}

/** A plan's price on Plans and prices. A retired plan shows no price: its old one is not on offer. */
export function planPriceLabel(plan: { active: boolean; currentPricePence: number }): string {
  return plan.active ? `${money(plan.currentPricePence)} a year` : 'No longer on sale'
}

/**
 * One part of a family's cover in the team's history. A part refunded in full says so with the day, never "Paid",
 * and one refunded before it started has no dates to run between.
 */
export function coverEntryLine(entry: {
  startsOn: string
  endsOn: string | null
  how: string
  refundedOn?: string | null
}): string {
  if (!entry.refundedOn) {
    return `${day(entry.startsOn)} to ${day(entry.endsOn ?? entry.startsOn)} · ${entry.how}`
  }
  const refunded = `${entry.how} · Refunded on ${day(entry.refundedOn)}`
  return entry.endsOn
    ? `${day(entry.startsOn)} to ${day(entry.endsOn)} · ${refunded}`
    : `${refunded}, before it started (${day(entry.startsOn)})`
}
