/**
 * Grey bars in the shape of the page while it loads, so it does not jump as the content arrives. Screen readers
 * hear "Loading" once.
 */
export default function Skeleton({ lines = 3, title = true }: { lines?: number; title?: boolean }) {
  return (
    <div className="skeleton" role="status" aria-label="Loading">
      {title && <div className="skeleton-bar skeleton-title" />}
      {Array.from({ length: lines }, (_, i) => (
        <div key={i} className="skeleton-card">
          <div className="skeleton-bar" />
          <div className="skeleton-bar skeleton-short" />
        </div>
      ))}
    </div>
  )
}
