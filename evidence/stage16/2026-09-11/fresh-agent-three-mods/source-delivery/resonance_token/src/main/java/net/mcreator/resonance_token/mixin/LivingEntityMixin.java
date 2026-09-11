package net.mcreator.resonance_token.mixin;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;

import net.mcreator.resonance_token.event.LivingEntityEvents;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Shadow
	protected Player lastHurtByPlayer;
	@Shadow
	protected int lastHurtByPlayerTime;

	@Shadow
	protected boolean isAlwaysExperienceDropper() {
		return false;
	}

	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
	public void swing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
		ItemStack stack = ((LivingEntity) (Object) this).getItemInHand(hand);
		if (!stack.isEmpty()) {
		}
	}

	@Inject(method = "startUsingItem(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"))
	public void startUsingItem(InteractionHand hand, CallbackInfo ci) {
		LivingEntity entity = (LivingEntity) (Object) this;
		ItemStack stack = entity.getItemInHand(hand);
		if (!stack.isEmpty() && !entity.isUsingItem())
			LivingEntityEvents.START_USE_ITEM.invoker().onStartUseItem(entity, stack);
	}

	@Inject(method = "heal(F)V", at = @At("HEAD"), cancellable = true)
	public void heal(float amount, CallbackInfo ci) {
		if (!LivingEntityEvents.ENTITY_HEAL.invoker().onEntityHeal((LivingEntity) (Object) this, amount))
			ci.cancel();
	}

	@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	public void hurt(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.isDamageSourceBlocked(damageSource) && !LivingEntityEvents.ENTITY_BLOCK.invoker().onEntityBlock(self, damageSource, (double) amount))
			cir.setReturnValue(false);
	}

	@Inject(method = "dropExperience(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	public void dropExperience(Entity entity, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level() instanceof ServerLevel serverLevel && !self.wasExperienceConsumed()
				&& (this.isAlwaysExperienceDropper() || this.lastHurtByPlayerTime > 0 && self.shouldDropExperience() && serverLevel.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT))) {
			if (!LivingEntityEvents.ENTITY_DROP_XP.invoker().onEntityDropXp(self, this.lastHurtByPlayer, (double) self.getExperienceReward(serverLevel, entity)))
				ci.cancel();
		}
	}

	@Inject(method = "causeFallDamage(FFLnet/minecraft/world/damagesource/DamageSource;)Z", at = @At("HEAD"), cancellable = true)
	public void causeFallDamage(float distance, float multiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
		if (!LivingEntityEvents.ENTITY_FALL.invoker().onEntityFall((LivingEntity) (Object) this, (double) distance, (double) multiplier))
			cir.setReturnValue(false);
	}

	@Inject(method = "onItemPickup(Lnet/minecraft/world/entity/item/ItemEntity;)V", at = @At("HEAD"))
	public void onItemPickup(ItemEntity itemEntity, CallbackInfo ci) {
		LivingEntityEvents.ENTITY_PICKUP_ITEM.invoker().onEntityPickupItem(itemEntity.getOwner(), itemEntity.getItem());
	}

	@Inject(method = "jumpFromGround()V", at = @At("TAIL"))
	public void jumpFromGround(CallbackInfo ci) {
		LivingEntityEvents.ENTITY_JUMP.invoker().onEntityJump((LivingEntity) (Object) this);
	}

	@Inject(method = "releaseUsingItem()V", at = @At("HEAD"))
	public void releaseUsingItem(CallbackInfo ci) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (!entity.getUseItem().isEmpty())
			LivingEntityEvents.ENTITY_STOP_USING_ITEM.invoker().onStopUsingItem(entity, entity.getUseItem(), entity.getUseItemRemainingTicks());
	}

	@Inject(method = "tick()V", at = @At("TAIL"))
	public void tick(CallbackInfo ci) {
		LivingEntityEvents.END_ENTITY_TICK.invoker().onEndTick((LivingEntity) (Object) this);
	}
}