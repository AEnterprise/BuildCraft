/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import buildcraft.api.mj.MjAPI;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.net.PacketBufferBC;

public abstract class SnapshotTask {

    public BlockPos pos;
    public long power;

    public SnapshotTask(BlockPos pos, long power) {
        this.pos = pos;
        this.power = power;
    }

    public SnapshotTask(PacketBufferBC buffer) {
        pos = MessageUtil.readBlockPos(buffer);
        power = buffer.readLong();
    }

    public BlockPos getPos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

    public long getPower() {
        return power;
    }

    public void setPower(long power) {
        this.power = power;
    }

    public long getTarget(ITileForSnapshotBuilder tile) {
        return BlockUtil.computeBlockBreakPower(tile.getWorldBC(), pos);
    }

    public void writePayload(PacketBufferBC buffer) {
        MessageUtil.writeBlockPos(buffer, pos);
        buffer.writeLong(power);
    }

    public static class PlaceTask extends SnapshotTask {
        public List<ItemStack> items;

        public PlaceTask(BlockPos pos, List<ItemStack> items, long power) {
            super(pos, power);
            this.items = items;
        }

        public PlaceTask(PacketBufferBC buffer) {
            super(buffer);
            items = IntStream.range(0, buffer.readInt()).mapToObj(j -> {
                try {
                    return buffer.readItemStack();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
        }

        public List<ItemStack> getItems() {
            return items;
        }

        public void setItems(List<ItemStack> items) {
            this.items = items;
        }


        @Override
        public long getTarget(ITileForSnapshotBuilder tile) {
            return (long) (Math.sqrt(pos.distanceSq(tile.getBuilderPos())) * 10 * MjAPI.MJ);
        }

        @Override
        public void writePayload(PacketBufferBC buffer) {
            super.writePayload(buffer);
            buffer.writeInt(items.size());
            items.forEach(buffer::writeItemStack);
        }
    }

    public static class BreakTask extends SnapshotTask {

        public BreakTask(BlockPos pos, long power) {
            super(pos, power);
        }

        public BreakTask(PacketBufferBC buffer) {
            super(buffer);
        }
    }
}
