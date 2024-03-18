package frc.robot.AutoStagePrograms;

import frc.robot.RobotCore;
import frc.robot.Utils.CommandSequenceGenerator;
import frc.robot.Utils.ComputerVisionUtils.AutoStageVisionAimBot;
import frc.robot.Utils.MathUtils.Rotation2D;
import frc.robot.Utils.SequentialCommandFactory;
import frc.robot.Utils.SequentialCommandSegment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RedAutoStageSixNoteUpperHalfTestRoute implements CommandSequenceGenerator {
    @Override
    public List<SequentialCommandSegment> getCommandSegments(RobotCore robotCore) {
        final List<SequentialCommandSegment> commandSegments = new ArrayList<>();
        final SequentialCommandFactory commandFactory = new SequentialCommandFactory(robotCore, SequentialCommandFactory.getRobotStartingPosition("first note and grab"), new Rotation2D(Math.toRadians(180)));
        final AutoStageVisionAimBot aimBot = new AutoStageVisionAimBot(robotCore, 6000);

        commandSegments.add(commandFactory.calibratePositionEstimator());

        /* shoot the preloaded and move to the second */
        commandSegments.addAll(Arrays.asList(commandFactory.followPathFacing(
                "first note and grab",
                FieldPositions.toActualRotation(new Rotation2D(Math.toRadians(180)))
        )));

        /* shoot second and move to third */
        commandSegments.addAll(Arrays.asList(commandFactory.followPathFacing(
                "shoot second and grab third",
                FieldPositions.toActualRotation(new Rotation2D(Math.toRadians(135)))
        )));

        /* shoot third and move to fourth */
        commandSegments.addAll(Arrays.asList(commandFactory.followPathFacing(
                "shoot third grab fourth",
                FieldPositions.toActualRotation(new Rotation2D(Math.toRadians(-150)))
        )));

        /* shoot fourth move to fifth */
        commandSegments.addAll(Arrays.asList(commandFactory.followPath(
                "shoot fourth move to fifth (half field)",
                new Rotation2D[] {
                        FieldPositions.toActualRotation(new Rotation2D(Math.toRadians(180))),
                        FieldPositions.toActualRotation(new Rotation2D(Math.toRadians(-135)))
                },
                ()->{}, ()->{}, ()->{}
        )));

        /* shoot fifth grab sixth */
        commandSegments.addAll(Arrays.asList(commandFactory.followPathFacing(
                "shoot fifth grab sixth (half field)",
                new Rotation2D(Math.toRadians(180))
        )));

        commandSegments.addAll(Arrays.asList(commandFactory.followPathFacing(
                "shoot sixth (half field)",
                new Rotation2D(Math.toRadians(180))
        )));

        return commandSegments;
    }
}