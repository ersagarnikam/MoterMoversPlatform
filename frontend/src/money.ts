const whole = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP', maximumFractionDigits: 0 })
const exact = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' })

/** £180, £1,487.50: pence shown only when there are some. */
export function gbp(pence: number): string {
  return (pence % 100 === 0 ? whole : exact).format(pence / 100)
}
