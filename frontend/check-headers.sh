#!/bin/sh
# Checks the headers nginx itself sends, which the browser tests cannot see: vite preview sets them another way. Needs
# the web image built and running on $1 (default http://localhost:8088) with STORAGE_ORIGIN set to $2.
base=${1:-http://localhost:8088}
storage=${2:-https://bucket.storage.test}
failed=0

check() { # path, header, text it must hold, text it must not hold
  value=$(curl -sI "$base$1" | tr -d '\r' | grep -i "^$2:")
  case "$value" in
    *"$3"*) ;;
    *) echo "FAIL $1: $2 lacks \"$3\": $value"; failed=1 ;;
  esac
  case "$value" in
    *"$4"*) echo "FAIL $1: $2 has \"$4\": $value"; failed=1 ;;
  esac
}

for path in / /admin/schools /login; do
  check "$path" content-security-policy "connect-src 'self';" "$storage"
  check "$path" permissions-policy "camera=()" "camera=(self)"
done
for path in /admin/content /admin/content/; do
  check "$path" content-security-policy "connect-src 'self' $storage;" zoom.us
  check "$path" permissions-policy "camera=()" "camera=(self)"
done
for path in /live/1 /live/1/; do
  check "$path" content-security-policy "script-src 'self' https://source.zoom.us" "$storage"
  check "$path" permissions-policy "camera=(self), microphone=(self)" "camera=()"
done

[ "$failed" = 0 ] && echo "Headers are as they should be"
exit "$failed"
