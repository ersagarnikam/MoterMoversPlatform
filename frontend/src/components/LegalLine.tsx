import { useCountry } from '../country'

/** Who the platform's invoices come from, once the legal entity is known; nothing while it is a placeholder. */
export default function LegalLine() {
  const line = useCountry()?.legalLine
  return line ? <p className="fine">{line}</p> : null
}
