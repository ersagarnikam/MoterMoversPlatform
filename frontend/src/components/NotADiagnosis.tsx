/**
 * Permanent banner: readiness for the programme, never a clinical assessment. The API's banners start with the same
 * words, so a leading "Not a diagnosis." in the text is not repeated.
 */
export default function NotADiagnosis({ text }: { text?: string }) {
  const rest = (text ?? 'Readiness against programme stages, not a clinical test.').replace(/^Not a diagnosis\.\s*/, '')
  return (
    <div className="banner-warn" role="note">
      <strong>Not a diagnosis.</strong> {rest}
    </div>
  )
}
