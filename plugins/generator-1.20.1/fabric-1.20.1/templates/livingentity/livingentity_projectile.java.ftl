<#-- @formatter:off -->
<#include "../mcitems.ftl">

package ${package}.entity;

public class ${name}EntityProjectile extends AbstractArrow implements ItemSupplier {

	public ${name}EntityProjectile(EntityType<? extends ${name}EntityProjectile> type, Level world) {
		super(type, world);
	}

	public ${name}EntityProjectile(EntityType<? extends ${name}EntityProjectile> type, double x, double y, double z, Level world) {
		super(type, x, y, z, world);
	}

	public ${name}EntityProjectile(EntityType<? extends ${name}EntityProjectile> type, LivingEntity entity, Level world) {
		super(type, entity, world);
	}

	@Override public ItemStack getItem() {
		return ${mappedMCItemToItemStackCode(data.rangedAttackItem, 1)};
	}

	@Override protected ItemStack getPickupItem() {
		return ${mappedMCItemToItemStackCode(data.rangedAttackItem, 1)};
	}

	@Override protected void doPostHurtEffects(LivingEntity livingEntity) {
		super.doPostHurtEffects(livingEntity);
		livingEntity.setArrowCount(livingEntity.getArrowCount() - 1);
	}
}
<#-- @formatter:on -->
