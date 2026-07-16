#!/usr/bin/env bash
#
# Apex — Full API Endpoint Test Script
# Covers: happy path + RBAC/negative cases + cross-tenant isolation + idempotency
# across every module implemented in Phases 0-13.
#
# Requirements: bash, curl, jq
# Usage:
#   BASE_URL=http://localhost:8080 ./test_all_endpoints.sh
#   (BASE_URL defaults to http://localhost:8080 if not set)
#
# Notes:
#   - Uses per-user cookie jars since the refresh token is an httpOnly cookie
#     (Phase 8 fix). Access tokens are stored in shell variables.
#   - Uses random emails per run so the script is safely re-runnable.
#   - Does NOT test WebSocket endpoints (curl can't do STOMP/SockJS handshakes
#     meaningfully) — see the printed reminder at the end for manual WS checks.
#   - Rate-limit test is opt-in (RUN_RATE_LIMIT_TEST=1) since it deliberately
#     trips the rate limiter and may briefly block further requests from your IP.

set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1"
RUN_RATE_LIMIT_TEST="${RUN_RATE_LIMIT_TEST:-0}"

COOKIE_DIR="$(mktemp -d)"
trap 'rm -rf "$COOKIE_DIR"' EXIT

PASS=0
FAIL=0
FAILURES=()

# ---------- colors ----------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BLUE='\033[0;34m'; BOLD='\033[1m'; NC='\033[0m'

section() { echo -e "\n${BLUE}${BOLD}=== $1 ===${NC}"; }
info()    { echo -e "${YELLOW}  → $1${NC}"; }

# ---------- dependency check ----------
for bin in curl jq; do
  command -v "$bin" >/dev/null 2>&1 || { echo "ERROR: '$bin' is required but not installed."; exit 1; }
done

# ---------- core request helper ----------
# api_call METHOD PATH JAR TOKEN BODY EXTRA_HEADER
# Prints "HTTPSTATUS<TAB>BODY" to stdout. Use `read` or the wrappers below.
api_call() {
  local method="$1" path="$2" jar="$3" token="$4" body="$5" extra_header="${6:-}"
  local jar_path=""
  [[ -n "$jar" ]] && jar_path="$COOKIE_DIR/$jar.jar"

  local -a args=(-sS -X "$method" "$API$path" -w '\n%{http_code}')
  [[ -n "$jar_path" ]] && args+=(-b "$jar_path" -c "$jar_path")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$extra_header" ]] && args+=(-H "$extra_header")
  if [[ -n "$body" ]]; then
    args+=(-H "Content-Type: application/json" -d "$body")
  fi
  curl "${args[@]}"
}

# assert HTTP_STATUS EXPECTED DESCRIPTION
assert() {
  local actual="$1" expected="$2" desc="$3"
  if [[ "$actual" == "$expected" ]]; then
    echo -e "  ${GREEN}PASS${NC} — $desc (got $actual)"
    PASS=$((PASS+1))
  else
    echo -e "  ${RED}FAIL${NC} — $desc (expected $expected, got $actual)"
    FAIL=$((FAIL+1))
    FAILURES+=("$desc (expected $expected, got $actual)")
  fi
}

# assert_json_true CONDITION_RESULT DESCRIPTION   (pass "true"/"false" string)
assert_true() {
  local actual="$1" desc="$2"
  if [[ "$actual" == "true" ]]; then
    echo -e "  ${GREEN}PASS${NC} — $desc"
    PASS=$((PASS+1))
  else
    echo -e "  ${RED}FAIL${NC} — $desc (got: $actual)"
    FAIL=$((FAIL+1))
    FAILURES+=("$desc (got: $actual)")
  fi
}

rand() { echo "$(date +%s%N)-$RANDOM"; }

echo -e "${BOLD}Apex Full API Test — target: $BASE_URL${NC}"

# ============================================================
section "0. Health Check"
# ============================================================
resp=$(curl -sS -w '\n%{http_code}' "$BASE_URL/actuator/health")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /actuator/health returns 200"


# ============================================================
section "1. Auth — Registration"
# ============================================================
EMAIL_A="trader-a-$(rand)@test.com"
EMAIL_B="trader-b-$(rand)@test.com"
PASSWORD="SecurePass123!"

resp=$(api_call POST "/auth/register" "userA" "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PASSWORD\",\"displayName\":\"Trader A\"}")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "201" "Register User A succeeds"
TOKEN_A=$(echo "$body" | jq -r '.accessToken // empty')
[[ -n "$TOKEN_A" ]] && echo "  info: got access token for User A" || echo -e "  ${RED}WARNING: no accessToken in response${NC}"

resp=$(api_call POST "/auth/register" "userB" "" "{\"email\":\"$EMAIL_B\",\"password\":\"$PASSWORD\",\"displayName\":\"Trader B\"}")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "201" "Register User B succeeds"
TOKEN_B=$(echo "$body" | jq -r '.accessToken // empty')

# Negative: duplicate email
resp=$(api_call POST "/auth/register" "" "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PASSWORD\",\"displayName\":\"Dup\"}")
status=$(echo "$resp" | tail -1)
assert "$status" "409" "Register with duplicate email returns 409"

# Negative: invalid email format
resp=$(api_call POST "/auth/register" "" "" "{\"email\":\"not-an-email\",\"password\":\"$PASSWORD\",\"displayName\":\"Bad\"}")
status=$(echo "$resp" | tail -1)
assert "$status" "400" "Register with invalid email returns 400"

# Negative: short password
resp=$(api_call POST "/auth/register" "" "" "{\"email\":\"short-$(rand)@test.com\",\"password\":\"123\",\"displayName\":\"Bad\"}")
status=$(echo "$resp" | tail -1)
assert "$status" "400" "Register with too-short password returns 400"


# ============================================================
section "2. Auth — Login / Refresh / Me"
# ============================================================
resp=$(api_call POST "/auth/login" "" "" "{\"email\":\"$EMAIL_A\",\"password\":\"WrongPassword!\"}")
status=$(echo "$resp" | tail -1)
assert "$status" "401" "Login with wrong password returns 401"

resp=$(api_call POST "/auth/login" "userA" "" "{\"email\":\"$EMAIL_A\",\"password\":\"$PASSWORD\"}")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "200" "Login with correct password succeeds"
TOKEN_A=$(echo "$body" | jq -r '.accessToken // empty')

resp=$(api_call POST "/auth/login" "userB" "" "{\"email\":\"$EMAIL_B\",\"password\":\"$PASSWORD\"}")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "200" "Login User B succeeds"
TOKEN_B=$(echo "$body" | jq -r '.accessToken // empty')

# GET /users/me without auth
resp=$(api_call GET "/users/me" "" "" "")
status=$(echo "$resp" | tail -1)
assert "$status" "401" "GET /users/me without token returns 401"

# GET /users/me with auth
resp=$(api_call GET "/users/me" "userA" "$TOKEN_A" "")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /users/me with token succeeds"
SEED_BALANCE=$(echo "$body" | jq -r '.portfolio.cashBalance // empty')
info "User A seed balance: $SEED_BALANCE"

# Refresh flow — cookie-based, relies on userA's jar holding the httpOnly refresh cookie
resp=$(api_call POST "/auth/refresh" "userA" "" "")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "POST /auth/refresh using httpOnly cookie succeeds"

# Refresh with no cookie at all (fresh jar) should fail
resp=$(api_call POST "/auth/refresh" "noCookieJar" "" "")
status=$(echo "$resp" | tail -1)
assert "$status" "401" "POST /auth/refresh with no refresh cookie returns 401"


# ============================================================
section "3. Organizations & RBAC"
# ============================================================
resp=$(api_call POST "/organizations" "userA" "$TOKEN_A" "{\"name\":\"Test Bootcamp $(rand)\",\"type\":\"BOOTCAMP\"}")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "201" "User A creates organization (becomes ORG_ADMIN)"
ORG_ID=$(echo "$body" | jq -r '.id // empty')
info "Org ID: $ORG_ID"

resp=$(api_call POST "/organizations/join" "userB" "$TOKEN_B" "{\"organizationId\":\"$ORG_ID\"}")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "User B joins organization (as TRADER)"

resp=$(api_call GET "/organizations" "userA" "$TOKEN_A" "")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /organizations (list mine) succeeds"

resp=$(api_call GET "/organizations/$ORG_ID" "userA" "$TOKEN_A" "")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /organizations/{id} detail succeeds for member"

# RBAC: TRADER (User B) cannot list members
resp=$(api_call GET "/organizations/$ORG_ID/members" "userB" "$TOKEN_B" "")
status=$(echo "$resp" | tail -1)
assert "$status" "403" "TRADER cannot list org members (403)"

# ORG_ADMIN (User A) can list members
resp=$(api_call GET "/organizations/$ORG_ID/members" "userA" "$TOKEN_A" "")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "200" "ORG_ADMIN can list org members"
USER_B_ID=$(echo "$body" | jq -r ".[] | select(.userEmail==\"$EMAIL_B\") | .userId // .id // empty" 2>/dev/null)
info "User B membership/user id: $USER_B_ID"

# RBAC: TRADER cannot update roles
resp=$(api_call PUT "/organizations/$ORG_ID/members/$USER_B_ID/role" "userB" "$TOKEN_B" "{\"role\":\"INSTRUCTOR\"}")
status=$(echo "$resp" | tail -1)
assert "$status" "403" "TRADER cannot update member roles (403)"

# ORG_ADMIN promotes B to INSTRUCTOR
resp=$(api_call PUT "/organizations/$ORG_ID/members/$USER_B_ID/role" "userA" "$TOKEN_A" "{\"role\":\"INSTRUCTOR\"}")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "ORG_ADMIN promotes member to INSTRUCTOR"

# Cannot demote last remaining ORG_ADMIN (User A demoting self, only admin)
resp=$(api_call GET "/organizations/$ORG_ID/members" "userA" "$TOKEN_A" "")
body=$(echo "$resp" | head -n -1)
USER_A_ID=$(echo "$body" | jq -r ".[] | select(.userEmail==\"$EMAIL_A\") | .userId // .id // empty" 2>/dev/null)
resp=$(api_call PUT "/organizations/$ORG_ID/members/$USER_A_ID/role" "userA" "$TOKEN_A" "{\"role\":\"TRADER\"}")
status=$(echo "$resp" | tail -1)
assert "$status" "409" "Cannot demote the last ORG_ADMIN (409)"

# RBAC: INSTRUCTOR cannot view audit log
resp=$(api_call GET "/organizations/$ORG_ID/audit-log" "userB" "$TOKEN_B" "")
status=$(echo "$resp" | tail -1)
assert "$status" "403" "INSTRUCTOR cannot view audit log (403)"

# ORG_ADMIN can view audit log
resp=$(api_call GET "/organizations/$ORG_ID/audit-log" "userA" "$TOKEN_A" "")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "ORG_ADMIN can view audit log"

# Leaderboard visibility toggle
resp=$(api_call PUT "/organizations/leaderboard/visibility" "userB" "$TOKEN_B" "{\"visible\":false}")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "User B opts out of leaderboard"

resp=$(api_call GET "/organizations/$ORG_ID/leaderboard" "userA" "$TOKEN_A" "")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET org leaderboard succeeds"
B_ON_BOARD=$(echo "$body" | jq -r "[.[] | select(.userId==\"$USER_B_ID\")] | length == 0" 2>/dev/null)
assert_true "$B_ON_BOARD" "Opted-out User B is fully absent from leaderboard response"

# re-enable B's visibility for later leaderboard checks
api_call PUT "/organizations/leaderboard/visibility" "userB" "$TOKEN_B" "{\"visible\":true}" > /dev/null


# ============================================================
section "4. Market Data"
# ============================================================
resp=$(api_call GET "/market/assets" "userA" "$TOKEN_A" "")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /market/assets succeeds"
BTC_ID=$(echo "$body" | jq -r '.[] | select(.symbol=="BTC") | .id // empty' 2>/dev/null | head -1)
info "BTC asset id: $BTC_ID"

resp=$(api_call GET "/market/prices?symbols=BTC,ETH" "userA" "$TOKEN_A" "")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /market/prices?symbols=BTC,ETH succeeds"

resp=$(api_call GET "/market/overview" "userA" "$TOKEN_A" "")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /market/overview succeeds"

resp=$(api_call GET "/market/BTC/history?days=30" "userA" "$TOKEN_A" "")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /market/BTC/history?days=30 succeeds"

# Negative: unknown symbol history
resp=$(api_call GET "/market/NOTASYMBOL/history?days=30" "userA" "$TOKEN_A" "")
status=$(echo "$resp" | tail -1)
assert "$status" "404" "GET history for unknown symbol returns 404"


# ============================================================
section "5. Trading — Execution, Idempotency, Concurrency-adjacent checks"
# ============================================================
if [[ -z "$BTC_ID" || "$BTC_ID" == "null" ]]; then
  echo -e "  ${RED}Cannot continue trading tests — BTC asset id not found${NC}"
else
  IDEMP_KEY_1="idem-$(rand)"

  # Unauthenticated trade attempt
  resp=$(api_call POST "/trading/execute" "" "" "{\"assetId\":\"$BTC_ID\",\"side\":\"BUY\",\"quantity\":0.01,\"idempotencyKey\":\"$IDEMP_KEY_1\"}")
  status=$(echo "$resp" | tail -1)
  assert "$status" "401" "Trade execution without auth returns 401"

  # Successful buy
  resp=$(api_call POST "/trading/execute" "userA" "$TOKEN_A" "{\"assetId\":\"$BTC_ID\",\"side\":\"BUY\",\"quantity\":0.01,\"idempotencyKey\":\"$IDEMP_KEY_1\"}")
  body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
  assert "$status" "201" "User A executes BUY 0.01 BTC"
  TRADE_ID_1=$(echo "$body" | jq -r '.id // empty')

  # Idempotency replay — same key, same trade
  resp=$(api_call POST "/trading/execute" "userA" "$TOKEN_A" "{\"assetId\":\"$BTC_ID\",\"side\":\"BUY\",\"quantity\":0.01,\"idempotencyKey\":\"$IDEMP_KEY_1\"}")
  body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
  REPLAY_ID=$(echo "$body" | jq -r '.id // empty')
  assert "$status" "201" "Idempotency-Key replay returns 201 (same result, not duplicated)"
  [[ "$REPLAY_ID" == "$TRADE_ID_1" ]] && { echo -e "  ${GREEN}PASS${NC} — replay returned identical trade id"; PASS=$((PASS+1)); } \
    || { echo -e "  ${RED}FAIL${NC} — replay returned a different trade id ($REPLAY_ID vs $TRADE_ID_1)"; FAIL=$((FAIL+1)); FAILURES+=("Idempotency replay returned different trade id"); }

  # Insufficient funds
  IDEMP_KEY_2="idem-$(rand)"
  resp=$(api_call POST "/trading/execute" "userA" "$TOKEN_A" "{\"assetId\":\"$BTC_ID\",\"side\":\"BUY\",\"quantity\":999999,\"idempotencyKey\":\"$IDEMP_KEY_2\"}")
  status=$(echo "$resp" | tail -1)
  assert "$status" "400" "BUY with insufficient funds returns 400"

  # Insufficient holdings (User B has never bought anything)
  IDEMP_KEY_3="idem-$(rand)"
  resp=$(api_call POST "/trading/execute" "userB" "$TOKEN_B" "{\"assetId\":\"$BTC_ID\",\"side\":\"SELL\",\"quantity\":1,\"idempotencyKey\":\"$IDEMP_KEY_3\"}")
  status=$(echo "$resp" | tail -1)
  assert "$status" "400" "SELL with insufficient holdings returns 400"

  # Invalid asset id
  IDEMP_KEY_4="idem-$(rand)"
  resp=$(api_call POST "/trading/execute" "userA" "$TOKEN_A" "{\"assetId\":\"00000000-0000-0000-0000-000000000000\",\"side\":\"BUY\",\"quantity\":0.01,\"idempotencyKey\":\"$IDEMP_KEY_4\"}")
  status=$(echo "$resp" | tail -1)
  assert "$status" "404" "Trade with invalid asset id returns 404"

  # Portfolio & trade history
  resp=$(api_call GET "/trading/portfolio" "userA" "$TOKEN_A" "")
  status=$(echo "$resp" | tail -1)
  assert "$status" "200" "GET /trading/portfolio succeeds"

  resp=$(api_call GET "/trading/trades" "userA" "$TOKEN_A" "")
  body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
  assert "$status" "200" "GET /trading/trades (paginated) succeeds"

  # Cross-tenant: User B cannot see User A's trades via any parameter tampering
  # (server resolves portfolio from auth principal, not from client input — just
  # confirm B's own trade list does not contain A's trade id)
  resp=$(api_call GET "/trading/trades" "userB" "$TOKEN_B" "")
  body=$(echo "$resp" | head -n -1)
  A_TRADE_LEAKED=$(echo "$body" | jq -r "[.content[]? | select(.id==\"$TRADE_ID_1\")] | length == 0" 2>/dev/null)
  assert_true "$A_TRADE_LEAKED" "User B's trade list does not contain User A's trade"
fi


# ============================================================
section "6. Analytics"
# ============================================================
resp=$(api_call GET "/analytics/summary" "userA" "$TOKEN_A" "")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /analytics/summary succeeds for User A"

resp=$(api_call GET "/analytics/history" "userA" "$TOKEN_A" "")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /analytics/history succeeds"

# Cross-tenant: User B (no trades) gets an empty/default summary, not A's data
resp=$(api_call GET "/analytics/summary" "userB" "$TOKEN_B" "")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /analytics/summary succeeds for User B (no trades)"
B_TOTAL_TRADES=$(echo "$body" | jq -r '.totalTrades // "null"')
[[ "$B_TOTAL_TRADES" == "0" ]] && { echo -e "  ${GREEN}PASS${NC} — User B's summary shows 0 trades, not User A's data"; PASS=$((PASS+1)); } \
  || { echo -e "  ${RED}FAIL${NC} — User B's totalTrades was '$B_TOTAL_TRADES', expected 0"; FAIL=$((FAIL+1)); FAILURES+=("Analytics cross-tenant isolation check"); }


# ============================================================
section "7. AI Trade Journal"
# ============================================================
resp=$(api_call POST "/journal/generate" "userA" "$TOKEN_A" "")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "201" "User A generates today's journal entry (has trades)"

# Duplicate generation same day
resp=$(api_call POST "/journal/generate" "userA" "$TOKEN_A" "")
status=$(echo "$resp" | tail -1)
assert "$status" "409" "Generating journal again same day returns 409"

# User B has no trades today
resp=$(api_call POST "/journal/generate" "userB" "$TOKEN_B" "")
status=$(echo "$resp" | tail -1)
assert "$status" "400" "Journal generation with zero trades returns 400"

resp=$(api_call GET "/journal" "userA" "$TOKEN_A" "")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /journal (paginated history) succeeds"


# ============================================================
section "8. Notifications"
# ============================================================
resp=$(api_call GET "/notifications" "userA" "$TOKEN_A" "")
body=$(echo "$resp" | head -n -1); status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /notifications succeeds"
NOTIF_ID=$(echo "$body" | jq -r '.content[0].id // .[0].id // empty' 2>/dev/null)

resp=$(api_call GET "/notifications/unread-count" "userA" "$TOKEN_A" "")
status=$(echo "$resp" | tail -1)
assert "$status" "200" "GET /notifications/unread-count succeeds"

if [[ -n "$NOTIF_ID" && "$NOTIF_ID" != "null" ]]; then
  resp=$(api_call PATCH "/notifications/$NOTIF_ID/read" "userA" "$TOKEN_A" "")
  status=$(echo "$resp" | tail -1)
  assert "$status" "200" "PATCH /notifications/{id}/read succeeds"
else
  echo -e "  ${YELLOW}SKIP${NC} — no notification id available to mark as read"
fi

# Cross-tenant: User B cannot mark User A's notification as read
if [[ -n "$NOTIF_ID" && "$NOTIF_ID" != "null" ]]; then
  resp=$(api_call PATCH "/notifications/$NOTIF_ID/read" "userB" "$TOKEN_B" "")
  status=$(echo "$resp" | tail -1)
  assert "$status" "404" "User B cannot mark User A's notification as read (404, not leaked as 403)"
fi


# ============================================================
section "9. Rate Limiting (opt-in — set RUN_RATE_LIMIT_TEST=1 to enable)"
# ============================================================
if [[ "$RUN_RATE_LIMIT_TEST" == "1" ]]; then
  info "Firing 12 rapid registrations to trip the per-IP auth rate limit..."
  last_status=""
  for i in $(seq 1 12); do
    resp=$(api_call POST "/auth/register" "" "" "{\"email\":\"ratelimit-$i-$(rand)@test.com\",\"password\":\"$PASSWORD\",\"displayName\":\"RL $i\"}")
    last_status=$(echo "$resp" | tail -1)
  done
  assert "$last_status" "429" "12th rapid registration attempt is rate-limited (429)"
else
  echo -e "  ${YELLOW}SKIPPED${NC} — set RUN_RATE_LIMIT_TEST=1 to run (will briefly rate-limit your IP)"
fi


# ============================================================
section "SUMMARY"
# ============================================================
TOTAL=$((PASS+FAIL))
echo -e "${BOLD}Total: $TOTAL   ${GREEN}Passed: $PASS${NC}   ${RED}Failed: $FAIL${NC}"

if [[ $FAIL -gt 0 ]]; then
  echo -e "\n${RED}${BOLD}Failures:${NC}"
  for f in "${FAILURES[@]}"; do
    echo -e "  ${RED}✗${NC} $f"
  done
fi

echo -e "\n${YELLOW}Reminder — not covered by this script (needs manual/browser check):${NC}"
echo "  - WebSocket: live price ticks on /topic/prices/{symbol}, per-user portfolio"
echo "    and notification delivery on /user/queue/*, cross-user isolation."
echo "  - Frontend UI: visual states, RBAC-conditional rendering, chart rendering."
echo "  - Multi-instance Redis pub/sub fan-out (requires 2+ backend instances)."

[[ $FAIL -eq 0 ]] && exit 0 || exit 1
