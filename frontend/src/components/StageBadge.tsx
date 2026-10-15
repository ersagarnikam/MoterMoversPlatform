import type { ColourStage } from '../api'
import { stageInfo } from '../stages'

export default function StageBadge({ stage, label }: { stage: ColourStage | null | undefined; label?: string }) {
  const info = stageInfo(stage)
  if (!info) return <span className="badge">Not placed</span>
  return (
    <span className="badge">
      <span className="dot" style={{ background: info.colour }} />
      {label ?? info.label}
    </span>
  )
}
