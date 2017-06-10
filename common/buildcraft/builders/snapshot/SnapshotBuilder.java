/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.mj.MjAPI;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FakePlayerProvider;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.net.PacketBufferBC;

public abstract class SnapshotBuilder<T extends ITileForSnapshotBuilder> {
    private static final int MAX_QUEUE_SIZE = 64;

    protected final T tile;
    private Queue<BreakTask> breakTasks = new ArrayDeque<>();
    public Queue<BreakTask> clientBreakTasks = new ArrayDeque<>();
    public Queue<BreakTask> prevClientBreakTasks = new ArrayDeque<>();
    private Queue<PlaceTask> placeTasks = new ArrayDeque<>();
    public Queue<PlaceTask> clientPlaceTasks = new ArrayDeque<>();
    public Queue<PlaceTask> prevClientPlaceTasks = new ArrayDeque<>();
    public Vec3d robotPos = null;
    public Vec3d prevRobotPos = null;
    public int leftToBreak;
    public int leftToPlace;

    protected SnapshotBuilder(T tile) {
        this.tile = tile;
    }

    protected abstract List<BlockPos> getToBreak();

    protected abstract List<BlockPos> getToPlace();

    protected abstract boolean canPlace(BlockPos blockPos);

    /**
     * @return items
     */
    protected abstract List<ItemStack> getToPlaceItems(BlockPos blockPos);

    /**
     * @return true if task done successfully, false otherwise
     */
    protected abstract boolean doPlaceTask(PlaceTask placeTask);

    /**
     * Executed if {@link #doPlaceTask} failed
     */
    protected abstract void cancelPlaceTask(PlaceTask placeTask);

    /**
     * @return true if block in wold is correct (is not to break) according to snapshot, false otherwise
     */
    protected abstract boolean isBlockCorrect(BlockPos blockPos);

    public abstract Box getBox();

    /**
     * @return Pos where flying item should be rendered
     */
    public Vec3d getPlaceTaskItemPos(PlaceTask placeTask) {
        Vec3d height = new Vec3d(placeTask.pos.subtract(tile.getBuilderPos()));
        double progress = placeTask.power * 1D / placeTask.getTarget();
        return new Vec3d(tile.getBuilderPos())
            .add(height.scale(progress))
            .add(new Vec3d(0, Math.sin(progress * Math.PI) * (height.yCoord + 1), 0))
            .add(new Vec3d(0.5, 1, 0.5));
    }

    /**
     * @return true is building is finished, false otherwise
     */
    public boolean tick() {
        if (tile.getWorldBC().isRemote) {
            prevClientBreakTasks.clear();
            prevClientBreakTasks.addAll(clientBreakTasks);
            clientBreakTasks.clear();
            clientBreakTasks.addAll(breakTasks);
            prevClientPlaceTasks.clear();
            prevClientPlaceTasks.addAll(clientPlaceTasks);
            clientPlaceTasks.clear();
            clientPlaceTasks.addAll(placeTasks);
            prevRobotPos = robotPos;
            if (!breakTasks.isEmpty()) {
                Vec3d newRobotPos = breakTasks.stream()
                    .map(BreakTask::getPos)
                    .map(Vec3d::new)
                    .reduce(Vec3d.ZERO, Vec3d::add)
                    .scale(1D / breakTasks.size());
                newRobotPos = new Vec3d(
                    newRobotPos.xCoord,
                    breakTasks.stream().map(BreakTask::getPos).mapToDouble(BlockPos::getY).max().orElse(newRobotPos.yCoord),
                    newRobotPos.zCoord
                );
                newRobotPos = newRobotPos.add(new Vec3d(0, 3, 0));
                Vec3d oldRobotPos = robotPos;
                robotPos = newRobotPos;
                if (oldRobotPos != null) {
                    robotPos = oldRobotPos.add(newRobotPos.subtract(oldRobotPos).scale(1 / 4D));
                }
            } else {
                robotPos = null;
            }
            return false;
        }

        breakTasks.removeIf(breakTask -> tile.getWorldBC().isAirBlock(breakTask.pos) || isBlockCorrect(breakTask.pos));
        placeTasks.removeIf(placeTask -> isBlockCorrect(placeTask.pos));

        boolean isDone = true;

        if (tile.canExcavate()) {
            List<BlockPos> blocks = Stream.concat(getToBreak().stream(), getToPlace().stream())
                .sorted(Comparator.comparing(blockPos ->
                    Math.pow(blockPos.getX() - getBox().center().getX(), 2) +
                        Math.pow(blockPos.getZ() - getBox().center().getZ(), 2) +
                        100_000 - Math.abs(blockPos.getY() - tile.getBuilderPos().getY()) * 100_000
                ))
                .filter(blockPos -> breakTasks.stream().map(BreakTask::getPos).noneMatch(Predicate.isEqual(blockPos)))
                .filter(blockPos -> !tile.getWorldBC().isAirBlock(blockPos))
                .filter(blockPos -> !isBlockCorrect(blockPos))
                .filter(blockPos -> BlockUtil.getFluidWithFlowing(tile.getWorldBC(), blockPos) == null)
                .collect(Collectors.toList());
            leftToBreak = blocks.size();
            if (!blocks.isEmpty()) {
                isDone = false;
            }
            blocks.stream()
                .map(blockPos ->
                    new BreakTask(
                        blockPos,
                        0
                    )
                )
                .limit(MAX_QUEUE_SIZE - breakTasks.size())
                .forEach(breakTasks::add);
        }

        {
            List<BlockPos> blocks = getToPlace().stream()
                .sorted(Comparator.comparing(blockPos ->
                    100_000 - (Math.pow(blockPos.getX() - tile.getBuilderPos().getX(), 2) +
                        Math.pow(blockPos.getZ() - tile.getBuilderPos().getZ(), 2)) +
                        Math.abs(blockPos.getY() - tile.getBuilderPos().getY()) * 100_000
                ))
                .filter(blockPos -> placeTasks.stream().map(PlaceTask::getPos).noneMatch(Predicate.isEqual(blockPos)))
                .filter(blockPos -> !isBlockCorrect(blockPos))
                .filter(this::canPlace)
                .collect(Collectors.toList());
            leftToPlace = blocks.size();
            if ((!tile.canExcavate() || breakTasks.isEmpty())) {
                if (!blocks.isEmpty()) {
                    isDone = false;
                }
                blocks.stream()
                    .map(blockPos ->
                        new PlaceTask(
                            blockPos,
                            getToPlaceItems(blockPos),
                            0
                        )
                    )
                    .filter(placeTask -> placeTask.items != null)
                    .filter(placeTask -> !placeTask.items.contains(ItemStack.EMPTY))
                    .limit(MAX_QUEUE_SIZE - placeTasks.size())
                    .forEach(placeTasks::add);
            }
        }

        if (!breakTasks.isEmpty()) {
            for (Iterator<BreakTask> iterator = breakTasks.iterator(); iterator.hasNext(); ) {
                BreakTask breakTask = iterator.next();
                long target = breakTask.getTarget();
                breakTask.power += tile.getBattery().extractPower(
                    0,
                    Math.min(target - breakTask.power, tile.getBattery().getStored() / breakTasks.size())
                );
                if (breakTask.power >= target) {
                    BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(
                        tile.getWorldBC(),
                        breakTask.pos,
                        tile.getWorldBC().getBlockState(breakTask.pos),
                        BuildCraftAPI.fakePlayerProvider.getFakePlayer(
                            (WorldServer) tile.getWorldBC(),
                            tile.getOwner(),
                            tile.getBuilderPos()
                        )
                    );
                    MinecraftForge.EVENT_BUS.post(breakEvent);
                    if (!breakEvent.isCanceled()) {
                        tile.getWorldBC().sendBlockBreakProgress(
                            breakTask.pos.hashCode(),
                            breakTask.pos,
                            -1
                        );
                        tile.getWorldBC().destroyBlock(breakTask.pos, false);
                    } else {
                        tile.getBattery().addPower(
                            Math.min(target, tile.getBattery().getCapacity() - tile.getBattery().getStored()),
                            false
                        );
                    }
                    iterator.remove();
                } else {
                    tile.getWorldBC().sendBlockBreakProgress(
                        breakTask.pos.hashCode(),
                        breakTask.pos,
                        (int) ((breakTask.power * 9) / target)
                    );
                }
            }
        }

        if (!placeTasks.isEmpty()) {
            for (Iterator<PlaceTask> iterator = placeTasks.iterator(); iterator.hasNext(); ) {
                PlaceTask placeTask = iterator.next();
                long target = placeTask.getTarget();
                placeTask.power += tile.getBattery().extractPower(
                    0,
                    Math.min(target - placeTask.power, tile.getBattery().getStored() / placeTasks.size())
                );
                if (placeTask.power >= target) {
                    if (!doPlaceTask(placeTask)) {
                        tile.getBattery().addPower(
                            Math.min(target, tile.getBattery().getCapacity() - tile.getBattery().getStored()),
                            false
                        );
                        cancelPlaceTask(placeTask);
                    }
                    iterator.remove();
                }
            }
        }

        return isDone;
    }

    public void writeToByteBuf(PacketBufferBC buffer) {
        buffer.writeInt(breakTasks.size());
        breakTasks.forEach(breakTask -> breakTask.writePayload(buffer));
        buffer.writeInt(placeTasks.size());
        placeTasks.forEach(placeTask -> placeTask.writePayload(buffer));
        buffer.writeInt(leftToBreak);
        buffer.writeInt(leftToPlace);
    }

    public void readFromByteBuf(PacketBufferBC buffer) {
        breakTasks.clear();
        IntStream.range(0, buffer.readInt()).mapToObj(i -> new BreakTask(buffer)).forEach(breakTasks::add);
        placeTasks.clear();
        IntStream.range(0, buffer.readInt()).mapToObj(i -> new PlaceTask(buffer)).forEach(placeTasks::add);
        leftToBreak = buffer.readInt();
        leftToPlace = buffer.readInt();
    }

    public void cancel() {
        breakTasks.forEach(breakTask ->
            tile.getBattery().addPower(
                Math.min(breakTask.getTarget(), tile.getBattery().getCapacity() - tile.getBattery().getStored()),
                false
            )
        );
        placeTasks.forEach(placeTask ->
            tile.getBattery().addPower(
                Math.min(placeTask.getTarget(), tile.getBattery().getCapacity() - tile.getBattery().getStored()),
                false
            )
        );
    }

    public class BreakTask {
        public BlockPos pos;
        public long power;

        public BreakTask(BlockPos pos, long power) {
            this.pos = pos;
            this.power = power;
        }

        public BreakTask(PacketBufferBC buffer) {
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

        public long getTarget() {
            return BlockUtil.computeBlockBreakPower(tile.getWorldBC(), pos);
        }

        public void writePayload(PacketBufferBC buffer) {
            MessageUtil.writeBlockPos(buffer, pos);
            buffer.writeLong(power);
        }
    }

    public class PlaceTask {
        public BlockPos pos;
        public List<ItemStack> items;
        public long power;

        public PlaceTask(BlockPos pos, List<ItemStack> items, long power) {
            this.pos = pos;
            this.items = items;
            this.power = power;
        }

        public PlaceTask(PacketBufferBC buffer) {
            pos = MessageUtil.readBlockPos(buffer);
            items = IntStream.range(0, buffer.readInt()).mapToObj(j -> {
                try {
                    return buffer.readItemStack();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
            power = buffer.readLong();
        }

        public BlockPos getPos() {
            return pos;
        }

        public void setPos(BlockPos pos) {
            this.pos = pos;
        }

        public List<ItemStack> getItems() {
            return items;
        }

        public void setItems(List<ItemStack> items) {
            this.items = items;
        }

        public long getPower() {
            return power;
        }

        public void setPower(long power) {
            this.power = power;
        }

        public long getTarget() {
            return (long) (Math.sqrt(pos.distanceSq(tile.getBuilderPos())) * 10 * MjAPI.MJ);
        }

        public void writePayload(PacketBufferBC buffer) {
            MessageUtil.writeBlockPos(buffer, pos);
            buffer.writeInt(items.size());
            items.forEach(buffer::writeItemStack);
            buffer.writeLong(power);
        }
    }
}
