package io.github.tmldude.blocksouls.bosses.client.palerider;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Wraith Steed: the Pale Rider's horse half on its own (same mesh, same texture layout). */
public class WraithSteedModel extends EntityModel<LivingEntityRenderState> {
	private final ModelPart neck;
	private final ModelPart tail;
	private final ModelPart frontLeftLeg;
	private final ModelPart frontRightLeg;
	private final ModelPart backLeftLeg;
	private final ModelPart backRightLeg;

	public WraithSteedModel(ModelPart root) {
		super(root);
		ModelPart horse = root.getChild("horse");
		neck = horse.getChild("neck");
		tail = horse.getChild("tail");
		frontLeftLeg = horse.getChild("front_left_leg");
		frontRightLeg = horse.getChild("front_right_leg");
		backLeftLeg = horse.getChild("back_left_leg");
		backRightLeg = horse.getChild("back_right_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PaleRiderModel.addHorse(mesh.getRoot());
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		PaleRiderModel.gallop(neck, tail, frontLeftLeg, frontRightLeg, backLeftLeg, backRightLeg, state.walkAnimationPos,
				Math.min(1f, state.walkAnimationSpeed), state.ageInTicks);
	}
}
