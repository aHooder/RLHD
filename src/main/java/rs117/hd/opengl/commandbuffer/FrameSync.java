package rs117.hd.opengl.commandbuffer;

import java.util.concurrent.locks.LockSupport;
import lombok.Getter;

public class FrameSync {
	@Getter
	private volatile boolean awaiting = false;
	private volatile boolean ready = false;
	private volatile boolean inFlight = false;
	private volatile Thread waitingThread;

	public boolean await() {
		if(inFlight) {
			if (!awaiting) {
				awaiting = true;
				waitingThread = Thread.currentThread();
			}

			while (!ready) {
				LockSupport.park(this);
				if (Thread.interrupted()) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}

		inFlight = false;
		waitingThread = null;
		return true;
	}


	public void markInFlight() {
		awaiting = false;
		ready = false;
		inFlight = true;
	}

	public void signalReady() {
		ready = true;
		Thread waiter = waitingThread;
		if (waiter != null)
			LockSupport.unpark(waiter);
	}
}

