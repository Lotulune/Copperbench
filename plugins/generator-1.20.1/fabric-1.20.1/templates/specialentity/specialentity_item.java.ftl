<#-- @formatter:off -->
package ${package}.item;

public class ${name}Item extends Item {
	public ${name}Item() {
		super(new Item.Properties().stacksTo(1)<#if data.rarity != "COMMON">.rarity(Rarity.${data.rarity})</#if>);
	}

	@Override public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
		ItemStack itemstack = player.getItemInHand(hand);
		BlockHitResult hitResult = getPlayerPOVHitResult(world, player, ClipContext.Fluid.ANY);
		if (hitResult.getType() == HitResult.Type.MISS)
			return InteractionResultHolder.pass(itemstack);

		if (hitResult.getType() == HitResult.Type.BLOCK) {
			Entity entity = ${JavaModName}Entities.${REGISTRYNAME}.create(world);
			if (entity == null)
				return InteractionResultHolder.fail(itemstack);

			Vec3 position = hitResult.getLocation();
			entity.setPos(position.x, position.y, position.z);
			entity.setYRot(player.getYRot());
			if (!world.noCollision(entity, entity.getBoundingBox()))
				return InteractionResultHolder.fail(itemstack);

			if (!world.isClientSide()) {
				world.addFreshEntity(entity);
				world.gameEvent(player, GameEvent.ENTITY_PLACE, position);
				if (!player.getAbilities().instabuild)
					itemstack.shrink(1);
			}
			player.awardStat(Stats.ITEM_USED.get(this));
			return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
		}

		return InteractionResultHolder.pass(itemstack);
	}
}
<#-- @formatter:on -->
