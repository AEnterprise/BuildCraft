/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StringUtilBC;
import buildcraft.lib.net.PacketBufferBC;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Snapshot implements INBTSerializable<NBTTagCompound> {
    public Header header = new Snapshot.Header();
    public BlockPos size;
    public EnumFacing facing;
    public BlockPos offset;

    public Snapshot(Header header) {
        this.header = header;
    }

    public Snapshot() {
    }

    public static Snapshot create(EnumSnapshotType type) {
        switch (type) {
            case TEMPLATE:
                return new Template();
            case BLUEPRINT:
                return new Blueprint();
        }
        throw new UnsupportedOperationException();
    }

    public abstract <T extends ITileForSnapshotBuilder> SnapshotBuilder<T> createBuilder(T tile);

    public static NBTTagCompound writeToNBT(Snapshot snapshot) {
        NBTTagCompound nbt = snapshot.serializeNBT();
        nbt.setTag("type", NBTUtilBC.writeEnum(snapshot.getType()));
        return nbt;
    }

    public static Snapshot readFromNBT(NBTTagCompound nbt) {
        Snapshot snapshot = Snapshot.create(NBTUtilBC.readEnum(nbt.getTag("type"), EnumSnapshotType.class));
        snapshot.deserializeNBT(nbt);
        return snapshot;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("header", header.serializeNBT());
        nbt.setTag("size", NBTUtil.createPosTag(size));
        nbt.setTag("facing", NBTUtilBC.writeEnum(facing));
        nbt.setTag("offset", NBTUtil.createPosTag(offset));
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        header.deserializeNBT(nbt.getCompoundTag("header"));
        size = NBTUtil.getPosFromTag(nbt.getCompoundTag("size"));
        facing = NBTUtilBC.readEnum(nbt.getTag("facing"), EnumFacing.class);
        offset = NBTUtil.getPosFromTag(nbt.getCompoundTag("offset"));
    }

    abstract public EnumSnapshotType getType();

    public static class Header implements INBTSerializable<NBTTagCompound> {
        public UUID id;
        public UUID owner;
        public Date created;
        public String name;

        public Header(UUID id, UUID owner, Date created, String name) {
            this.id = id;
            this.owner = owner;
            this.created = created;
            this.name = name;
        }

        public Header() {
        }

        public String getFileName() {
            return Stream.of(
                id,
                owner,
                created,
                name
            )
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(StringUtilBC::replaceCharactersForFilename)
                .collect(Collectors.joining(";"));
        }

        @Override
        public NBTTagCompound serializeNBT() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setUniqueId("id", id);
            nbt.setUniqueId("owner", owner);
            nbt.setLong("created", created.getTime());
            nbt.setString("name", name);
            return nbt;
        }

        @Override
        public void deserializeNBT(NBTTagCompound nbt) {
            id = nbt.getUniqueId("id");
            owner = nbt.getUniqueId("owner");
            created = new Date(nbt.getLong("created"));
            name = nbt.getString("name");
        }

        public void writeToByteBuf(PacketBufferBC buffer) {
            buffer.writeUniqueId(id);
            buffer.writeUniqueId(owner);
            buffer.writeLong(created.getTime());
            buffer.writeString(name);
        }

        public void readFromByteBuf(PacketBufferBC buffer) {
            id = buffer.readUniqueId();
            owner = buffer.readUniqueId();
            created = new Date(buffer.readLong());
            name = buffer.readString(Integer.MAX_VALUE / 4);
        }

        public EntityPlayer getOwnerPlayer(World world) {
            return world.getPlayerEntityByUUID(owner);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Header header = (Header) o;

            if (!id.equals(header.id)) {
                return false;
            }
            if (!owner.equals(header.owner)) {
                return false;
            }
            if (!created.equals(header.created)) {
                return false;
            }
            return name.equals(header.name);
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + owner.hashCode();
            result = 31 * result + created.hashCode();
            result = 31 * result + name.hashCode();
            return result;
        }
    }
}
