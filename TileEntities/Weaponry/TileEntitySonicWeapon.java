/*******************************************************************************
 * @author Reika Kalseki
 *
 * Copyright 2017
 *
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.RotaryCraft.TileEntities.Weaponry;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import Reika.DragonAPI.Interfaces.TileEntity.GuiController;
import Reika.DragonAPI.Libraries.ReikaAABBHelper;
import Reika.DragonAPI.Libraries.Java.ReikaRandomHelper;
import Reika.DragonAPI.Libraries.MathSci.ReikaPhysicsHelper;
import Reika.DragonAPI.Libraries.World.ReikaWorldHelper;
import Reika.RotaryCraft.Auxiliary.Interfaces.RangedEffect;
import Reika.RotaryCraft.Base.TileEntity.TileEntityPowerReceiver;
import Reika.RotaryCraft.Registry.MachineRegistry;
import Reika.RotaryCraft.Registry.SoundRegistry;

public class TileEntitySonicWeapon extends TileEntityPowerReceiver implements GuiController, RangedEffect {

	public long setpitch;
	public long setvolume;

	public static final long MAXBROWNNOTE = 64; //Triggers food poisoning
	public static final long BATKILL = 80000; //Well within their echolocation pitch range
	public static final long OMINOUS = 16; //That almost-infrasonic uneasy-feeling-generating tone; triggers blindness effect
	public static final long DOGWHISTLE = 40000; //dog whistle
	public static final long LRAD = 2400; //Pitch for most crowd-control audio weapons

	public static final long LETHALVOLUME = 100000000; //10^8 W/m (~38psi)=200dB for 1-hit kill
	public static final long BRICKDESTROY = 1000000; //== 20kPa overpressure -> "Brick walls destroyed"
	public static final long LRADVOLUME = 1260;
	public static final long SHATTERGLASS = 118680; //1psi overpressure
	public static final long BREAKWOOD = 475410; //2 psi overpressure
	public static final long LUNGDAMAGE = 2971000; //5 psi causes pulmonary damage -> cause drowning effect
	public static final long BRAINDAMAGE = 3906200; //125kPa causes TBIs
	public static final long EYEDAMAGE = 1807500; //Causes blindness
	public static final long SILVERFISHKILL = 400000;

	public static final long REFERENCE = 1000000000000L; // 10^-12 W/m^2 reference

	public static final int fudge = 1;//100;

	public static final int FALLOFF = 16384;

	public static final long INTENSITYPERTORQUE = 1000000L*LETHALVOLUME/262144;//262144L*65536L*256L*8L;
	public static final int HZPEROMEGA = 8192;

	public static final boolean ENABLEFREQ = false;
	public static final boolean DECIBELMODE = true;

	@Override
	public void updateEntity(World world, int x, int y, int z, int meta) {
		super.updateTileEntity();
		tickcount++;
		this.getSummativeSidedPower();
		if (power < MINPOWER)
			return;
		if (setpitch > this.getMaxPitch())
			setpitch = this.getMaxPitch();
		if (setvolume > this.getMaxVolume())
			setvolume = this.getMaxVolume();
		this.applyEffects(world, x, y, z);
		if (tickcount >= 10) {
			SoundRegistry.SONIC.playSoundAtBlock(world, x, y, z);
			tickcount = 0;
		}
	}

	public void applyEffects(World world, int x, int y, int z) {
		int range = this.getRange();
		AxisAlignedBB box = ReikaAABBHelper.getBlockAABB(this).expand(range, range, range);
		List<EntityLivingBase> inbox = world.getEntitiesWithinAABB(EntityLivingBase.class, box);
		for (EntityLivingBase ent : inbox) {
			boolean vuln = true;
			if (ent instanceof EntityPlayer)
				if (!this.isPlayerVulnerable((EntityPlayer)ent))
					vuln = false;
			//ReikaChatHelper.write(this.EYEDAMAGE-this.fudge*ReikaPhysicsHelper.inverseSquare(ent.posX-x-0.5, ent.posY-y-0.5, ent.posZ-z-0.5, this.getMaxVolume()));
			if (vuln) {
				double vol = this.getVolume();
				for (Effects e : Effects.list) {
					if (fudge*ReikaPhysicsHelper.inverseSquare(ent.posX-x-0.5, ent.posY-y-0.5, ent.posZ-z-0.5, vol) >= e.threshold) {
						e.doEffect(ent);
					}
				}
			}
		}

		this.breakBrick(world, x, y, z);
		this.killSilverfish(world, x, y, z);
	}

	private void killSilverfish(World world, int x, int y, int z) {
		if (this.getVolume() < SILVERFISHKILL)
			return;
		int range = (int)(6D*this.getVolume()/SILVERFISHKILL);
		if (range > 20)
			range = 20;
		//ReikaJavaLibrary.pConsole(range);
		for (int i = 0; i < range; i++) {
			int bx = ReikaRandomHelper.getRandomPlusMinus(x, range);
			int by = ReikaRandomHelper.getRandomPlusMinus(y, range);
			int bz = ReikaRandomHelper.getRandomPlusMinus(z, range);
			//ReikaJavaLibrary.pConsole("Block "+world.getBlock(bx, by, bz)+" @ "+bx+", "+by+", "+bz);
			if (world.getBlock(bx, by, bz) == Blocks.monster_egg) {
				//ReikaJavaLibrary.pConsole("Killed at "+bx+", "+by+", "+bz);
				int metadata = world.getBlockMetadata(bx, by, bz);
				switch(metadata) {
					case 0:
						world.setBlock(bx, by, bz, Blocks.stone);
						break;
					case 1:
						world.setBlock(bx, by, bz, Blocks.cobblestone);
						break;
					case 2:
						world.setBlock(bx, by, bz, Blocks.stonebrick);
						break;
				}
				world.playSoundEffect(bx+0.5, by+0.5, bz+0.5, "mob.silverfish.kill", 1, 1);
				ReikaWorldHelper.splitAndSpawnXP(world, x+0.5F, y+0.5F, z+0.5F, new EntitySilverfish(world).experienceValue);
			}
		}
	}

	public void breakBrick(World world, int x, int y, int z) {
		//ReikaWorldHelper
	}

	public long getMaxPitch() {
		return (omega*HZPEROMEGA);
	}

	public long getMaxVolume() {
		return (INTENSITYPERTORQUE*torque);
	}

	public long getVolume() {
		long maxvol = this.getMaxVolume();
		if (setvolume > maxvol)
			return maxvol/1000000;
		return setvolume/1000000;
	}

	public long getPitch() {
		long maxpitch = this.getMaxPitch();
		if (setpitch > maxpitch)
			return maxpitch;
		return setpitch;
	}

	public int getRange() {/*
		if (this != null)
			return 16;
		int overpower = (int)(power-MINPOWER)/FALLOFF;
		if (overpower > ConfigRegistry.SONICRANGE.getValue())
			return ConfigRegistry.SONICRANGE.getValue();
		return overpower;*/
		return 16;
	}

	private boolean isPlayerVulnerable(EntityPlayer ep) {
		if (ep.capabilities.isCreativeMode)
			return false;
		if (ep.inventory.armorInventory[3] != null) {
			//if (ep.inventory.armorInventory[3].getItem == RotaryCraft.earmuff.itemID)
			return false;
		}
		return true;
	}

	/**
	 * Writes a tile entity to NBT.
	 */
	@Override
	protected void writeSyncTag(NBTTagCompound NBT)
	{
		super.writeSyncTag(NBT);
		NBT.setLong("setfrequency", setpitch);
		NBT.setLong("setvolume", setvolume);
	}

	/**
	 * Reads a tile entity from NBT.
	 */
	@Override
	protected void readSyncTag(NBTTagCompound NBT)
	{
		super.readSyncTag(NBT);
		setpitch = NBT.getLong("setfrequency");
		setvolume = NBT.getLong("setvolume");
	}

	@Override
	public boolean hasModelTransparency() {
		return false;
	}

	@Override
	protected void animateWithTick(World world, int x, int y, int z) {

	}

	@Override
	public MachineRegistry getMachine() {
		return MachineRegistry.SONICWEAPON;
	}

	@Override
	public int getMaxRange() {
		return this.getRange();//Math.max(64, ConfigRegistry.SONICRANGE.getValue());
	}

	@Override
	public int getRedstoneOverride() {
		return 0;
	}

	private static enum Effects {
		EYE(EYEDAMAGE),
		BRAIN(BRAINDAMAGE),
		LUNG(LUNGDAMAGE),
		KILL(LETHALVOLUME),
		//BRICK,
		;

		private final long threshold;

		private static final Effects[] list = values();

		private Effects(long l) {
			threshold = l;
		}

		private void doEffect(EntityLivingBase ent) {
			switch(this) {
				case EYE:
					ent.addPotionEffect(new PotionEffect(Potion.blindness.id, 20, 0));
					//ReikaChatHelper.write(ent.getAITarget());
					//ent.getNavigator().clearPathEntity();
					//ent.setAttackTarget(null);
					//ent.setRevengeTarget(null);
					//ent.setLastAttackingEntity(null);
					if (ent instanceof EntityCreature) {
						//((EntityCreature)ent).setTarget(null);
					}
					break;
				case BRAIN:
					ent.addPotionEffect(new PotionEffect(Potion.confusion.id, 200, 10));
					ent.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, 20, 3));
					ent.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 20, 1));
					if (ent instanceof EntityAnimal) {
						EntityAnimal ani = (EntityAnimal)ent;
						ani.getNavigator().clearPathEntity();
						if (ani.getNavigator().noPath()) {
							double randx = ani.posX - 8 + rand.nextInt(17);
							double randz = ani.posZ - 8 + rand.nextInt(17);
							int randy = ent.worldObj.getTopSolidOrLiquidBlock((int)randx, (int)randz);
							PathEntity path = ani.getNavigator().getPathToXYZ(randx, randy, randz);
							ani.getNavigator().setPath(path, 0.2F);
						}
					}
					if (ent instanceof EntityMob) {
						AxisAlignedBB nearmob = AxisAlignedBB.getBoundingBox(ent.posX, ent.posY, ent.posZ, ent.posX, ent.posY, ent.posZ).expand(10, 10, 10);
						Entity target = ent.worldObj.findNearestEntityWithinAABB(EntityMob.class, nearmob, ent);
						if (target instanceof EntityMob) {
							((EntityMob)ent).setAttackTarget((EntityLivingBase)target);
							((EntityMob)ent).setTarget(target);
							ent.setRevengeTarget((EntityLivingBase)target);
							ent.setLastAttacker(target);
						}
					}
					break;
				case LUNG:
					if (rand.nextInt(40) == 0)
						ent.attackEntityFrom(DamageSource.drown, 1);
					break;
				case KILL:
					ent.attackEntityFrom(DamageSource.outOfWorld, Integer.MAX_VALUE);
					break;
			}
		}
	}
}
