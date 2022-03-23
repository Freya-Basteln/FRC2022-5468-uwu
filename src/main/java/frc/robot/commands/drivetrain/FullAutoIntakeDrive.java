package frc.robot.commands.drivetrain;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.devices.Lemonlight;
import frc.robot.subsystems.Conveyor;
import frc.robot.subsystems.Drivetrain;
import frc.robot.utilities.Functions;
import frc.robot.utilities.lists.PIDValues;
import java.util.ArrayList;
import frc.robot.subsystems.Conveyor.ConveyorState;
/** TODO: 
 * continue moving if loses sight
 * end if conveyor gets new ball
 */ 
/**
 * Full auto intake mode.
 */
public class FullAutoIntakeDrive extends CommandBase {

    private static final double 
    DISTANCE_FROM_BALL = 0,
    DISTANCE_TO_CONTINUE = 5;
    private ConveyorState lastState;
    private ConveyorState thisState;
    private boolean changed;
    // subsystems
    private Drivetrain drivetrain;
    private String teamColor;
    // devices
    private Lemonlight limelight;
    private int loopCount;
    // PID controllers
    private PIDController movePID;
    private PIDController alignPID;
    private double oldDistance;
    // tracker variables
    private double limelightDistanceEstimate;
    private boolean limelightHasTarget;
    private double horizontalOffset;
    private long timeStart;
    private Conveyor conveyor;
    /**
     * Constructor.
     *
     * @param drivetrain The drivetrain subsystem
     * @param limelight The ball detection limelight
     * @param conveyor for states to check if ball was intaked (what is the past tense verb for intake? intook?)
     */

    public FullAutoIntakeDrive(Drivetrain drivetrain,
        Lemonlight limelight, Conveyor conveyor) {
        this.drivetrain = drivetrain;
        this.limelight = limelight;
        this.conveyor = conveyor;
        this.movePID = new PIDController(0.02, 0, 0);
        this.alignPID = new PIDController(0.005, 0, 0);

        // TODO - set these
        movePID.setTolerance(1, 1);
        movePID.setSetpoint(DISTANCE_FROM_BALL);
        alignPID.setTolerance(1, 1);
        alignPID.setSetpoint(0);

        addRequirements(drivetrain);
        teamColor = DriverStation.getAlliance().toString();
    }

    @Override
    public void initialize() {
        teamColor = DriverStation.getAlliance().toString();
        movePID.reset();
        alignPID.reset();
        oldDistance = 999;
        loopCount = 0;
        changed = false;
        lastState = conveyor.getBeltState();
    }

    @Override
    public void execute() {
        double distTest;
        thisState = conveyor.getBeltState();
        double color = -100;
        horizontalOffset = 0;
        ArrayList<double[]> limelightData = limelight.getCustomVisionDataReadable();
        limelightDistanceEstimate = 999;
        if (limelightData.size() < 1) {
            System.out.println("NO BALL");
            //drivetrain.stop();
            return;
        }
        for (double[] eachLimelight : limelightData) {
            if ((eachLimelight[0] == 0.0 && teamColor == "Red") || (eachLimelight[0] == 1.0 && teamColor == "Blue")) {
                distTest = Lemonlight.getLimelightDistanceEstimateIN(
                    Lemonlight.BALL_MOUNT_HEIGHT,
                    Lemonlight.BALL_MOUNT_ANGLE,
                    Lemonlight.BALL_TARGET_HEIGHT,
                    -eachLimelight[2]) + 19;
                if (limelightDistanceEstimate > distTest) {
                    color = eachLimelight[0];
                    limelightDistanceEstimate = distTest;
                    horizontalOffset = eachLimelight[1];
                }
            }

        }

        System.out.println("DIST: " + limelightDistanceEstimate + " offset: " + horizontalOffset + " COLOR: " + color);

        if (limelightDistanceEstimate < 999) {
            double alignPower = alignPID.calculate(horizontalOffset);
            oldDistance = limelightDistanceEstimate;
            double movePower = -Functions.clampDouble(movePID.calculate(limelightDistanceEstimate), 0.5, -0.5);
            drivetrain.setLeftMotorPower(movePower - alignPower);
            drivetrain.setRightMotorPower(movePower + alignPower);
        } 
        if (lastState != thisState) {
            lastState = thisState;
            changed = true;
            //drivetrain.stop();
        }
    }

    @Override
    public void end(boolean interrupted) {
        System.out.println("END");
        drivetrain.stop();
    }

    @Override
    public boolean isFinished() {
        return movePID.atSetpoint() || changed || (conveyor.getBeltState() != ConveyorState.NONE && conveyor.getIndexState() != ConveyorState.NONE);
    }
}
