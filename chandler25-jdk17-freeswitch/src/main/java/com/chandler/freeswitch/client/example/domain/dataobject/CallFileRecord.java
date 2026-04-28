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
 * 录音文件记录表
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("call_file_record")
public class CallFileRecord extends CallCenterBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对应的通话UUID */
    private String uuid;

    /** 文件名称 */
    private String fileName;

    /** 文件存储绝对路径/URL */
    private String filePath;

    /** 文件大小(bytes) */
    private Long fileSize;
}
