/** Live course sessions: showing their times, and the Zoom Meeting SDK the room loads. */

const FORMAT: Intl.DateTimeFormatOptions = {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  timeZoneName: 'short',
}
const when = new Intl.DateTimeFormat('en-GB', FORMAT)

/**
 * "Tue 3 Nov, 19:00 GMT", in the viewer's own time zone (learners in Australia see their evening), or in
 * {@code timeZone} when given.
 */
export function sessionTime(iso: string, timeZone?: string): string {
  const format = timeZone ? new Intl.DateTimeFormat('en-GB', { ...FORMAT, timeZone }) : when
  return format.format(new Date(iso))
}

const VENDOR = 'https://source.zoom.us/6.5.0/lib/vendor/'

/**
 * Zoom's Meeting SDK (component view) from Zoom's CDN, in load order: the libraries it expects as page globals
 * (Zoom's own copies; the npm package needs React 18, the app uses 19), then the SDK. Loaded on the room page
 * only, pinned to 6.5.0 and checked against these hashes. To upgrade, change the version and recompute each
 * hash (openssl dgst -sha384 -binary FILE | openssl base64 -A).
 */
export const ZOOM_SDK: { src: string; integrity: string }[] = [
  { src: VENDOR + 'react.min.js', integrity: 'sha384-DGyLxAyjq0f9SPpVevD6IgztCFlnMF6oW/XQGmfe+IsZ8TqEiDrcHkMLKI6fiB/Z' },
  { src: VENDOR + 'react-dom.min.js', integrity: 'sha384-gTGxhz21lVGYNMcdJOyq01Edg0jhn/c22nsx0kyqP0TxaV5WVdsSH1fSDUf5YJj1' },
  { src: VENDOR + 'redux.min.js', integrity: 'sha384-rG4GKxh/ojcHqPD0zvAWGoTbdtGOR9jTH1oeTQ5pnRNDgts3oclAi9EI63TxXfMb' },
  { src: VENDOR + 'redux-thunk.min.js', integrity: 'sha384-W4l2TiN6a5ehGctYQAtP/Y30Inm1F7yySVQXePn9LgMxmr6vtKJdm/R3wgU2+yuI' },
  { src: VENDOR + 'lodash.min.js', integrity: 'sha384-jC7q3III065vSuJ5pJ5KABQTNoQYM3PkN9JDtaoCro/okpUkD6IKqkYr3D8hXxBp' },
  { src: VENDOR + 'react-redux.min.js', integrity: 'sha384-SolceUhVnIawRDbgCOJYlDOxNY288CfgrFwenvmpC4S9ucmE/Sd+rBLIKeoI/3u1' },
  {
    src: 'https://source.zoom.us/6.5.0/zoom-meeting-embedded-6.5.0.min.js',
    integrity: 'sha384-ImYj+E9pPxFOOyrLY/OLcP6Fke/uY5bNoTGERQFHjrHGtNc3sx3ww9mglTbnkfWe',
  },
]
