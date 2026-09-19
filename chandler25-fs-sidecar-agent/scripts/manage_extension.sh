#!/bin/bash
set -e

# ==============================================================================
# FreeSWITCH 分机动态管理脚本 (manage_extension.sh)
# 用途：由 Go-Sidecar 或运维 CLI 调用，动态生成/删除分机 XML 并触发 reloadxml
# ==============================================================================

ACTION=$1          # create | delete | check
EXT=$2             # 分机号，如 1008
PASSWORD=$3        # 密码
CONTEXT=${4:-default}
CALLGROUP=${5:-default}
DIR_PATH="/opt/homebrew/etc/freeswitch/directory/default"

if [ -z "$ACTION" ]; then
  echo '{"code": 400, "error": "Missing action parameter (create|delete|check)"}'
  exit 1
fi

case "$ACTION" in
  create)
    if [ -z "$EXT" ] || [ -z "$PASSWORD" ]; then
      echo '{"code": 400, "error": "Usage: manage_extension.sh create <extension> <password> [context] [callgroup]"}'
      exit 1
    fi
    
    TARGET_FILE="$DIR_PATH/$EXT.xml"
    TEMP_FILE="/tmp/fs_ext_${EXT}_$$.xml"

    # 生成标准 FreeSWITCH 分机 XML 配置
    cat <<EOF > "$TEMP_FILE"
<include>
  <user id="$EXT">
    <params>
      <param name="password" value="$PASSWORD"/>
      <param name="vm-password" value="$EXT"/>
    </params>
    <variables>
      <variable name="toll_allow" value="domestic,international,local"/>
      <variable name="accountcode" value="$EXT"/>
      <variable name="user_context" value="$CONTEXT"/>
      <variable name="effective_caller_id_name" value="Extension $EXT"/>
      <variable name="effective_caller_id_number" value="$EXT"/>
      <variable name="outbound_caller_id_name" value="\$${outbound_caller_name}"/>
      <variable name="outbound_caller_id_number" value="\$${outbound_caller_id}"/>
      <variable name="callgroup" value="$CALLGROUP"/>
      <!-- 支持 WebRTC / SIP 双模自适应加密与转码 -->
      <variable name="rtp_secure_media" value="optional"/>
    </variables>
  </user>
</include>
EOF

    mv "$TEMP_FILE" "$TARGET_FILE"
    chmod 644 "$TARGET_FILE"

    # 触发 FreeSWITCH 热重载
    RELOAD_RES=$(fs_cli -x "reloadxml" 2>&1 || true)
    echo "{\"code\": 200, \"message\": \"Extension $EXT created successfully\", \"extension\": \"$EXT\", \"reload_result\": \"$RELOAD_RES\"}"
    ;;

  delete)
    if [ -z "$EXT" ]; then
      echo '{"code": 400, "error": "Usage: manage_extension.sh delete <extension>"}'
      exit 1
    fi

    TARGET_FILE="$DIR_PATH/$EXT.xml"
    if [ -f "$TARGET_FILE" ]; then
      rm -f "$TARGET_FILE"
      RELOAD_RES=$(fs_cli -x "reloadxml" 2>&1 || true)
      echo "{\"code\": 200, \"message\": \"Extension $EXT deleted successfully\", \"extension\": \"$EXT\", \"reload_result\": \"$RELOAD_RES\"}"
    else
      echo "{\"code\": 404, \"message\": \"Extension $EXT does not exist\", \"extension\": \"$EXT\"}"
    fi
    ;;

  check)
    if [ -z "$EXT" ]; then
      echo '{"code": 400, "error": "Usage: manage_extension.sh check <extension>"}'
      exit 1
    fi

    TARGET_FILE="$DIR_PATH/$EXT.xml"
    if [ -f "$TARGET_FILE" ]; then
      REG_INFO=$(fs_cli -x "sofia status profile internal reg" 2>&1 | grep -w "$EXT" || true)
      echo "{\"code\": 200, \"exists\": true, \"extension\": \"$EXT\", \"registration\": \"$REG_INFO\"}"
    else
      echo "{\"code\": 404, \"exists\": false, \"extension\": \"$EXT\"}"
    fi
    ;;

  *)
    echo "{\"code\": 400, \"error\": \"Unknown action: $ACTION\"}"
    exit 1
    ;;
esac
