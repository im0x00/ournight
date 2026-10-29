#!/usr/bin/env bash
set -Eeuo pipefail

AWS_REGION="${AWS_REGION:-ap-northeast-2}"
INSTANCE_ID="${INSTANCE_ID:-i-093b8e876a58cab1f}"
INSTANCE_USER="${INSTANCE_USER:-ec2-user}"
KEY_PATH="${KEY_PATH:-/tmp/ournight-bastion-eic}"
LOCAL_PORT="${LOCAL_PORT:-15432}"
RDS_HOST="${RDS_HOST:-ournight-db.ca4x8guqb9w9.ap-northeast-2.rds.amazonaws.com}"
RDS_PORT="${RDS_PORT:-5432}"

if [[ ! -f "$KEY_PATH" || ! -f "${KEY_PATH}.pub" ]]; then
  ssh-keygen -t ed25519 -f "$KEY_PATH" -N "" -C "ournight-bastion-eic" >/dev/null
fi

read -r AVAILABILITY_ZONE PUBLIC_IP STATE < <(
  aws ec2 describe-instances \
    --region "$AWS_REGION" \
    --instance-ids "$INSTANCE_ID" \
    --query 'Reservations[0].Instances[0].[Placement.AvailabilityZone,PublicIpAddress,State.Name]' \
    --output text
)

if [[ "$STATE" != "running" ]]; then
  echo "Bastion $INSTANCE_ID is not running (state: $STATE)" >&2
  exit 1
fi

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

echo "Opening RDS tunnel: localhost:${LOCAL_PORT} -> ${RDS_HOST}:${RDS_PORT} via ${INSTANCE_ID}"
echo "Keep this process running while you use psql/DataGrip/DBeaver."

exec ssh \
  -i "$KEY_PATH" \
  -o StrictHostKeyChecking=accept-new \
  -N \
  -L "${LOCAL_PORT}:${RDS_HOST}:${RDS_PORT}" \
  "${INSTANCE_USER}@${PUBLIC_IP}"
