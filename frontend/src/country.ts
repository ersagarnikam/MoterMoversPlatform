import { useEffect, useState } from 'react'
import { api, type CountrySettings } from './api'

// The deployment's country (UK, Ireland or Australia): currency, tax, year names and the legal entity, from the open
// GET /api/settings. Money, tax lines and year names on screen follow it (go-live task 25).

/** "2026 to 2027", or "2026" where the school year is the calendar year, as in Australia. */
export function schoolYearLabel(year: number, country: CountrySettings | null): string {
  return country?.schoolYear.firstMonth === 1 ? String(year) : `${year} to ${year + 1}`
}

/** What the teacher's counter is called in this country; empty until the settings are known, so no wrong one shows. */
export function counterLabel(country: CountrySettings | null): string {
  return country?.termLabel ?? ''
}

let settings: Promise<CountrySettings | null> | null = null

/** The country settings, asked once per page load; null until known, or if the call fails. */
export function useCountry(): CountrySettings | null {
  const [country, setCountry] = useState<CountrySettings | null>(null)
  useEffect(() => {
    settings ??= api.settings().catch(() => null)
    let live = true
    settings.then((v) => live && setCountry(v))
    return () => {
      live = false
    }
  }, [])
  return country
}
