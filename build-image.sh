#!/usr/bin/env bash

set -euo pipefail

IMAGE_NAME="minha-rinha-app-spring"
IMAGE_TAG="${1:-latest}"
IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if docker image inspect "${IMAGE}" >/dev/null 2>&1; then
  echo "Removendo imagem anterior: ${IMAGE}"
  docker image rm --force "${IMAGE}"
fi

docker build \
  --tag "${IMAGE}" \
  "${SCRIPT_DIR}"

echo "Imagem criada: ${IMAGE}"
