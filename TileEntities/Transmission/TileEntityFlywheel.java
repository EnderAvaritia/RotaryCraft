/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2013
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.RotaryCraft.TileEntities.Transmission;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import Reika.DragonAPI.Libraries.MathSci.ReikaEngLibrary;
import Reika.DragonAPI.Libraries.MathSci.ReikaMathLibrary;
import Reika.DragonAPI.Libraries.MathSci.ReikaPhysicsHelper;
import Reika.RotaryCraft.RotaryConfig;
import Reika.RotaryCraft.API.PowerGenerator;
import Reika.RotaryCraft.API.ShaftMerger;
import Reika.RotaryCraft.API.ShaftPowerEmitter;
import Reika.RotaryCraft.Auxiliary.PowerSourceList;
import Reika.RotaryCraft.Auxiliary.TorqueUsage;
import Reika.RotaryCraft.Auxiliary.Interfaces.SimpleProvider;
import Reika.RotaryCraft.Base.TileEntity.TileEntityIOMachine;
import Reika.RotaryCraft.Base.TileEntity.TileEntityTransmissionMachine;
import Reika.RotaryCraft.Registry.ConfigRegistry;
import Reika.RotaryCraft.Registry.MachineRegistry;
import Reika.RotaryCraft.Registry.SoundRegistry;

public class TileEntityFlywheel extends TileEntityTransmissionMachine implements SimpleProvider, PowerGenerator, ShaftMerger {

	public static final int MINTORQUERATIO = 4;
	public static final int WOODFLYTORQUEMAX = 16;		// rho 0.8	-> 1	-> 1
	public static final int STONEFLYTORQUEMAX = 128;	// rho 3	-> 4	-> 8
	public static final int IRONFLYTORQUEMAX = 512;		// rho 8	-> 8	-> 32
	public static final int GOLDFLYTORQUEMAX = 4096;	// rho 19.3	-> 32	-> 256

	private int decayTime;

	private int maxtorque;
	public boolean failed = false;
	private int soundtick = 0;

	private int lasttorque;

	public int oppTorque = 0;
	private int updateticks = 0;

	public static int[] getLimitLoads() {
		double r = 0.75;
		int[] loads = new int[4];
		for (int i = 0; i < 4; i++) {
			double rho = getDensity(i);
			double s = 100*getStrength(i);
			double frac = 2*s/(rho*r*r);
			double base = Math.sqrt(frac);
			loads[i] = (int)base;
		}
		return loads;
	}

	public static int getMinTorque(int i) {
		switch(i) {
		case 0:
			return WOODFLYTORQUEMAX/MINTORQUERATIO;
		case 1:
			return STONEFLYTORQUEMAX/MINTORQUERATIO;
		case 2:
			return IRONFLYTORQUEMAX/MINTORQUERATIO;
		case 3:
			return GOLDFLYTORQUEMAX/MINTORQUERATIO;
		default:
			return 0;
		}
	}

	public void testFailure() {
		double factor = 0.25*Math.sqrt(omega);
		switch(this.getBlockMetadata()/4) {
		case 1:
			factor /= 2.5;
		case 3:
			factor *= 1.25;
		}
		factor *= ReikaMathLibrary.doubpow(omega/65536D, 1.5); //to reduce damage
		double energy = ReikaEngLibrary.rotenergy(this.getDensity(), 0.25, omega, 0.75);
		//ReikaJavaLibrary.pConsole(ReikaPhysicsHelper.getExplosionFromEnergy(energy/factor)+"  fails: "+ReikaEngLibrary.mat_rotfailure(this.getDensity(), 0.75, omega, 100*this.getStrength()));
		if (ReikaEngLibrary.mat_rotfailure(this.getDensity(), 0.75, omega, 100*this.getStrength()))
			this.fail(worldObj, xCoord, yCoord, zCoord, energy/factor);
	}

	private void fail(World world, int x, int y, int z, double e) {
		failed = true;
		if (!world.isRemote)
			world.createExplosion(null, x+0.5, y+0.5, z+0.5, ReikaPhysicsHelper.getExplosionFromEnergy(e), ConfigRegistry.BLOCKDAMAGE.getState());
	}

	private double getDensity() {
		return this.getDensity(this.getBlockMetadata()/4);
	}

	public static double getDensity(int dmg) {
		switch (dmg) {
		case 0:
			return ReikaEngLibrary.rhowood;
		case 1:
			return ReikaEngLibrary.rhorock;
		case 2:
			return ReikaEngLibrary.rhoiron;
		case 3:
			return ReikaEngLibrary.rhogold;
		}
		return 0;
	}

	private double getStrength() {
		return this.getStrength(this.getBlockMetadata()/4);
	}

	public static double getStrength(int i) {
		switch (i) {
		case 0:
			return ReikaEngLibrary.Twood;
		case 1:
			return 0.9*ReikaEngLibrary.Tstone;
		case 2:
			return 5*ReikaEngLibrary.Tiron;
		case 3:
			return ReikaEngLibrary.Tgold;
		}
		return 0;
	}

	public int frictionLosses(int speed) {
		int fric = RotaryConfig.friction;
		return (speed*fric);
	}

	@Override
	public void updateEntity(World world, int x, int y, int z, int meta) {
		super.updateTileEntity();
		this.getType(meta/4);
		this.getIOSides(world, x, y, z, meta%4);
		if (failed) {
			omega = 0;
			torque = 0;
			power = 0;
			return;
		}
		this.process(world, x, y, z);
		power = omega*torque;
		this.testFailure();
		this.playSounds();

		this.basicPowerReceiver();
	}

	private void playSounds() {
		if (omega == 0) {
			soundtick = 20000;
			return;
		}
		float pitch = (float)Math.sqrt(omega/1024F);
		if (pitch == 0)
			pitch = 0.01F;
		soundtick++;
		if (soundtick < -3F/(pitch*pitch)+69/(pitch))
			return;
		soundtick = 0;
		SoundRegistry.FLYWHEEL.playSoundAtBlock(worldObj, xCoord, yCoord, zCoord, 1, pitch);
	}

	public void getType(int meta) {
		switch (meta) {
		case 0:
			maxtorque = WOODFLYTORQUEMAX;
			decayTime = 2;
			break;
		case 1:
			maxtorque = STONEFLYTORQUEMAX;
			decayTime = 5;
			break;
		case 2:
			maxtorque = IRONFLYTORQUEMAX;
			decayTime = 15;
			break;
		case 3:
			maxtorque = GOLDFLYTORQUEMAX;
			decayTime = 40;
			break;
		default:
			maxtorque = 0;
			decayTime = 1;
			break;
		}
	}

	public void getIOSides(World world, int x, int y, int z, int metadata) {
		switch(metadata) {
		case 0:
			readx = xCoord-1;
			writex = xCoord+1;
			readz = writez = zCoord;
			break;
		case 1:
			readx = xCoord+1;
			writex = xCoord-1;
			readz = writez = zCoord;
			break;
		case 2:
			readz = zCoord-1;
			writez = zCoord+1;
			readx = writex = xCoord;
			break;
		case 3:
			readz = zCoord+1;
			writez = zCoord-1;
			readx = writex = xCoord;
			break;
		}
		ready = yCoord;
		writey = yCoord;
	}

	public void process(World world, int x, int y, int z) {
		ready = y;
		omegain = 0;
		tickcount++;
		MachineRegistry m = MachineRegistry.getMachine(world, readx, ready, readz);
		TileEntity te = world.getBlockTileEntity(readx, ready, readz);
		if (m == MachineRegistry.SHAFT) {
			TileEntityShaft devicein = (TileEntityShaft)world.getBlockTileEntity(readx, ready, readz);
			if (devicein.getBlockMetadata() >= 6) {
				omegain = this.readFromCross(devicein, false);
				torquein = this.readFromCross(devicein, true);
			}
			else if (devicein.writex == x && devicein.writey == y && devicein.writez == z) {
				omegain = devicein.omega;
				torquein = devicein.torque;
			}
		}
		if (te instanceof SimpleProvider) {
			this.copyStandardPower(worldObj, readx, ready, readz);
		}
		if (te instanceof ShaftPowerEmitter) {
			ShaftPowerEmitter sp = (ShaftPowerEmitter)te;
			if (sp.isEmitting() && sp.canWriteToBlock(xCoord, yCoord, zCoord)) {
				torquein = sp.getTorque();
				omegain = sp.getOmega();
			}
		}
		if (m == MachineRegistry.SPLITTER) {
			TileEntitySplitter devicein = (TileEntitySplitter)world.getBlockTileEntity(readx, ready, readz);
			if (devicein.getBlockMetadata() >= 8) {
				this.readFromSplitter(devicein);
				return;
			}
			else if (devicein.writex == xCoord && devicein.writez == zCoord) {
				omegain = devicein.omega;
				torquein = devicein.torque;
			}
		}
		if (te == null) {
			torquein = 0;
			omegain = 0;
		}
		double r = 0.75;  //this calculates the flywheel datas. You already assumed r=0.75 in previous formulas, so I used that. I set h=0.4 from the model in-game
		double h = 0.4;
		double iner = (h*r*r*Math.PI)*this.getDensity()*r*r/2; //standard inertial moment formula for a cylinder with its rotor on the central axis
		updateticks = 0;
		if (torquein > 0) {
			oppTorque = TorqueUsage.getTorque(world, x, y, z); //gets the minimum torque requirement of all machines connected to the flywheel
			if (oppTorque <= torquein) {
				if (omega <= omegain) {
					if ((torquein-oppTorque)/iner < 1 && (torquein-oppTorque) > 0) { //making up for the fact that numbers are integers
						int i = 1;
						while ((((double)torquein-oppTorque)/iner*i) < 1) {
							i++;
						}
						updateticks = i;
						if (tickcount >= updateticks) {
							tickcount=0;
							omega++;
						}
					}
					else {
						omega += (torquein-oppTorque)/iner; //increasing omega, following the formula torque=inertia*ang.acceleration
					}
					if (omega > omegain)
						omega = omegain; //to prevent oscillations once reached the input value
				}
				else {
					if ((torquein+oppTorque)/iner < 1) { //same as before, but to reduce omega if it's greater than the input omega
						int i = 1;
						while ((((double)torquein+oppTorque)/iner*i) < 1) {
							i++;
						}
						updateticks=i;
						if (tickcount >= updateticks) {
							tickcount = 0;
							omega--;
						}
					}
					else
						omega -= (torquein+oppTorque)/iner;
				}
				lasttorque = torquein;
				lasttorque = Math.min(lasttorque, maxtorque);
				torque = lasttorque;
			}
			else {
				if ((oppTorque-torquein)/iner < 1) { //this applies the same formula to reduce omega in the case the input is smaller than the required output
					int i = 1;
					while (((oppTorque-torquein)/iner*i) < 1) {
						i++;
					}
					updateticks = i;
					if (tickcount >= updateticks) {
						tickcount = 0;
						omega--;
					}
				}
				else
					omega -= (oppTorque-torquein)/iner;
				if (omega < 0)
					omega = 0;
			}
		}
		else {
			if (omega == 0) {
				lasttorque = 0;
				torque = 0;
				tickcount = 0;
			}
			else { //same as before, but without input
				oppTorque = TorqueUsage.getTorque(world, x, y, z);
				if (oppTorque/iner < 1 && oppTorque > 0) {
					int i = 1;
					while ((oppTorque/iner*i) < 1) {
						i++;
					}
					updateticks = i;
					if (tickcount>=updateticks) {
						tickcount = 0;
						omega--;
					}
				}
				else
					omega -= oppTorque/iner;
				torque = lasttorque;
				if (omega < 0)
					omega = 0;
			}
		}
	}

	private void decrSpeed() {
		if (omega > 0 && tickcount >= decayTime) {
			omega--;
			tickcount = 0;
		}
	}

	public int readFromCross(TileEntityShaft cross, boolean isTorque) {
		//ModLoader.getMinecraftInstance().thePlayer.addChatMessage(String.format("%d %d %d %d", cross.writex, cross.writex2, cross.writez, cross.writez2));
		if (xCoord == cross.writex && zCoord == cross.writez) {
			if (isTorque)
				return cross.readtorque[0];
			return cross.readomega[0];
		}
		else if (xCoord == cross.writex2 && zCoord == cross.writez2) {
			if (isTorque)
				return cross.readtorque[1];
			return cross.readomega[1];
		}
		else
			return 0; //not its output
	}

	/**
	 * Writes a tile entity to NBT.
	 */
	@Override
	public void writeToNBT(NBTTagCompound NBT)
	{
		super.writeToNBT(NBT);
		NBT.setBoolean("failed", failed);
	}

	/**
	 * Reads a tile entity from NBT.
	 */
	@Override
	public void readFromNBT(NBTTagCompound NBT)
	{
		super.readFromNBT(NBT);
		failed = NBT.getBoolean("failed");
	}

	@Override
	public boolean hasModelTransparency() {
		return false;
	}

	@Override
	public void animateWithTick(World world, int x, int y, int z) {
		if (!this.isInWorld()) {
			phi = 0;
			return;
		}
		phi += ReikaMathLibrary.doubpow(ReikaMathLibrary.logbase(omega+1, 2), 1.05);
	}

	@Override
	public int getMachineIndex() {
		return MachineRegistry.FLYWHEEL.ordinal();
	}

	@Override
	public int getRedstoneOverride() {
		return 0;
	}

	@Override
	public void onEMP() {}

	@Override
	public PowerSourceList getPowerSources(TileEntityIOMachine io, ShaftMerger caller) {
		return new PowerSourceList().addSource(this);
	}

	@Override
	public boolean isFlipped() {
		return isFlipped;
	}

	@Override
	public void setFlipped(boolean set) {
		isFlipped = set;
	}

	@Override
	public long getMaxPower() {
		return maxtorque*omega;
	}

	@Override
	public long getCurrentPower() {
		return power;
	}

	@Override
	public boolean canProvidePower() {
		return true;
	}
}
