package rs117.hd.opengl.commandbuffer;

import lombok.Getter;

public class FrameSync {
	@Getter
	private volatile boolean awaiting = false;
	private volatile boolean ready = false;
	private volatile boolean inFlight = false;

	public boolean await() {
		if(inFlight && !awaiting) {
			awaiting = true;
			while (!ready) {
				Thread.onSpinWait();
			}
		}

		inFlight = false;
		return true;
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

