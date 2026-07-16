#!/usr/bin/bash
export PATH="/c/Users/LENOVO/AppData/Local/Temp/opencode:$PATH"
RAND=$(date +%s%N)-$RANDOM

A_RAW=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test-a-'$RAND'@test.com","password":"password123","displayName":"A"}')
TOKEN_A=$(echo "$A_RAW" | jq -r .accessToken)
echo "TOKEN_A length: ${#TOKEN_A}"

BTC_ID=$(curl -s http://localhost:8080/api/v1/market/assets | jq -r '.[] | select(.symbol=="BTC") | .id')
echo "BTC_ID=$BTC_ID"

echo "=== Raw trade response ==="
TRADE_RESP=$(curl -s -w '\n---HTTP_CODE:%{http_code}---' -X POST http://localhost:8080/api/v1/trading/execute \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"assetId":"'$BTC_ID'","side":"BUY","quantity":0.01,"idempotencyKey":"idem-iso-'$RAND'"}')
echo "$TRADE_RESP"
