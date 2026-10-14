#!/bin/sh
set -eu

export VITE_IS_MICRO="${PUBLIC_MICRO_MODE}"

exec npm run build:prod
