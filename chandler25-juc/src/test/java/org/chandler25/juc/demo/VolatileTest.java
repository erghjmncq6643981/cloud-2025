/*
 * chandler25-juc
 * 2025/8/13 10:56
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package org.chandler25.juc.demo;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/8/13 10:56
 * @version 1.0.0
 * @since 1.8
 */
public class VolatileTest {
    public static  volatile int race=0;
    public static  void increase(){
        race++;
        //每次的最终结果可能不一致
        System.out.println(race);
    }

    private static  final int THREADS_COUNT=20;

    public static void main(String[] args) {
        Thread[] threads=new Thread[THREADS_COUNT];
        for (int i = 0; i < threads.length; i++) {
            threads[i]=new Thread(()->{
                for (int j = 0; j < 10000; j++) {
                    increase();
                }
            });
            threads[i].start();
        }
    }
}