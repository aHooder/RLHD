package rs117.hd.opengl.commandbuffer;

import java.util.concurrent.locks.LockSupport;
import lombok.Getter;

public class FrameSync {
	@Getter
	private volatile boolean awaiting = false;
	private volatile boolean ready = false;
	private volatile boolean inFlight = false;

	public boolean await() {
		if (inFlight && !awaiting) {
			awaiting = true;

			while (!ready) {
				LockSupport.parkNanos(1);
			}

			inFlight = false;
			return true;
		}
		return false;
	}

	public void markInFlight() {
		awaiting = false;
		ready = false;
		inFlight = true;
	}

	public void signalReady() {
		ready = true;
	}
}

