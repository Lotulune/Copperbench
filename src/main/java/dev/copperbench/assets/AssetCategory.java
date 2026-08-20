package dev.copperbench.assets;

import java.util.Locale;

/** Stable categories exposed by the asset browser and UI-Core queries. */
public enum AssetCategory {
	MODEL,
	TEXTURE,
	ANIMATION,
	LANGUAGE,
	SOUND,
	RESOURCE_PACK,
	BLOCKSTATE,
	OTHER;

	public static AssetCategory fromRelativePath(String relativePath) {
		String path = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
		String extension = extension(path);
		if (extension.equals(".bbmodel") || path.contains("/models/"))
			return MODEL;
		if (path.contains("/textures/") || extension.equals(".png") || extension.equals(".jpg")
				|| extension.equals(".jpeg"))
			return TEXTURE;
		if (path.contains("/animations/") || path.endsWith(".animation.json"))
			return ANIMATION;
		if (path.contains("/lang/") || extension.equals(".lang"))
			return LANGUAGE;
		if (path.contains("/sounds/") || extension.equals(".ogg") || extension.equals(".wav"))
			return SOUND;
		if (path.contains("/blockstates/") || path.endsWith("/blockstates.json"))
			return BLOCKSTATE;
		if (extension.equals(".zip") || extension.equals(".mcmeta"))
			return RESOURCE_PACK;
		return OTHER;
	}

	private static String extension(String path) {
		int slash = path.lastIndexOf('/');
		int dot = path.lastIndexOf('.');
		return dot > slash ? path.substring(dot) : "";
	}
}
