#!/usr/bin/bash
export PATH="/c/Users/LENOVO/AppData/Local/Temp/opencode:$PATH"

KNOWN_TRADE_ID="8e0982fa-172c-4bac-940d-1741cbc7b12f"

echo "=== Register a fresh User B ==="
B_RAW=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"isolation-verify-'$(date +%s)'@test.com","password":"testpass123","displayName":"VerifyUser"}')
TOKEN_B=$(echo "$B_RAW" | jq -r .accessToken)
echo "TOKEN_B length: ${#TOKEN_B}"

echo ""
echo "=== User B GET /trading/trades — RAW JSON RESPONSE ==="
B_TRADES=$(curl -s http://localhost:8080/api/v1/trading/trades -H "Authorization: Bearer $TOKEN_B")
echo "$B_TRADES" | jq .

echo ""
echo "=== jq extraction from script ==="
CMD='[.content[]? // .[]? | select(.id=="'"$KNOWN_TRADE_ID"'")] | length == 0'
echo "Command: $CMD"
RESULT=$(echo "$B_TRADES" | jq -r "$CMD" 2>/dev/null)
echo "Result: $RESULT"

echo ""
echo "=== Are there ANY trade IDs in the response? ==="
TRADE_IDS=$(echo "$B_TRADES" | jq -r '.content[]?.id // empty' 2>/dev/null)
if [ -z "$TRADE_IDS" ]; then
  echo "(empty — no trades visible to User B — isolation HOLDS)"
else
  echo "FOUND TRADE IDs: $TRADE_IDS"
  if echo "$TRADE_IDS" | grep -q "$KNOWN_TRADE_ID"; then
    echo "LEAK DETECTED: User A's trade is visible to User B!"
  else
    echo "Trade IDs belong to User B only — no leak"
  fi
fi
