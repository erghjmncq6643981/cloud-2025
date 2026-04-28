package com.chandler.freeswitch.client.example.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.freeswitch.client.example.domain.dataobject.CallFileRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 录音文件记录 Mapper
 */
@Mapper
public interface CallFileRecordMapper extends BaseMapper<CallFileRecord> {
}
