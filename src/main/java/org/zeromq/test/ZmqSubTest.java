package org.zeromq.test;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZPoller;

/**
 * @author: 唐眠 tangmian.tj@antgroup.com on 2020/10/1 12:07 下午
 * @desc:
 */
public class ZmqSubTest {

    public static void main(String[] args) {
        try (ZContext zContext = new ZContext()){
            ZMQ.Socket subSocket = zContext.createSocket(SocketType.REP);
//            subSocket.connect("inproc://5555");
            subSocket.connect("tcp://localhost:5555");
//            subSocket.subscribe("a".getBytes());
//            subSocket.subscribe("".getBytes());

            ZMQ.Poller poller = zContext.createPoller(1);
            int dataIndex = poller.register(subSocket);
            while (true){
                if (poller.poll() > 0 && poller.pollin(dataIndex)){
                    byte[] bytes = subSocket.recv();
                    System.out.println(new String(bytes));
                }
            }
        }
    }
}
