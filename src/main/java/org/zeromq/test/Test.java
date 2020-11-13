package org.zeromq.test;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.lang.reflect.Field;

/**
 * @author: 唐眠 tangmian.tj@antgroup.com on 2020/11/10 4:53 下午
 * @desc:
 */
public class Test {

    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
        ZContext zContext = new ZContext(1);
        Class<ZMQ.Context> contextClass = (Class<ZMQ.Context>) zContext.getContext().getClass();
        Field ctxFiled = contextClass.getDeclaredField("ctx");
        ctxFiled.setAccessible(true);
        System.out.println(ctxFiled.get(zContext));

    }

}
