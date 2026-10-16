// Motor Movers: navy and teal, with the seven colour stages as the only bright colours. Follows the
// phone's light or dark setting. Values match the web app (frontend/src/styles.css).
import { useColorScheme } from 'react-native'
import type { Band, ColourStage } from './api'

const light = {
  navy: '#1f2b3a',
  teal: '#2e7d6f',
  tealSoft: '#dcebe7',
  onTeal: '#ffffff',
  cream: '#f4f1ea',
  paper: '#ffffff',
  page: '#faf8f4',
  ink: '#1f2b3a',
  muted: '#5d6b7a',
  line: '#e2ded5',
  warnBg: '#fdf1d8',
  warnInk: '#6b4a00',
  error: '#b3261e',
  track: '#e7e9ee',
  header: '#1f2b3a',
  headerInk: '#ffffff',
  tabInactive: '#8c96a0',
  band: {
    EMERGING: { bg: '#fdf1d8', ink: '#6b4a00' },
    DEVELOPING: { bg: '#dce8f6', ink: '#1d4677' },
    SECURE: { bg: '#dcebe7', ink: '#1d5a4f' },
  } as Record<Band, { bg: string; ink: string }>,
}

export type Palette = typeof light

const dark: Palette = {
  navy: '#1f2b3a',
  teal: '#5fb8a6',
  tealSoft: '#1e3a35',
  onTeal: '#10161e',
  cream: '#232c38',
  paper: '#18202b',
  page: '#10161e',
  ink: '#e6ebf1',
  muted: '#9aa8b7',
  line: '#2c3746',
  warnBg: '#3a2f14',
  warnInk: '#f2d48a',
  error: '#f2b8b5',
  track: '#2a3442',
  header: '#18202b',
  headerInk: '#e6ebf1',
  tabInactive: '#7d8a98',
  band: {
    EMERGING: { bg: '#3a2f14', ink: '#f2d48a' },
    DEVELOPING: { bg: '#1f3047', ink: '#a9c8ef' },
    SECURE: { bg: '#1e3a35', ink: '#9fd9cb' },
  },
}

export function usePalette(): Palette {
  return useColorScheme() === 'dark' ? dark : light
}

export const STAGES: { key: ColourStage; label: string; colour: string }[] = [
  { key: 'RED', label: 'Red', colour: '#d9534f' },
  { key: 'ORANGE', label: 'Orange', colour: '#e8913a' },
  { key: 'YELLOW', label: 'Yellow', colour: '#e5c443' },
  { key: 'GREEN', label: 'Green', colour: '#56a05a' },
  { key: 'BLUE', label: 'Blue', colour: '#4a7fc1' },
  { key: 'INDIGO', label: 'Indigo', colour: '#5b5ea6' },
  { key: 'VIOLET', label: 'Violet', colour: '#9b6bb8' },
]

export function stageInfo(stage: ColourStage | null | undefined) {
  return STAGES.find((s) => s.key === stage)
}

export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 }
export const radius = 12
