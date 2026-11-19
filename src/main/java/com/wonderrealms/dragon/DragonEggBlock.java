package com.wonderrealms.dragon;

import com.wonderrealms.WonderAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A dragon egg. Set it down and it cracks through four stages over a few minutes, then hatches a hatchling bonded
 * to the nearest player. Ember eggs glow faintly.
 */
public class DragonEggBlock extends Block {
	public static final IntegerProperty HATCH = IntegerProperty.create("hatch", 0, 3);
	private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);
	private static final int BOND_RANGE = 16;

	private final DragonElement element;

	public DragonEggBlock(DragonElement element, Properties properties) {
		super(properties);
		this.element = element;
		this.registerDefaultState(this.stateDefinition.any().setValue(HATCH, 0));
	}

	public DragonElement element() {
		return this.element;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HATCH);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int stage = state.getValue(HATCH);
		if (stage < 3) {
			level.setBlock(pos, state.setValue(HATCH, stage + 1), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS, 0.7F, 0.6F + random.nextFloat() * 0.2F);
			return;
		}
		this.hatch(level, pos);
	}

	private void hatch(ServerLevel level, BlockPos pos) {
		level.removeBlock(pos, false);
		level.playSound(null, pos, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 1F, 0.6F);
		level.sendParticles(this.element.breathParticle, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
		level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 10, 0.3, 0.2, 0.3, 0.02);

		DragonEntity dragon = Dragons.TYPES.get(this.element).create(level, EntitySpawnReason.BREEDING);
		if (dragon == null) {
			return;
		}
		dragon.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
		dragon.setGrowth(0F);
		dragon.setPersistenceRequired();
		Player nearest = level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, BOND_RANGE, false);
		if (nearest != null) {
			dragon.tame(nearest);
			if (nearest instanceof ServerPlayer player) {
				WonderAdvancements.award(player, "hatch_dragon");
			}
		}
		level.addFreshEntity(dragon);
	}
}
