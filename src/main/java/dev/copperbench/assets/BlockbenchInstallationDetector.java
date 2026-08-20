package dev.copperbench.assets;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.W32APIOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Reads Blockbench installation metadata without launching the Electron application. */
public final class BlockbenchInstallationDetector {
	private final VersionReader versions;

	public BlockbenchInstallationDetector() {
		this(BlockbenchInstallationDetector::readWindowsFileVersion);
	}

	BlockbenchInstallationDetector(VersionReader versions) {
		this.versions = Objects.requireNonNull(versions);
	}

	public Installation detect(Path executable) {
		if (executable == null || !Files.isRegularFile(executable))
			return new Installation(State.UNAVAILABLE, executable, null, "BLOCKBENCH_NOT_CONFIGURED");
		String version = versions.read(executable);
		if (version == null)
			return new Installation(State.UNKNOWN_VERSION, executable, null, "BLOCKBENCH_VERSION_UNAVAILABLE");
		int separator = version.indexOf('.');
		int major;
		try {
			major = Integer.parseInt(separator < 0 ? version : version.substring(0, separator));
		} catch (NumberFormatException exception) {
			return new Installation(State.UNKNOWN_VERSION, executable, version, "BLOCKBENCH_VERSION_UNAVAILABLE");
		}
		if (major < 4)
			return new Installation(State.INCOMPATIBLE, executable, version, "BLOCKBENCH_VERSION_UNSUPPORTED");
		return new Installation(State.READY, executable, version, null);
	}

	private static String readWindowsFileVersion(Path executable) {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) return null;
		try {
			IntByReference ignored = new IntByReference();
			int size = VersionApi.INSTANCE.GetFileVersionInfoSizeW(executable.toString(), ignored);
			if (size <= 0) return null;
			Memory data = new Memory(size);
			if (!VersionApi.INSTANCE.GetFileVersionInfoW(executable.toString(), 0, size, data)) return null;
			PointerByReference value = new PointerByReference();
			IntByReference length = new IntByReference();
			if (!VersionApi.INSTANCE.VerQueryValueW(data, "\\", value, length) || length.getValue() < 52) return null;
			FixedFileInfo info = new FixedFileInfo(value.getValue());
			int major = info.fileVersionMs >>> 16;
			int minor = info.fileVersionMs & 0xffff;
			int patch = info.fileVersionLs >>> 16;
			return major + "." + minor + "." + patch;
		} catch (UnsatisfiedLinkError | RuntimeException exception) {
			return null;
		}
	}

	public enum State { UNAVAILABLE, UNKNOWN_VERSION, INCOMPATIBLE, READY }

	public record Installation(State state, Path executable, String version, String diagnosticCode) {
	}

	@FunctionalInterface interface VersionReader {
		String read(Path executable);
	}

	private interface VersionApi extends Library {
		VersionApi INSTANCE = Native.load("version", VersionApi.class, W32APIOptions.UNICODE_OPTIONS);
		int GetFileVersionInfoSizeW(String fileName, IntByReference handle);
		boolean GetFileVersionInfoW(String fileName, int handle, int length, Pointer data);
		boolean VerQueryValueW(Pointer block, String subBlock, PointerByReference buffer, IntByReference length);
	}

	@Structure.FieldOrder({ "signature", "structVersion", "fileVersionMs", "fileVersionLs", "productVersionMs",
			"productVersionLs", "fileFlagsMask", "fileFlags", "fileOs", "fileType", "fileSubtype", "fileDateMs",
			"fileDateLs" })
	public static final class FixedFileInfo extends Structure {
		public int signature;
		public int structVersion;
		public int fileVersionMs;
		public int fileVersionLs;
		public int productVersionMs;
		public int productVersionLs;
		public int fileFlagsMask;
		public int fileFlags;
		public int fileOs;
		public int fileType;
		public int fileSubtype;
		public int fileDateMs;
		public int fileDateLs;

		FixedFileInfo(Pointer pointer) {
			super(pointer);
			read();
		}
	}
}
