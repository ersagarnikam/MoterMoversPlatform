// One store app serves three countries, each with its own server (go-live plan). The parent says where they are at
// first launch; the choice decides where every request goes, and stays on the phone after signing out.
import Constants from 'expo-constants'
import { getLocales } from 'expo-localization'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Platform } from 'react-native'
import { CountryChooser } from '@/components/CountryChooser'
import { Loading } from '@/components/ui'
import { setApiUrl, setCountry as setServerCountry } from './api'
import { getLocal, setLocal } from './storage'

/** Not "mm.": those keys are the signed-in parent's, cleared on signing out; the country outlasts that. */
const CHOICE_KEY = 'motormovers.country'

export interface Country {
  code: 'UK' | 'IE' | 'AU' | 'DEV'
  name: string
  url: string
  /** Shown until GET /api/settings answers. */
  currency: string
  taxLabel: string
}

/** Without a configured address: the machine running the Expo dev server, so a phone on the same Wi-Fi finds the API. */
function developmentUrl(): string {
  const devHost = Constants.expoConfig?.hostUri?.split(':')[0]
  if (Platform.OS !== 'web' && devHost) return `http://${devHost}:8080`
  if (Platform.OS === 'android') return 'http://10.0.2.2:8080'
  return 'http://localhost:8080'
}

/**
 * The countries this build can use. Each address comes from the build's EXPO_PUBLIC_API_URL_UK, _IE and _AU (Expo only
 * reads these written out in full); in development a missing one falls back to the local API, and EXPO_PUBLIC_API_URL
 * adds one "Development" choice.
 */
export function availableCountries(): Country[] {
  const fallback = __DEV__ ? developmentUrl() : undefined
  const countries = [
    { code: 'UK', name: 'United Kingdom', url: process.env.EXPO_PUBLIC_API_URL_UK ?? fallback, currency: 'GBP', taxLabel: 'VAT' },
    { code: 'IE', name: 'Ireland', url: process.env.EXPO_PUBLIC_API_URL_IE ?? fallback, currency: 'EUR', taxLabel: 'VAT' },
    { code: 'AU', name: 'Australia', url: process.env.EXPO_PUBLIC_API_URL_AU ?? fallback, currency: 'AUD', taxLabel: 'GST' },
    { code: 'DEV', name: 'Development', url: process.env.EXPO_PUBLIC_API_URL, currency: 'GBP', taxLabel: 'VAT' },
  ] as const
  return countries.flatMap((c) => (c.url ? [{ ...c, url: c.url.replace(/\/$/, '') }] : []))
}

/** The country the phone is set to, to suggest first; the parent is always asked. */
export function suggestedCountry(): Country['code'] | null {
  const region = getLocales()[0]?.regionCode
  if (region === 'GB') return 'UK'
  return region === 'IE' || region === 'AU' ? region : null
}

interface ChosenCountry {
  country: Country
  /** Forgets the choice, so the next thing shown is the country chooser. */
  forgetCountry: () => Promise<void>
}

const CountryContext = createContext<ChosenCountry | null>(null)

/** Holds everything else back until a country is chosen, since no request can go anywhere without one. */
export function CountryProvider({ children }: { children: ReactNode }) {
  const [country, setCountry] = useState<Country | null>(null)
  const [loading, setLoading] = useState(true)

  const use = useCallback((chosen: Country) => {
    setApiUrl(chosen.url)
    setServerCountry({ currency: chosen.currency, taxLabel: chosen.taxLabel, legalLine: null })
    setCountry(chosen)
  }, [])

  useEffect(() => {
    void (async () => {
      const saved = await getLocal<Country['code'] | null>(CHOICE_KEY, null)
      const known = availableCountries().find((c) => c.code === saved)
      if (known) use(known)
      setLoading(false)
    })()
  }, [use])

  const choose = useCallback(
    async (chosen: Country) => {
      await setLocal(CHOICE_KEY, chosen.code)
      use(chosen)
    },
    [use],
  )

  const value = useMemo<ChosenCountry | null>(
    () =>
      country && {
        country,
        forgetCountry: async () => {
          await setLocal(CHOICE_KEY, null)
          setCountry(null)
        },
      },
    [country],
  )

  if (loading) return <Loading />
  if (!value) return <CountryChooser onChoose={choose} />
  return <CountryContext.Provider value={value}>{children}</CountryContext.Provider>
}

export function useChosenCountry(): ChosenCountry {
  const chosen = useContext(CountryContext)
  if (!chosen) throw new Error('useChosenCountry must be used inside CountryProvider')
  return chosen
}
