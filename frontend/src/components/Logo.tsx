import { BRAND_PERSON, BRAND_TITLE, BRAND_WORD } from '../brand'

/** The running child in the seven stage colours. The head takes the text colour, so it shows on dark and light. */
export function LogoChild() {
  return (
    <svg className="logo-child" viewBox="0 0 208 246" aria-hidden="true" focusable="false">
      <g transform="translate(-4 2)">
        <path fill="#d9534f" d="M74.05 178.11L32.05 162.11A17 17 0 0 0 19.95 193.89L61.95 209.89A17 17 0 0 0 74.05 178.11Z" />
        <path fill="#e8913a" d="M90.93 143.62L54.93 181.62A18 18 0 0 0 81.07 206.38L117.07 168.38A18 18 0 0 0 90.93 143.62Z" />
        <path fill="#e5c443" d="M118.14 94.31L60.14 68.31A15 15 0 0 0 47.86 95.69L105.86 121.69A15 15 0 0 0 118.14 94.31Z" />
        <path fill="#56a05a" d="M94 101L80 149A25 25 0 0 0 128 163L142 115A25 25 0 0 0 94 101Z" />
        <path fill="#4a7fc1" d="M133.18 123.17L199.18 87.17A15 15 0 0 0 184.82 60.83L118.82 96.83A15 15 0 0 0 133.18 123.17Z" />
        <path fill="#5b5ea6" d="M100.76 175.22L146.76 189.22A18 18 0 0 0 157.24 154.78L111.24 140.78A18 18 0 0 0 100.76 175.22Z" />
        <path fill="#9b6bb8" d="M135.21 174.69L143.21 224.69A17 17 0 0 0 176.79 219.31L168.79 169.31A17 17 0 0 0 135.21 174.69Z" />
        <circle className="logo-head" cx="132" cy="40" r="37" />
      </g>
    </svg>
  )
}

/**
 * The logo: the child, then the name as live text ("Professor Pat Preedy" over "Programme"). Side by side for
 * headers; stacked for the sign-in pages. The words are read out as the name in full, and the child is decoration.
 */
export default function Logo({ stacked = false }: { stacked?: boolean }) {
  return (
    <span className={stacked ? 'logo logo-stacked' : 'logo'}>
      <LogoChild />
      <span className="logo-words">
        <span className="logo-name">
          <span className="logo-title">{BRAND_TITLE} </span>
          {BRAND_PERSON}
        </span>{' '}
        <span className="logo-word">{BRAND_WORD}</span>
      </span>
    </span>
  )
}
