/**
 * A session's film with its credit line. Only the film's details load until someone presses play, and the browser then
 * fetches it in ranges as it plays or seeks, so a film of several hundred megabytes is never downloaded whole.
 */
export default function Film({
  src,
  credit,
  onPlay,
  onPause,
}: {
  src: string
  credit: string | null
  onPlay?: () => void
  onPause?: () => void
}) {
  return (
    <figure className="film">
      <video src={src} controls preload="metadata" playsInline onPlay={onPlay} onPause={onPause} />
      {credit && <figcaption className="film-credit">{credit}</figcaption>}
    </figure>
  )
}
