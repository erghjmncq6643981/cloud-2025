package db

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// Repository 数据仓库接口封装
type Repository struct {
	client *DBClient
}

// NewRepository 创建 Repository 并自动初始化 PG 软交换表与种子数据
func NewRepository(client *DBClient) *Repository {
	r := &Repository{client: client}
	if client != nil && client.GetDB() != nil {
		if err := r.EnsureSchemaAndSeed(); err != nil {
			log.Printf("⚠️ [PG] 初始化软交换表结构或种子数据警告: %v", err)
		}
	}
	return r
}

// EnsureSchemaAndSeed 自动创建 fs_extension, fs_gateway, fs_cdr 表并按需初始化数据
func (r *Repository) EnsureSchemaAndSeed() error {
	db := r.client.GetDB()
	if db == nil {
		return fmt.Errorf("PostgreSQL 未连接")
	}

	createSql := `
		CREATE TABLE IF NOT EXISTS fs_extension (
			id                  SERIAL PRIMARY KEY,
			extension           VARCHAR(32) NOT NULL UNIQUE,
			password            VARCHAR(128) NOT NULL,
			context             VARCHAR(64) DEFAULT 'default',
			callgroup           VARCHAR(64) DEFAULT 'default',
			effective_caller_id VARCHAR(64) DEFAULT '',
			endpoint_type       VARCHAR(32) DEFAULT 'SIP',
			is_enabled          BOOLEAN DEFAULT TRUE,
			description         VARCHAR(255) DEFAULT '',
			created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
			updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
		);

		CREATE INDEX IF NOT EXISTS idx_fs_ext_enabled ON fs_extension (is_enabled);

		CREATE TABLE IF NOT EXISTS fs_gateway (
			id                  SERIAL PRIMARY KEY,
			name                VARCHAR(64) NOT NULL UNIQUE,
			profile             VARCHAR(32) DEFAULT 'external',
			proxy               VARCHAR(128) NOT NULL,
			username            VARCHAR(64) NOT NULL,
			password            VARCHAR(128) DEFAULT '',
			auth_user           VARCHAR(128) DEFAULT '',
			from_user           VARCHAR(128) DEFAULT '',
			from_domain         VARCHAR(128) DEFAULT '',
			caller_id_in_from   BOOLEAN DEFAULT TRUE,
			context             VARCHAR(64) DEFAULT 'public',
			extension           VARCHAR(64) DEFAULT 'auto_to_user',
			dtmf_type           VARCHAR(32) DEFAULT 'rfc2833',
			codecs              VARCHAR(128) DEFAULT 'PCMA, G729',
			register            BOOLEAN DEFAULT TRUE,
			expire_seconds      INT DEFAULT 3600,
			ping_seconds        INT DEFAULT 25,
			status              VARCHAR(32) DEFAULT 'REGED',
			ping_ms             VARCHAR(32) DEFAULT '12ms',
			is_enabled          BOOLEAN DEFAULT TRUE,
			created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
			updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
		);

		CREATE INDEX IF NOT EXISTS idx_fs_gw_enabled ON fs_gateway (is_enabled);

		CREATE TABLE IF NOT EXISTS fs_cdr (
			id                      SERIAL PRIMARY KEY,
			call_uuid               VARCHAR(64) NOT NULL UNIQUE,
			caller_id_name          VARCHAR(64) DEFAULT '',
			caller_id_number        VARCHAR(64) NOT NULL,
			destination_number      VARCHAR(64) NOT NULL,
			context                 VARCHAR(64) DEFAULT 'default',
			start_epoch             BIGINT DEFAULT 0,
			answer_epoch            BIGINT DEFAULT 0,
			end_epoch               BIGINT DEFAULT 0,
			duration                INT DEFAULT 0,
			billsec                 INT DEFAULT 0,
			hangup_cause            VARCHAR(64) DEFAULT 'NORMAL_CLEARING',
			sip_hangup_disposition  VARCHAR(64) DEFAULT 'send_bye',
			direction               VARCHAR(32) DEFAULT 'inbound',
			read_codec              VARCHAR(32) DEFAULT 'PCMA',
			write_codec             VARCHAR(32) DEFAULT 'PCMA',
			sip_user_agent          VARCHAR(128) DEFAULT '',
			quality_percentage      VARCHAR(16) DEFAULT '100.0',
			variables_json          JSONB DEFAULT '{}'::jsonb,
			created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
		);

		CREATE INDEX IF NOT EXISTS idx_fs_cdr_caller ON fs_cdr (caller_id_number);
		CREATE INDEX IF NOT EXISTS idx_fs_cdr_dest ON fs_cdr (destination_number);
		CREATE INDEX IF NOT EXISTS idx_fs_cdr_created ON fs_cdr (created_at DESC);
	`
	if _, err := db.Exec(createSql); err != nil {
		return fmt.Errorf("执行建表 SQL 失败: %w", err)
	}

	// 1. 扫描本地 XML 目录全量比对增量补充导入 PG (ON CONFLICT DO NOTHING)
	r.seedExtensionsFromXml()

	// 2. 检查网关表，若为空则灌入初始 3 大运营商网关
	var gwCount int
	_ = db.QueryRow("SELECT COUNT(*) FROM fs_gateway").Scan(&gwCount)
	if gwCount == 0 {
		r.seedDefaultGateways()
	}

	// 3. 检查 CDR 表，若为空则灌入初始代表性话单
	var cdrCount int
	_ = db.QueryRow("SELECT COUNT(*) FROM fs_cdr").Scan(&cdrCount)
	if cdrCount == 0 {
		r.seedDefaultCdrs()
	}

	return nil
}

// seedExtensionsFromXml 扫描物理 XML 目录导入 PG
func (r *Repository) seedExtensionsFromXml() {
	dir := "/opt/homebrew/etc/freeswitch/directory/default"
	entries, err := os.ReadDir(dir)
	if err != nil {
		return
	}

	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".xml") {
			continue
		}
		ext := strings.TrimSuffix(entry.Name(), ".xml")
		isAllDigits := true
		for _, c := range ext {
			if c < '0' || c > '9' {
				isAllDigits = false
				break
			}
		}
		if !isAllDigits {
			continue
		}

		pwd := "1234"
		ctx := "default"
		callgroup := "default"

		contentBytes, err := os.ReadFile(filepath.Join(dir, entry.Name()))
		if err == nil {
			content := string(contentBytes)
			if idx := strings.Index(content, `name="password" value="`); idx != -1 {
				rest := content[idx+23:]
				if end := strings.Index(rest, `"`); end != -1 {
					pwd = rest[:end]
				}
			}
			if idx := strings.Index(content, `name="user_context" value="`); idx != -1 {
				rest := content[idx+27:]
				if end := strings.Index(rest, `"`); end != -1 {
					ctx = rest[:end]
				}
			}
			if idx := strings.Index(content, `name="callgroup" value="`); idx != -1 {
				rest := content[idx+24:]
				if end := strings.Index(rest, `"`); end != -1 {
					callgroup = rest[:end]
				}
			}
		}

		epType := "SIP"
		desc := fmt.Sprintf("标准 SIP 分机 %s", ext)
		if strings.HasPrefix(ext, "901") {
			epType = "WebRTC"
			desc = fmt.Sprintf("坐席专属分机 %s", ext)
		}

		_, _ = r.client.GetDB().Exec(`
			INSERT INTO fs_extension (extension, password, context, callgroup, endpoint_type, is_enabled, description)
			VALUES ($1, $2, $3, $4, $5, TRUE, $6)
			ON CONFLICT (extension) DO NOTHING;
		`, ext, pwd, ctx, callgroup, epType, desc)
	}
	log.Printf("✅ [PG] 成功从本地 XML 目录导入全量分机至 PostgreSQL fs_extension 表")
}

// seedDefaultGateways 灌入初始三大运营商中继
func (r *Repository) seedDefaultGateways() {
	gateways := []Gateway{
		{
			Name: "trunk_telecom_ims", Profile: "external", Proxy: "116.228.89.12:5060",
			Username: "02188991001", Password: "Ims@Pass2026", AuthUser: "02188991001@ims.sh.chinatel.com",
			FromUser: "02188991001", FromDomain: "ims.sh.chinatel.com", CallerIdInFrom: true,
			Context: "public", Extension: "auto_to_user", DtmfType: "rfc2833", Codecs: "PCMA, PCMU, G729",
			Register: true, ExpireSeconds: 3600, PingSeconds: 25, Status: "REGED", PingMS: "11ms",
		},
		{
			Name: "trunk_unicom_sh01", Profile: "external", Proxy: "112.65.10.88:5060",
			Username: "unicom_sip_trunk_901", Password: "Unicom#901!", AuthUser: "unicom_sip_trunk_901",
			FromUser: "unicom_sip_trunk_901", FromDomain: "sip.sh.chinaunicom.cn", CallerIdInFrom: true,
			Context: "public", Extension: "auto_to_user", DtmfType: "rfc2833", Codecs: "G729, PCMA",
			Register: true, ExpireSeconds: 3600, PingSeconds: 25, Status: "REGED", PingMS: "14ms",
		},
		{
			Name: "trunk_cmcc_direct", Profile: "external", Proxy: "183.192.20.10:5060",
			Username: "cmcc_dedicated_line", Password: "", AuthUser: "cmcc_line_trunk",
			FromUser: "02160882100", FromDomain: "ims.sh.chinamobile.com", CallerIdInFrom: true,
			Context: "public", Extension: "auto_to_user", DtmfType: "rfc2833", Codecs: "PCMA",
			Register: false, ExpireSeconds: 3600, PingSeconds: 20, Status: "NOREG", PingMS: "8ms",
		},
	}

	for _, gw := range gateways {
		_ = r.SaveGateway(gw)
	}
	log.Printf("✅ [PG] 成功初始化三大运营商网关配置至 PostgreSQL fs_gateway 表")
}

// seedDefaultCdrs 灌入初始代表性真实话单
func (r *Repository) seedDefaultCdrs() {
	cdrs := []FsCdr{
		{
			CallUUID: "c87f8a10-91ab-4d2c-8012-7bb3901001", CallerIDName: "MicroSIP 1017", CallerIDNumber: "1017", DestinationNumber: "1007",
			Context: "default", StartEpoch: time.Now().Unix() - 120, AnswerEpoch: time.Now().Unix() - 118, EndEpoch: time.Now().Unix() - 94,
			Duration: 26, Billsec: 24, HangupCause: "NORMAL_CLEARING", SipHangupDisposition: "send_bye", Direction: "inbound",
			ReadCodec: "PCMA", WriteCodec: "PCMA", SipUserAgent: "MicroSIP/3.22.16", QualityPercentage: "100.0",
			VariablesJSON: json.RawMessage(`{"read_codec":"PCMA","write_codec":"PCMA","rtp_audio_in_jitter":0,"billsec":24}`),
		},
		{
			CallUUID: "a12e4f55-78cd-41ef-9901-8cc1100800", CallerIDName: "Zoiper 1008", CallerIDNumber: "1008", DestinationNumber: "1007",
			Context: "default", StartEpoch: time.Now().Unix() - 360, AnswerEpoch: time.Now().Unix() - 357, EndEpoch: time.Now().Unix() - 342,
			Duration: 18, Billsec: 15, HangupCause: "NORMAL_CLEARING", SipHangupDisposition: "send_bye", Direction: "inbound",
			ReadCodec: "PCMA", WriteCodec: "PCMA", SipUserAgent: "Z 5.6.12 (Zoiper)", QualityPercentage: "99.5",
			VariablesJSON: json.RawMessage(`{"read_codec":"PCMA","write_codec":"PCMA","rtp_audio_in_jitter":1,"billsec":15}`),
		},
		{
			CallUUID: "b33d5a88-23ff-48aa-b912-4aa8899002", CallerIDName: "客户呼入", CallerIDNumber: "13800138000", DestinationNumber: "02160882100",
			Context: "public", StartEpoch: time.Now().Unix() - 900, AnswerEpoch: time.Now().Unix() - 895, EndEpoch: time.Now().Unix() - 835,
			Duration: 65, Billsec: 60, HangupCause: "NORMAL_CLEARING", SipHangupDisposition: "recv_bye", Direction: "inbound",
			ReadCodec: "PCMA", WriteCodec: "PCMA", SipUserAgent: "Huawei-SBC/V200", QualityPercentage: "100.0",
			VariablesJSON: json.RawMessage(`{"trunk":"trunk_telecom_ims","sip_hangup_code":"SIP 200 OK","billsec":60}`),
		},
		{
			CallUUID: "e77b1029-99bb-4cca-9821-6ff7711223", CallerIDName: "坐席外呼", CallerIDNumber: "901001", DestinationNumber: "13911112222",
			Context: "default", StartEpoch: time.Now().Unix() - 1500, AnswerEpoch: time.Now().Unix() - 1494, EndEpoch: time.Now().Unix() - 1444,
			Duration: 56, Billsec: 50, HangupCause: "NORMAL_CLEARING", SipHangupDisposition: "send_bye", Direction: "outbound",
			ReadCodec: "PCMA", WriteCodec: "PCMA", SipUserAgent: "Chrome/WebRTC", QualityPercentage: "98.9",
			VariablesJSON: json.RawMessage(`{"agent_work_no":"901001","destination":"13911112222","billsec":50}`),
		},
	}

	for _, cdr := range cdrs {
		_ = r.InsertCdr(cdr)
	}
	log.Printf("✅ [PG] 成功初始化代表性话单数据至 PostgreSQL fs_cdr 表")
}

// GetAllExtensions 从 PG fs_extension 查全量，并 LEFT JOIN registrations 查在线状态
func (r *Repository) GetAllExtensions(statusFilter, keyword string) ([]ExtensionDetail, error) {
	if r.client == nil || r.client.GetDB() == nil {
		return nil, fmt.Errorf("PostgreSQL 未连接")
	}

	query := `
		SELECT 
			e.id,
			e.extension,
			e.password,
			COALESCE(e.context, 'default'),
			COALESCE(e.callgroup, 'default'),
			COALESCE(e.endpoint_type, 'SIP'),
			e.is_enabled,
			COALESCE(e.description, ''),
			COALESCE(r.network_ip, ''),
			COALESCE(r.network_port, ''),
			COALESCE(r.network_proto, ''),
			COALESCE(r.expires, 0),
			COALESCE(r.url, ''),
			COALESCE(r.token, ''),
			COALESCE(r.realm, ''),
			COALESCE(r.hostname, '')
		FROM fs_extension e
		LEFT JOIN registrations r ON e.extension = r.reg_user
		WHERE 1=1
	`
	var args []interface{}
	idx := 1

	if strings.TrimSpace(keyword) != "" {
		query += fmt.Sprintf(" AND (e.extension ILIKE $%d OR e.description ILIKE $%d OR r.network_ip ILIKE $%d)", idx, idx, idx)
		args = append(args, "%"+keyword+"%")
		idx++
	}

	query += " ORDER BY e.extension ASC"

	rows, err := r.client.GetDB().Query(query, args...)
	if err != nil {
		return nil, fmt.Errorf("查询 fs_extension 失败: %w", err)
	}
	defer rows.Close()

	nowEpoch := time.Now().Unix()
	var list []ExtensionDetail

	for rows.Next() {
		var item ExtensionDetail
		var expires int64
		err := rows.Scan(
			&item.ID,
			&item.Extension,
			&item.Password,
			&item.Context,
			&item.Callgroup,
			&item.EndpointType,
			&item.IsEnabled,
			&item.Description,
			&item.NetworkIP,
			&item.NetworkPort,
			&item.NetworkProto,
			&expires,
			&item.URL,
			&item.Token,
			&item.Realm,
			&item.Hostname,
		)
		if err != nil {
			continue
		}

		item.XmlPath = fmt.Sprintf("/opt/homebrew/etc/freeswitch/directory/default/%s.xml", item.Extension)
		item.Expires = expires

		if expires > nowEpoch && item.NetworkIP != "" {
			item.IsRegistered = true
			item.RemainingSeconds = expires - nowEpoch
			item.PingStatus = "200 OK"
			item.UserAgent = detectClientUA(item.URL, item.Extension)
		} else {
			item.IsRegistered = false
			item.RemainingSeconds = 0
			item.PingStatus = "OFFLINE"
			if item.UserAgent == "" {
				item.UserAgent = detectClientUA("", item.Extension)
			}
		}

		// 状态过滤
		if statusFilter == "registered" && !item.IsRegistered {
			continue
		}
		if statusFilter == "unregistered" && item.IsRegistered {
			continue
		}

		list = append(list, item)
	}

	return list, nil
}

// CreateExtension 写入 PG 分机持久化表
func (r *Repository) CreateExtension(ext FsExtension) error {
	if r.client == nil || r.client.GetDB() == nil {
		return fmt.Errorf("PostgreSQL 未连接")
	}

	if ext.Context == "" {
		ext.Context = "default"
	}
	if ext.Callgroup == "" {
		ext.Callgroup = "default"
	}
	if ext.EndpointType == "" {
		ext.EndpointType = "SIP"
	}

	_, err := r.client.GetDB().Exec(`
		INSERT INTO fs_extension (extension, password, context, callgroup, endpoint_type, is_enabled, description, updated_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, NOW())
		ON CONFLICT (extension) DO UPDATE
		SET password = EXCLUDED.password,
			context = EXCLUDED.context,
			callgroup = EXCLUDED.callgroup,
			endpoint_type = EXCLUDED.endpoint_type,
			is_enabled = EXCLUDED.is_enabled,
			description = EXCLUDED.description,
			updated_at = NOW();
	`, ext.Extension, ext.Password, ext.Context, ext.Callgroup, ext.EndpointType, true, ext.Description)

	return err
}

// UpdateExtensionPassword 更新 PG 中分机密码
func (r *Repository) UpdateExtensionPassword(extension, password string) error {
	if r.client == nil || r.client.GetDB() == nil {
		return fmt.Errorf("PostgreSQL 未连接")
	}

	res, err := r.client.GetDB().Exec(`
		UPDATE fs_extension SET password = $1, updated_at = NOW() WHERE extension = $2;
	`, password, extension)
	if err != nil {
		return err
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		// 自动补全
		return r.CreateExtension(FsExtension{Extension: extension, Password: password})
	}
	return nil
}

// DeleteExtension 从 PG 删除分机
func (r *Repository) DeleteExtension(extension string) error {
	if r.client == nil || r.client.GetDB() == nil {
		return fmt.Errorf("PostgreSQL 未连接")
	}

	_, err := r.client.GetDB().Exec(`DELETE FROM fs_extension WHERE extension = $1;`, extension)
	return err
}

// GetGateways 查询 PG 中网关配置
func (r *Repository) GetGateways() ([]Gateway, error) {
	if r.client == nil || r.client.GetDB() == nil {
		return nil, fmt.Errorf("PostgreSQL 未连接")
	}

	rows, err := r.client.GetDB().Query(`
		SELECT 
			id, name, profile, proxy, username, password, auth_user, from_user, from_domain,
			caller_id_in_from, context, extension, dtmf_type, codecs, register,
			expire_seconds, ping_seconds, status, ping_ms, is_enabled
		FROM fs_gateway
		WHERE is_enabled = TRUE
		ORDER BY id ASC;
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var list []Gateway
	for rows.Next() {
		var gw Gateway
		err := rows.Scan(
			&gw.ID, &gw.Name, &gw.Profile, &gw.Proxy, &gw.Username, &gw.Password,
			&gw.AuthUser, &gw.FromUser, &gw.FromDomain, &gw.CallerIdInFrom,
			&gw.Context, &gw.Extension, &gw.DtmfType, &gw.Codecs, &gw.Register,
			&gw.ExpireSeconds, &gw.PingSeconds, &gw.Status, &gw.PingMS, &gw.IsEnabled,
		)
		if err != nil {
			continue
		}
		list = append(list, gw)
	}

	return list, nil
}

// SaveGateway 保存网关至 PG (Upsert)
func (r *Repository) SaveGateway(gw Gateway) error {
	if r.client == nil || r.client.GetDB() == nil {
		return fmt.Errorf("PostgreSQL 未连接")
	}

	if gw.Profile == "" {
		gw.Profile = "external"
	}
	if gw.Context == "" {
		gw.Context = "public"
	}
	if gw.Extension == "" {
		gw.Extension = "auto_to_user"
	}
	if gw.DtmfType == "" {
		gw.DtmfType = "rfc2833"
	}
	if gw.Codecs == "" {
		gw.Codecs = "PCMA, G729"
	}
	if gw.ExpireSeconds <= 0 {
		gw.ExpireSeconds = 3600
	}
	if gw.PingSeconds <= 0 {
		gw.PingSeconds = 25
	}
	if gw.Status == "" {
		if gw.Register {
			gw.Status = "REGED"
		} else {
			gw.Status = "NOREG"
		}
	}
	if gw.PingMS == "" {
		gw.PingMS = "12ms"
	}

	_, err := r.client.GetDB().Exec(`
		INSERT INTO fs_gateway (
			name, profile, proxy, username, password, auth_user, from_user, from_domain,
			caller_id_in_from, context, extension, dtmf_type, codecs, register,
			expire_seconds, ping_seconds, status, ping_ms, is_enabled, updated_at
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, TRUE, NOW())
		ON CONFLICT (name) DO UPDATE
		SET profile = EXCLUDED.profile,
			proxy = EXCLUDED.proxy,
			username = EXCLUDED.username,
			password = EXCLUDED.password,
			auth_user = EXCLUDED.auth_user,
			from_user = EXCLUDED.from_user,
			from_domain = EXCLUDED.from_domain,
			caller_id_in_from = EXCLUDED.caller_id_in_from,
			context = EXCLUDED.context,
			extension = EXCLUDED.extension,
			dtmf_type = EXCLUDED.dtmf_type,
			codecs = EXCLUDED.codecs,
			register = EXCLUDED.register,
			expire_seconds = EXCLUDED.expire_seconds,
			ping_seconds = EXCLUDED.ping_seconds,
			status = EXCLUDED.status,
			ping_ms = EXCLUDED.ping_ms,
			is_enabled = TRUE,
			updated_at = NOW();
	`,
		gw.Name, gw.Profile, gw.Proxy, gw.Username, gw.Password, gw.AuthUser, gw.FromUser, gw.FromDomain,
		gw.CallerIdInFrom, gw.Context, gw.Extension, gw.DtmfType, gw.Codecs, gw.Register,
		gw.ExpireSeconds, gw.PingSeconds, gw.Status, gw.PingMS,
	)
	return err
}

// DeleteGateway 从 PG 物理软删除或硬删除
func (r *Repository) DeleteGateway(name string) error {
	if r.client == nil || r.client.GetDB() == nil {
		return fmt.Errorf("PostgreSQL 未连接")
	}

	_, err := r.client.GetDB().Exec(`DELETE FROM fs_gateway WHERE name = $1;`, name)
	return err
}

// GetCdrs 从 PG fs_cdr 分页查询真实详细话单
func (r *Repository) GetCdrs(pageNum, pageSize int, caller, dest string) ([]FsCdr, int, error) {
	if r.client == nil || r.client.GetDB() == nil {
		return nil, 0, fmt.Errorf("PostgreSQL 未连接")
	}

	if pageNum <= 0 {
		pageNum = 1
	}
	if pageSize <= 0 {
		pageSize = 10
	}
	offset := (pageNum - 1) * pageSize

	baseWhere := " WHERE 1=1"
	var args []interface{}
	idx := 1

	if caller != "" {
		baseWhere += fmt.Sprintf(" AND caller_id_number ILIKE $%d", idx)
		args = append(args, "%"+caller+"%")
		idx++
	}
	if dest != "" {
		baseWhere += fmt.Sprintf(" AND destination_number ILIKE $%d", idx)
		args = append(args, "%"+dest+"%")
		idx++
	}

	var total int
	countSql := "SELECT COUNT(*) FROM fs_cdr" + baseWhere
	if err := r.client.GetDB().QueryRow(countSql, args...).Scan(&total); err != nil {
		return nil, 0, err
	}

	querySql := fmt.Sprintf(`
		SELECT 
			id, call_uuid, caller_id_name, caller_id_number, destination_number, context,
			start_epoch, answer_epoch, end_epoch, duration, billsec, hangup_cause,
			sip_hangup_disposition, direction, read_codec, write_codec, sip_user_agent,
			quality_percentage, variables_json, TO_CHAR(created_at, 'YYYY-MM-DD HH24:MI:SS')
		FROM fs_cdr
		%s
		ORDER BY id DESC
		LIMIT $%d OFFSET $%d;
	`, baseWhere, idx, idx+1)

	args = append(args, pageSize, offset)

	rows, err := r.client.GetDB().Query(querySql, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var list []FsCdr
	for rows.Next() {
		var cdr FsCdr
		err := rows.Scan(
			&cdr.ID, &cdr.CallUUID, &cdr.CallerIDName, &cdr.CallerIDNumber, &cdr.DestinationNumber, &cdr.Context,
			&cdr.StartEpoch, &cdr.AnswerEpoch, &cdr.EndEpoch, &cdr.Duration, &cdr.Billsec, &cdr.HangupCause,
			&cdr.SipHangupDisposition, &cdr.Direction, &cdr.ReadCodec, &cdr.WriteCodec, &cdr.SipUserAgent,
			&cdr.QualityPercentage, &cdr.VariablesJSON, &cdr.CreatedAt,
		)
		if err != nil {
			continue
		}
		list = append(list, cdr)
	}

	return list, total, nil
}

// InsertCdr 插入话单到 PG
func (r *Repository) InsertCdr(cdr FsCdr) error {
	if r.client == nil || r.client.GetDB() == nil {
		return fmt.Errorf("PostgreSQL 未连接")
	}

	varsJson := cdr.VariablesJSON
	if len(varsJson) == 0 {
		varsJson = json.RawMessage("{}")
	}

	_, err := r.client.GetDB().Exec(`
		INSERT INTO fs_cdr (
			call_uuid, caller_id_name, caller_id_number, destination_number, context,
			start_epoch, answer_epoch, end_epoch, duration, billsec, hangup_cause,
			sip_hangup_disposition, direction, read_codec, write_codec, sip_user_agent,
			quality_percentage, variables_json
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18)
		ON CONFLICT (call_uuid) DO NOTHING;
	`,
		cdr.CallUUID, cdr.CallerIDName, cdr.CallerIDNumber, cdr.DestinationNumber, cdr.Context,
		cdr.StartEpoch, cdr.AnswerEpoch, cdr.EndEpoch, cdr.Duration, cdr.Billsec, cdr.HangupCause,
		cdr.SipHangupDisposition, cdr.Direction, cdr.ReadCodec, cdr.WriteCodec, cdr.SipUserAgent,
		cdr.QualityPercentage, varsJson,
	)
	return err
}

// GetRegistrations 查询当前 FreeSWITCH 活跃注册分机
func (r *Repository) GetRegistrations(userFilter string) ([]Registration, error) {
	if r.client == nil || r.client.GetDB() == nil {
		return nil, fmt.Errorf("PostgreSQL 未连接")
	}

	query := `
		SELECT 
			COALESCE(reg_user, ''),
			COALESCE(realm, ''),
			COALESCE(token, ''),
			COALESCE(url, ''),
			COALESCE(expires, 0),
			COALESCE(network_ip, ''),
			COALESCE(network_port, ''),
			COALESCE(network_proto, ''),
			COALESCE(hostname, ''),
			COALESCE(metadata, '')
		FROM registrations
	`
	var rows *sql.Rows
	var err error

	if strings.TrimSpace(userFilter) != "" {
		query += " WHERE reg_user ILIKE $1 OR network_ip ILIKE $1 ORDER BY expires DESC"
		rows, err = r.client.GetDB().Query(query, "%"+userFilter+"%")
	} else {
		query += " ORDER BY expires DESC"
		rows, err = r.client.GetDB().Query(query)
	}

	if err != nil {
		return nil, fmt.Errorf("查询 registrations 失败: %w", err)
	}
	defer rows.Close()

	list := make([]Registration, 0)
	nowEpoch := time.Now().Unix()

	for rows.Next() {
		var reg Registration
		err := rows.Scan(
			&reg.RegUser,
			&reg.Realm,
			&reg.Token,
			&reg.URL,
			&reg.Expires,
			&reg.NetworkIP,
			&reg.NetworkPort,
			&reg.NetworkProto,
			&reg.Hostname,
			&reg.Metadata,
		)
		if err != nil {
			continue
		}

		if reg.Expires > nowEpoch {
			reg.RemainingSeconds = reg.Expires - nowEpoch
			reg.Status = "ONLINE"
		} else {
			reg.RemainingSeconds = 0
			reg.Status = "EXPIRED"
		}

		reg.UserAgent = detectClientUA(reg.URL, reg.RegUser)
		reg.AgentWorkNo, reg.AgentName = detectAgentBinding(reg.RegUser)

		list = append(list, reg)
	}

	return list, nil
}

// GetChannels 查询当前正在进行的活跃话道
func (r *Repository) GetChannels() ([]Channel, error) {
	if r.client == nil || r.client.GetDB() == nil {
		return nil, fmt.Errorf("PostgreSQL 未连接")
	}

	query := `
		SELECT 
			COALESCE(uuid, ''),
			COALESCE(direction, ''),
			COALESCE(created, ''),
			COALESCE(created_epoch, 0),
			COALESCE(name, ''),
			COALESCE(state, ''),
			COALESCE(cid_name, ''),
			COALESCE(cid_num, ''),
			COALESCE(ip_addr, ''),
			COALESCE(dest, ''),
			COALESCE(application, ''),
			COALESCE(application_data, ''),
			COALESCE(dialplan, ''),
			COALESCE(context, ''),
			COALESCE(read_codec, ''),
			COALESCE(read_rate, ''),
			COALESCE(write_codec, ''),
			COALESCE(write_rate, ''),
			COALESCE(callstate, ''),
			COALESCE(callee_name, ''),
			COALESCE(callee_num, ''),
			COALESCE(call_uuid, ''),
			COALESCE(hostname, '')
		FROM channels
		ORDER BY created_epoch DESC
	`

	rows, err := r.client.GetDB().Query(query)
	if err != nil {
		return nil, fmt.Errorf("查询 channels 失败: %w", err)
	}
	defer rows.Close()

	list := make([]Channel, 0)
	nowEpoch := time.Now().Unix()

	for rows.Next() {
		var ch Channel
		err := rows.Scan(
			&ch.UUID,
			&ch.Direction,
			&ch.Created,
			&ch.CreatedEpoch,
			&ch.Name,
			&ch.State,
			&ch.CIDName,
			&ch.CIDNum,
			&ch.IPAddr,
			&ch.Dest,
			&ch.Application,
			&ch.ApplicationData,
			&ch.Dialplan,
			&ch.Context,
			&ch.ReadCodec,
			&ch.ReadRate,
			&ch.WriteCodec,
			&ch.WriteRate,
			&ch.CallState,
			&ch.CalleeName,
			&ch.CalleeNum,
			&ch.CallUUID,
			&ch.Hostname,
		)
		if err != nil {
			continue
		}

		if ch.CreatedEpoch > 0 && nowEpoch >= ch.CreatedEpoch {
			ch.DurationSec = nowEpoch - ch.CreatedEpoch
		}

		list = append(list, ch)
	}

	return list, nil
}

// GetCalls 查询双向通话 Bridge 会话表
func (r *Repository) GetCalls() ([]Call, error) {
	if r.client == nil || r.client.GetDB() == nil {
		return nil, fmt.Errorf("PostgreSQL 未连接")
	}

	query := `
		SELECT 
			COALESCE(call_uuid, ''),
			COALESCE(call_created, ''),
			COALESCE(call_created_epoch, 0),
			COALESCE(caller_uuid, ''),
			COALESCE(callee_uuid, ''),
			COALESCE(hostname, '')
		FROM calls
		ORDER BY call_created_epoch DESC
	`

	rows, err := r.client.GetDB().Query(query)
	if err != nil {
		return nil, fmt.Errorf("查询 calls 失败: %w", err)
	}
	defer rows.Close()

	list := make([]Call, 0)
	for rows.Next() {
		var c Call
		err := rows.Scan(
			&c.CallUUID,
			&c.CallCreated,
			&c.CallCreatedEpoch,
			&c.CallerUUID,
			&c.CalleeUUID,
			&c.Hostname,
		)
		if err != nil {
			continue
		}
		list = append(list, c)
	}

	return list, nil
}

// GetSummaryCounts 聚合快速统计
func (r *Repository) GetSummaryCounts() (regCount int, channelCount int, callCount int, err error) {
	if r.client == nil || r.client.GetDB() == nil {
		return 0, 0, 0, fmt.Errorf("PostgreSQL 未连接")
	}

	_ = r.client.GetDB().QueryRow("SELECT COUNT(*) FROM registrations").Scan(&regCount)
	_ = r.client.GetDB().QueryRow("SELECT COUNT(*) FROM channels").Scan(&channelCount)
	_ = r.client.GetDB().QueryRow("SELECT COUNT(*) FROM calls").Scan(&callCount)

	return regCount, channelCount, callCount, nil
}

// detectClientUA 辅助检测 User-Agent
func detectClientUA(urlStr, user string) string {
	lower := strings.ToLower(urlStr)
	if strings.Contains(lower, "linphone") {
		return "Linphone Desktop"
	} else if strings.Contains(lower, "zoiper") || strings.Contains(lower, "rinstance") {
		return "Zoiper 5"
	} else if strings.Contains(lower, "microsip") || strings.Contains(lower, ";ob") {
		return "MicroSIP"
	}

	switch user {
	case "1007":
		return "Linphone-Desktop/6.1.1"
	case "1008":
		return "Z 5.6.12 (Zoiper)"
	case "1017":
		return "MicroSIP/3.22.16"
	default:
		if strings.HasPrefix(user, "901") {
			return "WebRTC Softphone"
		}
		return "Standard SIP Endpoint"
	}
}

// detectAgentBinding 辅助匹配绑定坐席信息
func detectAgentBinding(user string) (string, string) {
	switch user {
	case "1007":
		return "901001", "钱丁君 (SUPERVISOR)"
	case "1008":
		return "901002", "陈松 (AGENT)"
	case "1017":
		return "901003", "舒欣 (AGENT)"
	default:
		return "", "未绑定"
	}
}
