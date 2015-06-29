/*******************************************************************************
 * @author Reika Kalseki
 * 
 * Copyright 2015
 * 
 * All rights reserved.
 * Distribution of the software in any form is only allowed with
 * explicit, prior permission from the owner.
 ******************************************************************************/
package Reika.RotaryCraft.TileEntities;

import java.util.Collection;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import Reika.DragonAPI.Auxiliary.ChunkManager;
import Reika.DragonAPI.Interfaces.BreakAction;
import Reika.DragonAPI.Interfaces.ChunkLoadingTile;
import Reika.RotaryCraft.Base.TileEntity.TileEntityPowerReceiver;
import Reika.RotaryCraft.Registry.MachineRegistry;

public class TileEntityChunkLoader extends TileEntityPowerReceiver implements ChunkLoadingTile, BreakAction {

	private boolean loaded;

	public TileEntityChunkLoader() {

	}

	@Override
	public void updateEntity(World world, int x, int y, int z, int meta) {
		super.updateTileEntity();
		this.getPowerBelow();
		if (omega >= MINSPEED) {
			this.load();
		}
		else {
			this.unload();
		}
	}

	private void load() {
		if (loaded)
			return;
		loaded = true;
		ChunkManager.instance.loadChunks(this);
	}

	private void unload() {
		if (loaded) {
			loaded = false;
			ChunkManager.instance.unloadChunks(this);
		}
	}

	@Override
	public boolean hasModelTransparency() {
		return false;
	}

	@Override
	protected void animateWithTick(World world, int x, int y, int z) {

	}

	@Override
	protected void onInvalidateOrUnload(World world, int x, int y, int z, boolean invalid) {
		if (!invalid) {
			throw new RuntimeException("Chunkloader "+this+" unloaded!");
		}
		this.unload();
	}

	@Override
	public MachineRegistry getMachine() {
		return MachineRegistry.CHUNKLOADER;
	}

	@Override
	public int getRedstoneOverride() {
		return 0;
	}

	@Override
	public Collection<ChunkCoordIntPair> getChunksToLoad() {
		return ChunkManager.getChunkSquare(xCoord, zCoord, 16);
	}

	@Override
	public void breakBlock() {
		this.unload();
	}

}
