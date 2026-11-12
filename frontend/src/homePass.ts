import type { HomePass } from './api'
import { day } from './dates'
import { money } from './money'

/**
 * What the family sees about its Motor Movers at Home pass, and the button to offer: buy the first pass, renew one
 * that ended, or nothing while it runs or is not on sale yet (go-live task 26).
 */
export interface PassState {
  kind: 'running' | 'buy' | 'renew' | 'not-on-sale' | 'no-family'
  text: string
  action: string | null
}

export function passState(pass: HomePass): PassState {
  if (!pass.hasFamily) return { kind: 'no-family', text: '', action: null }
  if (pass.running) {
    return { kind: 'running', text: `Your family’s pass runs to ${day(pass.endsOn)}.`, action: null }
  }
  if (!pass.onSale || pass.pricePence == null) {
    return {
      kind: 'not-on-sale',
      text: 'The Motor Movers at Home pass is not on sale yet. Your children’s details are safe here until it is.',
      action: null,
    }
  }
  const price = money(pass.pricePence)
  if (pass.ended && pass.endsOn) {
    return {
      kind: 'renew',
      text: `Your pass ended on ${day(pass.endsOn)}. Your children’s records are kept; renew to carry on with the sessions.`,
      action: `Renew for ${price}`,
    }
  }
  return {
    kind: 'buy',
    text: `Sessions need a 12-month pass for your family: ${price}, for all your children and anyone you share with.`,
    action: `Buy the pass for ${price}`,
  }
}
