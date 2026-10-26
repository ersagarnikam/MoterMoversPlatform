import type { ColourStage } from '../api'
import { stageInfo } from '../stages'

/**
 * A colour stage as a pill washed in the stage's own colour and edged in it (classes in styles.css, so the strict
 * CSP is untouched). The text stays in the normal ink, so it reads in both themes.
 */
export default function StageBadge({ stage, label }: { stage: ColourStage | null | undefined; label?: string }) {
  const info = stageInfo(stage)
  if (!info) return <span className="badge">Not placed</span>
  return (
    <span className={`badge badge-stage stage-tint-${info.key.toLowerCase()}`}>
      <span className="dot" aria-hidden="true" />
      {label ?? info.label}
    </span>
  )
}
