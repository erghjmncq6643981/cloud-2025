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
import org.chandler25.ai.demo.common.UserStatus;
import org.chandler25.ai.demo.common.UserType;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 用户表
 * user
 */
@Data
@Table("user")
public class User implements Serializable {
    /**
     * 主键KEY
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 登陆名
     */
    private String loginName;

    /**
     * 密码
     */
    private String loginPassword;

    /**
     * 手机号码
     */
    @Column(onInsertValue = "''")
    private String phoneNumber;

    /**
     * 用户类型
     */
    private UserType userType;

    /**
     * 用户状态，ENABLED、DISABLED
     */
    @Column(onInsertValue = "'ENABLED'")
    private UserStatus userStatus;

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