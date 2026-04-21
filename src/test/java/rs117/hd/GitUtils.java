package rs117.hd;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import rs117.hd.utils.GitInfo;

@Slf4j
public class GitUtils {
	private static List<String> packedRefs = null;

	static {
		GitInfo.HOOK = () -> {
			try {
				populateGitInfo();
				packedRefs = null;
			} catch (IOException ex) {
				log.error("Error populating git info:", ex);
			}
		};
	}

	private static boolean isCommitHash(String hash) {
		return
			(hash.length() == 40 || hash.length() == 64) &&
			hash.matches("^[0-9a-f]+$");
	}

	private static void populateGitInfo() throws IOException {
		File gitDir = new File(".git");
		File headFile = new File(gitDir, "HEAD");
		if (!headFile.exists())
			return;

		String head = Files.readString(headFile.toPath()).trim();
		if (head.startsWith("ref: ")) {
			String ref = head.substring(5);
			String result = resolveRef(gitDir, ref);
			if (result != null) {
				head = ref;
			}
		}

		if (!isCommitHash(head))
			return;

		COMMIT = head;

		File configFile = new File(gitDir, "config");
		if (!configFile.exists())
			return;

		String remote = null;
		String branch = null;

		Path remotesDir = gitDir.toPath().resolve("refs/remotes");
		Optional<Path> looseRef;
		try (var stream = Files.walk(remotesDir, 2)) {
			looseRef = stream
				.filter(Files::isRegularFile)
				.filter(path -> {
					try {
						return COMMIT.equals(Files.readString(path).trim());
					} catch (IOException ignored) {
						return false;
					}
				})
				.findFirst();
		}

		if (looseRef.isPresent()) {
			var path = remotesDir.relativize(looseRef.get());
			remote = path.subpath(0, 1).toString();
			branch = path.subpath(1, 2).toString();
		} else {
			File packedRefsFile = new File(gitDir, "packed-refs");
			if (packedRefsFile.exists()) {
				String pattern = COMMIT + " refs/remotes/";
				var lines = Files.readAllLines(packedRefsFile.toPath());
				for (String line : lines) {
					if (!line.startsWith(pattern))
						continue;

					String packedRef = line.substring(pattern.length()).trim();
					String[] parts = packedRef.split("/");
					if (parts.length == 2) {
						remote = parts[0];
						branch = parts[1];
						break;
					}
				}
			}
		}

		if (remote != null) {
			String remoteHeader = "[remote \"" + remote + "\"]";

			boolean foundRemote = false;
			var lines = Files.readAllLines(configFile.toPath());
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				if (foundRemote) {
					if (line.startsWith("["))
						break;

					line = line.trim();
					if (line.startsWith("url = ")) {
						REMOTE = line.substring(6);
						BRANCH = branch;
						break;
					}
				} else if (line.equals(remoteHeader)) {
					foundRemote = true;
				}
			}
		}
	}

	private static void loadPackedRefs(File gitDir) throws IOException {
		if (packedRefs != null)
			return;

		File packedRefsFile = new File(gitDir, "packed-refs");
		if (packedRefsFile.exists()) {
			packedRefs = Files.readAllLines(packedRefsFile.toPath());
		} else {
			packedRefs = Collections.emptyList();
		}
	}

	private static String resolveRef(File gitDir, String ref) throws IOException {
		File refFile = new File(gitDir, ref);
		if (refFile.exists())
			return Files.readString(refFile.toPath()).trim();

		loadPackedRefs(gitDir);
		String pattern = " " + ref;
		for (String line : packedRefs)
			if (line.endsWith(pattern))
				return line.substring(0, line.indexOf(' '));

		return null;
	}
}
