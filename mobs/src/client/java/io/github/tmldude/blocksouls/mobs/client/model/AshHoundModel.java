package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.mobs.client.render.MobRenderState;

/** Lean, low hunter: long narrow body, long snout, thin legs, a ragged tail. Eyes glow (emissive layer). */
public class AshHoundModel extends EntityModel<MobRenderState> {
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart tail;
	private final ModelPart[] legs = new ModelPart[4];

	public AshHoundModel(ModelPart root) {
		super(root);
		head = root.getChild("head");
		body = root.getChild("body");
		tail = root.getChild("tail");
		String[] names = {"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg"};
		for (int i = 0; i < 4; i++) {
			legs[i] = root.getChild(names[i]);
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5f, -2.5f, -7, 5, 5, 14)
				.texOffs(38, 0).addBox(-1, -4, -6, 2, 2, 9), PartPose.offset(0, 13, 0));
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 19).addBox(-2.5f, -2.5f, -4, 5, 5, 5)
				.texOffs(20, 19).addBox(-1.5f, -0.5f, -8, 3, 3, 4), PartPose.offset(0, 11.5f, -7));
		head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(34, 19).addBox(-1, -3, -0.5f, 2, 3, 1),
				PartPose.offsetAndRotation(-1.7f, -2.5f, -1, -0.3f, 0, -0.2f));
		head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(34, 19).mirror().addBox(-1, -3, -0.5f, 2, 3, 1),
				PartPose.offsetAndRotation(1.7f, -2.5f, -1, -0.3f, 0, 0.2f));
		root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(40, 19).addBox(-1, 0, -1, 2, 9, 2),
				PartPose.offsetAndRotation(0, 11.5f, 7, 1.1f, 0, 0));
		float[][] legPos = {{-1.6f, -5}, {1.6f, -5}, {-1.6f, 5.5f}, {1.6f, 5.5f}};
		String[] names = {"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg"};
		for (int i = 0; i < 4; i++) {
			root.addOrReplaceChild(names[i], CubeListBuilder.create().texOffs(48, 0).mirror(i % 2 == 1).addBox(-1, 0, -1, 2, 9, 2),
					PartPose.offset(legPos[i][0], 15, legPos[i][1]));
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		head.yRot = state.yRot * Mth.DEG_TO_RAD;
		head.xRot = state.xRot * Mth.DEG_TO_RAD + (state.aggressive ? 0.2f : 0);
		// A galloping run: front and hind pairs in phase.
		float gallop = Mth.cos(pos * 0.9f) * 1.2f * speed;
		legs[0].xRot = gallop;
		legs[1].xRot = gallop * 0.8f;
		legs[2].xRot = -gallop;
		legs[3].xRot = -gallop * 0.8f;
		body.xRot = Mth.sin(pos * 0.9f) * 0.08f * speed;
		tail.yRot = Mth.sin(state.ageInTicks * 0.25f) * 0.25f;
		tail.xRot = 1.1f - speed * 0.5f;
		if (state.swing > 0) {
			head.xRot += Mth.sin(state.swing * Mth.PI) * 0.5f;
		}
	}
}
