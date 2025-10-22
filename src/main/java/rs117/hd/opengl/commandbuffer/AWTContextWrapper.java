package rs117.hd.opengl.commandbuffer;

import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.extern.slf4j.Slf4j;
import net.runelite.rlawt.AWTContext;
import org.lwjgl.opengl.*;
import rs117.hd.HdPlugin;

@Slf4j
public final class AWTContextWrapper {
	public enum Owner { CLIENT, RENDER_THREAD, NONE }

	private final AWTContext context;
	private volatile Owner currentOwner = Owner.CLIENT;

	private static final int MAX_OWNERSHIP_LOG = 8;
	private final ConcurrentLinkedQueue<String> ownershipLog = new ConcurrentLinkedQueue<>();

	private final StringBuilder sb = new StringBuilder();
	private final boolean validate;

	public AWTContextWrapper(AWTContext context, boolean validate) {
		this.context = context;
		this.validate = validate;
	}

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

	private void updateOwner(Owner newOwner, String action) {
		if (currentOwner != newOwner && validate) {
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
	}

	private void printOwnershipLog() {
		if (!validate) return;

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
