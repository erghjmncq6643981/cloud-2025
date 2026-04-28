package com.chandler.freeswitch.client.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.freeswitch.client.example.command.FreeSwitchCommandResult;
import com.chandler.freeswitch.client.example.domain.dataobject.CommandLog;
import com.chandler.freeswitch.client.example.domain.mapper.CommandLogMapper;
import org.springframework.stereotype.Service;

/**
 * ESL 控制指令日志服务
 */
@Service
public class CommandLogService extends ServiceImpl<CommandLogMapper, CommandLog> {

    public static final String DTMF_AUDIO_FILE_COMMAND = "DTMF_AUDIO_FILE";
    public static final String DTMF_DIGIT_COMMAND = "DTMF_DIGIT";

    public void saveCommandResult(FreeSwitchCommandResult result) {
        if (result == null) {
            return;
        }
        saveBusinessLog(
                result.channelUuid(),
                result.command(),
                result.argument(),
                result.response(),
                isSuccessResponse(result.response()) ? 1 : 0
        );
    }

    public void saveBusinessLog(String uuid, String commandName, String commandArgs, String responseText, Integer status) {
        save(CommandLog.builder()
                .uuid(uuid)
                .commandName(commandName)
                .commandArgs(commandArgs)
                .responseText(responseText)
                .status(status)
                .build());
    }

    public CommandLog getLatestByUuidAndCommandName(String uuid, String commandName) {
        return getOne(
                new LambdaQueryWrapper<CommandLog>()
                        .eq(CommandLog::getUuid, uuid)
                        .eq(CommandLog::getCommandName, commandName)
                        .orderByDesc(CommandLog::getId)
                        .last("limit 1")
        );
    }

    private boolean isSuccessResponse(String response) {
        return response == null || !response.startsWith("-ERR");
    }
}
