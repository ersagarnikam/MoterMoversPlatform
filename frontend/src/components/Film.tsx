import { useEffect, useRef } from 'react'

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
  onTime,
  autoPlay = false,
  startAt = 0,
  stopped = false,
}: {
  src: string
  credit: string | null
  onPlay?: () => void
  onPause?: () => void
  onEnded?: () => void
  /** Where the film has got to, in seconds, as it plays. */
  onTime?: (seconds: number) => void
  /** Start at once: the session is already under way when this film comes on screen. */
  autoPlay?: boolean
  /** Carry on from here, for a film that left the screen and has come back. */
  startAt?: number
  /** The session is over: a film still playing is paused (for example after Skip to end). */
  stopped?: boolean
}) {
  const video = useRef<HTMLVideoElement>(null)
  useEffect(() => {
    if (stopped) video.current?.pause()
  }, [stopped])
  return (
    <figure className="film">
      <video
        ref={video}
        src={src}
        controls
        preload="metadata"
        playsInline
        autoPlay={autoPlay}
        onLoadedMetadata={(e) => {
          if (startAt > 0 && startAt < e.currentTarget.duration) e.currentTarget.currentTime = startAt
        }}
        onTimeUpdate={(e) => onTime?.(e.currentTarget.currentTime)}
        onPlay={onPlay}
        onPause={onPause}
        onEnded={onEnded}
      />
      {credit && <figcaption className="film-credit">{credit}</figcaption>}
    </figure>
  )
}
