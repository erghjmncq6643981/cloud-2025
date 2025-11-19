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
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 笔记表
 * note
 */
@Data
@Table("note")
public class Note implements Serializable {
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
     * 数据
     */
    private Object data;

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
    private String lastUpdateBy;

    /**
     * 逻辑删除
     */
    @Column(isLogicDelete = true)
    private Boolean logicDelete;

    private static final long serialVersionUID = 1L;
}