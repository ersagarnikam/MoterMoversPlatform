import { useState } from 'react'
import { Body, Button, H1, Screen, StageStripe } from '@/components/ui'
import { availableCountries, suggestedCountry, type Country } from '@/lib/country'

/** First launch, or after changing country: asks where the parent is, with the phone's own country first. */
export function CountryChooser({ onChoose }: { onChoose: (country: Country) => Promise<void> }) {
  const [busy, setBusy] = useState(false)
  const suggested = suggestedCountry()
  const countries = [...availableCountries()].sort(
    (a, b) => Number(b.code === suggested) - Number(a.code === suggested),
  )

  async function choose(country: Country) {
    setBusy(true)
    await onChoose(country)
  }

  return (
    <Screen>
      <StageStripe height={4} />
      <H1>Where are you?</H1>
      <Body>Motor Movers runs separately in each country, so choose yours to sign in.</Body>
      {countries.map((c) => (
        <Button
          key={c.code}
          label={c.name}
          variant={c.code === suggested ? 'primary' : 'secondary'}
          disabled={busy}
          onPress={() => void choose(c)}
        />
      ))}
    </Screen>
  )
}
