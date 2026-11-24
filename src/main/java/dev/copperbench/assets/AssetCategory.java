package dev.copperbench.assets;

import java.util.Locale;
import java.util.Set;

/** Stable categories exposed by the asset browser and UI-Core queries. */
public enum AssetCategory {
	MODEL,
	TEXTURE,
	ANIMATION,
	LANGUAGE,
	SOUND,
	RESOURCE_PACK,
	BLOCKSTATE,
	/** Structure templates ({@code .nbt}) under a data root. */
	STRUCTURE,
	OTHER;

	/** Folders under {@code textures/} that name what a texture is for. */
	private static final Set<String> TEXTURE_USAGES = Set.of("block", "item", "entity", "gui", "painting", "particle",
			"mob_effect", "environment", "misc", "models", "trims", "map", "font", "colormap", "effect");

	public static AssetCategory fromRelativePath(String relativePath) {
		String path = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
		String extension = extension(path);
		if (extension.equals(".nbt"))
			return STRUCTURE;
		if (extension.equals(".bbmodel") || path.startsWith("models/") || path.contains("/models/"))
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

	/**
	 * What a texture is for, from its first folder under {@code textures/}: block, item, entity, gui and the other
	 * vanilla texture folders, {@code other} for anything else, or {@code null} when the path is not under textures/.
	 */
	public static String textureUsage(String relativePath) {
		String path = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
		int start;
		if (path.startsWith("textures/")) start = "textures/".length();
		else if (path.contains("/textures/")) start = path.indexOf("/textures/") + "/textures/".length();
		else return null;
		String rest = path.substring(start);
		int slash = rest.indexOf('/');
		return slash > 0 && TEXTURE_USAGES.contains(rest.substring(0, slash)) ? rest.substring(0, slash) : "other";
	}

	private static String extension(String path) {
		int slash = path.lastIndexOf('/');
		int dot = path.lastIndexOf('.');
		return dot > slash ? path.substring(dot) : "";
	}
}
