#!/usr/bin/env bash
set -Eeuo pipefail

AWS_REGION="${AWS_REGION:-ap-northeast-2}"
INSTANCE_ID="${INSTANCE_ID:-i-01b61355cd9f05761}"
INSTANCE_USER="${INSTANCE_USER:-ec2-user}"
KEY_PATH="${KEY_PATH:-/tmp/ournight-alpha-eic}"

if [[ ! -f "$KEY_PATH" || ! -f "${KEY_PATH}.pub" ]]; then
  ssh-keygen -t ed25519 -f "$KEY_PATH" -N "" -C "ournight-alpha-eic" >/dev/null
fi

read -r AVAILABILITY_ZONE PUBLIC_IP < <(
  aws ec2 describe-instances \
    --region "$AWS_REGION" \
    --instance-ids "$INSTANCE_ID" \
    --query 'Reservations[0].Instances[0].[Placement.AvailabilityZone,PublicIpAddress]' \
    --output text
)

if [[ -z "$AVAILABILITY_ZONE" || -z "$PUBLIC_IP" || "$PUBLIC_IP" == "None" ]]; then
  echo "Could not resolve availability zone or public IP for $INSTANCE_ID" >&2
  exit 1
fi

aws ec2-instance-connect send-ssh-public-key \
  --region "$AWS_REGION" \
  --instance-id "$INSTANCE_ID" \
  --availability-zone "$AVAILABILITY_ZONE" \
  --instance-os-user "$INSTANCE_USER" \
  --ssh-public-key "file://${KEY_PATH}.pub" >/dev/null

exec ssh \
  -i "$KEY_PATH" \
  -o StrictHostKeyChecking=accept-new \
  "${INSTANCE_USER}@${PUBLIC_IP}"
