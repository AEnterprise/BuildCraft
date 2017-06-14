/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

import buildcraft.api.core.BuildCraftAPI;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.net.PacketBufferBC;

public abstract class SnapshotBuilder<T extends ITileForSnapshotBuilder> {
    private static final int MAX_QUEUE_SIZE = 64;
    private static final int MAX_CHECK = 80;

    protected final T tile;
    private Queue<SnapshotTask.BreakTask> breakTasks = new ArrayDeque<>();
    public Queue<SnapshotTask.BreakTask> clientBreakTasks = new ArrayDeque<>();
    public Queue<SnapshotTask.BreakTask> prevClientBreakTasks = new ArrayDeque<>();
    private Queue<SnapshotTask.PlaceTask> placeTasks = new ArrayDeque<>();
    public Queue<SnapshotTask.PlaceTask> clientPlaceTasks = new ArrayDeque<>();
    public Queue<SnapshotTask.PlaceTask> prevClientPlaceTasks = new ArrayDeque<>();
    private Queue<BlockPos> blocksToCheck = new ArrayDeque<>();
    private List<BlockPos> locations = new CopyOnWriteArrayList<>();
    private List<BlockPos> toCheckForBreaking = new ArrayList<>();
    private List<BlockPos> toCheckForPlacing = new ArrayList<>();
    public Vec3d robotPos = null;
    public Vec3d prevRobotPos = null;
    public int leftToBreak;
    public int leftToPlace;
    private int blocksChecked = 0;
    private int total = 0;

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
    protected abstract boolean doPlaceTask(SnapshotTask.PlaceTask placeTask);

    /**
     * Executed if {@link #doPlaceTask} failed
     */
    protected abstract void cancelPlaceTask(SnapshotTask.PlaceTask placeTask);

    /**
     * @return true if block in wold is correct (is not to break) according to snapshot, false otherwise
     */
    protected abstract boolean isBlockCorrect(BlockPos blockPos);

    public abstract Box getBox();

    /**
     * @return Pos where flying item should be rendered
     */
    public Vec3d getPlaceTaskItemPos(SnapshotTask.PlaceTask placeTask) {
        Vec3d height = new Vec3d(placeTask.pos.subtract(tile.getBuilderPos()));
        double progress = placeTask.power * 1D / placeTask.getTarget(tile);
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
                        .map(SnapshotTask.BreakTask::getPos)
                        .map(Vec3d::new)
                        .reduce(Vec3d.ZERO, Vec3d::add)
                        .scale(1D / breakTasks.size());
                newRobotPos = new Vec3d(
                        newRobotPos.xCoord,
                        breakTasks.stream().map(SnapshotTask.BreakTask::getPos).mapToDouble(BlockPos::getY).max().orElse(newRobotPos.yCoord),
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

        placeTasks.removeIf(placeTask -> isBlockCorrect(placeTask.pos));
            if (blocksToCheck.isEmpty()) {
                blocksToCheck.addAll(getToPlace());
                blocksToCheck.addAll(getToBreak());
                total = blocksToCheck.size();
            }
            int i = 0;
            while (i < MAX_CHECK && !blocksToCheck.isEmpty()) {
                i++;
                blocksChecked++;
                if (blocksChecked >= total) {
                    blocksChecked = 0;
                    if (toCheckForPlacing.isEmpty() && placeTasks.isEmpty() && breakTasks.isEmpty() && ((toCheckForBreaking.isEmpty() && breakTasks.isEmpty()) || (!tile.canExcavate() && blocksToCheck.size() == toCheckForBreaking.size())))
                        return true;
                }
                BlockPos pos = blocksToCheck.poll();
                if (!isBlockCorrect(pos)) {
                    if (!tile.getWorldBC().isAirBlock(pos) && !toCheckForBreaking.contains(pos)) {
                        toCheckForBreaking.add(pos);
                    } else if (!toCheckForPlacing.contains(pos)) {
                        toCheckForPlacing.add(pos);
                    }
                }
                blocksToCheck.add(pos);
            }
            toCheckForBreaking.sort(Comparator.comparing(blockPos ->
                Math.pow(blockPos.getX() - getBox().center().getX(), 2) +
                        Math.pow(blockPos.getZ() - getBox().center().getZ(), 2) +
                        100_000 - Math.abs(blockPos.getY() - tile.getBuilderPos().getY()) * 100_000
            ));
            i = 0;
            Iterator<BlockPos> iterator = toCheckForBreaking.iterator();
            while (breakTasks.size() < MAX_QUEUE_SIZE && iterator.hasNext() && i < MAX_CHECK) {
                i++;
                BlockPos pos = iterator.next();
                if (BlockUtil.getFluidWithFlowing(tile.getWorldBC(), pos) == null) {
                    breakTasks.add(new SnapshotTask.BreakTask(pos, 0));
                }
                iterator.remove();
            }

        toCheckForPlacing.sort(Comparator.comparing(blockPos ->
                Math.pow(blockPos.getX() - getBox().center().getX(), 2) +
                        Math.pow(blockPos.getZ() - getBox().center().getZ(), 2) +
                        100_000 - Math.abs(blockPos.getY() - tile.getBuilderPos().getY()) * 100_000
        ));

        i = 0;
        iterator = toCheckForPlacing.iterator();
        while (placeTasks.size() < MAX_QUEUE_SIZE && iterator.hasNext() && i < MAX_CHECK) {
            i++;
            BlockPos pos = iterator.next();
            List<ItemStack> stacks = getToPlaceItems(pos);
            if (!stacks.isEmpty() && !stacks.contains(ItemStack.EMPTY) && canPlace(pos)) {
                placeTasks.add(new SnapshotTask.PlaceTask(pos, stacks, 0));
            }
            iterator.remove();
        }

        leftToBreak = toCheckForBreaking.size() + breakTasks.size();
        leftToPlace = toCheckForPlacing.size();

        if (!breakTasks.isEmpty()) {
            for (Iterator<SnapshotTask.BreakTask> breakIterator = breakTasks.iterator(); breakIterator.hasNext(); ) {
                SnapshotTask.BreakTask breakTask = breakIterator.next();
                long target = breakTask.getTarget(tile);
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
                    breakIterator.remove();
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
            for (Iterator<SnapshotTask.PlaceTask> placeIterator = placeTasks.iterator(); placeIterator.hasNext(); ) {
                SnapshotTask.PlaceTask placeTask = placeIterator.next();
                long target = placeTask.getTarget(tile);
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
                        toCheckForPlacing.add(placeTask.pos);
                    }
                    placeIterator.remove();
                }
            }
        }

        return false;
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
        IntStream.range(0, buffer.readInt()).mapToObj(i -> new SnapshotTask.BreakTask(buffer)).forEach(breakTasks::add);
        placeTasks.clear();
        IntStream.range(0, buffer.readInt()).mapToObj(i -> new SnapshotTask.PlaceTask(buffer)).forEach(placeTasks::add);
        leftToBreak = buffer.readInt();
        leftToPlace = buffer.readInt();
    }

    public void cancel() {
        breakTasks.forEach(breakTask ->
                tile.getBattery().addPower(
                        Math.min(breakTask.getTarget(tile), tile.getBattery().getCapacity() - tile.getBattery().getStored()),
                        false
                )
        );
        placeTasks.forEach(placeTask ->
                tile.getBattery().addPower(
                        Math.min(placeTask.getTarget(tile), tile.getBattery().getCapacity() - tile.getBattery().getStored()),
                        false
                )
        );
    }

    public T getTile() {
        return tile;
    }
}
