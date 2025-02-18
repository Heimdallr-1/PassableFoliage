package snownee.passablefoliage;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class PassableFoliage {

	public static final String ID = "passablefoliage";

	public static boolean enchantmentEnabled;
	public static ThreadLocal<Boolean> suppressPassableCheck = ThreadLocal.withInitial(() -> false);

	public static void onEntityCollidedWithLeaves(Level world, BlockPos pos, BlockState blockState, Entity entity) {
		if (!(entity instanceof LivingEntity livingEntity)) {
			return;
		}

		if (entity instanceof Player player) {
			if (player.isCreative() && player.getAbilities().flying) {
				return;
			}
		}

		if (!entity.isPassenger()) {
			setSuppressPassableCheck(true);
			if (!entity.isColliding(pos, blockState)) {
				setSuppressPassableCheck(false);
				return;
			}
			setSuppressPassableCheck(false);
		}

		if (!PassableFoliageCommonConfig.soundsPlayerOnly || entity instanceof Player) {
			if (blockState.is(BlockTags.LEAVES)) {
				// play a sound when an entity falls into leaves; do this before altering motion
				if (livingEntity.fallDistance > 3f) {
					entity.playSound(
							SoundEvents.GRASS_BREAK,
							SoundType.GRASS.getVolume() * 0.6f * PassableFoliageCommonConfig.soundVolume,
							SoundType.GRASS.getPitch() * 0.65f);
				}
				// play a sound when an entity is moving through leaves (only play sound every 6 ticks as to not flood sound events)
				else if (world.getGameTime() % 6 == 0) {
					double motion = entity.getDeltaMovement().lengthSqr();
					if (motion > 5e-7) {
						entity.playSound(
								SoundEvents.GRASS_HIT,
								SoundType.GRASS.getVolume() * 0.5f * PassableFoliageCommonConfig.soundVolume,
								SoundType.GRASS.getPitch() * 0.45f);
					}
				}
			}
		}

		float h = 1, v = 1;
		if ((PassableFoliageCommonConfig.alwaysLeafWalking || !hasLeafWalker(livingEntity)) && livingEntity.getDeltaMovement().y() <= 0) {
			v = PassableFoliageCommonConfig.speedReductionVertical;
			h = PassableFoliageCommonConfig.speedReductionHorizontal;
		}
		// reduce movement speed when inside of leaves, but allow players/mobs to jump out of them
		if (h < 1 || v < 1) {
			Vec3 newMotion = entity.getDeltaMovement().multiply(h, v, h);
			entity.setDeltaMovement(newMotion);
		}

		// modify falling damage when falling into leaves
		if (livingEntity.fallDistance > PassableFoliageCommonConfig.fallDamageThreshold) {
			livingEntity.fallDistance -= PassableFoliageCommonConfig.fallDamageThreshold;
			livingEntity.causeFallDamage(
					PassableFoliageCommonConfig.fallDamageThreshold,
					1 - PassableFoliageCommonConfig.fallDamageReduction,
					world.damageSources().fall());
		}

		// reset fallDistance
		if (livingEntity.fallDistance > 1f) {
			livingEntity.fallDistance = 1f;
		}

		// Riding a mob won't protect you; Process riders last
		if (entity.isVehicle()) {
			for (Entity ent : entity.getPassengers()) {
				onEntityCollidedWithLeaves(world, pos, blockState, ent);
			}
		}
	}

	public static boolean isPassable(BlockState state) {
		return ((PassableFoliageBlock) state.getBlock()).pfoliage$isPassable() && !suppressPassableCheck.get();
	}

	public static boolean hasLeafWalker(LivingEntity entity) {
		return PassableFoliageCommonConfig.alwaysLeafWalking || enchantmentEnabled && EnchantmentHelper.has(entity.getItemBySlot(
				EquipmentSlot.FEET), EnchantmentModule.LEAF_WALKER.get());
	}

	public static void setSuppressPassableCheck(boolean suppressPassableCheck) {
		PassableFoliage.suppressPassableCheck.set(suppressPassableCheck);
	}

}
