import type { ColourStage } from './api'

export const STAGES: { key: ColourStage; label: string; colour: string }[] = [
  { key: 'RED', label: 'Red', colour: 'var(--stage-red)' },
  { key: 'ORANGE', label: 'Orange', colour: 'var(--stage-orange)' },
  { key: 'YELLOW', label: 'Yellow', colour: 'var(--stage-yellow)' },
  { key: 'GREEN', label: 'Green', colour: 'var(--stage-green)' },
  { key: 'BLUE', label: 'Blue', colour: 'var(--stage-blue)' },
  { key: 'INDIGO', label: 'Indigo', colour: 'var(--stage-indigo)' },
  { key: 'VIOLET', label: 'Violet', colour: 'var(--stage-violet)' },
]

export function stageInfo(stage: ColourStage | null | undefined) {
  return STAGES.find((s) => s.key === stage)
}

export function pct(fraction: number): string {
  return `${Math.round(fraction * 100)}%`
}
