// Amounts in the deployment's currency (UK pounds, Irish euros, Australian dollars; go-live task 25). Amounts are held
// in the currency's minor unit (pence or cents; the *Pence names stay). The currency and locale come from
// GET /api/settings: App sets them once they arrive, and until then amounts show in pounds, as the UK deployment does.

let currency = 'GBP'
let locale = 'en-GB'
let taxLabel = 'VAT'
let whole = formatter(0)
let exact = formatter()
let digits = exact.resolvedOptions().maximumFractionDigits ?? 2

function formatter(fractionDigits?: number): Intl.NumberFormat {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    ...(fractionDigits === undefined ? {} : { minimumFractionDigits: 0, maximumFractionDigits: fractionDigits }),
  })
}

/** Use this currency and locale from now on, e.g. ('EUR', 'en-IE', 'VAT'). */
export function setCurrency(code: string, languageTag: string, tax = taxLabel): void {
  taxLabel = tax
  if (code === currency && languageTag === locale) return
  currency = code
  locale = languageTag
  whole = formatter(0)
  exact = formatter()
  digits = exact.resolvedOptions().maximumFractionDigits ?? 2
}

/** £180, €1,487.50, $45: the minor unit shown only when there is some. */
export function money(minor: number): string {
  const unit = 10 ** digits
  return (minor % unit === 0 ? whole : exact).format(minor / unit)
}

/** "includes VAT £75"; empty for a payment with no tax kept (from before tax was recorded) or none in it. */
export function taxLine(taxPence: number | null | undefined): string {
  return taxPence ? `includes ${taxLabel} ${money(taxPence)}` : ''
}

/** The currency's symbol for form labels: "£", "€" or "$". */
export function currencySymbol(): string {
  return exact.formatToParts(0).find((p) => p.type === 'currency')?.value ?? currency
}
