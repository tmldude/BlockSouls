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

/** Low torso on four splayed, spider-like limbs, head thrust forward. */
public class CrawlerModel extends EntityModel<MobRenderState> {
	private final ModelPart head;
	private final ModelPart[] limbs = new ModelPart[4];

	public CrawlerModel(ModelPart root) {
		super(root);
		head = root.getChild("head");
		for (int i = 0; i < 4; i++) {
			limbs[i] = root.getChild("limb" + i);
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -3, -7, 8, 6, 14)
				.texOffs(44, 0).addBox(-2, -5, -4, 4, 2, 6), PartPose.offset(0, 16, 0));
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 20).addBox(-3.5f, -3, -6, 7, 6, 6)
				.texOffs(26, 20).addBox(-2.5f, 1, -7, 5, 2, 3), PartPose.offset(0, 15, -7));
		// Front pair, then back pair; odd indices are the left side.
		float[][] pivots = {{-4, 15, -5}, {4, 15, -5}, {-4, 15, 5}, {4, 15, 5}};
		for (int i = 0; i < 4; i++) {
			boolean left = i % 2 == 1;
			float yaw = (i < 2 ? -0.5f : 0.5f) * (left ? -1 : 1);
			root.addOrReplaceChild("limb" + i, CubeListBuilder.create().texOffs(0, 32).mirror(left).addBox(-1, 0, -1, 2, 13, 2),
					PartPose.offsetAndRotation(pivots[i][0], pivots[i][1], pivots[i][2], 0, yaw, left ? -0.85f : 0.85f));
		}
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(MobRenderState state) {
		super.setupAnim(state);
		head.yRot = state.yRot * Mth.DEG_TO_RAD;
		head.xRot = state.xRot * Mth.DEG_TO_RAD - (state.swing > 0 ? Mth.sin(state.swing * Mth.PI) * 0.6f : 0);
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		for (int i = 0; i < 4; i++) {
			// Diagonal pairs move together, like a scuttling insect.
			float phase = (i == 0 || i == 3) ? 0 : Mth.PI;
			limbs[i].yRot += Mth.cos(pos * 1.3f + phase) * 0.5f * speed;
			limbs[i].zRot += Math.abs(Mth.sin(pos * 0.65f + phase)) * 0.4f * speed * (i % 2 == 1 ? -1 : 1);
		}
	}
}
