// Apply the saved theme before first paint so dark mode does not flash white. A file rather than an inline
// script, so the Content Security Policy can forbid inline scripts.
try {
  var t = localStorage.getItem('mm.theme')
  var dark = t === 'dark' || (t !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
} catch (e) {}
