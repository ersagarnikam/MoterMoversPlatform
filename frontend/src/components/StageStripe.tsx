import { STAGES } from '../stages'

/** The seven colour stages, as on the programme materials. */
export default function StageStripe() {
  return (
    <span className="stage-stripe" aria-hidden="true">
      {STAGES.map((s) => (
        <span key={s.key} style={{ background: s.colour }} />
      ))}
    </span>
  )
}
