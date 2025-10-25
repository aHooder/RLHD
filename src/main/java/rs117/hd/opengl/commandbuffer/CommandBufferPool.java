package rs117.hd.opengl.commandbuffer;

import java.util.ArrayDeque;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class CommandBufferPool {

	public static CommandBufferPool INSTANCE;

	private final ArrayDeque<CommandBuffer> pool = new ArrayDeque<>();

	public CommandBufferPool() {
		INSTANCE = this;
	}

	public CommandBuffer acquire() {
		if (pool.isEmpty()) {
			CommandBuffer buffer = new CommandBuffer();
			buffer.pooled = true;
			return buffer;
		}

		return pool.pop();
	}

	public void release(CommandBuffer buffer) {
		assert buffer.pooled;
		buffer.reset();
		pool.push(buffer);
	}
}
