#!/usr/bin/env bash
export PATH="/c/tools:$PATH"
EMAIL_A="diag-a-$(date +%s)@test.com"
EMAIL_B="diag-b-$(date +%s)@test.com"
TOK_A=$(curl -sS -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_A\",\"password\":\"SecurePass123!\",\"displayName\":\"A\"}" | jq -r .accessToken)
TOK_B=$(curl -sS -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_B\",\"password\":\"SecurePass123!\",\"displayName\":\"B\"}" | jq -r .accessToken)
ORG=$(curl -sS -X POST http://localhost:8080/api/v1/organizations \
  -H "Authorization: Bearer $TOK_A" -H "Content-Type: application/json" \
  -d "{\"name\":\"DiagOrg\",\"type\":\"BOOTCAMP\"}")
ORG_ID=$(echo "$ORG" | jq -r .id)
echo "ORG_ID=$ORG_ID"
curl -sS -X POST http://localhost:8080/api/v1/organizations/join \
  -H "Authorization: Bearer $TOK_B" -H "Content-Type: application/json" \
  -d "{\"organizationId\":\"$ORG_ID\"}" > /dev/null

echo "=== members ==="
curl -sS "http://localhost:8080/api/v1/organizations/$ORG_ID/members" \
  -H "Authorization: Bearer $TOK_A" | jq .

echo "=== promote B to INSTRUCTOR ==="
# Get user B's membership id from the members list
MEMBER_ID=$(curl -sS "http://localhost:8080/api/v1/organizations/$ORG_ID/members" \
  -H "Authorization: Bearer $TOK_A" | jq -r ".[] | select(.email==\"$EMAIL_B\") | .userId // .id // empty")
echo "MEMBER_ID=$MEMBER_ID"
if [ -z "$MEMBER_ID" ]; then
  echo "Trying alternative jq extraction..."
  curl -sS "http://localhost:8080/api/v1/organizations/$ORG_ID/members" \
    -H "Authorization: Bearer $TOK_A" | jq .
fi

echo "=== history endpoint ==="
curl -sS -w "\nHTTP %{http_code}" "http://localhost:8080/api/v1/market/BTC/history?days=30" \
  -H "Authorization: Bearer $TOK_A"

echo ""
echo "=== journal generate ==="
curl -sS -w "\nHTTP %{http_code}" -X POST "http://localhost:8080/api/v1/journal/generate" \
  -H "Authorization: Bearer $TOK_A"

echo ""
echo "=== users/me without auth ==="
curl -sS -w "\nHTTP %{http_code}" "http://localhost:8080/api/v1/users/me"
