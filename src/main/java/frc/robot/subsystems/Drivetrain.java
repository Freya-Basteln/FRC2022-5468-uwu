package frc.robot.subsystems;

import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.kinematics.DifferentialDriveKinematics;
import edu.wpi.first.math.kinematics.DifferentialDriveOdometry;
import edu.wpi.first.math.kinematics.DifferentialDriveWheelSpeeds;
import edu.wpi.first.math.trajectory.constraint.DifferentialDriveVoltageConstraint;
import edu.wpi.first.wpilibj.PneumaticsModuleType;
import edu.wpi.first.wpilibj.Solenoid;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.devices.LEDs.LEDCall;
import frc.robot.devices.LEDs.LEDRange;
import frc.robot.utilities.Functions;
import frc.robot.utilities.lists.Colors;
import frc.robot.utilities.lists.LEDPriorities;
import frc.robot.utilities.lists.Ports;

import java.util.function.BooleanSupplier;
import com.kauailabs.navx.frc.AHRS;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkMaxPIDController;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkMax.ControlType;
import com.revrobotics.CANSparkMax.IdleMode;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;

/**
 * Subsystem to control the drivetrain of the robot
 */
public class Drivetrain extends SubsystemBase {

	// TODO re tune/calculate all these
	public static final double 
		LowP = 2.29,
		LowI = 0.0,
		LowD = 0.0,
		LowKs = 0.177,
		LowKv = 1.55,
		LowKa = 0.133,
		HighP = 2.85,
		HighI = 0.0,
		HighD = 0.0,
		HighKs = 0.211,
		HighKv = 0.734,
		HighKa = 0.141,
		HighGearRatio = 9.1,
		LowGearRatio = 19.65,
		WheelRadiusInMeters = 0.0762,
		wheleCirconfranceInMeters = (2 * WheelRadiusInMeters) * Math.PI,
		MaxOutputVoltage = 11,
		DriveWidth = 0.7112;

	// left motors
	private CANSparkMax left = new CANSparkMax(Ports.LEFT_DRIVE_3, MotorType.kBrushless);
	private CANSparkMax leftMiddle = new CANSparkMax(Ports.LEFT_DRIVE_2, MotorType.kBrushless);
	private CANSparkMax leftBack = new CANSparkMax(Ports.LEFT_DRIVE_1, MotorType.kBrushless);

	// right motors
	private CANSparkMax right = new CANSparkMax(Ports.RIGHT_DRIVE_3, MotorType.kBrushless);
	private CANSparkMax rightMiddle = new CANSparkMax(Ports.RIGHT_DRIVE_2, MotorType.kBrushless);
	private CANSparkMax rightBack = new CANSparkMax(Ports.RIGHT_DRIVE_1, MotorType.kBrushless);

	// pid controllers
	private SparkMaxPIDController leftPID = left.getPIDController();
	private SparkMaxPIDController rightPID = right.getPIDController();

	// encoders
	private RelativeEncoder leftEncoder = left.getEncoder();
	private RelativeEncoder rightEncoder = right.getEncoder();

	private DifferentialDriveOdometry odometry;

	private AHRS gyro;

	public static DifferentialDriveKinematics DriveKinimatics = new DifferentialDriveKinematics(DriveWidth);

	public static SimpleMotorFeedforward HighFeedFoward = new SimpleMotorFeedforward(HighKs, HighKv, HighKa);

	public static SimpleMotorFeedforward LowFeedFoward = new SimpleMotorFeedforward(LowKs, LowKv, LowKa);

	public static DifferentialDriveVoltageConstraint HighVoltageConstraint = new DifferentialDriveVoltageConstraint(HighFeedFoward, DriveKinimatics, MaxOutputVoltage);

	public static DifferentialDriveVoltageConstraint LowVoltageConstraint = new DifferentialDriveVoltageConstraint(LowFeedFoward, DriveKinimatics, MaxOutputVoltage);

	private Solenoid shift;

	private boolean oldShift;

	private LEDCall lowShift = new LEDCall(LEDPriorities.lowGear, LEDRange.All).sine(Colors.Red);

	//for making robot distance consistant across shifts
	private double leftDistanceAcum = 0;
	private double rightDistanceAcum = 0;

	private Timer odemetryTime = new Timer();
	
	/**
	 * i am in PAIN wow this is BAD
	 * 
	 * @param gyro       odimetry is bad
	 * @param shiftState shifting was a mistake
	 *                   just be glad i am trying to limit my sins and am not
	 *                   passing in the whole shift object in and using fake
	 *                   callbacks from hell
	 */
	public Drivetrain(AHRS gyro) {

		this.gyro = gyro;

		shift = new Solenoid(Ports.PCM_1, PneumaticsModuleType.REVPH, Ports.SHIFT_SOLENOID_UP);

		odemetryTime.reset();
		odemetryTime.start();


		odometry = new DifferentialDriveOdometry(gyro.getRotation2d());

		// tells other two motors to follow the first
		leftMiddle.follow(left);
		leftBack.follow(left);

		rightMiddle.follow(right);
		rightBack.follow(right);

		// inverts right side
		left.setInverted(true);
		right.setInverted(false);

		// sets pid values
		zeroDistance();

		// pid for position
		leftPID.setP(0.05);
		leftPID.setI(0);
		leftPID.setD(0);
		leftPID.setOutputRange(-.25, .25);

		rightPID.setP(0.05);
		rightPID.setI(0);
		rightPID.setD(0);
		rightPID.setOutputRange(-.25, .25);

		// pid for velocity
		leftPID.setP(0.000278, 2);
		leftPID.setI(0, 2);
		leftPID.setD(0.0001, 2);

		rightPID.setP(0.000278, 2);
		rightPID.setI(0, 2);
		rightPID.setD(0.0001, 2);

		left.disableVoltageCompensation();
		right.disableVoltageCompensation();

		leftMiddle.disableVoltageCompensation();
		rightMiddle.disableVoltageCompensation();

		leftBack.disableVoltageCompensation();
		rightBack.disableVoltageCompensation();

		setClosedRampRate(0);
		setOpenRampRate(0);

		left.setSmartCurrentLimit(40);
		leftMiddle.setSmartCurrentLimit(40);
		leftBack.setSmartCurrentLimit(40);

		right.setSmartCurrentLimit(40);
		rightMiddle.setSmartCurrentLimit(40);
		rightBack.setSmartCurrentLimit(40);

		left.setIdleMode(IdleMode.kBrake);
		leftMiddle.setIdleMode(IdleMode.kBrake);
		leftBack.setIdleMode(IdleMode.kBrake);

		right.setIdleMode(IdleMode.kBrake);
		rightMiddle.setIdleMode(IdleMode.kBrake);
		rightBack.setIdleMode(IdleMode.kBrake);

	}


	public void highGear() {
		lowShift.cancel();
		oldShift = true;
		updateDistanceAcum();
		shift.set(true);
	}

	public void lowGear() {
		lowShift.activate();
		oldShift = false;
		updateDistanceAcum();
		shift.set(false);
	}

	private void updateDistanceAcum(){
		leftDistanceAcum += getLeftDistance();
		rightDistanceAcum += getRightDistance();
		zeroEncoders();
	}

	/**
	 * Gets the shift state
	 * 
	 * @return the shift state where true is high and false is low
	 */
	public boolean getShift() {
		return oldShift;
	}

	/**
	 * Sets the power of the left side of the drivetrain
	 * 
	 * @param power -1 - 1
	 */
	public void setLeftMotorPower(double power) {
		power = Functions.clampDouble(power, 1.0, -1.0);
		synchronized (left) {
			left.set(power);
		}
	}

	/**
	 * Sets the power of the right side of the drivetrain
	 * 
	 * @param power -1 - 1
	 */
	public void setRightMotorPower(double power) {
		power = Functions.clampDouble(power, 1.0, -1.0);
		synchronized (right) {
			right.set(power);
		}
	}

	public void setLeftMotorVolts(double volts) {
		synchronized (left) {
			left.setVoltage(volts);
		}
	}

	public void setRightMotorVolts(double volts) {
		synchronized (right) {
			right.setVoltage(volts);
		}
	}

	/**
	 * Sets the voltage of the motors
	 * 
	 * @param left  the right motor voltage
	 * @param right the left motor voltage
	 */
	public void setMotorVolts(double left, double right) {
		// System.out.println(String.format("left is: %f, right is %f", left, right));
		setRightMotorVolts(right);
		setLeftMotorVolts(left);
	}

	/**
	 * Sets the motor power based on desired meters per second
	 * 
	 * @param leftMS  the left motor MPS
	 * @param rightMS the right motor MPS
	 */
	public void setMotorTargetSpeed(double leftMS, double rightMS) {
		leftPID.setReference(MPStoRPM(leftMS), ControlType.kVelocity, 2);
		rightPID.setReference(MPStoRPM(rightMS), ControlType.kVelocity, 2);
	}

	/**
	 * Converts robot meters per second into motor rotations per minute
	 * 
	 * @param input the MPS to convert
	 * @return the corresponding RPM
	 */
	public double MPStoRPM(double input) {
		double out = input / WheelRadiusInMeters;
		out = out * 60;
		out = out * (2 * Math.PI);
		out = out * HighGearRatio;
		out /= 39.4784176044;
		return out;
	}

	/**
	 * Sets the target position of the left side of the drivetrain
	 * 
	 * @param position the target position in terms of motor rotations
	 */
	public synchronized void setLeftMotorTarget(double position) {
		leftPID.setReference(position, ControlType.kPosition);
	}

	/**
	 * Sets the target position of the right side of the drivetrain
	 * 
	 * @param position the target position in terms of motor rotations
	 */
	public synchronized void setRightMotorTarget(double position) {
		rightPID.setReference(position, ControlType.kPosition);
	}

	//TODO test if this works
	public double distToEncoder(double dist){
		if (getShift()) {
			return (dist/wheleCirconfranceInMeters) * HighGearRatio;
		} else {
			return (dist/wheleCirconfranceInMeters) * LowGearRatio;		}
	}

	/**
	 * The position you want the left side to register when it is in the position it
	 * is currently in
	 * 
	 * @param position the position for the encoder to register in rotations
	 */
	public synchronized void setLeftEncoder(double position) {
		leftEncoder.setPosition(position);
	}

	/**
	 * The position you want the right side to register when it is in the position
	 * it is currently in
	 * 
	 * @param position the position for the encoder to register in rotations
	 */
	public synchronized void setRightEncoder(double position) {
		rightEncoder.setPosition(position);
	}

	/**
	 * probably NOT what you want
	 * zeros the raw encoder values
	 */
	private synchronized void zeroEncoders(){
		setRightEncoder(0);
		setLeftEncoder(0);
	}

	/**
	 * zeros the enocders and encoder acum, this is probably what you want
	 */
	public synchronized void zeroDistance() {
		leftDistanceAcum = 0;
		rightDistanceAcum = 0;
		zeroEncoders();
	}

	/**
	 * Returns the current position of right side of the drivetrain
	 * 
	 * @return position of motor in rotations
	 */
	public double getRightEncoderPosition() {
		return rightEncoder.getPosition();
	}

	/**
	 * Returns the current position of right side of the drivetrain
	 * 
	 * @return position of motor in rotations
	 */
	public double getLeftEncoderPosition() {
		return leftEncoder.getPosition();
	}

	public double getLeftRPM() {
		return leftEncoder.getVelocity();
	}

	public double getRightRPM() {
		return rightEncoder.getVelocity();
	}

	/**
	 * @return the total distance in meters the side as travled sense the last reset
	 */
	public double getLeftDistance() {
		if (getShift()) {
			return ((getLeftEncoderPosition() / HighGearRatio) * wheleCirconfranceInMeters) + leftDistanceAcum;
		} else {
			return ((getLeftEncoderPosition() / LowGearRatio) * wheleCirconfranceInMeters) + leftDistanceAcum;
		}
	}

	/**
	 * @return the total distance in meters the side as travled sense the last reset
	 */
	public synchronized double getRightDistance() {
		if (getShift()) {
			return ((getRightEncoderPosition() / HighGearRatio) * wheleCirconfranceInMeters) + rightDistanceAcum;
		} else {
			return ((getRightEncoderPosition() / LowGearRatio) * wheleCirconfranceInMeters) + rightDistanceAcum;
		}
	}

	/**
	 * @return the linear speed of the side in meters per second
	 */
	public synchronized double getLeftSpeed() {
		if (getShift()) {
			return convertRpmToMetersPerSecond((getLeftRPM() / HighGearRatio));
		} else {
			return convertRpmToMetersPerSecond((getLeftRPM() / LowGearRatio));
		}
	}

	/**
	 * @return the linear speed of the side in meters per second
	 */
	public synchronized double getRightSpeed() {
		if (getShift()) {
			return convertRpmToMetersPerSecond((getRightRPM() / HighGearRatio));
		} else {
			return convertRpmToMetersPerSecond((getRightRPM() / LowGearRatio));
		}
	}

	// could things be good for once please
	private double convertRpmToMetersPerSecond(double RPM) {
		return ((RPM / 60) * (2 * Math.PI)) * WheelRadiusInMeters;
	}

	/**
	 * Sets the rate at witch the motors ramp up and down in open loop control mode
	 * 
	 * @param rate time in seconds to go from 0 to full power
	 */
	public void setOpenRampRate(double rate) {
		left.setOpenLoopRampRate(rate);
		right.setOpenLoopRampRate(rate);
	}

	/**
	 * Sets the rate at which the motors ramp up and down in closed loop control
	 * mode
	 * 
	 * @param rate time in seconds to go from 0 to full power
	 */
	public void setClosedRampRate(double rate) {
		left.setClosedLoopRampRate(rate);
		right.setClosedLoopRampRate(rate);
	}

	/**
	 * Stops the motors
	 */
	public void stop() {
		left.stopMotor();
		right.stopMotor();
	}

	public synchronized DifferentialDriveWheelSpeeds getWheelSpeeds() {
		return new DifferentialDriveWheelSpeeds(getLeftSpeed(), getRightSpeed());
	}

	/**
	 * sets the curent pos and RESETS ENCODERS TO 0
	 * 
	 * @param pose the new pose
	 */
	public synchronized void setPose(Pose2d pose) {
		zeroDistance();
		odometry.resetPosition(pose, gyro.getRotation2d());
	}

	public synchronized Pose2d getPose() {
		return odometry.getPoseMeters();
	}

	/**
	 * gets the right pid values for the curent shift state
	 * 
	 * @return double array of p,i,d
	 */
	public double[] getPid() {
		if (getShift()) {
			double[] out = { HighP, HighI, HighD };
			return out;

		} else {
			double[] out = { LowP, LowI, LowD };
			return out;
		}
	}

	public SimpleMotorFeedforward getFeedFoward() {
		if (getShift()) {
			return HighFeedFoward;
		} else {
			return LowFeedFoward;
		}
	}

	public DifferentialDriveVoltageConstraint getVoltageConstraint() {
		if (getShift()) {
			return HighVoltageConstraint;
		} else {
			return LowVoltageConstraint;
		}
	}

	//TODO run a check so we dont update at a unnessaryly fast rate beacuse its being updated by periodic and followSpline
	public synchronized void updateOdometry() {
		//prevemts unnessarly fast updates to the odemetry (2 ms)
		if(odemetryTime.get() > 0.002)
		{
			odometry.update(gyro.getRotation2d(), getLeftDistance(), getRightDistance());
			odemetryTime.reset();
		}
	}


	public void periodic() {
		// Update the odometry in the periodic block
		updateOdometry();
		// System.out.println(MPStoRPM(getRightSpeed()));
		// System.out.println(rightEncoder.getVelocity());
		// System.out.println("------------------------");
	}
}
