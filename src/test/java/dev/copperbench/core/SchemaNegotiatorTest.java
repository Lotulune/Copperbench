package dev.copperbench.core;

import com.google.gson.JsonObject;
import dev.copperbench.core.contract.SchemaNegotiator;
import dev.copperbench.core.contract.UiCore.Handshake;
import dev.copperbench.core.contract.UiCore.HandshakeResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SchemaNegotiatorTest {

	@Test void selectsHighestMutualVersionAndRejectsAnUntypedFallback() {
		SchemaNegotiator negotiator = new SchemaNegotiator(List.of("0.1", "1.0"));
		JsonObject client = new JsonObject();
		client.addProperty("id", "product_shell");
		client.addProperty("version", "0.1.0");
		UUID compatibleId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa31");
		UUID incompatibleId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa32");

		HandshakeResult compatible = negotiator.negotiate(
				new Handshake("handshake", compatibleId, List.of("0.1", "1.0"), client));
		HandshakeResult incompatible = negotiator.negotiate(
				new Handshake("handshake", incompatibleId, List.of("2.0"), client));

		assertEquals("compatible", compatible.status());
		assertEquals("1.0", compatible.selectedSchemaVersion());
		assertEquals("incompatible", incompatible.status());
		assertNull(incompatible.selectedSchemaVersion());
		assertEquals("UI_CORE_SCHEMA_INCOMPATIBLE", incompatible.diagnostics().getFirst().code());
	}
}
