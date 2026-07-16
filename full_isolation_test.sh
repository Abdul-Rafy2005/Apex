#!/usr/bin/bash
export PATH="/c/Users/LENOVO/AppData/Local/Temp/opencode:$PATH"
RAND=$(date +%s)

echo "=== Register User A ==="
A_RAW=$(curl -s -w '\n%{http_code}' -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"iso-a-$RAND@test.com\",\"password\":\"testpass123\",\"displayName\":\"A\"}")
A_STATUS=$(echo "$A_RAW" | tail -1)
A_BODY=$(echo "$A_RAW" | head -n -1)
echo "Status: $A_STATUS"
TOKEN_A=$(echo "$A_BODY" | jq -r .accessToken 2>/dev/null)
echo "TOKEN_A length: ${#TOKEN_A}"
if [ "${#TOKEN_A}" -lt 10 ]; then
  echo "Body: $A_BODY"
  exit 1
fi

echo ""
echo "=== Register User B ==="
B_RAW=$(curl -s -w '\n%{http_code}' -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"iso-b-$RAND@test.com\",\"password\":\"testpass123\",\"displayName\":\"B\"}")
B_STATUS=$(echo "$B_RAW" | tail -1)
B_BODY=$(echo "$B_RAW" | head -n -1)
echo "Status: $B_STATUS"
TOKEN_B=$(echo "$B_BODY" | jq -r .accessToken 2>/dev/null)
echo "TOKEN_B length: ${#TOKEN_B}"
if [ "${#TOKEN_B}" -lt 10 ]; then
  echo "Body: $B_BODY"
  exit 1
fi

echo ""
echo "=== Get BTC ID ==="
BTC_ID=$(curl -s http://localhost:8080/api/v1/market/assets | jq -r '.[] | select(.symbol=="BTC") | .id')
echo "BTC_ID=$BTC_ID"

echo ""
echo "=== User A executes BUY 0.01 BTC ==="
TRADE_RAW=$(curl -s -w '\n%{http_code}' -X POST http://localhost:8080/api/v1/trading/execute \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d "{\"assetId\":\"$BTC_ID\",\"side\":\"BUY\",\"quantity\":0.01,\"idempotencyKey\":\"idem-iso-$RAND\"}")
TRADE_STATUS=$(echo "$TRADE_RAW" | tail -1)
TRADE_BODY=$(echo "$TRADE_RAW" | head -n -1)
echo "Status: $TRADE_STATUS"
TRADE_ID=$(echo "$TRADE_BODY" | jq -r .id 2>/dev/null)
echo "TRADE_ID=$TRADE_ID"

if [ "$TRADE_STATUS" != "201" ]; then
  echo "Trade failed. Body: $TRADE_BODY"
  echo "Cannot test isolation without a successful trade."
  exit 1
fi

echo ""
echo "========================================="
echo "=== User B GET /trading/trades RAW ==="
echo "========================================="
B_TRADES=$(curl -s http://localhost:8080/api/v1/trading/trades -H "Authorization: Bearer $TOKEN_B")
echo "$B_TRADES" | jq .

echo ""
echo "=== Script jq command ==="
CMD='[.content[]? // .[]? | select(.id=="'"$TRADE_ID"'")] | length == 0'
echo "Command: $CMD"
RESULT=$(echo "$B_TRADES" | jq -r "$CMD" 2>/dev/null)
echo "assert_true result: $RESULT"

echo ""
echo "=== Direct verification: list all trade IDs in B response ==="
IDS=$(echo "$B_TRADES" | jq -r '.content[]?.id // empty' 2>/dev/null)
if [ -z "$IDS" ]; then
  echo "NO TRADES VISIBLE TO USER B. Isolation HOLDS."
else
  echo "Trade IDs found: $IDS"
  if echo "$IDS" | grep -q "$TRADE_ID"; then
    echo "LEAK DETECTED!"
  else
    echo "No leak - trades belong to B only."
  fi
fi
