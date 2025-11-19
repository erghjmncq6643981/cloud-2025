package org.chandler25.ai.demo.respository.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import org.chandler25.ai.demo.common.AiFieldType;
import org.chandler25.ai.demo.common.TemplateStatus;
import org.chandler25.ai.demo.common.UserStatus;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * AI请求模版表
 * ai_template
 */
@Data
@Table("ai_template")
public class AiTemplate implements Serializable {
    /**
     * 主键KEY
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 领域类型，COMMON、PROGRAMME、ENGLISH
     */
    private AiFieldType fieldType;

    /**
     * AI请求模版
     */
    private String template;

    /**
     * 用户状态，ENABLED、DISABLED
     */
    @Column(onInsertValue = "'ENABLED'")
    private TemplateStatus status;

    /**
     * 创建时间
     */
    @Column(onInsertValue = "now()")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 创建人
     */
    @Column(onInsertValue = "''")
    private String createBy;

    /**
     * 修改时间
     */
    @Column(onInsertValue = "now()",onUpdateValue = "now()")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdateTime;

    /**
     * 修改人
     */
    @Column(onInsertValue = "''")
    private String lastUpdateBy;

    /**
     * 逻辑删除
     */
    @Column(isLogicDelete = true)
    private Boolean logicDelete;

    private static final long serialVersionUID = 1L;
}