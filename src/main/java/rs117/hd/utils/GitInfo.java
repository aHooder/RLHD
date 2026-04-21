package rs117.hd.utils;

import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import rs117.hd.BuildInfo;

public class GitInfo {
	public String COMMIT;
	public String BRANCH;
	public String REMOTE;

	public static Supplier<GitInfo> SUPPLIER;

	public static String get() {
		if (HOOK != null)
			HOOK.run();

		String info = Stream
			.of(
				BRANCH,
				COMMIT.substring(0, 7),
				REMOTE.replaceAll("^https?://", "")
			)
			.filter(s -> !"unknown".equals(s))
			.collect(Collectors.joining(" · "));
		return info.isEmpty() ? null :
	}
}
