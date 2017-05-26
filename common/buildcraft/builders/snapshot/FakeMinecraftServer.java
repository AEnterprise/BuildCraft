/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedPlayerList;
import net.minecraft.server.management.PlayerList;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;

public class FakeMinecraftServer extends MinecraftServer {

    public FakeMinecraftServer() {
        super(new File("FakeWorld"), null, null, null, null, null, null);
    }

    @Override
    public ServerCommandManager createCommandManager() {
        return null;
    }

    @Override
    public boolean init() throws IOException {
        return false;
    }

    @Override
    public boolean canStructuresSpawn() {
        return false;
    }

    @Override
    public GameType getGameType() {
        return null;
    }

    @Override
    public EnumDifficulty getDifficulty() {
        return null;
    }

    @Override
    public boolean isHardcore() {
        return false;
    }

    @Override
    public int getOpPermissionLevel() {
        return 0;
    }

    @Override
    public boolean shouldBroadcastRconToOps() {
        return false;
    }

    @Override
    public boolean shouldBroadcastConsoleToOps() {
        return false;
    }

    @Override
    public boolean isDedicatedServer() {
        return false;
    }

    @Override
    public boolean shouldUseNativeTransport() {
        return false;
    }

    @Override
    public boolean isCommandBlockEnabled() {
        return false;
    }

    @Override
    public String shareToLAN(GameType type, boolean allowCheats) {
        return null;
    }

    @Override
    public PlayerList getPlayerList() {
        return new FakePlayerList(this);
    }
}
