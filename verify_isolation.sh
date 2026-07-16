#!/usr/bin/bash
export PATH="/c/Users/LENOVO/AppData/Local/Temp/opencode:$PATH"

KNOWN_TRADE_ID="8e0982fa-172c-4bac-940d-1741cbc7b12f"

echo "=== Login as User B (not the trade owner) ==="
B_RAW=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test-b-1784200140481692200-1379@test.com","password":"password123"}')
TOKEN_B=$(echo "$B_RAW" | jq -r .accessToken)
echo "TOKEN_B length: ${#TOKEN_B}"

echo ""
echo "=== User B GET /trading/trades — RAW JSON RESPONSE ==="
B_TRADES=$(curl -s http://localhost:8080/api/v1/trading/trades -H "Authorization: Bearer $TOKEN_B")
echo "$B_TRADES" | jq .

echo ""
echo "=== Script's jq command ==="
CMD='[.content[]? // .[]? | select(.id=="'"$KNOWN_TRADE_ID"'")] | length == 0'
echo "Command: $CMD"
RESULT=$(echo "$B_TRADES" | jq -r "$CMD" 2>/dev/null)
echo "Result: $RESULT"

echo ""
echo "=== Direct check: is the trade owner's ID in B's response? ==="
echo "$B_TRADES" | jq -r '.content[]?.id // empty' 2>/dev/null
echo "(empty means no trades found — isolation holds)"
