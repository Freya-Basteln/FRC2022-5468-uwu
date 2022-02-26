// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;
import java.util.function.Supplier;
import com.kauailabs.navx.frc.AHRS;
import edu.wpi.first.wpilibj.GenericHID;
import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.shuffleboard.BuiltInLayouts;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.commands.conveyor.ConveyorMO;
import frc.robot.commands.drivetrain.ArcadeDrive;
import frc.robot.commands.intake.DefaultIntake;
import frc.robot.commands.intake.FullAutoIntake;
import frc.robot.commands.intake.IntakeMO;
import frc.robot.commands.intake.LowerIntake;
import frc.robot.commands.shooter.ShooterMO;
import frc.robot.commands.shooter.FullAutoShooterAssembly;
import frc.robot.utilities.Functions;
import frc.robot.devices.ColorSensor;
import frc.robot.devices.LEDs.LEDCall;
import frc.robot.devices.LEDs.LEDRange;
import frc.robot.devices.LEDs.LEDs;
import frc.robot.devices.Lemonlight;
import frc.robot.devices.Lemonlight.LEDModes;
import frc.robot.devices.LidarV3;
import frc.robot.devices.PCM;
import frc.robot.devices.PDP;
import frc.robot.oi.drivers.ControllerDriver;
import frc.robot.oi.drivers.JoystickDriver;
import frc.robot.oi.drivers.LaunchpadDriver;
import frc.robot.oi.drivers.ShuffleboardDriver;
import frc.robot.subsystems.Climb;
import frc.robot.subsystems.Conveyor;
import frc.robot.subsystems.Drivetrain;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.Shooter;
import frc.robot.utilities.ChangeRateLimiter;
import frc.robot.utilities.lists.Colors;
import frc.robot.utilities.lists.LEDPriorities;
import frc.robot.utilities.lists.Ports;
import frc.robot.utilities.lists.StatusPriorities;

/**
 * This class is where the bulk of the robot should be declared. Since Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in the {@link Robot}
 * periodic methods (other than the scheduler calls). Instead, the structure of the robot (including
 * subsystems, commands, and button mappings) should be declared here.
 */
public class RobotContainer {

    private final CommandScheduler scheduler;
    private final ControllerDriver controller1;
    private final LaunchpadDriver launchpad;
    private final JoystickDriver joystick;

    // Subsystems
    private final Drivetrain drivetrain;
    private final Shooter shooter;
    private final Conveyor conveyor;
    private final Intake intake;
    private final Climb climb;

    private final Lemonlight targetingLimelight, ballDetectionLimelight;
    private final PDP pdp;
    private final PCM pcm;
    private final AHRS gyro;
    
    //private final ColorSensor colorSensor;
    //private final LidarV3 lidarV3;
    private Supplier<Command> fullAutoShooterAssembly;
    private Supplier<Command> fullAutoIntake;
    private final Command teleInit;
    private final Command autoInit;
    private Command auto;

    /**
     * The container for the robot. Contains subsystems, OI devices, and commands.
     */
    public RobotContainer() {

        scheduler = CommandScheduler.getInstance();
        controller1 = new ControllerDriver(Ports.XBOX_PORT);
        launchpad = new LaunchpadDriver(Ports.LAUNCHPAD_PORT);
        joystick = new JoystickDriver(Ports.JOYSTICK_PORT);
        pdp = new PDP(Ports.PDP);
        pcm = new PCM(Ports.PCM_1);
        //colorSensor = new ColorSensor();
        //lidarV3 = new LidarV3();

        new LEDCall("disabled", LEDPriorities.ON, LEDRange.All).solid(Colors.DIM_GREEN).activate();
        ShuffleboardDriver.statusDisplay.addStatus(
            "default", "robot on", Colors.WHITE, StatusPriorities.ON);

        gyro = new AHRS();
        targetingLimelight = new Lemonlight("limelight");
        // TODO: need to ensure that this name is set on the limelight as well.
        ballDetectionLimelight = new Lemonlight("balldetect");

        // Init Subsystems
        drivetrain = new Drivetrain(gyro);
        shooter = new Shooter();
        conveyor = new Conveyor(null, null);
        intake = new Intake();
        climb = new Climb();
        fullAutoShooterAssembly =() -> new FullAutoShooterAssembly(shooter, conveyor, drivetrain, targetingLimelight);
        fullAutoIntake = () -> new FullAutoIntake(drivetrain, ballDetectionLimelight);
        autoInit = new SequentialCommandGroup(
                new InstantCommand(
                        () -> ShuffleboardDriver.statusDisplay.addStatus(
                            "auto",
                            "robot in auto",
                            Colors.TEAM,
                            StatusPriorities.ENABLED)),
                new InstantCommand(drivetrain::highGear),
                new InstantCommand(intake::lowerIntake),
                new InstantCommand(() -> {
                    launchpad.bigLEDRed.set(false);
                    launchpad.bigLEDGreen.set(true);
                }),
                new InstantCommand(climb::zeroClimb));
                

        teleInit =
            new SequentialCommandGroup(
                new InstantCommand(
                    () -> {
                        // simulated robots don't have joysticks
                        if (RobotBase.isReal()) {
                            if (!controller1.isConnected()
                                || !launchpad.isConnected()
                                || !joystick.isConnected()
                            ) {
                                System.out.println(
                                    "not enough joysticks connected,"
                                        + "please make sure the xbox controller,launchpad,"
                                        + "and joystick are connected to the driver-station");
                                throw (new RuntimeException("not enough joysticks connected"));
                            }
                            
                            if (!controller1.isXboxController()) {
                                System.out.println("controller 0 is not the xbox controller");
                                throw (new RuntimeException("incorrect joystick in port 0"));
                            }
                        }
                    }),
                new InstantCommand(() -> pcm.enableCompressorDigital()),
                new InstantCommand(() -> ShuffleboardDriver.statusDisplay.removeStatus("auto")),
                new InstantCommand(
                        () -> ShuffleboardDriver.statusDisplay.addStatus(
                            "enabled",
                            "robot enabled",
                            Colors.TEAM,
                            StatusPriorities.ENABLED)),
                new InstantCommand(() -> joystick.reEnableJoystickCalibrationCheck()),
                new InstantCommand(drivetrain::highGear),
                // new InstantCommand(() -> targetingLimelight.setLEDMode(LEDModes.FORCE_OFF)),
                // new InstantCommand(() -> ballDetectionLimelight.setLEDMode(LEDModes.FORCE_OFF)),
                new InstantCommand(() -> {
                    launchpad.bigLEDRed.set(false);
                    launchpad.bigLEDGreen.set(true);
                }));

        // Configure the button bindings
        setDefaultCommands();
        configureButtonBindings();
        setLedButtons();

        // Init Telemetry
        initTelemetry();
    }

    private void setLedButtons() {
        launchpad.buttonC.booleanSupplierBind(shooter::getHoodPos);
    }

    private void setDefaultCommands() {
        // drive by controller
        drivetrain.setDefaultCommand(new ArcadeDrive(
            drivetrain,
            controller1.rightTrigger,
            controller1.leftTrigger,
            controller1.leftX));

        // intake.setDefaultCommand(new DefaultIntake(intake, conveyor));
        shooter.setDefaultCommand(new ShooterMO(shooter, joystick.axisZ, launchpad.buttonC));
    }

    /**
     * Use this method to define your button->command mappings. Buttons can be created by
     * instantiating a {@link GenericHID} or one of its subclasses ({@link
     * edu.wpi.first.wpilibj.Joystick} or {@link XboxController}), and then passing it to a {@link
     * edu.wpi.first.wpilibj2.command.button.JoystickButton}.
     */
    private void configureButtonBindings() {
        controller1.rightBumper.whenReleased(new InstantCommand(() -> drivetrain.toggleShift()));

        // MOs
        launchpad.buttonB.whileHeld(new ConveyorMO(conveyor, joystick.axisY, joystick.button2, joystick.button3));
    }

    /**
     * Use this method to init all the subsystems' telemetry stuff.
     */
    private void initTelemetry() {
        SmartDashboard.putData("PDP", pdp);
        SmartDashboard.putData("PCM", pcm);
        SmartDashboard.putData("Drivetrain", drivetrain);
        // SmartDashboard.putData("Lemonlight", targetingLimelight);
        // SmartDashboard.putData("Lemonlight", ballDetectionLimelight);
        SmartDashboard.putData("Shooter", shooter);
        SmartDashboard.putData("Conveyor", conveyor);
        // SmartDashboard.putData("Intake", intake);
        // SmartDashboard.putData("Color Sensor", colorSensor);
        // SmartDashboard.putData("LidarV3", lidarV3);
    }

    /**
     * runs when the robot gets disabled.
     */
    public void disabledInit() {
        LEDs.getInstance().removeAllCalls();
        new LEDCall("disabled", LEDPriorities.ON, LEDRange.All).solid(Colors.DIM_GREEN).activate();
        ShuffleboardDriver.statusDisplay.removeStatus("enabled");
        ChangeRateLimiter.resetAllChangeRateLimiters();
    }

    /**
     * runs when the robot is powered on.
     */
    public void robotInit() {
        ShuffleboardDriver.init();
        //sets up all the splines so we dont need to spend lots of time
        //turning the json files into trajectorys when we want to run them
        //TODO make this run faster somehow
        String ball1 = "paths\1stBlue.path";
        String ball2 = "paths\2ndBlue.path";
        String ball3 = "paths\3rdBlue.path";

        try {
            Command fball1 = Functions.splineCommandFromFile(drivetrain, ball1);
            Command fball2 = Functions.splineCommandFromFile(drivetrain, ball2);
            Command fball3 = Functions.splineCommandFromFile(drivetrain, ball3);
            //possible 4 ball auto
            auto = new SequentialCommandGroup(
                autoInit,
                fball1, 
                fullAutoShooterAssembly.get(),
                fullAutoIntake.get(),
                fullAutoShooterAssembly.get(),
                fullAutoIntake.get(),
                fullAutoShooterAssembly.get()
            );
        
        }catch (Exception e){
            System.out.println("An error occured when making autoInit: " + e);
        }

    }
    
    /**
     * runs once every ~20ms when in teleop.
     */
    public void teleopPeriodic() {
    }

    /**
     * runs when robot is inited to teleop.
     */
    public void teleopInit() {
        scheduler.schedule(teleInit);
    }

    /**
     * Use this to pass the autonomous command to the main {@link Robot} class.
     *
     * @return the command to run in autonomous
     */
    public Command getAutonomousCommand() {
        // An ExampleCommand will run in autonomous
        return null;
    }
}
