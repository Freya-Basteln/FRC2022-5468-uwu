package frc.robot.commands.intake;

import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.commands.drivetrain.FullAutoIntakeDrive;
import frc.robot.devices.Lemonlight;
import frc.robot.subsystems.Drivetrain;
import frc.robot.subsystems.Intake;

/**
 * puts intake down and drives until a ball is found.
 */
public class FullAutoIntake extends SequentialCommandGroup {

    public FullAutoIntake(Drivetrain drivetrain, Intake intake, Lemonlight limelight) {
        addCommands(new LowerIntake(intake), new FullAutoIntakeDrive(drivetrain, limelight));
    }
    
}