package org.chandler25.ai.demo.util;

import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/13 13:58
 * @version 1.0.0
 * @since 21
 */
@Slf4j
public class ExcludeURLRole {
    public List<Rol> rols;

    public boolean findMatch(String uri) {
        if (null != uri && null != this.rols && !this.rols.isEmpty()) {
            for(Rol rol : this.rols) {
                if (rol.match(uri)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public ExcludeURLRole(String paths) {
        log.info("需要排除的输入路径为：{}", paths);
        if (!StringUtils.isEmpty(paths)) {
            String[] excludeStr = paths.trim().split(",");
            if (null != excludeStr && excludeStr.length != 0) {
                List<Rol> list = new ArrayList(excludeStr.length);

                for(String str : excludeStr) {
                    list.add(new Rol(str));
                }

                this.rols = list;
            } else {
                log.warn("没有发现需要处理的排除路径");
            }
        }
    }

    public ExcludeURLRole(List<String> paths) {
        if (null != paths && !paths.isEmpty()) {
            List<Rol> list = new ArrayList(paths.size());

            for(String str : paths) {
                list.add(new Rol(str));
            }

            this.rols = list;
        } else {
            log.info("没有发现需要处理的排除路径");
        }
    }

    public List<Rol> getRols() {
        return this.rols;
    }

    public void setRols(final List<Rol> rols) {
        this.rols = rols;
    }

    public static enum MatchRole {
        START_WITH,
        END_WITH,
        CONTAIN,
        EXACT;

        private MatchRole() {
        }
    }

    class Rol {
        MatchRole matchRole;
        String path;

        public Rol(String roleStr) {
            if (!StringUtils.isEmpty(roleStr)) {
                if (roleStr.startsWith("*") && roleStr.endsWith("*")) {
                    this.path = roleStr.substring(1, roleStr.length() - 1);
                    this.matchRole = ExcludeURLRole.MatchRole.CONTAIN;
                } else if (roleStr.startsWith("*")) {
                    this.path = roleStr.substring(1);
                    this.matchRole = ExcludeURLRole.MatchRole.END_WITH;
                } else if (roleStr.endsWith("*")) {
                    this.path = roleStr.substring(0, roleStr.length() - 1);
                    this.matchRole = ExcludeURLRole.MatchRole.START_WITH;
                } else {
                    this.path = roleStr;
                    this.matchRole = ExcludeURLRole.MatchRole.EXACT;
                }
            }
        }

        public boolean match(String uri) {
            if (null == uri) {
                return false;
            } else {
                switch (this.matchRole) {
                    case EXACT:
                        return this.path.equals(uri);
                    case CONTAIN:
                        return uri.contains(this.path);
                    case START_WITH:
                        return uri.startsWith(this.path);
                    case END_WITH:
                        return uri.endsWith(this.path);
                    default:
                        return false;
                }
            }
        }
    }
}