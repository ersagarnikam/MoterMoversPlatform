// The names people see, in one place for the web app (go-live task 84). The API has the same in common/Brand.java and
// the parent app in mobile/src/lib/brand.ts. Code names (the demo domains, storage keys, file paths) are not the brand.

/** The logo's words: the first is dropped at phone width, so the header reads "Pat Preedy Programme". */
export const BRAND_TITLE = 'Professor'
export const BRAND_PERSON = 'Pat Preedy'
export const BRAND_WORD = 'Programme'

/** The programme's name in full: the logo, page headings, the certificate. */
export const BRAND = `${BRAND_TITLE} ${BRAND_PERSON} ${BRAND_WORD}`

/** The home programme: "the Programme at Home" inside a sentence, "The Programme at Home" at the start of one. */
export const HOME_PROGRAMME = `${BRAND_WORD} at Home`

/** The team that runs the programme and the platform (the ADMIN role). */
export const TEAM = `${BRAND_WORD} team`
