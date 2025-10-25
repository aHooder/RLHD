package rs117.hd.opengl.commandbuffer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import rs117.hd.HdPlugin;
import rs117.hd.opengl.commandbuffer.commands.CallbackCommand;
import rs117.hd.overlays.FrameTimer;
import rs117.hd.overlays.Timer;

@Slf4j
@Singleton
public final class RenderThread implements Runnable {
	public static final int RENDER_THREAD_STACK_SIZE = 10 * 1024 * 1024; // 10 MB

	private static final RenderTask POISON_PILL = new RenderTask();
	private static final ArrayDeque<RenderTask> TASK_BIN = new ArrayDeque<>();

	private final LinkedBlockingDeque<RenderTask> queue = new LinkedBlockingDeque<>();
	private final List<RenderTask> completedTasks = new ArrayList<>();

	private final AtomicInteger pendingCount = new AtomicInteger(0);
	private final Object completionLock = new Object();
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final Semaphore invokeOnRenderThreadSemaphore = new Semaphore(0);
	private Thread thread;

	@Inject
	private HdPlugin plugin;

	@Inject
	private FrameTimer timer;

	private Thread clientThread;

	@Inject
	private AWTContextWrapper contextWrapper;

	@Inject
	private CommandBufferPool pool;

	public void initialize() {
		clientThread = Thread.currentThread();

		running.set(true);

		thread = new Thread(this, "HD-RenderThread");
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.setDaemon(true);
		thread.start();
	}

	public void submit(CommandBuffer buffer) { submit(buffer, null); }

	public void submit(CommandBuffer buffer, boolean forceThread) {
		submit(buffer, null, forceThread);
	}

	public void submit(CommandBuffer buffer, Runnable onComplete) { submit(buffer, onComplete, false); }

	public void submit(CommandBuffer buffer, Runnable onComplete, boolean forceThread) {
		if (buffer == null)
			throw new IllegalArgumentException("CommandBuffer cannot be null");

		if(!plugin.configRenderThread && !forceThread) {
			buffer.execute();
			if (onComplete != null) onComplete.run();
			if (buffer.pooled) pool.release(buffer);
			return;
		}

		pendingCount.incrementAndGet();

		if (contextWrapper.getOwner() == AWTContextWrapper.Owner.CLIENT && Thread.currentThread() == clientThread) {
			contextWrapper.detachCurrent("detached for render submit");
		}

		RenderTask newTask = TASK_BIN.poll();
		if (newTask == null) {
			newTask = new RenderTask();
		}

		newTask.buffer = buffer;
		newTask.callback = onComplete;

		if(buffer.highPriority) {
			queue.addFirst(newTask);
		} else {
			queue.addLast(newTask);
		}
	}

	private boolean isClientThread() {return Thread.currentThread() == clientThread; }

	public void processCompletedTasks() {
		if(isClientThread()) {
			synchronized (completedTasks) {
				for (RenderTask task : completedTasks) {
					if (task.callback != null) {
						try {
							task.callback.run();
						} catch (Throwable t) {
							log.warn("Exception in render completion callback", t);
						}
					}

					if (task.buffer.pooled) {
						pool.release(task.buffer);
					}

					task.buffer = null;
					task.callback = null;

					TASK_BIN.add(task);
				}
				completedTasks.clear();
			}
		}
	}

	@SneakyThrows
	public void waitForRenderingCompleted() {
		if(pendingCount.get() == 0 && completedTasks.isEmpty() && contextWrapper.getOwner() == AWTContextWrapper.Owner.CLIENT) {
			return;
		}

		synchronized (completionLock) {
			try {
				while (pendingCount.get() > 0) {
					if (contextWrapper.getOwner() == AWTContextWrapper.Owner.CLIENT && isClientThread())
						contextWrapper.detachCurrent("waiting for render completion");

					timer.begin(Timer.RENDER_THREAD_COMPLETION);
					completionLock.wait();
					timer.end(Timer.RENDER_THREAD_COMPLETION);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

			invokeOnRenderThread(() -> contextWrapper.detachCurrent("detached after render completion"));
			contextWrapper.makeCurrent(AWTContextWrapper.Owner.CLIENT);

			processCompletedTasks();
		}
	}

	@SneakyThrows
	@Override
	public void run() {
		log.debug("RenderThread started!");

		try(MemoryStack stack = MemoryStack.create(RENDER_THREAD_STACK_SIZE)) {
			while (running.get()) {
				RenderTask task;
				try {
					task = queue.take();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}

				if (task == POISON_PILL)
					break;

				if (task.buffer == null) {
					taskCompleted(task);
					continue;
				}

				contextWrapper.makeCurrent(AWTContextWrapper.Owner.RENDER_THREAD);

				try {
					stack.push();
					task.buffer.execute(stack);
					stack.pop();
				} catch (Throwable t) {
					log.error("Error during CommandBuffer execution", t);
				} finally {
					taskCompleted(task);
				}
			}
		}

		log.debug("RenderThread stopped!");
	}

	public void invokeOnRenderThread(CallbackCommand.ICallback callback) {
		invokeOnRenderThreadSemaphore.drainPermits();
		CommandBuffer cmd = pool.acquire();
		cmd.highPriority = true;
		cmd.Callback(callback);
		cmd.Signal(invokeOnRenderThreadSemaphore);
		submit(cmd);
		if (contextWrapper.getOwner() == AWTContextWrapper.Owner.CLIENT && isClientThread())
			contextWrapper.detachCurrent("waiting for callback to be invoked on render thread");
		invokeOnRenderThreadSemaphore.acquireUninterruptibly();
	}

	private void taskCompleted(RenderTask task) {
		synchronized (completedTasks) {
			completedTasks.add(task);
		}

		int remaining = pendingCount.decrementAndGet();
		if (remaining <= 0) {
			pendingCount.set(0);
			synchronized (completionLock) {
				completionLock.notifyAll();
			}
		}
	}

	public void shutdown() {
		if (running.getAndSet(false)) {
			queue.add(POISON_PILL);
			try {
				thread.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		thread = null;
	}

	private static class RenderTask {
		public CommandBuffer buffer = null;
		public Runnable callback = null;
	}
}
