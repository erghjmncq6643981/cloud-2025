/*
 * chandler25-freeswitch
 * 2025/8/24 11:34
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package org.chandler25.freeswitch.esl;

import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.freeswitch.esl.client.transport.message.EslHeaders;

import javax.naming.Context;
import java.util.Map;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/8/24 11:34
 * @version 1.0.0
 * @since 1.8
 */
public class InboundEventListener implements IEslEventListener {
    public void eventReceived(EslEvent event){
        String eventName = event.getEventName();
        String calleeNumber = event.getEventHeaders().get("Caller-Callee-ID-Number");
        String callerNumber = event.getEventHeaders().get("Caller-Caller-ID-Number");
        switch (eventName) {
            case "HEARTBEAT"://心跳事件
                break;
            case "CHANNEL_CREATE"://创建事件，发起呼叫
                System.out.println("CHANNEL_CREATE——发起呼叫, 主叫：" + callerNumber + " , 被叫：" + calleeNumber);
                break;
            case "CHANNEL_BRIDGE"://用户转接，一个呼叫两个端点之间的桥接事件
                System.out.println("CHANNEL_BRIDGE——用户转接, 主叫：" + callerNumber + " , 被叫：" + calleeNumber);
                break;
            case "CHANNEL_ANSWER"://呼叫应答事件。
                System.out.println("CHANNEL_ANSWER——用户应答, 主叫：" + callerNumber + " , 被叫：" + calleeNumber);
                break;
            case "CHANNEL_HANGUP": {//挂机事件
                String response = event.getEventHeaders().get("variable_current_application_response");
                String hangupCause = event.getEventHeaders().get("Hangup-Cause");
                System.out.println("CHANNEL_HANGUP——用户挂断, 主叫：" + callerNumber + " , 被叫：" + calleeNumber + " , response:" + response + " ,hangup cause:" + hangupCause);
                break;
            }
            case "CHANNEL_HANGUP_COMPLETE": {//挂机完成事件
                String response = event.getEventHeaders().get("variable_current_application_response");
                String hangupCause = event.getEventHeaders().get("Hangup-Cause");
                System.out.println("CHANNEL_HANGUP_COMPLETE——用户挂断, 主叫：" + callerNumber + " , 被叫：" + calleeNumber + " , response:" + response + " ,hangup cause:" + hangupCause);
                break;
            }
            case "PLAYBACK_START"://背景音乐播放
                System.out.println("--音乐播放-开始！--");
                break;
            case "PLAYBACK_STOP"://背景音乐结束
                System.out.println("--音乐播放-结束！--");
                break;
            case "CODEC"://编码协商
                System.out.println("--CODEC--");
                break;
            case "RE_SCHEDULE"://心跳相关
                System.out.println("--RE_SCHEDULE--");
                break;
            case "CALL_UPDATE"://更新呼叫
                System.out.println("--CALL_UPDATE--");
                break;
            case "PRESENCE_IN"://
                System.out.println("--PRESENCE_IN--");
                break;
            case "MESSAGE_QUERY"://
                System.out.println("--MESSAGE_QUERY--");
                break;
            case "MESSAGE_WAITING"://
                System.out.println("--MESSAGE_WAITING--");
                break;
            default:
                System.out.println("事件：" + eventName);
                Map<String, String> eventHeaders = event.getEventHeaders();
                System.out.println("--------eventHeaders:-------");
                if(eventHeaders!=null){
                    eventHeaders.forEach((key,value) ->{
                        System.out.println(" "+eventName+" Headers "+key+":"+value);
                    });
                }
                Map<EslHeaders.Name, String>  messageHeaders = event.getMessageHeaders();
                System.out.println("--------messageHeaders:-------");
                if(messageHeaders!=null){
                    messageHeaders.forEach((key,value) ->{
                        System.out.println("  "+eventName+" message  "+key+":"+value);
                    });
                }
                break;
        }
    }

    public void backgroundJobResultReceived( EslEvent event ){
        System.out.println("--------Event-Name:-------"+event.getEventName());
    }
}
