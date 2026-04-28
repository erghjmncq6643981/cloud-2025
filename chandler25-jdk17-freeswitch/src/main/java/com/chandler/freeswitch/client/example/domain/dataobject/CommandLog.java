package com.chandler.freeswitch.client.example.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * ESL控制指令日志表
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("command_log")
public class CommandLog extends CallCenterBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的UUID */
    private String uuid;

    /** 指令名称: uuid_broadcast, originate等 */
    private String commandName;

    /** 指令完整参数 */
    private String commandArgs;

    /** FreeSWITCH 的响应内容 */
    private String responseText;

    /** 执行状态: 1-成功, 0-失败 */
    private Integer status;
}
