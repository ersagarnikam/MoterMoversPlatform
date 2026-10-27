// Ages for Motor Movers at Home. The app asks for years and months and keeps months; no date of birth is held.

/** 44 -> "3 years 8 months", 11 -> "11 months". */
export function ageLabel(months: number): string {
  const y = Math.floor(months / 12)
  const m = months % 12
  const years = y === 0 ? '' : y === 1 ? '1 year' : `${y} years`
  const rest = m === 0 ? '' : m === 1 ? '1 month' : `${m} months`
  return [years, rest].filter(Boolean).join(' ') || '0 months'
}

/** Years and months as typed, to months; null when they do not make an age the programme can use. */
export function toMonths(years: string, months: string): number | null {
  const y = years.trim() === '' ? 0 : Number(years)
  const m = months.trim() === '' ? 0 : Number(months)
  if (!Number.isInteger(y) || !Number.isInteger(m) || y < 0 || m < 0 || m > 11) return null
  const total = y * 12 + m
  return total > 0 && total <= 120 ? total : null
}

/** The parent's words for each answer in the starting check. */
export const CHECK_ANSWERS = [
  { value: 'YES', label: 'Yes' },
  { value: 'NOT_YET', label: 'Not yet' },
  { value: 'NOT_SURE', label: 'Not sure' },
] as const
