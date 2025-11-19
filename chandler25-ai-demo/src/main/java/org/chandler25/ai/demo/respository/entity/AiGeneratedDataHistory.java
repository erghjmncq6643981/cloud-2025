package org.chandler25.ai.demo.respository.entity;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import lombok.Data;
import org.chandler25.ai.demo.common.AiModelType;
import org.chandler25.ai.demo.common.AiQuestionStatus;
import org.chandler25.ai.demo.domain.bo.UserQuestion;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * AI生成数据历史表
 * ai_generated_data_history
 */
@Data
@Table("ai_generated_data_history")
public class AiGeneratedDataHistory implements Serializable {
    /**
     * 主键KEY
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 问题ID
     */
    private String questionId;

    /**
     * 模型类型，deepseek
     */
    private AiModelType modelType;

    /**
     * 问题
     */
    @Column(value = "data", typeHandler = JacksonTypeHandler.class)
    private UserQuestion data;

    /**
     * 问题状态
     */
    private AiQuestionStatus questionStatus;

    /**
     * 创建时间
     */
    @Column(onInsertValue = "now()")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

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
    private Date lastUpdateTime;

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