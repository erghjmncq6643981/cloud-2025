/*
 * chandler25-juc
 * 2025/8/1 17:16
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package org.chandler25.juc.demo;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/8/1 17:16
 * @version 1.0.0
 * @since 1.8
 */
public class AtomicClassesTest {
    public static void main(String[] args) {
        AtomicInteger counter = new AtomicInteger(0);
        counter.incrementAndGet(); // 线程安全的自增
        System.out.println(counter);
        AtomicBoolean flag = new AtomicBoolean(true);
        flag.compareAndSet(true, false); // 仅当为true时更新为false
        System.out.println(flag);
        int[] arr = {1, 2};
        AtomicIntegerArray atomicArray = new AtomicIntegerArray(arr);
        atomicArray.getAndSet(0, 3); // arr[0]仍为1，atomicArray[0]变为3
        System.out.println(atomicArray);
    }
}