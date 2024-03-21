package frc.robot;

import edu.wpi.first.wpilibj.TimedRobot;
import frc.robot.Utils.Tests.BezierTest;
import frc.robot.Utils.Tests.SimpleRobotTest;

public class MyRobotSimulation extends TimedRobot {
    private final SimpleRobotTest robotTest = new BezierTest();

    @Override
    public void simulationInit() {
        robotTest.testStart();
    }

    @Override
    public void simulationPeriodic() {
        robotTest.testPeriodic();
    }
}
