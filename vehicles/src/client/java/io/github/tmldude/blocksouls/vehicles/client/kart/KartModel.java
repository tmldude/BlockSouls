package io.github.tmldude.blocksouls.vehicles.client.kart;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import io.github.tmldude.blocksouls.vehicles.VehicleRegistry;

/**
 * Low open kart: chassis, bumper, four seats, a roll bar, steering wheel, rear engine with an exhaust, and four
 * wheels that spin with speed (front ones steer). Model space: y = 24 is the ground, -z is the front, +x is
 * the kart's left. The wreck uses the same model with parts hidden or bent.
 */
public class KartModel extends EntityModel<KartRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(VehicleRegistry.id("kart"), "main");
	/** Wheel order: front-left, front-right, rear-left, rear-right. */
	private static final String[] WHEELS = {"wheel_front_left", "wheel_front_right", "wheel_rear_left", "wheel_rear_right"};

	private final ModelPart[] wheels = new ModelPart[WHEELS.length];
	private final ModelPart bumper;
	private final ModelPart rollBar;
	private final ModelPart steeringWheel;
	private final ModelPart engine;
	private final ModelPart seatRearLeft;
	private final ModelPart seatRearRight;

	public KartModel(ModelPart root) {
		super(root);
		for (int i = 0; i < WHEELS.length; i++) {
			wheels[i] = root.getChild(WHEELS[i]);
		}
		bumper = root.getChild("bumper");
		rollBar = root.getChild("roll_bar");
		steeringWheel = root.getChild("steering_wheel");
		engine = root.getChild("engine");
		seatRearLeft = root.getChild("seat_rear_left");
		seatRearRight = root.getChild("seat_rear_right");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("chassis", CubeListBuilder.create().texOffs(0, 0).addBox(-10, 18, -16, 20, 3, 32), PartPose.ZERO);
		root.addOrReplaceChild("bumper", CubeListBuilder.create().texOffs(0, 36).addBox(-8, 17, -19, 16, 4, 3), PartPose.ZERO);

		CubeListBuilder seat = CubeListBuilder.create()
				.texOffs(72, 36).addBox(-3.5f, -2, -3.5f, 7, 2, 7)
				.texOffs(100, 36).addBox(-3.5f, -9, 3.5f, 7, 7, 1);
		root.addOrReplaceChild("seat_front_left", seat, PartPose.offset(6, 18, -1));
		root.addOrReplaceChild("seat_front_right", seat, PartPose.offset(-6, 18, -1));
		root.addOrReplaceChild("seat_rear_left", seat, PartPose.offset(6, 18, 9));
		root.addOrReplaceChild("seat_rear_right", seat, PartPose.offset(-6, 18, 9));

		root.addOrReplaceChild("roll_bar", CubeListBuilder.create()
				.texOffs(24, 48).addBox(-10, -12, -0.5f, 1, 12, 1)
				.texOffs(24, 48).addBox(9, -12, -0.5f, 1, 12, 1)
				.texOffs(30, 48).addBox(-10, -13, -0.5f, 20, 1, 1), PartPose.offset(0, 18, 4.5f));
		root.addOrReplaceChild("steering_wheel", CubeListBuilder.create().texOffs(72, 48).addBox(-2, -2, -0.5f, 4, 4, 1),
				PartPose.offsetAndRotation(6, 12, -7, -0.6f, 0, 0));
		root.addOrReplaceChild("engine", CubeListBuilder.create()
				.texOffs(40, 36).addBox(-5, -6, -2, 10, 6, 5)
				.texOffs(84, 48).addBox(-7, -10, 0, 2, 8, 2), PartPose.offset(0, 18, 14));

		CubeListBuilder wheel = CubeListBuilder.create().texOffs(0, 48).addBox(-1.5f, -3.5f, -3.5f, 3, 7, 7);
		float[][] wheelPos = {{11.5f, -11}, {-11.5f, -11}, {11.5f, 11}, {-11.5f, 11}};
		for (int i = 0; i < WHEELS.length; i++) {
			root.addOrReplaceChild(WHEELS[i], wheel, PartPose.offset(wheelPos[i][0], 20.5f, wheelPos[i][1]));
		}
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(KartRenderState state) {
		super.setupAnim(state);
		for (int i = 0; i < wheels.length; i++) {
			wheels[i].xRot = state.wheelSpin;
			wheels[i].visible = i < state.wheels;
		}
		wheels[0].yRot = state.steer;
		wheels[1].yRot = state.steer;
		steeringWheel.zRot = -state.steer * 2;
		engine.visible = state.engine;

		// A wreck gets its bits back as scrap goes in: bumper, rear seats, then the roll bar straightens.
		bumper.visible = !state.wreck || state.scrap >= 0.25f;
		seatRearRight.visible = !state.wreck || state.scrap >= 0.5f;
		seatRearLeft.visible = !state.wreck || state.scrap >= 0.75f;
		if (state.wreck) {
			rollBar.zRot = 0.35f * (1 - state.scrap);
			rollBar.xRot = 0.2f * (1 - state.scrap);
			steeringWheel.zRot = 0.8f;
		}
	}
}
