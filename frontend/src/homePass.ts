import { HOME_PROGRAMME } from './brand'
import type { HomePass } from './api'
import { day } from './dates'
import { money } from './money'

/**
 * What the family sees about its cover on the Programme at Home, and the one thing to offer: subscribe monthly, or
 * manage the subscription on Stripe's own page (go-live task 68; the 12-month pass of task 26 is no longer sold, but
 * one that still runs is shown).
 */
export interface PassState {
  kind:
    | 'no-family'
    | 'active'
    | 'cancelling'
    | 'past-due'
    | 'covered'
    | 'subscribe'
    | 'ended'
    | 'not-on-sale'
  heading: string
  text: string
  action: 'subscribe' | 'manage' | null
  actionLabel: string | null
}

/**
 * "Please contact us at hello@example.com or 0118 000 0000 to subscribe": the country's contact details, leaving out
 * any not yet confirmed (task 82).
 */
export function contactUs(pass: Pick<HomePass, 'contactEmail' | 'contactPhone'>): string {
  const ways = [pass.contactEmail, pass.contactPhone].filter((v): v is string => !!v)
  return `Please contact us${ways.length ? ` at ${ways.join(' or ')}` : ''} to subscribe.`
}

/** Sessions and the starting check are open: covered today and this parent's induction done. */
export function sessionsOpen(pass: HomePass | null): boolean {
  return !!pass && pass.running && pass.inductionDone
}

/** Whether the card says something the family must act on (so it shows even where it is otherwise left out). */
export function needsAttention(pass: HomePass): boolean {
  return pass.hasFamily && (!pass.running || pass.subscription === 'PAST_DUE')
}

export function passState(pass: HomePass): PassState {
  const none = { action: null, actionLabel: null } as const
  if (!pass.hasFamily) return { kind: 'no-family', heading: '', text: '', ...none }
  const price = pass.pricePence == null ? '' : money(pass.pricePence)
  const manage = pass.canManage
    ? ({ action: 'manage', actionLabel: 'Manage' } as const)
    : none
  const whoManages = pass.canManage ? '' : ' The parent or carer who subscribed manages it.'

  if (pass.subscription === 'ACTIVE') {
    return {
      kind: 'active',
      heading: 'Your subscription',
      text:
        `Your family subscribes to the ${HOME_PROGRAMME} for ${price} a month. ` +
        `The next payment is taken on ${day(pass.renewsOn)}.${whoManages}`,
      ...manage,
    }
  }
  if (pass.subscription === 'CANCELLING') {
    return {
      kind: 'cancelling',
      heading: 'Your subscription is ending',
      text:
        `Your subscription has been cancelled, and no more payments will be taken. ` +
        `Sessions stay open until ${day(pass.endsOn)}. Your children’s records are kept.${whoManages}`,
      ...manage,
    }
  }
  if (pass.subscription === 'PAST_DUE') {
    return {
      kind: 'past-due',
      heading: 'This month’s payment did not go through',
      text:
        (pass.running
          ? `Sessions stay open until ${day(pass.endsOn)}. `
          : 'Sessions have stopped until a payment goes through. ') +
        (pass.canManage
          ? 'We will try your card again. To pay with another card, choose Manage.'
          : 'We will try the card again. The parent or carer who subscribed can change the card.'),
      ...manage,
    }
  }

  const subscribe = pass.canSubscribe
    ? ({ action: 'subscribe', actionLabel: `Subscribe for ${price} a month` } as const)
    : none
  if (pass.running) {
    // A 12-month pass, an extension by the team, or the rest of a month already paid for.
    return {
      kind: 'covered',
      heading: 'Your family is covered',
      text:
        `Sessions are open until ${day(pass.endsOn)}.` +
        (!pass.onSale
          ? ''
          : pass.canSubscribe
            ? ' To carry on after that, subscribe monthly.'
            : ` To carry on after that, you can subscribe from that day, for ${price} a month.`),
      ...subscribe,
    }
  }
  if (!pass.onSale || pass.pricePence == null) {
    return {
      kind: 'not-on-sale',
      heading: 'Monthly subscription',
      text: `The ${HOME_PROGRAMME} is not on sale yet. Your children’s details are safe here until it is.`,
      ...none,
    }
  }
  if (pass.ended && pass.endsOn) {
    return {
      kind: 'ended',
      heading: 'Sessions have stopped',
      text: `Your family was covered until ${day(pass.endsOn)}. Your children’s records are kept; subscribe to carry on with the sessions.`,
      ...subscribe,
    }
  }
  return {
    kind: 'subscribe',
    heading: 'Monthly subscription',
    text:
      `Sessions need a subscription for your family: ${price} a month, for all your children and anyone you share with. ` +
      'You can cancel at any time, and keep the month you have paid for.',
    ...subscribe,
  }
}
