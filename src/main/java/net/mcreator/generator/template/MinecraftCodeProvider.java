/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or combining
 * it with JBoss Forge (or a modified version of that library), containing
 * parts covered by the terms of Eclipse Public License, the licensors of
 * this Program grant you additional permission to convey the resulting work.
 */

package net.mcreator.generator.template;

import net.mcreator.generator.GeneratorGradleCache;
import net.mcreator.io.zip.ZipIO;
import net.mcreator.java.ProjectJarManager;
import net.mcreator.util.TestUtil;
import net.mcreator.workspace.Workspace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fife.rsta.ac.java.buildpath.DirSourceLocation;
import org.fife.rsta.ac.java.buildpath.SourceLocation;
import org.fife.rsta.ac.java.rjc.ast.CompilationUnit;
import org.fife.rsta.ac.java.rjc.ast.TypeDeclaration;
import org.fife.rsta.ac.java.rjc.lexer.Scanner;
import org.fife.rsta.ac.java.rjc.parser.ASTFactory;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused") public class MinecraftCodeProvider {

	private static final Logger LOG = LogManager.getLogger(MinecraftCodeProvider.class);

	private final Workspace workspace;

	private final Map<String, String> CACHE = new HashMap<>();

	public MinecraftCodeProvider(@Nonnull Workspace workspace) {
		this.workspace = workspace;
	}

	private String readCode(@Nonnull String template) {
		return CACHE.computeIfAbsent(template, key -> {
			try {
				ProjectJarManager jarManager = workspace.getGenerator().getProjectJarManager();
				if (jarManager != null) {
					SourceLocation sourceLocation = jarManager.getSourceLocForClass(key);
					String sourcePath = key.replace(".", "/") + ".java";
					String code = null;
					if (sourceLocation instanceof DirSourceLocation) {
						// CFR and some Gradle tooling configurations expose sources as a directory.
						File sourceRoot = new File(sourceLocation.getLocationAsString());
						File sourceFile = new File(sourceRoot, sourcePath);
						if (sourceFile.isFile()) {
							LOG.debug("Reading Minecraft source {} from {}", key, sourceFile);
							code = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
						}
					} else if (sourceLocation != null) {
						code = ZipIO.readCodeInZip(new File(sourceLocation.getLocationAsString()), sourcePath);
					}
					if (code == null)
						code = readClasspathSource(jarManager, key, sourcePath);
					if (code == null)
						code = readIndexedDirectorySource(key, sourcePath);
					if (code == null)
						throw new IllegalStateException("No source location for " + key);

					return code;
				}
				return null;
			} catch (Exception e) {
				this.workspace.markFailingGradleDependencies();
				LOG.error("Failed to load code provider for {}", key, e);
				TestUtil.failIfTestingEnvironmentIgnoreIf("net.mcreator.integration.WorkspaceConvertersTest");
				return null;
			}
		});
	}

	private String readClasspathSource(ProjectJarManager jarManager, String key, String sourcePath) throws Exception {
		for (GeneratorGradleCache.ClasspathEntry classpathEntry : jarManager.getClasspath()) {
			String source = classpathEntry.getSrc(workspace);
			if (source == null || source.isBlank())
				continue;
			File sourceLocation = new File(source);
			if (sourceLocation.isDirectory()) {
				File sourceFile = new File(sourceLocation, sourcePath);
				if (sourceFile.isFile()) {
					LOG.debug("Reading classpath Minecraft source {} from {}", key, sourceFile);
					return Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
				}
			} else if (sourceLocation.isFile()) {
				String code = ZipIO.readCodeInZip(sourceLocation, sourcePath);
				if (code != null) {
					LOG.debug("Reading classpath Minecraft source {} from {}", key, sourceLocation);
					return code;
				}
			}
		}
		return null;
	}

	private String readIndexedDirectorySource(String key, String sourcePath) throws Exception {
		File sourceIndex = new File(workspace.getWorkspaceFolder(), "build/mcreator/minecraft-sources.txt");
		if (!sourceIndex.isFile())
			return null;
		for (String sourceRoot : Files.readAllLines(sourceIndex.toPath(), StandardCharsets.UTF_8)) {
			if (sourceRoot.isBlank())
				continue;
			File sourceFile = new File(sourceRoot, sourcePath);
			if (sourceFile.isFile()) {
				LOG.debug("Reading indexed Minecraft source {} from {}", key, sourceFile);
				return Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
			}
		}
		throw new java.io.FileNotFoundException("Source file not found in directories indexed by " + sourceIndex + ": "
				+ sourcePath);
	}


	public CodeString getCodeFor(@Nonnull String template, int lineFrom, int lineTo) {
		String code = readCode(template);
		if (code != null) {
			String[] lines = code.split("\\r?\\n");
			String[] usedLines = Arrays.copyOfRange(lines, lineFrom - 1, lineTo);
			return CodeString.of(String.join(System.lineSeparator(), usedLines));
		} else {
			TestUtil.failIfTestingEnvironmentIgnoreIf("net.mcreator.integration.WorkspaceConvertersTest");
			return CodeString.of("/* failed to load code for " + template + " */");
		}
	}

	public CodeString getMethod(@Nonnull String template, String method, String... params) {
		String code = readCode(template);
		if (code != null) {
			JavaClassSource classJavaSource = (JavaClassSource) Roaster.parse(code);
			MethodSource<?> methodSource = classJavaSource.getMethod(method, params);
			methodSource.removeAllAnnotations();
			return CodeString.of(methodSource.toString());
		} else {
			TestUtil.failIfTestingEnvironmentIgnoreIf("net.mcreator.integration.WorkspaceConvertersTest");
			return CodeString.of("/* failed to load code for " + template + " */");
		}
	}

	public CodeString getInnerClassBody(@Nonnull String template, String innerClass) {
		String code = readCode(template);
		if (code != null) {
			CompilationUnit cu = new ASTFactory().getCompilationUnit(template, new Scanner(new StringReader(code)));

			TypeDeclaration inner = null;

			TypeDeclaration mainClass = cu.getTypeDeclaration(0);
			for (int i = 0; i < mainClass.getChildTypeCount(); i++) {
				if (mainClass.getChildType(i).getName().equals(innerClass)) {
					inner = mainClass.getChildType(i);
					break;
				}
			}

			if (inner != null)
				return CodeString.of(code.substring(inner.getBodyStartOffset(), inner.getBodyEndOffset() + 1));
		}

		TestUtil.failIfTestingEnvironmentIgnoreIf("net.mcreator.integration.WorkspaceConvertersTest");
		return CodeString.of("/* failed to load code for " + template + " */");
	}

	public CodeString getClassBody(@Nonnull String template) {
		String code = readCode(template);
		if (code != null) {
			CompilationUnit cu = new ASTFactory().getCompilationUnit(template, new Scanner(new StringReader(code)));
			TypeDeclaration mainClass = cu.getTypeDeclaration(0);
			if (mainClass != null)
				return CodeString.of(code.substring(mainClass.getBodyStartOffset(), mainClass.getBodyEndOffset() + 1));
		}

		TestUtil.failIfTestingEnvironmentIgnoreIf("net.mcreator.integration.WorkspaceConvertersTest");
		return CodeString.of("/* failed to load code for " + template + " */");
	}

	public record CodeString(String value) implements CharSequence {

		public static CodeString of(String value) {
			return new CodeString(value);
		}

		@Override public int length() {
			return value.length();
		}

		@Override public char charAt(int index) {
			return value.charAt(index);
		}

		@Nonnull @Override public CharSequence subSequence(int start, int end) {
			return value.subSequence(start, end);
		}

		@Nonnull @Override public String toString() {
			return value;
		}

		public CodeString replace(String old, String replacement) {
			if (!value.contains(old)) {
				TestUtil.failIfTestingEnvironmentIgnoreIf("net.mcreator.integration.WorkspaceConvertersTest");
				return this;
			}

			return new CodeString(value.replace(old, replacement));
		}

	}

}
