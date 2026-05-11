#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVICE="qart-web"

echo "[deploy] Pulling latest changes from origin/web..."
git -C "$DEPLOY_DIR" pull origin web

echo "[deploy] Building..."
mvn -f "$DEPLOY_DIR/pom.xml" package -DskipTests -q

echo "[deploy] Restarting service..."
systemctl restart "$SERVICE"
systemctl is-active "$SERVICE"

echo "[deploy] Done."
