package be.kuleuven.codes.tup.thread;

import java.util.*;
import java.util.concurrent.*;

public class SequentialExecutor extends ThreadExecutor {

    public SequentialExecutor() {
        super(1);
    }

    public int getCorePoolSize() {
        return 1;
    }

    public synchronized void setCorePoolSize(int value) { }

    public boolean hasEmptySlot() {
        return false;
    }

    @Override public List<Runnable> shutdownNow() {
        return new ArrayList<>(0);
    }

    @Override public Future submit(Runnable runnable) {
        runnable.run();
        return null;
    }

    @Override public <T> Future<T> submit(Runnable runnable, T value) {
        runnable.run();
        return null;
    }

    public List<Future> submitAll(Collection<? extends Runnable> tasks) {
        for (Runnable task : tasks) {
            try {
                task.run();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new ArrayList<>(0);
    }
}
