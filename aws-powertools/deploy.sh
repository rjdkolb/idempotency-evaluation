#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

STACK_NAME="aws-powertools-idempotency"
REGION="eu-central-1"

echo "Building Lambda zip..."
mise exec -- ./gradlew lambdaZip -q

echo "Deploying SAM stack..."
sam deploy

URL=$(aws cloudformation describe-stacks \
  --stack-name "$STACK_NAME" --region "$REGION" \
  --query "Stacks[0].Outputs[?OutputKey=='HttpApiUrl'].OutputValue" \
  --output text)

if [[ -z "$URL" || "$URL" == "None" ]]; then
  echo "Failed to read HttpApiUrl from stack outputs" >&2
  exit 1
fi

echo -n "$URL" > url.txt
echo "Deployed. Base URL: $URL"
echo "(written to $(pwd)/url.txt)"
