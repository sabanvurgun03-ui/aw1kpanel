#!/bin/sh
# AW1K Panel - veri kullanımı kaydedici kurulumu.
# Modemde root olarak çalıştır (SSH'a yapıştırabilir ya da dosya olarak `sh` ile çalıştırabilirsin).
#
# Yaptığı şey:
#   1) /usr/bin/aw1k-usage          : her 2 dakikada wwan0_1 sayaçlarını okuyup günlük toplamı tutar
#   2) /usr/libexec/rpcd/aw1k       : `ubus call aw1k usage` (geçmiş), `kediray` (durum), `kediray_ctl` (başlat/durdur)
#   3) /usr/share/rpcd/acl.d/...    : uygulamanın bu çağrıyı yapmasına izin verir
#   4) cron kaydı ve rpcd yeniden başlatma
set -e

cat > /usr/bin/aw1k-usage <<'EOF'
#!/bin/sh
# Günlük mobil veri kaydı. Satır biçimi: YYYY-MM-DD rx_bayt tx_bayt
DEV="wwan0_1"
PERSIST="/etc/aw1k-usage.db"   # kalıcı kopya (flash), saatte bir yazılır
RUN="/tmp/aw1k-usage.db"       # çalışma kopyası (RAM)
LAST="/tmp/aw1k-usage.last"    # önceki sayaç değerleri

st=$(ubus call network.device status "{\"name\":\"$DEV\"}" 2>/dev/null) || exit 0
rx=$(echo "$st" | jsonfilter -e '@.statistics.rx_bytes')
tx=$(echo "$st" | jsonfilter -e '@.statistics.tx_bytes')
[ -n "$rx" ] && [ -n "$tx" ] || exit 0

if [ ! -f "$RUN" ]; then
    if [ -f "$PERSIST" ]; then cp "$PERSIST" "$RUN"; else : > "$RUN"; fi
fi

if [ -f "$LAST" ]; then
    read prx ptx < "$LAST"
elif [ -f "$PERSIST" ]; then
    prx=0; ptx=0            # modem yeniden başlamış: sayaçlar sıfırdan başladı
else
    prx=$rx; ptx=$tx        # ilk kurulum: geçmiş sayaçlar yok sayılır
fi

# Sayaç geriye gittiyse (yeniden başlatma) mevcut değer yeni farktır
if [ "$rx" -lt "$prx" ]; then drx=$rx; else drx=$((rx - prx)); fi
if [ "$tx" -lt "$ptx" ]; then dtx=$tx; else dtx=$((tx - ptx)); fi
echo "$rx $tx" > "$LAST"

today=$(date +%F)
awk -v d="$today" -v rx="$drx" -v tx="$dtx" '
    $1 == d { $2 += rx; $3 += tx; found = 1 }
    { printf "%s %.0f %.0f\n", $1, $2, $3 }
    END { if (!found) printf "%s %.0f %.0f\n", d, rx, tx }
' "$RUN" | tail -n 400 > "$RUN.new" && mv "$RUN.new" "$RUN"

if [ ! -f "$PERSIST" ] || [ "$(date +%M)" -lt 2 ]; then
    cp "$RUN" "$PERSIST"
fi
EOF
chmod 755 /usr/bin/aw1k-usage

mkdir -p /usr/libexec/rpcd
cat > /usr/libexec/rpcd/aw1k <<'EOF'
#!/bin/sh
# rpcd eklentisi: ubus call aw1k usage | kediray | kediray_ctl
case "$1" in
list)
    echo '{ "usage": {}, "kediray": {}, "kediray_ctl": { "action": "str" } }'
    ;;
call)
    case "$2" in
    kediray)
        running=false; /etc/init.d/kediray status 2>/dev/null | grep -q running && running=true
        enabled=false; /etc/init.d/kediray enabled 2>/dev/null && enabled=true
        st=$(ubus call network.device status '{"name":"kediray0"}' 2>/dev/null)
        rx=$(echo "$st" | jsonfilter -e '@.statistics.rx_bytes'); [ -n "$rx" ] || rx=0
        tx=$(echo "$st" | jsonfilter -e '@.statistics.tx_bytes'); [ -n "$tx" ] || tx=0
        since=0
        [ -f /var/etc/kediray/config.json ] && since=$(stat -c %Y /var/etc/kediray/config.json 2>/dev/null || echo 0)
        printf '{"running":%s,"enabled":%s,"since":%s,"now":%s,"rx":%s,"tx":%s}' \
            "$running" "$enabled" "$since" "$(date +%s)" "$rx" "$tx"
        ;;
    kediray_ctl)
        read -r input
        action=$(echo "$input" | jsonfilter -e '@.action')
        case "$action" in
        start|stop|restart)
            ( /etc/init.d/kediray "$action" >/dev/null 2>&1 & )
            printf '{"ok":true}'
            ;;
        *) printf '{"ok":false}' ;;
        esac
        ;;
    usage)
        f=/tmp/aw1k-usage.db
        [ -f "$f" ] || f=/etc/aw1k-usage.db
        printf '{"today":"%s","days":[' "$(date +%F)"
        [ -f "$f" ] && awk 'NF == 3 { if (n++) printf ","; printf "{\"d\":\"%s\",\"rx\":%.0f,\"tx\":%.0f}", $1, $2, $3 }' "$f"
        printf ']}'
        ;;
    esac
    ;;
esac
EOF
chmod 755 /usr/libexec/rpcd/aw1k

mkdir -p /usr/share/rpcd/acl.d
cat > /usr/share/rpcd/acl.d/aw1k-panel.json <<'EOF'
{
    "aw1k-panel": {
        "description": "AW1K Panel veri kullanımı ve KediRay",
        "read": {
            "ubus": {
                "aw1k": [ "usage", "kediray", "kediray_ctl" ]
            }
        },
        "write": {
            "ubus": {
                "aw1k": [ "kediray_ctl" ]
            }
        }
    }
}
EOF

grep -q '/usr/bin/aw1k-usage' /etc/crontabs/root 2>/dev/null || echo '*/2 * * * * /usr/bin/aw1k-usage' >> /etc/crontabs/root
/etc/init.d/cron enable
/etc/init.d/cron restart

/usr/bin/aw1k-usage || true
/etc/init.d/rpcd restart
echo "Kurulum tamam. Dene: ubus call aw1k usage ; ubus call aw1k kediray"
