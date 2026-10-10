#!/usr/bin/env bash
# Галерея моделей на домашнем сервере (Proxmox 192.168.0.183, контейнер 100), доступ по ссылке через Cloudflare.
#   bash scripts/gallery_deploy.sh push   — выгрузить галерею (после python scripts/gallery_export.py)
#   bash scripts/gallery_deploy.sh pull   — забрать отметки с сервера в docs/model_review.json (потом gen_data.py)
#   bash scripts/gallery_deploy.sh link   — напечатать ссылку с ключом
# На сервере: /opt/rpm-gallery (файлы), /opt/rpm-gallery/review/model_review.json (отметки — push их не трогает),
# /etc/rpm-gallery.env (ключ), службы rpm-gallery и rpm-gallery-tunnel.
set -euo pipefail
HOST=root@192.168.0.183
CT=100
cd "$(dirname "$0")/.."
ct() { ssh -o BatchMode=yes "$HOST" "pct exec $CT -- bash -c '$1'"; }

case "${1:-}" in
push)
    tar czf - --owner=0 --group=0 scripts/gallery_server.py tools/model_gallery \
        | ssh -o BatchMode=yes "$HOST" "pct exec $CT -- bash -c 'rm -rf /opt/rpm-gallery/tools && tar xzf - --no-same-owner -C /opt/rpm-gallery && chown -R gallery:gallery /opt/rpm-gallery && systemctl restart rpm-gallery'"
    echo "выгружено"
    ;;
pull)
    ct "cat /opt/rpm-gallery/review/model_review.json" > docs/model_review.json
    echo "отметки -> docs/model_review.json"
    ;;
link)
    url=$(ct "journalctl -u rpm-gallery-tunnel --no-pager -o cat | grep -o \"https://[a-z0-9-]*\.trycloudflare\.com\" | tail -1")
    key=$(ct "sed -n s/^GALLERY_KEY=//p /etc/rpm-gallery.env")
    echo "$url/?k=$key"
    ;;
*)
    sed -n 2,7p "$0"
    exit 1
    ;;
esac
