<#-- @formatter:off -->
package ${package}.network;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD) public class MenuStateUpdateMessage {
	private final int elementType;
	private final String name;
	private final Object elementState;

	public MenuStateUpdateMessage(int elementType, String name, Object elementState) {
		this.elementType = elementType; this.name = name; this.elementState = elementState;
	}
	public MenuStateUpdateMessage(FriendlyByteBuf buffer) {
		this.elementType = buffer.readInt();
		this.name = buffer.readUtf(256);
		if (elementType == 0) this.elementState = buffer.readUtf(8192);
		else if (elementType == 1) this.elementState = buffer.readBoolean();
		else if (elementType == 2) this.elementState = buffer.readDouble();
		else this.elementState = null;
	}
	public static void buffer(MenuStateUpdateMessage message, FriendlyByteBuf buffer) {
		buffer.writeInt(message.elementType); buffer.writeUtf(message.name, 256);
		if (message.elementType == 0) buffer.writeUtf((String) message.elementState, 8192);
		else if (message.elementType == 1) buffer.writeBoolean((Boolean) message.elementState);
		else if (message.elementType == 2 && message.elementState instanceof Number n) buffer.writeDouble(n.doubleValue());
	}
	public static void handler(MenuStateUpdateMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context context = contextSupplier.get();
		context.enqueueWork(() -> { });
		context.setPacketHandled(true);
	}
	@SubscribeEvent public static void registerMessage(FMLCommonSetupEvent event) {
		${JavaModName}.addNetworkMessage(MenuStateUpdateMessage.class, MenuStateUpdateMessage::buffer, MenuStateUpdateMessage::new, MenuStateUpdateMessage::handler);
	}
}
<#-- @formatter:on -->
