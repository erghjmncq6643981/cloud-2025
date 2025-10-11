/*
 * chandler25-juc
 * 2025/8/1 18:15
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package org.chandler25.juc.demo;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/8/1 18:15
 * @version 1.0.0
 * @since 1.8
 */
public class BlockingQueueDemo {
    public static void main(String[] args) {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(3); // 容量为3的队列

        // 生产者
        Runnable producer = () -> {
            try {
                for (int i = 0; i < 5; i++) {
                    String item = "Item-" + i;
                    queue.put(item); // 队列满时阻塞
                    System.out.println("Produced: " + item);
                    TimeUnit.MILLISECONDS.sleep(500); // 模拟生产耗时
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // 消费者
        Runnable consumer = () -> {
            try {
                while (true) {
                    String item = queue.take(); // 队列空时阻塞
                    System.out.println("Consumed: " + item);
                    TimeUnit.SECONDS.sleep(1); // 模拟消费耗时
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        new Thread(producer).start();
        new Thread(consumer).start();
    }
}