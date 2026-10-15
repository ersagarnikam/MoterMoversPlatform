import { useEffect, useRef, useState } from 'react'
import { saveTheme, storedTheme, watchSystemTheme, type ThemeChoice } from '../theme'

const OPTIONS: { value: ThemeChoice; label: string }[] = [
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
  { value: 'system', label: 'System' },
]

export default function ThemeSwitch() {
  const [choice, setChoice] = useState<ThemeChoice>(storedTheme)
  const choiceRef = useRef(choice)
  choiceRef.current = choice

  useEffect(() => watchSystemTheme(() => choiceRef.current), [])

  return (
    <div className="theme-switch" role="group" aria-label="Theme">
      {OPTIONS.map((o) => (
        <button
          key={o.value}
          type="button"
          aria-pressed={choice === o.value}
          onClick={() => {
            setChoice(o.value)
            saveTheme(o.value)
          }}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}
