package rs117.hd.opengl.commandbuffer;

import java.awt.Canvas;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.rlawt.AWTContext;
import org.lwjgl.opengl.*;
import rs117.hd.HdPlugin;

@Slf4j
@Singleton
public final class AWTContextWrapper {
	private static final boolean VALIDATE = log.isDebugEnabled();

	public enum Owner { CLIENT, RENDER_THREAD, NONE }

	private final Object ownershipLock = new Object();
	private volatile Owner currentOwner = Owner.CLIENT;

	private static final int MAX_OWNERSHIP_LOG = 8;
	private final ConcurrentLinkedQueue<String> ownershipLog = new ConcurrentLinkedQueue<>();

	private final StringBuilder sb = new StringBuilder();

	@Inject
	private Client client;

	@Getter
	private AWTContext context;

	@Getter
	private Canvas canvas;

	public boolean initialize() {
		AWTContext.loadNatives();
		canvas = client.getCanvas();

		synchronized (canvas.getTreeLock()) {
			// Delay plugin startup until the client's canvas is valid
			if (!canvas.isValid())
				return false;

			context = new AWTContext(canvas);
			context.configurePixelFormat(0, 0, 0);
		}

		context.createGLContext();
		canvas.setIgnoreRepaint(true);
		return true;
	}

	public void shutdown() {
		if (context != null)
			context.destroy();
		context = null;

		// Validate the canvas so it becomes valid without having to manually resize the client
		if(canvas != null)
			canvas.validate();
		canvas = null;
	}

	public int setSwapInterval(int interval) { return context.setSwapInterval(interval); }

	public int getBufferMode() { return context.getBufferMode(); }

	public int getBackBuffer() { return context.getFramebuffer(false); }

	public synchronized Owner getOwner() {
		return currentOwner;
	}

	public synchronized void makeCurrent(Owner newOwner) {
		if (currentOwner == newOwner)
			return;

		try {
			context.makeCurrent();
			if(newOwner == Owner.RENDER_THREAD && HdPlugin.GL_RENDER_THREAD_CAPS == null){
				HdPlugin.GL_RENDER_THREAD_CAPS = GL.createCapabilities();
			}
			updateOwner(newOwner, "makeCurrent");
		} catch (Exception e) {
			printOwnershipLog();
			log.error("Failed to make OpenGL context current for {}", newOwner, e);
			throw new RuntimeException(e);
		}
	}

	public synchronized void detachCurrent(String reason) {
		try {
			context.detachCurrent();
			updateOwner(Owner.NONE, reason);
		} catch (Exception e) {
			printOwnershipLog();
			log.error("Failed to detach OpenGL context: " + reason, e);
			throw new RuntimeException(e);
		}
	}

	public void awaitOwnership(Owner desiredOwner) {
		synchronized (ownershipLock) {
			while (currentOwner != desiredOwner) {
				try {
					ownershipLock.wait(1);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	private void updateOwner(Owner newOwner, String action) {
		if (currentOwner != newOwner && VALIDATE) {
			if (ownershipLog.size() >= MAX_OWNERSHIP_LOG)
				ownershipLog.poll();

			sb.append(currentOwner)
				.append(" -> ")
				.append(newOwner)
				.append(" via ")
				.append(action);

			ownershipLog.add(sb.toString());

			sb.setLength(0);
		}

		currentOwner = newOwner;

		synchronized (ownershipLock) {
			ownershipLock.notifyAll();
		}
	}

	private void printOwnershipLog() {
		if (!VALIDATE) return;

		if (ownershipLog.isEmpty()) {
			log.error("No recent ownership transitions recorded.");
		} else {
			log.error("Recent ownership transitions:");
			for (String entry : ownershipLog) {
				log.error(" - {}", entry);
			}
		}
	}
}
