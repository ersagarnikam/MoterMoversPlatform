import type { ColourStage } from '../api'
import { stageInfo } from '../stages'

/**
 * A colour stage as a pill. {@code tinted} fills the pill with a light wash of the stage's own colour and edges it in
 * that colour (classes in styles.css, so the strict CSP is untouched); the text stays in the normal ink for contrast.
 */
export default function StageBadge({
  stage,
  label,
  tinted = false,
}: {
  stage: ColourStage | null | undefined
  label?: string
  tinted?: boolean
}) {
  const info = stageInfo(stage)
  if (!info) return <span className="badge">Not placed</span>
  if (tinted) {
    return (
      <span className={`badge badge-stage stage-tint-${info.key.toLowerCase()}`}>
        <span className="dot" aria-hidden="true" />
        {label ?? info.label}
      </span>
    )
  }
  return (
    <span className="badge">
      <span className="dot" style={{ background: info.colour }} />
      {label ?? info.label}
    </span>
  )
}
