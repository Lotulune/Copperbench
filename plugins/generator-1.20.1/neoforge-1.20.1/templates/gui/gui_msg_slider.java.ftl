<#-- @formatter:off -->
<#include "../procedures.java.ftl">

package ${package}.network;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD) public class ${name}SliderMessage {
	private final int sliderID, x, y, z;
	private final double value;
	public ${name}SliderMessage(int sliderID, int x, int y, int z, double value) {
		this.sliderID = sliderID; this.x = x; this.y = y; this.z = z; this.value = value;
	}
	public ${name}SliderMessage(FriendlyByteBuf buffer) {
		this(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readDouble());
	}
	public static void buffer(${name}SliderMessage message, FriendlyByteBuf buffer) {
		buffer.writeInt(message.sliderID); buffer.writeInt(message.x); buffer.writeInt(message.y); buffer.writeInt(message.z); buffer.writeDouble(message.value);
	}
	public static void handler(${name}SliderMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context context = contextSupplier.get();
		context.enqueueWork(() -> {
			Player entity = context.getSender();
			if (entity != null) handleSliderAction(entity, message.sliderID, message.x, message.y, message.z, message.value);
		});
		context.setPacketHandled(true);
	}
	public static void handleSliderAction(Player entity, int sliderID, int x, int y, int z, double value) {
		Level world = entity.level();
		if (!world.hasChunkAt(new BlockPos(x, y, z))) return;
		<#assign slid = 0>
		<#list data.getComponentsOfType("Slider") as component>
			<#if hasProcedure(component.whenSliderMoves)>
			if (sliderID == ${slid}) {
				<@procedureOBJToCode component.whenSliderMoves/>
			}
			</#if>
			<#assign slid += 1>
		</#list>
	}
	@SubscribeEvent public static void registerMessage(FMLCommonSetupEvent event) {
		${JavaModName}.addNetworkMessage(${name}SliderMessage.class, ${name}SliderMessage::buffer, ${name}SliderMessage::new, ${name}SliderMessage::handler);
	}
}
<#-- @formatter:on -->
