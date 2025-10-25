package rs117.hd.opengl.commandbuffer;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.SneakyThrows;

public class FrameSync {
	@Getter
	private final Semaphore sema = new Semaphore(0);

	private final AtomicBoolean inFlight = new AtomicBoolean(false);
	private final AtomicBoolean awaiting = new AtomicBoolean(false);

	public boolean isAwaiting() { return awaiting.get();}

	@SneakyThrows
	public boolean await() {
		if(inFlight.get() && !awaiting.get()) {
			sema.acquire();
			awaiting.set(true);
			inFlight.set(false);
			return true;
		}
		return false;
	}

	public void markInFlight() {
		awaiting.set(false);
		inFlight.set(true);
		sema.drainPermits();
	}
}
