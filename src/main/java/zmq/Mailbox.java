package zmq;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import zmq.pipe.YPipe;
import zmq.util.Errno;

public final class Mailbox implements IMailbox
{
    //  The pipe to store actual commands.
    //  存储命令的管道
    private final YPipe<Command> cpipe;

    //  Signaler to pass signals from writer thread to reader thread.
    //  从写线程到读线程发射信号 的信号器
    private final Signaler signaler;

    //  There's only one thread receiving from the mailbox, but there
    //  is arbitrary number of threads sending. Given that ypipe requires
    //  synchronized access on both of its endpoints, we have to synchronize
    //  the sending side.
    //  mailbox 的接收方只有一个线程，但是有无数的发送方线程，鉴于 ypipe 要求可以通过在两端访问（即同时写和读），
    //  所以有必要在发送方设置同步锁
    private final Lock sync;

    //  True if the underlying pipe is active, i.e. when we are allowed to
    //  read commands from it.
    //  true 代表所依赖的管道是激活的。 例如当我们可以从中读取命令时。
    private boolean active;

    // mailbox name, for better debugging
    // 邮箱名字 为了更好的debug
    private final String name;

    private final Errno errno;

    public Mailbox(Ctx ctx, String name, int tid)
    {
        this.errno = ctx.errno();
        cpipe = new YPipe<>(Config.COMMAND_PIPE_GRANULARITY.getValue());
        sync = new ReentrantLock();
        signaler = new Signaler(ctx, tid, errno);

        //  Get the pipe into passive state. That way, if the users starts by
        //  polling on the associated file descriptor it will get woken up when
        //  new command is posted.

        Command cmd = cpipe.read();
        assert (cmd == null);
        active = false;

        this.name = name;
    }

    public SelectableChannel getFd()
    {
        return signaler.getFd();
    }

    @Override
    public void send(final Command cmd)
    {
        boolean ok = false;
        sync.lock();
        try {
            cpipe.write(cmd, false);
            ok = cpipe.flush();
        }
        finally {
            sync.unlock();
        }

        if (!ok) {
            signaler.send();
        }
    }

    @Override
    public Command recv(long timeout)
    {
        Command cmd;
        //  Try to get the command straight away.
        if (active) {
            cmd = cpipe.read();
            if (cmd != null) {
                return cmd;
            }

            //  If there are no more commands available, switch into passive state.
            active = false;
        }

        //  Wait for signal from the command sender.
        boolean rc = signaler.waitEvent(timeout);
        if (!rc) {
            assert (errno.get() == ZError.EAGAIN || errno.get() == ZError.EINTR) : errno.get();
            return null;
        }

        //  Receive the signal.
        signaler.recv();
        if (errno.get() == ZError.EINTR) {
            return null;
        }

        //  Switch into active state.
        active = true;

        //  Get a command.
        cmd = cpipe.read();
        assert (cmd != null) : "command shall never be null when read";

        return cmd;
    }

    @Override
    public void close() throws IOException
    {
        //  TODO: Retrieve and deallocate commands inside the cpipe.

        // Work around problem that other threads might still be in our
        // send() method, by waiting on the mutex before disappearing.
        sync.lock();
        sync.unlock();

        signaler.close();
    }

    @Override
    public String toString()
    {
        return super.toString() + "[" + name + "]";
    }
}
