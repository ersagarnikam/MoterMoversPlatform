/**
 * A session's film with its credit line. Only the film's details load until someone presses play, and the browser then
 * fetches it in ranges as it plays or seeks, so a film of several hundred megabytes is never downloaded whole.
 */
export default function Film({
  src,
  credit,
  onPlay,
  onPause,
  onEnded,
  autoPlay = false,
}: {
  src: string
  credit: string | null
  onPlay?: () => void
  onPause?: () => void
  onEnded?: () => void
  /** Start at once: for the next step's film, when the one before it has just ended. */
  autoPlay?: boolean
}) {
  return (
    <figure className="film">
      <video
        src={src}
        controls
        preload="metadata"
        playsInline
        autoPlay={autoPlay}
        onPlay={onPlay}
        onPause={onPause}
        onEnded={onEnded}
      />
      {credit && <figcaption className="film-credit">{credit}</figcaption>}
    </figure>
  )
}
