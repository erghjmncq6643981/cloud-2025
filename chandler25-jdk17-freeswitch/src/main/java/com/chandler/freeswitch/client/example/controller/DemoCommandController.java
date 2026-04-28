package com.chandler.freeswitch.client.example.controller;

import com.chandler.freeswitch.client.example.command.FreeSwitchCommandGateway;
import com.chandler.freeswitch.client.example.command.FreeSwitchCommandResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "FreeSWITCH Command Verification")
@RestController
@RequestMapping("/api/commands")
public class DemoCommandController {

    private final FreeSwitchCommandGateway commandGateway;

    public DemoCommandController(FreeSwitchCommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @Operation(summary = "Originate a user channel")
    @PostMapping("/originate/user")
    public FreeSwitchCommandResult originateUser(
            @RequestParam String userId,
            @RequestParam(required = false) String uuid,
            @RequestParam(defaultValue = "park") String application) {
        String channelUuid = StringUtils.defaultIfBlank(uuid, UUID.randomUUID().toString());
        return commandGateway.originateUser(userId, channelUuid, Map.of("absolute_codec_string", "PCMU"), application);
    }

    @Operation(summary = "Answer a channel")
    @PostMapping("/{uuid}/answer")
    public FreeSwitchCommandResult answer(@PathVariable String uuid) {
        return commandGateway.answer(uuid);
    }

    @Operation(summary = "Pre-answer a channel")
    @PostMapping("/{uuid}/pre-answer")
    public FreeSwitchCommandResult preAnswer(@PathVariable String uuid) {
        return commandGateway.preAnswer(uuid);
    }

    @Operation(summary = "Hang up a channel")
    @PostMapping("/{uuid}/hangup")
    public FreeSwitchCommandResult hangup(
            @PathVariable String uuid,
            @RequestParam(defaultValue = FreeSwitchCommandGateway.DEFAULT_HANGUP_CAUSE) String cause) {
        return commandGateway.kill(uuid, cause);
    }

    @Operation(summary = "Bridge two channels")
    @PostMapping("/bridge")
    public FreeSwitchCommandResult bridge(@RequestParam String uuidA, @RequestParam String uuidB) {
        return commandGateway.bridge(uuidA, uuidB);
    }

    @Operation(summary = "Transfer a channel to park inline")
    @PostMapping("/{uuid}/park")
    public FreeSwitchCommandResult park(@PathVariable String uuid) {
        return commandGateway.park(uuid);
    }

    @Operation(summary = "Hold a channel")
    @PostMapping("/{uuid}/hold")
    public FreeSwitchCommandResult hold(@PathVariable String uuid) {
        return commandGateway.hold(uuid);
    }

    @Operation(summary = "Unhold a channel")
    @PostMapping("/{uuid}/unhold")
    public FreeSwitchCommandResult unhold(@PathVariable String uuid) {
        return commandGateway.unhold(uuid);
    }

    @Operation(summary = "Transfer a channel")
    @PostMapping("/{uuid}/transfer")
    public FreeSwitchCommandResult transfer(
            @PathVariable String uuid,
            @RequestParam String destination,
            @RequestParam(defaultValue = FreeSwitchCommandGateway.DEFAULT_DIALPLAN) String dialplan,
            @RequestParam(defaultValue = FreeSwitchCommandGateway.DEFAULT_CONTEXT) String context,
            @RequestParam(defaultValue = "aleg") String leg) {
        if ("both".equalsIgnoreCase(leg)) {
            return commandGateway.transferBoth(uuid, destination, dialplan, context);
        }
        if ("bleg".equalsIgnoreCase(leg)) {
            return commandGateway.transferBleg(uuid, destination, dialplan, context);
        }
        return commandGateway.transfer(uuid, destination, dialplan, context);
    }

    @Operation(summary = "Transfer a channel with inline application")
    @PostMapping("/{uuid}/transfer/inline")
    public FreeSwitchCommandResult transferInline(@PathVariable String uuid, @RequestParam String application) {
        return commandGateway.transferInline(uuid, application);
    }

    @Operation(summary = "Blind transfer a channel to an extension")
    @PostMapping("/{uuid}/transfer/blind")
    public FreeSwitchCommandResult blindTransfer(@PathVariable String uuid, @RequestParam String extension) {
        return commandGateway.blindTransferToExtension(uuid, extension);
    }

    @Operation(summary = "Play media on a channel")
    @PostMapping("/{uuid}/play")
    public FreeSwitchCommandResult play(
            @PathVariable String uuid,
            @RequestParam String mediaPath,
            @RequestParam(defaultValue = "aleg") String leg) {
        return commandGateway.play(uuid, mediaPath, leg);
    }

    @Operation(summary = "Stop current playback on a channel")
    @PostMapping("/{uuid}/play/stop")
    public FreeSwitchCommandResult stopPlayback(@PathVariable String uuid) {
        return commandGateway.stopPlayback(uuid);
    }

    @Operation(summary = "Run play_and_get_digits and return to park")
    @PostMapping("/{uuid}/ivr/play-and-get-digits")
    public FreeSwitchCommandResult playAndGetDigits(
            @PathVariable String uuid,
            @RequestParam String audioFile,
            @RequestParam(defaultValue = "1") int minDigits,
            @RequestParam(defaultValue = "1") int maxDigits,
            @RequestParam(defaultValue = "3") int tries,
            @RequestParam(defaultValue = "5000") int timeoutMs,
            @RequestParam(defaultValue = "#") String terminators,
            @RequestParam(defaultValue = "silence_stream://250") String invalidAudioFile,
            @RequestParam(defaultValue = "dtmf_var") String variableName,
            @RequestParam(defaultValue = "\\d+") String regex) {
        return commandGateway.playAndGetDigitsThenPark(uuid, minDigits, maxDigits, tries, timeoutMs,
                terminators, audioFile, invalidAudioFile, variableName, regex);
    }

    @Operation(summary = "Start channel recording")
    @PostMapping("/{uuid}/record/start")
    public FreeSwitchCommandResult recordStart(@PathVariable String uuid, @RequestParam String filePath) {
        return commandGateway.recordStart(uuid, filePath);
    }

    @Operation(summary = "Stop channel recording")
    @PostMapping("/{uuid}/record/stop")
    public FreeSwitchCommandResult recordStop(@PathVariable String uuid, @RequestParam String filePath) {
        return commandGateway.recordStop(uuid, filePath);
    }

    @Operation(summary = "Mute channel audio direction")
    @PostMapping("/{uuid}/audio/mute")
    public FreeSwitchCommandResult mute(
            @PathVariable String uuid,
            @RequestParam(defaultValue = "read") String direction) {
        return commandGateway.mute(uuid, direction);
    }

    @Operation(summary = "Unmute channel audio direction")
    @PostMapping("/{uuid}/audio/unmute")
    public FreeSwitchCommandResult unmute(
            @PathVariable String uuid,
            @RequestParam(defaultValue = "read") String direction) {
        return commandGateway.unmute(uuid, direction);
    }

    @Operation(summary = "Send DTMF digits on a channel")
    @PostMapping("/{uuid}/dtmf")
    public FreeSwitchCommandResult sendDtmf(@PathVariable String uuid, @RequestParam String digits) {
        return commandGateway.sendDtmf(uuid, digits);
    }

    @Operation(summary = "Move a channel into a conference")
    @PostMapping("/{uuid}/conference")
    public FreeSwitchCommandResult conference(@PathVariable String uuid, @RequestParam String conferenceName) {
        return commandGateway.joinConference(uuid, conferenceName);
    }

    @Operation(summary = "Deflect a channel")
    @PostMapping("/{uuid}/deflect")
    public FreeSwitchCommandResult deflect(@PathVariable String uuid, @RequestParam String sipUri) {
        return commandGateway.deflect(uuid, sipUri);
    }

    @Operation(summary = "Schedule channel hangup")
    @PostMapping("/{uuid}/sched-hangup")
    public FreeSwitchCommandResult schedHangup(
            @PathVariable String uuid,
            @RequestParam int seconds,
            @RequestParam(defaultValue = FreeSwitchCommandGateway.DEFAULT_HANGUP_CAUSE) String cause) {
        return commandGateway.schedHangup(uuid, seconds, cause);
    }

    @Operation(summary = "Set a channel variable")
    @PostMapping("/{uuid}/vars/{name}")
    public FreeSwitchCommandResult setVar(
            @PathVariable String uuid,
            @PathVariable String name,
            @RequestParam String value) {
        return commandGateway.setVar(uuid, name, value);
    }

    @Operation(summary = "Get a channel variable")
    @GetMapping("/{uuid}/vars/{name}")
    public FreeSwitchCommandResult getVar(@PathVariable String uuid, @PathVariable String name) {
        return commandGateway.getVar(uuid, name);
    }

    @Operation(summary = "Check whether a channel exists")
    @GetMapping("/{uuid}/exists")
    public FreeSwitchCommandResult exists(@PathVariable String uuid) {
        return commandGateway.exists(uuid);
    }

    @Operation(summary = "Dump channel state")
    @GetMapping("/{uuid}/dump")
    public FreeSwitchCommandResult dump(@PathVariable String uuid) {
        return commandGateway.dump(uuid);
    }

    @Operation(summary = "Show active channels")
    @GetMapping("/channels")
    public FreeSwitchCommandResult showChannels() {
        return commandGateway.showChannels();
    }

    @Operation(summary = "Show active calls")
    @GetMapping("/calls")
    public FreeSwitchCommandResult showCalls() {
        return commandGateway.showCalls();
    }

    @Operation(summary = "Show FreeSWITCH status")
    @GetMapping("/status")
    public FreeSwitchCommandResult status() {
        return commandGateway.status();
    }
}
