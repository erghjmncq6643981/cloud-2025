package com.chandler.freeswitch.client.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.freeswitch.client.example.domain.dataobject.CallFileRecord;
import com.chandler.freeswitch.client.example.domain.mapper.CallFileRecordMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * 录音文件记录服务
 */
@Service
public class CallFileRecordService extends ServiceImpl<CallFileRecordMapper, CallFileRecord> {

    public CallFileRecord createRecord(String uuid, String fileName, String filePath) {
        CallFileRecord record = CallFileRecord.builder()
                .uuid(uuid)
                .fileName(fileName)
                .filePath(filePath)
                .fileSize(0L)
                .build();
        save(record);
        return record;
    }

    public void updateActualFileSize(Long recordId, String filePath) {
        if (recordId == null || filePath == null) {
            return;
        }
        File file = new File(filePath);
        if (file.exists() && file.isFile()) {
            CallFileRecord record = getById(recordId);
            if (record != null) {
                record.setFileSize(file.length());
                updateById(record);
            }
        }
    }

    public List<CallFileRecord> listByUuid(String uuid) {
        return list(new LambdaQueryWrapper<CallFileRecord>().eq(CallFileRecord::getUuid, uuid));
    }
}
