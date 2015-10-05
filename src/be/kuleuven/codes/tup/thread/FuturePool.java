package be.kuleuven.codes.tup.thread;

import java.util.*;
import java.util.concurrent.*;

public class FuturePool {

    private ConcurrentLinkedQueue<Future> queue = new ConcurrentLinkedQueue<>();
    private ThreadExecutor executor;

    public FuturePool() {
        this.executor = null;
    }

    public FuturePool(ThreadExecutor counter) {
        this.executor = counter;
    }

    public void add(Future future) {
        if (future != null)
            queue.add(future);
    }

    public void addAll(Collection<Future> futures) {
        queue.addAll(futures);
    }

    public void join() {
        if (executor != null) executor.decrementCounter();

        while (!queue.isEmpty()) {
            try {
                queue.poll().get();
            }
            catch (InterruptedException interruptException) {
                Thread.currentThread().interrupt();
                break;
            }
            catch (ExecutionException ignore) { }
        }

        if (executor != null) executor.incrementCounter();
    }
}
