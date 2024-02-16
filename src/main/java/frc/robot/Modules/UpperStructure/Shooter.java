package frc.robot.Modules.UpperStructure;

import frc.robot.Drivers.Motors.Motor;
import frc.robot.Modules.PositionReader.PositionEstimator;
import frc.robot.Modules.RobotModuleBase;
import frc.robot.Utils.ComputerVisionUtils.AprilTagReferredTarget;
import frc.robot.Utils.EasyShuffleBoard;
import frc.robot.Utils.MathUtils.LookUpTable;
import frc.robot.Utils.MathUtils.Rotation2D;
import frc.robot.Utils.MathUtils.Vector2D;
import frc.robot.Utils.MechanismControllers.EncoderMotorMechanism;
import frc.robot.Utils.MechanismControllers.FlyWheelSpeedController;
import frc.robot.Utils.RobotConfigReader;
import frc.robot.Utils.RobotModuleOperatorMarker;

import java.util.Arrays;

public class Shooter extends RobotModuleBase {
    public enum ShooterMode {
        DISABLED,
        SHOOT,
        AMPLIFY,
        SPECIFIED_RPM
    }

    private final EncoderMotorMechanism[] shooters;
    private final FlyWheelSpeedController[] speedControllers;
    private final AimingSystem aimingSystem;
    private final double encoderVelocityToRPM;
    private final double shooterReadyErrorBound;
    private final RobotConfigReader robotConfig;
    private double specifiedRPM;
    private ShooterMode currentMode;
    public Shooter(EncoderMotorMechanism[] shooters, RobotConfigReader robotConfig) {
        this(shooters, null, robotConfig);
    }
    public Shooter(EncoderMotorMechanism[] shooters, AimingSystem aimingSystem, RobotConfigReader robotConfig) {
        super("Shooter");
        this.shooters = shooters;
        this.aimingSystem = aimingSystem;
        this.robotConfig = robotConfig;
        super.motors.addAll(Arrays.asList(shooters));
        speedControllers = new FlyWheelSpeedController[shooters.length];
        for (int shooterID = 0; shooterID < shooters.length; shooterID++) {
            speedControllers[shooterID] = new FlyWheelSpeedController(
                    new FlyWheelSpeedController.FlyWheelSpeedControllerProfile(0,0,0,0,0,0)
            );
            shooters[shooterID].setController(speedControllers[shooterID]);
        }
        this.encoderVelocityToRPM = 60.0 / robotConfig.getConfig("shooter", "shooterMotorEncoderTicksPerRevolution");
        this.shooterReadyErrorBound = robotConfig.getConfig("shooter", "flyWheelSpeedErrorTolerance") * robotConfig.getConfig("shooter", "speedControllerMaximumSpeed");
    }

    /**
     * set the desired speed of the shooter, 0 for stop
     * this is for test only
     * @param desiredSpeedRPM the desired speed of the shooter, in rpm
     * */
    @Deprecated
    public void setDesiredSpeed(double desiredSpeedRPM, RobotModuleOperatorMarker operator) {
        if (!isOwner(operator)) return;

        this.specifiedRPM = desiredSpeedRPM;
    }

    public void setShooterMode(ShooterMode desiredMode, RobotModuleOperatorMarker operator) {
        if (!isOwner(operator)) return;

        this.currentMode = desiredMode;
    }

    public boolean shooterReady() {
        final double decidedRPM = decideRPM();
        if (decidedRPM == 0) return false;
        double maxErrorRPM = 0;
        for (EncoderMotorMechanism shooter:shooters)
            maxErrorRPM = Math.max(Math.abs(shooter.getEncoderVelocity() * encoderVelocityToRPM - decidedRPM), maxErrorRPM);
        System.out.println("shooter speed error (rpm): " + maxErrorRPM + ", tolerance: " + shooterReadyErrorBound);
        return maxErrorRPM < shooterReadyErrorBound;
    }

    @Override
    public void init() {
        for (EncoderMotorMechanism shooter:shooters) {
            shooter.setMotorZeroPowerBehavior(Motor.ZeroPowerBehavior.BRAKE, this);
            shooter.gainOwnerShip(this);
        }
        this.onReset();
        updateConfigs();
    }

    @Override
    protected void periodic(double dt) {
        final double desiredEncoderVelocity = decideRPM() / encoderVelocityToRPM;
        for (int shooterID = 0; shooterID < shooters.length; shooterID++) {
            speedControllers[shooterID].setDesiredSpeed(desiredEncoderVelocity);
            EasyShuffleBoard.putNumber("shooter", "motor " + shooterID + " actual speed", shooters[shooterID].getEncoderVelocity() * encoderVelocityToRPM);
        }

        EasyShuffleBoard.putNumber("shooter", "Shooter Desired RPM", decideRPM());

        for (EncoderMotorMechanism shooter : shooters)
            shooter.updateWithController(this);

        // System.out.println("<-- (shooter periodic) -->");
    }

    private double decideRPM() {
        // System.out.println("shooter current mode: " + currentMode.name());
        return switch (currentMode) {
            case SHOOT -> getShooterSpeedWithAimingSystem();
            case AMPLIFY -> amplifyingRPM;
            case SPECIFIED_RPM -> specifiedRPM;
            default -> idleRPM; // idle
        };
    }

    @Override
    public void onReset() {
        setDesiredSpeed(0, null);
        updateConfigs();
        for (EncoderMotorMechanism shooter : shooters)
            shooter.setMotorZeroPowerBehavior(Motor.ZeroPowerBehavior.RELAX, this);

        currentMode = ShooterMode.DISABLED;
    }

    @Override
    public void onDestroy() {

    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {
        for (EncoderMotorMechanism shooter:shooters)
            shooter.disableMotor(this);
    }

    /* TODO the following constants, move then to robotConfig and tune them */
    /** when the target is unseen */
    private static final double defaultShootingRPM = 6300;
    private static final double amplifyingRPM = 1200;
    private static final double idleRPM = -200;
    private static final double projectileSpeed = 10;
    private static final double shootingRange = 6;
    private static final LookUpTable shooterRPMToTargetDistanceLookUpTable = new LookUpTable(new double[] {1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5, 6, 7}, new double[] {5000, 5200, 5600, 5800, 5800, 6000, 6200, 6300, 6400, 6500});

    private static final double defaultShootingAngle = 2;

    /** the desired arm position for aiming, in degrees and in reference to the default shooting position of the arm, which is specified in the arm configs */
    private static final LookUpTable armPositionDegreesToTargetDistanceLookUpTable = new LookUpTable(new double[] {1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5, 6, 7}, new double[] {6.5, 0, -6, -8, -9, -10, -12, -14, -16, -17});
    @Override
    public void updateConfigs() {
        final FlyWheelSpeedController.FlyWheelSpeedControllerProfile speedControllerProfile = new FlyWheelSpeedController.FlyWheelSpeedControllerProfile(
                robotConfig.getConfig("shooter", "speedControllerProportionGain"),
                robotConfig.getConfig("shooter", "speedControllerFeedForwardGain"),
                robotConfig.getConfig("shooter", "speedControllerFrictionGain"),
                robotConfig.getConfig("shooter", "speedControllerFeedForwardDelay"),
                robotConfig.getConfig("shooter", "speedControllerMaximumSpeed") / this.encoderVelocityToRPM,
                robotConfig.getConfig("shooter", "speedControllerTimeNeededToAccelerateToMaxSpeed")
        );
        for (FlyWheelSpeedController speedController:speedControllers)
            speedController.setProfile(speedControllerProfile);
    }

    /**
     * gets the aiming angle from the aiming system, so that the arm knows where to go
     * @return the arm position to shoot, in radian and in reference to default shooting position specified in arm configs, positive direction is towards ground.
     * 0, which is the default position, will be returned if target unseen
     * */
    public double getArmPositionWithAimingSystem() {
        final Vector2D targetRelativePositionToRobot;
        if (aimingSystem == null
                || (targetRelativePositionToRobot = aimingSystem.getRelativePositionToTarget(projectileSpeed)) == null)
            return Math.toRadians(defaultShootingAngle);
        final double distanceToTarget = targetRelativePositionToRobot.getMagnitude();
        return Math.toRadians(armPositionDegreesToTargetDistanceLookUpTable.getYPrediction(distanceToTarget));
    }

    public boolean targetInRange() {
        final Vector2D targetRelativePositionToRobot;
        if (aimingSystem == null
                || (targetRelativePositionToRobot = aimingSystem.getRelativePositionToTarget(projectileSpeed)) == null)
            return false;
        return targetRelativePositionToRobot.getMagnitude() < shootingRange;
    }

    private double getShooterSpeedWithAimingSystem() {
        final Vector2D targetRelativePositionToRobot;
        if (aimingSystem == null
                || (targetRelativePositionToRobot = aimingSystem.getRelativePositionToTarget(projectileSpeed)) == null)
            return defaultShootingRPM;
        final double distanceToTarget = targetRelativePositionToRobot.getMagnitude();

        // System.out.println("shooter corresponding (RPM): " + shooterRPMToTargetDistanceLookUpTable.getYPrediction(distanceToTarget));
        EasyShuffleBoard.putNumber("shooter", "target distance", distanceToTarget);
        EasyShuffleBoard.putNumber("shooter", "shooter corresponding RPM",  shooterRPMToTargetDistanceLookUpTable.getYPrediction(distanceToTarget));
        return shooterRPMToTargetDistanceLookUpTable.getYPrediction(distanceToTarget);
    }

    public static final class AimingSystem {
        public final PositionEstimator chassisPositionEstimator;
        public final AprilTagReferredTarget referencingTool;
        public final long timeUnseenTolerance;

        /**
         * @param timeUnseenTolerance the amount of time, in ms, that we can accept the target to be gone
         * */
        public AimingSystem(PositionEstimator chassisPositionEstimator, AprilTagReferredTarget aprilTagReferredTarget, long timeUnseenTolerance) {
            this.chassisPositionEstimator = chassisPositionEstimator;
            this.referencingTool = aprilTagReferredTarget;
            this.timeUnseenTolerance = timeUnseenTolerance;
        }

        /**
         * calculate the target's relative position to the robot, ignore flight time
         * @return the relative position to target, in meters, and in reference to the robot; if not seen for too long, return null
         * */
        public Vector2D getRelativePositionToTarget() {
            return getRelativePositionToTarget(Double.POSITIVE_INFINITY);
        }
        /**
         * with the target position tracker and the position estimator, calculate the target's relative position to the robot
         * @param projectileSpeed the speed of the projectile, in m/s, this is to reduce the deviation due to flight time
         * @return the relative position to target, in meters, and in reference to the robot; if not seen for too long, return null
          */
        public Vector2D getRelativePositionToTarget(double projectileSpeed) {
            final Vector2D targetFieldPosition = referencingTool.getTargetFieldPositionWithAprilTags(timeUnseenTolerance);
            if (targetFieldPosition == null) return null;
            final double distanceToTarget = Vector2D.displacementToTarget(chassisPositionEstimator.getRobotPosition2D(), targetFieldPosition).getMagnitude(),
                    flightTime = distanceToTarget / projectileSpeed;
            final Vector2D chassisPositionAfterFlightTime = chassisPositionEstimator.getRobotPosition2D().addBy(chassisPositionEstimator.getRobotVelocity2D().multiplyBy(flightTime)),
                    targetPositionToRobot = Vector2D.displacementToTarget(chassisPositionAfterFlightTime, targetFieldPosition);
            return targetPositionToRobot.multiplyBy(
                    new Rotation2D(chassisPositionEstimator.getRobotRotation()).getReversal()
            );
        }
    }
}
