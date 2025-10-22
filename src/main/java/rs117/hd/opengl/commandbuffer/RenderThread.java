package rs117.hd.opengl.commandbuffer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import net.runelite.rlawt.AWTContext;
import rs117.hd.overlays.FrameTimer;
import rs117.hd.overlays.Timer;

@Slf4j
public final class RenderThread implements Runnable {
	private static final boolean VALIDATE = false;

	private static final RenderTask POISON_PILL = new RenderTask();
	private static final ArrayDeque<RenderTask> TASK_BIN = new ArrayDeque<>();

	private final BlockingQueue<RenderTask> queue = new LinkedBlockingQueue<>();
	private final List<RenderTask> completedTasks = new ArrayList<>();

	private final AtomicInteger pendingCount = new AtomicInteger(0);
	private final Object completionLock = new Object();
	private final AtomicBoolean running = new AtomicBoolean(true);

	private final FrameTimer timer;
	private final AWTContextWrapper contextWrapper;
	private final Thread thread;

	public RenderThread(FrameTimer timer, AWTContext context) {
		this.timer = timer;
		this.contextWrapper = new AWTContextWrapper(context, VALIDATE);
		this.thread = new Thread(this, "HD-RenderThread");
		this.thread.setPriority(Thread.MAX_PRIORITY);
		this.thread.setDaemon(true);
		this.thread.start();
	}

	public void submit(CommandBuffer buffer) {
		submit(buffer, null);
	}

	public void submit(CommandBuffer buffer, Runnable onComplete) {
		if (buffer == null)
			throw new IllegalArgumentException("CommandBuffer cannot be null");

		pendingCount.incrementAndGet();

		if (contextWrapper.getOwner() == AWTContextWrapper.Owner.CLIENT)
			contextWrapper.detachCurrent("detached for render submit");

		RenderTask newTask = TASK_BIN.poll();
		if (newTask == null) {
			newTask = new RenderTask();
		}

		newTask.buffer = buffer;
		newTask.callback = onComplete;

		queue.add(newTask);
	}

	public void waitForRenderingCompleted() {
		synchronized (completionLock) {
			try {
				while (pendingCount.get() > 0) {
					timer.begin(Timer.RENDER_THREAD_COMPLETION);
					completionLock.wait();
					timer.end(Timer.RENDER_THREAD_COMPLETION);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

			contextWrapper.makeCurrent(AWTContextWrapper.Owner.CLIENT);

			synchronized (completedTasks) {
				for (RenderTask task : completedTasks) {
					if (task.callback != null) {
						try {
							task.callback.run();
						} catch (Throwable t) {
							log.warn("Exception in render completion callback", t);
						}
					}

					task.buffer = null;
					task.callback = null;

					TASK_BIN.add(task);
				}
				completedTasks.clear();
			}
		}
	}

	@Override
	public void run() {
		log.debug("RenderThread started!");

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
				task.buffer.submit();
			} catch (Throwable t) {
				log.error("Error during CommandBuffer execution", t);
			} finally {
				taskCompleted(task);
			}
		}

		log.debug("RenderThread stopped!");
	}

	private void taskCompleted(RenderTask task) {
		synchronized (completedTasks) {
			completedTasks.add(task);
		}

		int remaining = pendingCount.decrementAndGet();
		if (remaining <= 0) {
			// Release ownership now that there is no more work pending
			if (contextWrapper.getOwner() == AWTContextWrapper.Owner.RENDER_THREAD)
				contextWrapper.detachCurrent("released by render thread");

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
	}

	private static class RenderTask {
		public CommandBuffer buffer = null;
		public Runnable callback = null;
	}
}
