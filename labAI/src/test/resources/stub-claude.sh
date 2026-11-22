#!/bin/sh
# Stub Claude Code CLI for tests. Behaviour is selected with STUB_MODE:
#   ok (default) - prints a JSON result echoing stdin; session id taken from --session-id/--resume
#   fail         - prints an error JSON result and exits 1
#   big          - prints a ~300 KB result (pipe-buffer regression)
#   is_error     - prints an is_error result but exits 0
#   sleep        - spawns a child `sleep`, writes its pid to STUB_CHILD_PID_FILE, then waits
# argv is written one per line to STUB_ARGS_FILE, the working directory to STUB_CWD_FILE and the
# environment to STUB_ENV_FILE when set.
[ -n "$STUB_ARGS_FILE" ] && printf '%s\n' "$@" > "$STUB_ARGS_FILE"
[ -n "$STUB_CWD_FILE" ] && pwd > "$STUB_CWD_FILE"
[ -n "$STUB_ENV_FILE" ] && env > "$STUB_ENV_FILE"
sid="stub-session"
prev=""
for a in "$@"; do
  if [ "$prev" = "--session-id" ] || [ "$prev" = "--resume" ]; then sid="$a"; fi
  prev="$a"
done
prompt=$(cat)
case "${STUB_MODE:-ok}" in
  fail)
    echo '{"type":"result","subtype":"success","is_error":true,"result":"Not logged in"}'
    exit 1 ;;
  is_error)
    echo '{"type":"result","subtype":"error_max_turns","is_error":true,"result":"Reached max turns"}' ;;
  big)
    printf '{"type":"result","is_error":false,"session_id":"%s","result":"' "$sid"
    head -c 300000 /dev/zero | tr '\0' 'a'
    printf '"}\n' ;;
  sleep)
    sleep 60 &
    [ -n "$STUB_CHILD_PID_FILE" ] && echo $! > "$STUB_CHILD_PID_FILE"
    wait ;;
  *)
    printf '{"type":"result","subtype":"success","is_error":false,"result":"echo:%s","session_id":"%s","total_cost_usd":0.012,"num_turns":2,"duration_ms":1234}\n' "$prompt" "$sid" ;;
esac
