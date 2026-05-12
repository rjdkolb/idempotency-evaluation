#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

STACK_NAME="aws-powertools-idempotency"
REGION="eu-central-1"

echo "Deleting SAM stack ${STACK_NAME} in ${REGION}..."
sam delete --stack-name "$STACK_NAME" --region "$REGION" --no-prompts

rm -f url.txt
echo "Stack deleted and url.txt removed."

