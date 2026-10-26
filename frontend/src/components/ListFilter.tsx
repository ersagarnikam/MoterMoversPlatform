/**
 * A search box, and optionally one drop-down, above a long list: payments, schools, bookings. Says how many of the
 * list are shown when it is narrowed.
 */
export default function ListFilter<T extends string>({
  search,
  onSearch,
  placeholder,
  choice,
  shown,
  total,
}: {
  search: string
  onSearch: (s: string) => void
  placeholder: string
  choice?: { label: string; value: T; options: [T, string][]; onChange: (v: T) => void }
  shown: number
  total: number
}) {
  return (
    <div className="list-filter">
      <input
        type="search"
        aria-label={placeholder}
        placeholder={placeholder}
        value={search}
        onChange={(e) => onSearch(e.target.value)}
      />
      {choice && (
        <label>
          <span className="visually-hidden">{choice.label}</span>
          <select aria-label={choice.label} value={choice.value} onChange={(e) => choice.onChange(e.target.value as T)}>
            {choice.options.map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </select>
        </label>
      )}
      {shown !== total && (
        <span className="muted" aria-live="polite">
          Showing {shown} of {total}
        </span>
      )}
    </div>
  )
}

/** Whether any of the texts contains the search, ignoring case; an empty search matches everything. */
export function matches(search: string, ...texts: (string | null | undefined)[]): boolean {
  const needle = search.trim().toLowerCase()
  return !needle || texts.some((t) => t?.toLowerCase().includes(needle))
}
