/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import buildcraft.lib.fluid.Tank;

import java.util.ArrayList;
import java.util.List;

public class FluidUtilBC {
    public static void pushFluidAround(IBlockAccess world, BlockPos pos, Tank tank) {
        FluidStack potential = tank.drain(tank.getFluidAmount(), false);
        int drained = 0;
        if (potential == null || potential.amount <= 0) {
            return;
        }
        FluidStack working = potential.copy();
        for (EnumFacing side : EnumFacing.VALUES) {
            if (potential.amount <= 0) {
                break;
            }
            TileEntity target = world.getTileEntity(pos.offset(side));
            if (target == null) {
                continue;
            }
            IFluidHandler handler = target.getCapability(CapUtil.CAP_FLUIDS, side.getOpposite());
            if (handler != null) {
                int used = handler.fill(potential, true);

                if (used > 0) {
                    drained += used;
                    potential.amount -= used;
                }
            }
        }
        if (drained > 0) {
            FluidStack actuallyDrained = tank.drain(drained, true);
            if (actuallyDrained == null || actuallyDrained.amount != drained) {
                throw new IllegalStateException("Bad tank! Could drain " + working + " but only drained " + actuallyDrained + "( tank " + tank.getClass() + ")");
            }
        }
    }

    public static void pullFluidAround(IBlockAccess world, BlockPos pos, Tank tank) {
        int max = tank.getCapacity() - tank.getFluidAmount();
        if (max <= 0) {
            return;
        }
        max = Math.min(max, 1000);

        for (EnumFacing side : EnumFacing.VALUES) {
            TileEntity target = world.getTileEntity(pos.offset(side));
            if (target == null) {
                continue;
            }
            IFluidHandler handler = target.getCapability(CapUtil.CAP_FLUIDS, side.getOpposite());
            if (handler == null) {
                continue;
            }

            FluidStack fluidFilter = tank.getFluid();
            if (fluidFilter == null) {
                FluidStack drained = handler.drain(max, false);
                if (drained == null) continue;
                int filled = tank.fill(drained, true);
                if (filled > 0) {
                    FluidStack reallyDrained = handler.drain(filled, true);
                    if (reallyDrained == null || reallyDrained.amount != filled) {
                        throw new IllegalStateException("Bad IFluidHandler.drain implementation! ( drained = " + drained + " reallyDrained = " + reallyDrained + " handler " + handler.getClass());
                    }
                    max -= filled;
                }
            } else {
                fluidFilter = fluidFilter.copy();
                fluidFilter.amount = max;
                FluidStack drained = handler.drain(fluidFilter, false);
                if (drained == null) continue;
                int filled = tank.fill(drained, true);
                if (filled > 0) {
                    fluidFilter.amount = filled;
                    FluidStack reallyDrained = handler.drain(fluidFilter, true);
                    if (reallyDrained == null || reallyDrained.amount != filled) {
                        throw new IllegalStateException("Bad IFluidHandler.drain implementation! ( drained = " + drained + " reallyDrained = " + reallyDrained + " handler " + handler.getClass());
                    }
                    max -= filled;
                }

            }
        }
    }

    public static List<FluidStack> mergeSameFluids(List<FluidStack> fluids) {
        List<FluidStack> stacks = new ArrayList<>();
        fluids.forEach(toAdd -> {
            boolean found = false;
            for (FluidStack stack : stacks) {
                if (stack.isFluidEqual(toAdd)) {
                    stack.amount += toAdd.amount;
                    found = true;
                }
            }
            if (!found) {
                stacks.add(toAdd.copy());
            }
        });
        return stacks;
    }

    public static boolean areFluidStackEqual(FluidStack a, FluidStack b) {
        return (a == null && b == null) || (a != null && a.isFluidEqual(b) && a.amount == b.amount);
    }
}
