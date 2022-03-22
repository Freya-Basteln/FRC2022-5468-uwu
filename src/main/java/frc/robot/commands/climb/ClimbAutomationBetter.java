// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.commands.climb;

import java.util.function.Supplier;

import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.WaitCommand;
import frc.robot.commands.climb.climbAutomationSteps.AutoAlign;
import frc.robot.commands.climb.climbAutomationSteps.CycleArms;
import frc.robot.commands.climb.climbAutomationSteps.MoveArms;
import frc.robot.subsystems.Climb;
import frc.robot.subsystems.Drivetrain;
// NOTE:  Consider using this command inline, rather than writing a subclass.  For more
// information, see:
// https://docs.wpilib.org/en/stable/docs/software/commandbased/convenience-features.html4
/**
 * climb command to go automatically.
 */

public class ClimbAutomationBetter extends StatefullSequentalCommandGroup {
    
    private Climb climb;
    private Drivetrain drivetrain;
    /** Creates a new climbCommand.
     *
     * @param drivetrain needs a drivetrain for alignment
     * @param climb needs a climb subsystem to work
    */

    public ClimbAutomationBetter(Drivetrain drivetrain, Climb climb) {
        this.climb = climb;
        this.drivetrain = drivetrain;
        AutoAlign autoAlign = new AutoAlign(climb, drivetrain);
        Supplier<CycleArms> cycleArms = () -> new CycleArms(climb, climb.FORWARD_LIMIT);
        Supplier<MoveArms> moveArms = () -> new MoveArms(climb, climb.BACK_LIMIT);
        // Add your commands in the addCommands() call, e.g.
        // addCommands(new FooCommand(), new BarCommand());
        //TODO Tune WaitCommand timings
        addCommands(
            new InstantCommand(() -> climb.setDetachPos(true)),
            new MoveArms(climb, -140),
            autoAlign,
            new MoveArms(climb, climb.FORWARD_LIMIT),
            new InstantCommand(() -> climb.setDetachPos(false)),
            new WaitCommand(.1),
            new InstantCommand(() -> climb.setPivotPos(true)),
            new WaitCommand(.2),
            moveArms.get(),
            new InstantCommand(()-> climb.setPivotPos(false)),
            new WaitCommand(.5),
            cycleArms.get(),
            new InstantCommand(() -> climb.setDetachPos(false)),
            new WaitCommand(.1),
            new InstantCommand(() -> climb.setPivotPos(true)),
            new WaitCommand(.2),
            moveArms.get(),
            new InstantCommand(()-> climb.setPivotPos(false)),
            new WaitCommand(.5),
            new CycleArms(climb, -50)
            );
    
        
    }
}
