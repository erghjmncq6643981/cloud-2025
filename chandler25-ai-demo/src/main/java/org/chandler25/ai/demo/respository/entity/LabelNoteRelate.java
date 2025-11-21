package org.chandler25.ai.demo.respository.entity;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 标签笔记关联表
 * label_note_relate
 */
@Data
@Table("label_note_relate")
public class LabelNoteRelate implements Serializable {
    /**
     * 主键KEY
     */
    private Long id;

    /**
     * 标签ID
     */
    private Long labelId;

    /**
     * 笔记ID
     */
    private Long noteId;

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
    private Byte logicDelete;

    private static final long serialVersionUID = 1L;
}