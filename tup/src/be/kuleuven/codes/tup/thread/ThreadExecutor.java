package be.kuleuven.codes.tup.thread;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ThreadExecutor extends ThreadPoolExecutor {

    private AtomicInteger counter = new AtomicInteger(0);

    public ThreadExecutor(int maxThreads) {
        super(maxThreads, maxThreads, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>());
    }

    public boolean hasEmptySlot() {
        return counter.get() < getCorePoolSize()*10;
    }

    @Override public Future<?> submit(Runnable task) {
        return submit(task, -1);
    }

    @Override public <T> Future<T> submit(Callable<T> task) {
        throw new Error("Method is not implemented");
    }

    @Override public <T> Future<T> submit(Runnable task, T result) {
        counter.incrementAndGet();
        return super.submit(() -> {
            task.run();
            counter.decrementAndGet();
        }, result);
    }

    public List<Future> submitAll(Collection<? extends Runnable> tasks) {
        List<Future> list = new ArrayList<>();
        counter.addAndGet(tasks.size());

        int t = tasks.size();
        for (Runnable task : tasks) {
            list.add(super.submit(() -> {
                task.run();
                counter.decrementAndGet();
            }, --t));
        }

        return list;
    }


    public void incrementCounter() {
        counter.incrementAndGet();
    }

    public void decrementCounter() {
        counter.decrementAndGet();
    }


    @Override protected <T> RunnableFuture<T> newTaskFor(final Callable<T> callable) {
        if (callable instanceof Important)
            return new PriorityTask<>((( Important ) callable).getPriority(), callable);
        else
            return new PriorityTask<>(0, callable);
    }

    @Override protected <T> RunnableFuture<T> newTaskFor(final Runnable runnable, final T value) {
        if (runnable instanceof Important)
            return new PriorityTask<>((( Important ) runnable).getPriority(), runnable, value);
        else if (value instanceof Integer)
            return new PriorityTask<>((( Integer ) value).intValue(), runnable, value);
        else
            return new PriorityTask<>(0, runnable, value);
    }


    public interface Important {

        int getPriority();
    }

    private static final class PriorityTask<T>
      extends FutureTask<T>
      implements Comparable<PriorityTask<T>> {

        private final int priority;

        public PriorityTask(final int priority, final Callable<T> tCallable) {
            super(tCallable);

            this.priority = priority;
        }

        public PriorityTask(final int priority, final Runnable runnable, final T result) {
            super(runnable, result);

            this.priority = priority;
        }

        @Override
        public int compareTo(final PriorityTask<T> o) {
            final long diff = o.priority - priority;
            return 0 == diff ? 0 : 0 > diff ? -1 : 1;
        }
    }

    private static class PriorityTaskComparator
      implements Comparator<Runnable> {

        @Override
        public int compare(final Runnable left, final Runnable right) {
            return (( PriorityTask ) left).compareTo(( PriorityTask ) right);
        }
    }
}
