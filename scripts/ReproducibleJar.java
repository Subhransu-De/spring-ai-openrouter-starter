/*
 * Canonical thin-JAR writer shared by Maven and Gradle.
 *
 * Run with:
 *   java scripts/ReproducibleJar.java <input-directory>... <output-jar>
 */
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ReproducibleJar {

	private static final byte[] MANIFEST = "Manifest-Version: 1.0\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

	private static final LocalDateTime TIMESTAMP = LocalDateTime.of(1980, 2, 1, 0, 0);

	private ReproducibleJar() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			throw new IllegalArgumentException("Expected <input-directory>... <output-jar>");
		}

		Path outputJar = Path.of(args[args.length - 1]);
		Path temporaryJar = outputJar.resolveSibling(outputJar.getFileName() + ".canonical");
		Files.createDirectories(outputJar.getParent());

		Map<String, Path> files = new HashMap<>();
		for (int index = 0; index < args.length - 1; index++) {
			Path inputDirectory = Path.of(args[index]);
			if (Files.isDirectory(inputDirectory)) {
				try (var paths = Files.walk(inputDirectory)) {
					paths.filter(Files::isRegularFile)
						.forEach(path -> files.put(entryName(inputDirectory, path), path));
				}
			}
		}
		var names = files.keySet().stream().sorted().toList();

		try (OutputStream output = Files.newOutputStream(temporaryJar);
				ZipOutputStream zip = new ZipOutputStream(output)) {
			writeEntry(zip, "META-INF/", new byte[0]);
			writeEntry(zip, "META-INF/MANIFEST.MF", MANIFEST);

			Set<String> directories = new LinkedHashSet<>();
			for (String name : names) {
				int slash = name.lastIndexOf('/');
				while (slash >= 0) {
					directories.add(name.substring(0, slash + 1));
					slash = name.lastIndexOf('/', slash - 1);
				}
			}
			directories.stream()
				.filter(directory -> !directory.equals("META-INF/"))
				.sorted()
				.forEach(directory -> writeUnchecked(zip, directory, new byte[0]));

			for (String name : names) {
				writeEntry(zip, name, Files.readAllBytes(files.get(name)));
			}
		}

		Files.move(temporaryJar, outputJar, StandardCopyOption.REPLACE_EXISTING);
	}

	private static String entryName(Path root, Path path) {
		return root.relativize(path).toString().replace('\\', '/');
	}

	private static void writeUnchecked(ZipOutputStream zip, String name, byte[] content) {
		try {
			writeEntry(zip, name, content);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Cannot write " + name, exception);
		}
	}

	private static void writeEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
		CRC32 crc = new CRC32();
		crc.update(content);
		ZipEntry entry = new ZipEntry(name);
		entry.setTimeLocal(TIMESTAMP);
		entry.setMethod(ZipEntry.STORED);
		entry.setSize(content.length);
		entry.setCompressedSize(content.length);
		entry.setCrc(crc.getValue());
		zip.putNextEntry(entry);
		zip.write(content);
		zip.closeEntry();
	}
}
