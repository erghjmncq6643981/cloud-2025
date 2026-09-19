package com.chandler.fcc.fcc.client;

import com.chandler.fcc.fcc.config.FccProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 软交换管理面 HTTP 同步客户端 (与 Sidecar Agent 管理端交互)
 * 职责：分机开户、分机销户、分机状态查询、节点状态探活等同步强一致性管理操作
 */
@Slf4j
@Component
public class SidecarAdminClient {

    private final FccProperties properties;
    private final RestTemplate restTemplate;

    public SidecarAdminClient(FccProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtensionCreateReq {
        private String extension;
        private String password;
        private String context;
        private String callgroup;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SidecarResponse {
        private int code;
        private String message;
        private String extension;
        private Map<String, Object> data;
        private String error;

        public boolean isSuccess() {
            return code == 200;
        }
    }

    /**
     * 同步创建/注册分机 (写 XML + reloadxml)
     */
    public SidecarResponse createExtension(String extension, String password, String context, String callgroup) {
        String url = properties.getSidecarHttpUrl() + "/api/v1/extensions";
        ExtensionCreateReq req = ExtensionCreateReq.builder()
                .extension(extension)
                .password(password)
                .context(context != null ? context : "default")
                .callgroup(callgroup != null ? callgroup : "default")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ExtensionCreateReq> entity = new HttpEntity<>(req, headers);

        log.info("🌐 [Sidecar HTTP] 准备创建分机: ext={}, context={}", extension, req.getContext());
        ResponseEntity<SidecarResponse> resp = restTemplate.postForEntity(url, entity, SidecarResponse.class);
        log.info("🌐 [Sidecar HTTP] 创建分机响应: {}", resp.getBody());
        return resp.getBody();
    }

    /**
     * 同步注销/删除分机 (删 XML + reloadxml)
     */
    public SidecarResponse deleteExtension(String extension) {
        String url = properties.getSidecarHttpUrl() + "/api/v1/extensions?extension=" + extension;
        log.info("🌐 [Sidecar HTTP] 准备删除分机: ext={}", extension);
        ResponseEntity<SidecarResponse> resp = restTemplate.exchange(url, HttpMethod.DELETE, null, SidecarResponse.class);
        log.info("🌐 [Sidecar HTTP] 删除分机响应: {}", resp.getBody());
        return resp.getBody();
    }

    /**
     * 检查分机是否存在及注册情况
     */
    public SidecarResponse checkExtension(String extension) {
        String url = properties.getSidecarHttpUrl() + "/api/v1/extensions?extension=" + extension;
        log.info("🌐 [Sidecar HTTP] 检查分机状态: ext={}", extension);
        ResponseEntity<SidecarResponse> resp = restTemplate.getForEntity(url, SidecarResponse.class);
        log.info("🌐 [Sidecar HTTP] 检查分机响应: {}", resp.getBody());
        return resp.getBody();
    }

    /**
     * Sidecar 节点健康检查
     */
    public Map<String, Object> healthCheck() {
        String url = properties.getSidecarHttpUrl() + "/api/v1/health";
        log.info("🌐 [Sidecar HTTP] 探活请求: {}", url);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
        log.info("🌐 [Sidecar HTTP] 探活响应: {}", resp);
        return resp;
    }
}
