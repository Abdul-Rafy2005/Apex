#!/usr/bin/bash
export PATH="/c/Users/LENOVO/AppData/Local/Temp/opencode:$PATH"

TRADE_ID="24f6dcca-a190-475a-a2be-953c283cb9fa"
BODY='{"content":[],"empty":true,"first":true,"last":true,"number":0,"numberOfElements":0,"size":20,"totalElements":0,"totalPages":0}'

echo "=== Script command ==="
RESULT=$(echo "$BODY" | jq -r "[.content[]? // .[]? | select(.id==\"$TRADE_ID\")] | length == 0" 2>&1)
echo "Result: [$RESULT]"
echo "Exit: $?"

echo ""
echo "=== .content length ==="
echo "$BODY" | jq ".content | length"

echo ""
echo "=== Alternative: if-then ==="
echo "$BODY" | jq -r 'if (.content | length) == 0 then "true" else "false" end'
