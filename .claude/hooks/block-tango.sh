#!/usr/bin/env bash
# PreToolUse hook: hard-block tango CLI and tango skills for this project.
# See AGENTS.md ("Never use `tango-cli`" / "Never use tango skills").
set -euo pipefail

input=$(cat)
tool=$(printf '%s' "$input" | jq -r '.tool_name // empty')

deny() {
  jq -cn --arg r "$1" \
    '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
  exit 0
}

case "$tool" in
  Skill)
    skill=$(printf '%s' "$input" | jq -r '.tool_input.skill // empty')
    case "$skill" in
      tango-cli|tango|tango:*|tango-ted:*|tango-*)
        deny "Blocked by project policy: tango skills are not allowed in this repository (see AGENTS.md). Skill '$skill' was not run."
        ;;
    esac
    ;;
  Bash)
    cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty')
    # Match `tango` invoked as a command: at start, or after a shell separator.
    if printf '%s' "$cmd" | grep -Eq '(^|[;&|(]|[[:space:]])tango([[:space:]]|$)'; then
      deny "Blocked by project policy: the tango CLI is not allowed in this repository (see AGENTS.md)."
    fi
    ;;
esac

exit 0
