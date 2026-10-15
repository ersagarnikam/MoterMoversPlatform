/** Permanent banner: readiness for the programme, never a clinical assessment. */
export default function NotADiagnosis({ text }: { text?: string }) {
  return (
    <div className="banner-warn" role="note">
      <strong>Not a diagnosis.</strong> {text ?? 'Readiness against programme stages, not a clinical test.'}
    </div>
  )
}
