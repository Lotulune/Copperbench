package dev.copperbench.generator;

/** Exact execution identity of a registered first-party Java generator. */
public interface JavaGeneratorProfile {

	/** @return the persisted, exact generator ID */
	String generatorId();

	/** @return the exact Minecraft version used by the backend */
	String minecraftVersion();

	/** @return the Java language release required by the generated workspace */
	int javaRelease();
}
