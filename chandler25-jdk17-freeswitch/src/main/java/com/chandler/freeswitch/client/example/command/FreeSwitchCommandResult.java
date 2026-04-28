package com.chandler.freeswitch.client.example.command;

public record FreeSwitchCommandResult(
        String fsAddr,
        String mode,
        String command,
        String argument,
        String response,
        String channelUuid
) {
}
