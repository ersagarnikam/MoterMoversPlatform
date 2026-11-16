// The names people see, in one place for the parent app (go-live task 84). The web app has the same in
// frontend/src/brand.ts and the API in common/Brand.java. The store name in app.json must match BRAND (the web's
// brand.test.ts checks it). Code names (the slug, scheme and store ids in app.json) are not the brand.

/** The programme's name in full, and the app's name in the stores (30 characters, Apple's limit). */
export const BRAND = 'Professor Pat Preedy Programme'

/** The logo's two lines. */
export const BRAND_PERSON = 'Professor Pat Preedy'
export const BRAND_WORD = 'Programme'

/** The home programme: "the Programme at Home" inside a sentence, "The Programme at Home" at the start of one. */
export const HOME_PROGRAMME = 'Programme at Home'
