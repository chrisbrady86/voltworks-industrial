package dev.voltworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.voltworks.block.entity.ItemPipeBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the items travelling through a pipe network. Each batch is drawn by the pipe that sent it, at the point
 * along its route matching the current (partial) game tick, so it glides smoothly through the glass.
 */
public class PipeRenderer implements BlockEntityRenderer<ItemPipeBlockEntity, PipeRenderer.State> {
	private static final float SCALE = 0.7F;

	public static class State extends BlockEntityRenderState {
		final List<ItemStackRenderState> items = new ArrayList<>();
		final List<float[]> offsets = new ArrayList<>();
		final List<Float> yaws = new ArrayList<>();
	}

	private final ItemModelResolver itemModelResolver;

	public PipeRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ItemPipeBlockEntity pipe, State state, float partialTick, Vec3 cameraPos,
			ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
		BlockEntityRenderer.super.extractRenderState(pipe, state, partialTick, cameraPos, crumbling);
		state.items.clear();
		state.offsets.clear();
		state.yaws.clear();
		Level level = pipe.getLevel();
		if (level == null) return;
		double time = level.getGameTime() + partialTick;
		BlockPos origin = pipe.getBlockPos();
		int seed = (int) origin.asLong();
		for (ItemPipeBlockEntity.Transit t : pipe.getTransits()) {
			if (time > t.arrival() + 1) continue; // delivered; waiting for the server's update
			double[] p = t.positionAt(time);
			ItemStackRenderState item = new ItemStackRenderState();
			itemModelResolver.updateForTopItem(item, t.stack(), ItemDisplayContext.FIXED, level, null, seed++);
			state.items.add(item);
			state.offsets.add(new float[]{(float) (p[0] - origin.getX()), (float) (p[1] - origin.getY()), (float) (p[2] - origin.getZ())});
			// Turn the item to face the camera, so flat items never show edge-on
			state.yaws.add((float) Math.toDegrees(Math.atan2(cameraPos.x - p[0], cameraPos.z - p[2])));
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		for (int i = 0; i < state.items.size(); i++) {
			ItemStackRenderState item = state.items.get(i);
			if (item.isEmpty()) continue;
			float[] o = state.offsets.get(i);
			pose.pushPose();
			pose.translate(o[0], o[1], o[2]);
			pose.rotateDegrees(Axis.YP, state.yaws.get(i));
			pose.scale(SCALE, SCALE, SCALE);
			item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
	}

	/** Items can travel far from the pipe that sent them, so don't cull it just because its own block is off screen. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}
}
