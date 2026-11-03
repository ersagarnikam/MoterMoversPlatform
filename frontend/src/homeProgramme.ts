import { useEffect, useState } from 'react'
import { api } from './api'

// Motor Movers at Home on the website: shared words and helpers. The parent app has the same in mobile/src/lib/ages.ts.

/** 44 -> "3 years 8 months", 11 -> "11 months". */
export function ageLabel(months: number): string {
  const y = Math.floor(months / 12)
  const m = months % 12
  const years = y === 0 ? '' : y === 1 ? '1 year' : `${y} years`
  const rest = m === 0 ? '' : m === 1 ? '1 month' : `${m} months`
  return [years, rest].filter(Boolean).join(' ') || '0 months'
}

/** Years and months as typed, to months; null when they do not make an age the programme can use (1 to 120 months). */
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

/** The programme's two movement charts, in the parent's words. */
export const CHART_NAMES = { FINE: 'Hands and fingers', GROSS: 'Whole body' } as const

/** "Module 2" or the colour's name, for the end-of-module and end-of-colour reviews. */
export function reviewName(point: string, stageName: string): string {
  return point === 'END' ? stageName : `module ${point.replace('MODULE_', '')}`
}

let available: Promise<boolean> | null = null

/** Whether Motor Movers at Home is switched on (asked once per page load); false until known or if the call fails. */
export function useHomeAvailable(): boolean {
  const [on, setOn] = useState(false)
  useEffect(() => {
    available ??= api.homeAvailable().then(
      (r) => r.enabled,
      () => false,
    )
    let live = true
    available.then((v) => live && setOn(v))
    return () => {
      live = false
    }
  }, [])
  return on
}
