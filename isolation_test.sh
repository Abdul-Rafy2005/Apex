#!/usr/bin/bash
export PATH="/c/Users/LENOVO/AppData/Local/Temp/opencode:$PATH"
RAND=$(date +%s%N)-$RANDOM

A_RAW=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test-a-'$RAND'@test.com","password":"password123","displayName":"A"}')
TOKEN_A=$(echo "$A_RAW" | jq -r .accessToken)
USER_A_ID=$(echo "$A_RAW" | jq -r .user.id)

B_RAW=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test-b-'$RAND'@test.com","password":"password123","displayName":"B"}')
TOKEN_B=$(echo "$B_RAW" | jq -r .accessToken)

BTC_ID=$(curl -s http://localhost:8080/api/v1/market/assets | jq -r '.[] | select(.symbol=="BTC") | .id')

TRADE_RESP=$(curl -s -w '\n%{http_code}' -X POST http://localhost:8080/api/v1/trading/execute \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"assetId":"'$BTC_ID'","side":"BUY","quantity":0.01,"idempotencyKey":"idem-iso-'$RAND'"}')
TRADE_ID=$(echo "$TRADE_RESP" | head -n -1 | jq -r .id)
echo "TRADE_ID=$TRADE_ID"

echo "=== User B raw trade list ==="
B_TRADES=$(curl -s http://localhost:8080/api/v1/trading/trades -H "Authorization: Bearer $TOKEN_B")
echo "$B_TRADES" | jq .

echo "=== jq command from script ==="
CMD='[.content[]? // .[]? | select(.id=="'"$TRADE_ID"'")] | length == 0'
echo "Command: $CMD"
RESULT=$(echo "$B_TRADES" | jq -r "$CMD" 2>/dev/null)
echo "Result: $RESULT"
