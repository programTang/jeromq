package org.zeromq.test;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * @author: 唐眠 tangmian.tj@antgroup.com on 2020/11/10 5:16 下午
 * @desc:
 */
public class TestScoketClose {

    public static void main(String[] args) throws InterruptedException {
        ZContext zContext = new ZContext(1);
        ConcurrentHashMap<String, ZMQ.Socket> cacheMap = new ConcurrentHashMap<>();
        String url = "tcp://127.0.0.1:4444";

        ScheduledExecutorService createSocketService = Executors.newScheduledThreadPool(2);
        ScheduledExecutorService closeSocketService = Executors.newScheduledThreadPool(2);

        createSocketService.schedule(new Runnable() {
            @Override
            public void run() {
                if (!cacheMap.containsKey(url)){
                    ZMQ.Socket socket = zContext.createSocket(SocketType.REP);
                    System.out.println("增加");
                    socket.connect(url);
                    cacheMap.put(url, socket);
                }

            }
        }, 1, TimeUnit.SECONDS);
        System.out.println("移除1");
        closeSocketService.schedule(new Runnable() {
            @Override
            public void run() {

                cacheMap.forEach((s, socket) -> {
                    zContext.destroySocket(socket);
                    System.out.println("移除");
                    cacheMap.remove(s);
                });
            }
        }, 1, TimeUnit.SECONDS);

        Thread.sleep(100000);

    }

}
