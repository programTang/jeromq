package org.zeromq.test;

import com.google.common.util.concurrent.RateLimiter;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * @author: 唐眠 tangmian.tj@antgroup.com on 2020/10/1 11:56 上午
 * @desc:
 */
public class ZmqPubTest {

    /**
     * 发送的数据量大小
     */
    private static int DATA_SIZE = 1024;

    /**
     * 发送频率
     */
    private static int SEND_LIMIT = 20;

    public static void main(String[] args) {
        try         (ZContext zContext = new ZContext()){
            ZMQ.Socket pubSocket = zContext.createSocket(SocketType.REQ);
//            pubSocket.bind("inproc://5555");
            pubSocket.bind("tcp://localhost:5555");

            RateLimiter rateLimiter = RateLimiter.create(SEND_LIMIT);
            int index = 0;

//            ExecutorCreator.newFixed(1, "test-zmq-pub").submit(() -> {
//                while (true){
                    rateLimiter.acquire();
//                    System.out.println("发送数据");
                    pubSocket.sendMore("a".getBytes());
                    pubSocket.send(Integer.valueOf(index++).toString().getBytes(ZMQ.CHARSET), ZMQ.NOBLOCK);
                    byte[] bytes = new byte[DATA_SIZE];
            System.out.println("关闭socket");
                    pubSocket.close();
                    while (true){

                    }
//                }
//            });
        }
    }
}
