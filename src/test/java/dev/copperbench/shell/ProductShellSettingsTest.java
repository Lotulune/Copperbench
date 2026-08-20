package dev.copperbench.shell;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductShellSettingsTest {

	@AfterEach void clearProperty() {
		System.clearProperty(ProductShellSettings.PROPERTY);
	}

	@Test void testsLeaveTheShellOffUnlessExplicitlyEnabled() {
		System.clearProperty(ProductShellSettings.PROPERTY);
		assertFalse(ProductShellSettings.enabled());
	}

	@Test void packagedAndGradleRunsEnableTheShellWithTheProperty() {
		System.setProperty(ProductShellSettings.PROPERTY, "true");
		assertTrue(ProductShellSettings.enabled());
		System.setProperty(ProductShellSettings.PROPERTY, "false");
		assertFalse(ProductShellSettings.enabled());
	}
}
