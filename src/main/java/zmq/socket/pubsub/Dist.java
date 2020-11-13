package zmq.socket.pubsub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import zmq.Msg;
import zmq.pipe.Pipe;

public class Dist
{
    //  List of outbound pipes.
    private final List<Pipe> pipes;

    //  Number of all the pipes to send the next message to.
    //  下条消息要发送到管道的数量
    private int matching;

    //  Number of active pipes. All the active pipes are located at the
    //  beginning of the pipes array. These are the pipes the messages
    //  can be sent to at the moment.
    //  active 激活状态的管道的数量
    //  所有激活的管道都在管道数组的前面 都可以被发送消息。
    private int active;

    //  Number of pipes eligible for sending messages to. This includes all
    //  the active pipes plus all the pipes that we can in theory send
    //  messages to (the HWM is not yet reached), but sending a message
    //  to them would result in partial message being delivered, ie. message
    //  with initial parts missing.
    //  合格管道的数量，包含所有的激活管道和理论上我可以发送消息的管道（HWM还没达到）。
    //  但是发送消息给他们可能会引起消息的部分丢失，比如消息的初始化部分
    private int eligible;

    //  True if last we are in the middle of a multipart message.
    //  是否正处于发送一个多部分消息中， 如果true，代表正在发送一个多部分消息。
    private boolean more;

    public Dist()
    {
        matching = 0;
        active = 0;
        eligible = 0;
        more = false;
        pipes = new ArrayList<>();
    }

    //  Adds the pipe to the distributor object.
    //  增加管道
    public void attach(Pipe pipe)
    {
        //  If we are in the middle of sending a message, we'll add new pipe
        //  into the list of eligible pipes. Otherwise we add it to the list
        //  of active pipes.
        //  如果我们正处于发送一个多部分消息中（代表该消息还未发送全部），我们把新的管道加到合格的管道中（不会往里面发送消息）
        //  否则加到
        if (more) {
            //加载末尾
            pipes.add(pipe);
            //把刚加的管道放到合格的位置
            Collections.swap(pipes, eligible, pipes.size() - 1);
            eligible++;
        }
        else {
            pipes.add(pipe);
            //把刚加的管道放到激活可用的管道的位置
            Collections.swap(pipes, active, pipes.size() - 1);
            active++;
            eligible++;
        }
    }

    //  Mark the pipe as matching. Subsequent call to sendToMatching
    //  will send message also to this pipe.
    //  使管道处于可匹配状态  后续调用 sendToMatching 也会发送到这个管道
    //  注意 管道必须在 pipes 里面，并且还是合格状态
    public void match(Pipe pipe)
    {
        int idx = pipes.indexOf(pipe);
        //  If pipe is already matching do nothing.
        if (idx < matching) {
            return;
        }

        //  If the pipe isn't eligible, ignore it.
        //  如果管道不合格 忽略
        if (idx >= eligible) {
            return;
        }

        //  Mark the pipe as matching.
        //  把管道放到 matching 管道部分的末尾， matching+1
        Collections.swap(pipes, idx, matching);
        matching++;
    }

    //  Mark all pipes as non-matching.
    public void unmatch()
    {
        matching = 0;
    }

    //  Removes the pipe from the distributor object.
    //  把管道移除
    public void terminated(Pipe pipe)
    {
        //  Remove the pipe from the list; adjust number of matching, active and/or
        //  eligible pipes accordingly.
        if (pipes.indexOf(pipe) < matching) {
            Collections.swap(pipes, pipes.indexOf(pipe), matching - 1);
            matching--;
        }
        if (pipes.indexOf(pipe) < active) {
            Collections.swap(pipes, pipes.indexOf(pipe), active - 1);
            active--;
        }
        if (pipes.indexOf(pipe) < eligible) {
            Collections.swap(pipes, pipes.indexOf(pipe), eligible - 1);
            eligible--;
        }
        pipes.remove(pipe);
    }

    //  Activates pipe that have previously reached high watermark.
    //  激活之前达到高水位的管道
    public void activated(Pipe pipe)
    {
        //  Move the pipe from passive to eligible state.
        //  把管道从 passive 状态移动到合格状态
        if (eligible < pipes.size()) {
            Collections.swap(pipes, pipes.indexOf(pipe), eligible);
            eligible++;
        }

        //  If there's no message being sent at the moment, move it to
        //  the active state.
        //  如果没有消息在发送中，直接移到激活状态
        if (!more && active < pipes.size()) {
            Collections.swap(pipes, eligible - 1, active);
            active++;
        }
    }

    //  Send the message to all the outbound pipes.
    //  发送给所有激活管道
    public boolean sendToAll(Msg msg)
    {
        //把匹配管道数量设置成激活的数量  这样发送给所有的管道
        matching = active;
        return sendToMatching(msg);
    }

    //  Send the message to the matching outbound pipes.
    public boolean sendToMatching(Msg msg)
    {
        //  Is this end of a multipart message?
        boolean msgMore = msg.hasMore();

        //  Push the message to matching pipes.
        distribute(msg);

        //  If multipart message is fully sent, activate all the eligible pipes.
        if (!msgMore) {
            active = eligible;
        }

        more = msgMore;

        return true;
    }

    //  Put the message to all active pipes. 把消息发到所有活着的管道上
    private void distribute(Msg msg)
    {
        //  If there are no matching pipes available, simply drop the message.
        if (matching == 0) {
            return;
        }

        // TODO isVsm

        //  Push copy of the message to each matching pipe.
        for (int idx = 0; idx < matching; ++idx) {
            if (!write(pipes.get(idx), msg)) {
                --idx; //  Retry last write because index will have been swapped
            }
        }
    }

    public boolean hasOut()
    {
        return true;
    }

    //  Write the message to the pipe. Make the pipe inactive if writing
    //  fails. In such a case false is returned.
    private boolean write(Pipe pipe, Msg msg)
    {
        if (!pipe.write(msg)) {
            //写失败 把pipe移出matching范围
            Collections.swap(pipes, pipes.indexOf(pipe), matching - 1);
            matching--;
            //把pipe移出active范围
            Collections.swap(pipes, pipes.indexOf(pipe), active - 1);
            active--;
            //把pipe移出合格范围
            Collections.swap(pipes, active, eligible - 1);
            eligible--;
            return false;
        }
        if (!msg.hasMore()) {
            //如果该消息已发送完  管道往下游冲洗消息
            pipe.flush();
        }
        return true;
    }

    public boolean checkHwm()
    {
        for (int idx = 0; idx < matching; ++idx) {
            if (!pipes.get(idx).checkHwm()) {
                return false;
            }
        }
        return true;
    }

    int active()
    {
        return active;
    }

    int eligible()
    {
        return eligible;
    }

    int matching()
    {
        return matching;
    }
}
