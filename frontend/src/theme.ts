// Light, dark, or follow the device. The choice is remembered on this device only.

export type ThemeChoice = 'light' | 'dark' | 'system'

const KEY = 'mm.theme'
const media = () => window.matchMedia('(prefers-color-scheme: dark)')

export function storedTheme(): ThemeChoice {
  try {
    const value = localStorage.getItem(KEY)
    return value === 'light' || value === 'dark' ? value : 'system'
  } catch {
    return 'system'
  }
}

export function applyTheme(choice: ThemeChoice) {
  const dark = choice === 'dark' || (choice === 'system' && media().matches)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
}

export function saveTheme(choice: ThemeChoice) {
  try {
    if (choice === 'system') localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, choice)
  } catch {
    // storage blocked: the choice lasts for this page only
  }
  applyTheme(choice)
}

/** Re-applies "system" when the device switches between light and dark. */
export function watchSystemTheme(getChoice: () => ThemeChoice): () => void {
  const listener = () => {
    if (getChoice() === 'system') applyTheme('system')
  }
  media().addEventListener('change', listener)
  return () => media().removeEventListener('change', listener)
}
