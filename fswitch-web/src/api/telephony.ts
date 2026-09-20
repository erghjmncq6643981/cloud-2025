import axios from 'axios'

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

export interface SystemStatus {
  fs_alive: boolean
  pg_connected: boolean
  uptime: string
  version: string
  total_sessions: number
  active_sessions: number
  max_sessions: number
  cps: number
  registrations: number
  channels: number
  calls: number
  node_id: string
  node_state: string
}

export interface Registration {
  reg_user: string
  realm: string
  token: string
  url: string
  expires: number
  network_ip: string
  network_port: string
  network_proto: string
  hostname: string
  metadata: string
  user_agent?: string
  status?: string
  remaining_seconds: number
  agent_work_no?: string
  agent_name?: string
}

export interface Channel {
  uuid: string
  direction: string
  created: string
  created_epoch: number
  name: string
  state: string
  cid_name: string
  cid_num: string
  ip_addr: string
  dest: string
  application: string
  application_data: string
  dialplan: string
  context: string
  read_codec: string
  read_rate: string
  write_codec: string
  write_rate: string
  callstate: string
  callee_name: string
  callee_num: string
  call_uuid: string
  hostname: string
  duration_sec: number
}

export interface Call {
  call_uuid: string
  call_created: string
  call_created_epoch: number
  caller_uuid: string
  callee_uuid: string
  hostname: string
}

export interface SofiaProfile {
  name: string
  state: string
  sip_port: number
  ws_port?: number
  wss_port?: number
  bind_ip: string
  codecs: string
  dialplan: string
  context: string
  raw_lines?: string[]
}

export interface Gateway {
  name: string
  profile: string
  proxy: string
  username?: string
  password?: string
  auth_user: string
  from_user?: string
  from_domain?: string
  caller_id_in_from?: boolean
  context?: string
  extension?: string
  dtmf_type?: string
  codecs?: string
  register?: boolean
  expire_seconds?: number
  ping_seconds?: number
  status?: string
  ping_ms?: string
}

export interface ExtensionItem {
  extension: string
  password?: string
  context: string
  callgroup: string
  xml_path?: string
  is_registered: boolean
  network_ip: string
  network_port: string
  network_proto: string
  user_agent: string
  remaining_seconds: number
  ping_status: string
  expires?: number
  url?: string
  token?: string
  realm?: string
  hostname?: string
}

export interface FsCdr {
  id: number
  call_uuid: string
  caller_id_name: string
  caller_id_number: string
  destination_number: string
  context: string
  start_epoch: number
  answer_epoch: number
  end_epoch: number
  duration: number
  billsec: number
  hangup_cause: string
  sip_hangup_disposition: string
  direction: string
  read_codec: string
  write_codec: string
  sip_user_agent: string
  quality_percentage: string
  variables_json: any
  created_at: string
}

export const telephonyApi = {
  async getStatus(): Promise<SystemStatus> {
    const res = await client.get('/telephony/status')
    const raw = res.data?.data || {}
    const isEslAlive = raw.esl_connected ?? raw.fs_alive ?? false
    return {
      fs_alive: isEslAlive,
      pg_connected: raw.pg_connected === true,
      uptime: raw.uptime || '',
      version: raw.version || raw.free_switch_version || '',
      total_sessions: Number(raw.total_sessions) || 0,
      active_sessions: raw.active_calls ?? 0,
      max_sessions: Number(raw.max_channels) || 0,
      cps: Number(raw.current_cps) || 0,
      registrations: raw.registrations ?? 0,
      channels: raw.active_channels ?? raw.channels ?? 0,
      calls: raw.active_calls ?? raw.calls ?? 0,
      node_id: raw.node_id || '',
      node_state: raw.node_state || ''
    }
  },

  async getRegistrations(user?: string): Promise<Registration[]> {
    const res = await client.get('/telephony/registrations', { params: { user } })
    return res.data.data || []
  },

  async getAllExtensions(params?: { status?: string; keyword?: string }): Promise<ExtensionItem[]> {
    const res = await client.get('/telephony/extensions', { params })
    return res.data.data || []
  },

  async updateExtensionPassword(data: { extension: string; password: string; context?: string; callgroup?: string }): Promise<any> {
    const res = await client.post('/telephony/extensions/password', data)
    return res.data
  },

  async flushRegistration(extension: string, realm?: string): Promise<any> {
    const res = await client.post('/telephony/registrations/flush', { extension, realm })
    return res.data
  },

  async getChannels(): Promise<{ channels: Channel[]; calls: Call[] }> {
    const res = await client.get('/telephony/channels')
    return {
      channels: res.data.channels || [],
      calls: res.data.calls || []
    }
  },

  async killChannel(uuid: string, cause: string = 'NORMAL_CLEARING'): Promise<any> {
    const res = await client.post('/telephony/channels/kill', { uuid, cause })
    return res.data
  },

  async transferChannel(uuid: string, destination: string, context: string = 'default'): Promise<any> {
    const res = await client.post('/telephony/channels/transfer', { uuid, destination, context })
    return res.data
  },

  async getProfiles(): Promise<SofiaProfile[]> {
    const res = await client.get('/telephony/sofia/profiles')
    return res.data.data || []
  },

  async getGateways(): Promise<Gateway[]> {
    const res = await client.get('/telephony/gateways')
    return res.data.data || []
  },

  async saveGateway(gw: Gateway): Promise<any> {
    const res = await client.post('/telephony/gateways', gw)
    return res.data
  },

  async deleteGateway(name: string): Promise<any> {
    const res = await client.delete('/telephony/gateways', { params: { name } })
    return res.data
  },

  async pingGateway(name: string): Promise<any> {
    const res = await client.post('/telephony/gateways/ping', { name })
    return res.data
  },

  async getCdrs(params?: { pageNum?: number; pageSize?: number; caller?: string; destination?: string }): Promise<{ data: FsCdr[]; total: number; pageNum: number; pageSize: number }> {
    const res = await client.get('/telephony/cdr', { params })
    return res.data
  },

  async executeCli(command: string): Promise<string> {
    const res = await client.post('/telephony/cli/exec', { command })
    return res.data.output || ''
  },

  async dialEcho(extension: string): Promise<string> {
    assertDialTarget(extension)
    return this.executeCli(`originate user/${extension} &echo`)
  },

  async bridgeExtensions(caller: string, callee: string): Promise<string> {
    assertDialTarget(caller)
    assertDialTarget(callee)
    if (caller === callee) throw new Error('主叫与被叫不能相同')
    return this.executeCli(`originate user/${callee} &bridge(user/${caller})`)
  },

  async createExtension(data: { extension: string; password: string; context?: string; callgroup?: string; endpoint_type?: string; description?: string }): Promise<any> {
    const res = await client.post('/telephony/extensions', data)
    return res.data
  },

  async reloadXml(): Promise<string> {
    return this.executeCli('reloadxml')
  },

  async getVars(): Promise<VarsConfig> {
    const res = await client.get('/telephony/vars')
    return res.data.data
  },

  async updateVars(vars: Record<string, string>): Promise<any> {
    const res = await client.post('/telephony/vars', { vars })
    return res.data
  }
}

export interface VarsConfig {
  file_path: string
  vars: {
    local_ip_v4: string
    domain: string
    default_password: string
    external_sip_ip: string
    external_rtp_ip: string
    sound_prefix: string
    hold_music: string
    rtp_sdes_suites?: string
    [key: string]: string | undefined
  }
  available_ips: string[]
  raw_content: string
}

function assertDialTarget(value: string): void {
  if (!/^[0-9A-Za-z_.-]{1,32}$/.test(value)) {
    throw new Error('分机标识格式不合法')
  }
}
