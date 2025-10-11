/*
 * chandler25-freeswitch
 * 2025/8/24 11:17
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package org.chandler25.freeswitch.esl;

import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.inbound.InboundConnectionFailure;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/8/24 11:17
 * @version 1.0.0
 * @since 1.8
 */
public class InboundExampleApp {
    public static void main(String[] args) {
        Client client = new Client();
        try {

            //客户端连接FS服务器
            client.connect("localhost", 8021, "ClueCon", 10);

            //注册监听
            client.addEventListener(new InboundEventListener());
            //监听各种事件
            client.setEventSubscriptions("plain", "ALL");

        } catch (InboundConnectionFailure inboundConnectionFailure) {
            System.out.println("连接失败！");
            inboundConnectionFailure.printStackTrace();
        }



        //异步发送指令
        try{

            //这里必须检查，防止网络抖动时，连接断开
            if (client.canSend()) {
                //（异步）向1004用户发起呼叫，用户接通后，后台播放音乐/tmp/demo1.wav
                String callResult = client.sendAsyncApiCommand("originate", "user/1001 &playback(test.wav)");
                System.out.println("api uuid:" + callResult);
            }

        }catch(Exception e) {
            e.printStackTrace();
        }
    }
}