package dev.copperbench;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProductIdentityTest {

	@Test void distributionIdentityIsIndependentFromUpstreamBrand() {
		assertEquals("Copperbench", ProductIdentity.NAME);
		assertEquals("dev.copperbench.studio", ProductIdentity.ID);
		assertFalse(ProductIdentity.NAME.toLowerCase().contains("mcreator"));
		assertFalse(ProductIdentity.IMPLICIT_NETWORK_SERVICES_ENABLED);
	}
}
