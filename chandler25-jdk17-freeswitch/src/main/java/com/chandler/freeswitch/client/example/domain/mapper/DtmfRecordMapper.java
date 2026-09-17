package com.chandler.freeswitch.client.example.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.freeswitch.client.example.domain.dataobject.DtmfRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * DTMF记录 Mapper
 */
@Mapper
public interface DtmfRecordMapper extends BaseMapper<DtmfRecord> {
}
