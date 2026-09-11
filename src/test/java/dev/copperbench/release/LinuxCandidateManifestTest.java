package dev.copperbench.release;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LinuxCandidateManifestTest {
	@Test void candidateManifestCarriesLinuxRuntimesWithoutClaimingFormalSupport() {
		var manifest = LinuxCandidateManifest.development();
		assertEquals("stage15-linux-candidate", manifest.get("kind").getAsString());
		assertEquals("development-not-certified", manifest.get("status").getAsString());
		assertFalse(manifest.get("formalSupportClaim").getAsBoolean());
		assertEquals("Ubuntu 24.04 LTS",
				manifest.getAsJsonObject("platform").get("certificationBaseline").getAsString());
		String sbom = manifest.getAsJsonObject("candidateInventory").toString();
		assertTrue(sbom.contains("candidate-inventory"));
		assertTrue(sbom.contains("\"path\":\"jdk\""));
		assertTrue(sbom.contains("\"path\":\"jdk21\""));
		assertTrue(sbom.contains("gradle-dists/gradle-9.7.0-bin"));
		assertTrue(sbom.contains("gradle-dists/gradle-9.6.1-bin"));
		assertTrue(sbom.contains("gradle-dists/gradle-8.8-bin"));
		assertFalse(sbom.contains("jdk/jbr25_win_64"));
		assertFalse(manifest.getAsJsonObject("prerequisites").get("systemJavaRequired").getAsBoolean());
	}
}
