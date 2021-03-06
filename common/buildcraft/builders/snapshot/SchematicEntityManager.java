/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.Lists;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fluids.FluidStack;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicEntity;
import buildcraft.api.schematics.SchematicEntityContext;
import buildcraft.api.schematics.SchematicEntityFactory;
import buildcraft.api.schematics.SchematicEntityFactoryRegistry;

import buildcraft.lib.dimension.FakeWorldServer;

public class SchematicEntityManager {
    public static ISchematicEntity<?> getSchematicEntity(SchematicEntityContext context) {
        for (SchematicEntityFactory<?> schematicEntityFactory : Lists.reverse(SchematicEntityFactoryRegistry.getFactories())) {
            if (schematicEntityFactory.predicate.test(context)) {
                ISchematicEntity<?> schematicEntity = schematicEntityFactory.supplier.get();
                schematicEntity.init(context);
                return schematicEntity;
            }
        }
        return null;
    }

    public static ISchematicEntity<?> getSchematicEntity(World world,
                                                         BlockPos basePos,
                                                         Entity entity) {
        SchematicEntityContext context = new SchematicEntityContext(world, basePos, entity);
        ISchematicEntity<?> schematicEntity = getSchematicEntity(context);
        if (schematicEntity != null) {
            return schematicEntity;
        }
        return null;
    }

    public static Pair<List<List<ItemStack>>, List<List<FluidStack>>> computeRequired(Blueprint blueprint) {
        List<List<ItemStack>> requiredItems = new ArrayList<>(
            Collections.nCopies(
                blueprint.entities.size(),
                Collections.emptyList()
            )
        );
        List<List<FluidStack>> requiredFluids = new ArrayList<>(
            Collections.nCopies(
                blueprint.entities.size(),
                Collections.emptyList()
            )
        );
        FakeWorldServer world = FakeWorldServer.INSTANCE;
        world.uploadBlueprint(blueprint, true);
        int i = 0;
        for (ISchematicEntity<?> schematicEntity : blueprint.entities) {
            Entity entity = schematicEntity.buildWithoutChecks(world, FakeWorldServer.BLUEPRINT_OFFSET);
            if (entity != null) {
                world.editable = false;
                SchematicEntityContext schematicEntityContext = new SchematicEntityContext(world, FakeWorldServer.BLUEPRINT_OFFSET, entity);
                requiredItems.set(i, schematicEntity.computeRequiredItems(schematicEntityContext));
                requiredFluids.set(i, schematicEntity.computeRequiredFluids(schematicEntityContext));
                world.editable = true;
                world.removeEntity(entity);
            }
            i++;
        }
        world.clear();
        return Pair.of(requiredItems, requiredFluids);
    }

    @Nonnull
    public static NBTTagCompound writeToNBT(ISchematicEntity<?> schematicEntity) {
        NBTTagCompound schematicEntityTag = new NBTTagCompound();
        schematicEntityTag.setString(
            "name",
            SchematicEntityFactoryRegistry
                .getFactoryByInstance(schematicEntity)
                .name
                .toString()
        );
        schematicEntityTag.setTag("data", schematicEntity.serializeNBT());
        return schematicEntityTag;
    }

    @Nonnull
    public static ISchematicEntity<?> readFromNBT(NBTTagCompound schematicEntityTag) throws InvalidInputDataException {
        ResourceLocation name = new ResourceLocation(schematicEntityTag.getString("name"));
        SchematicEntityFactory<?> factory = SchematicEntityFactoryRegistry.getFactoryByName(name);
        if (factory == null) {
            throw new InvalidInputDataException("Unknown schematic type " + name);
        }
        ISchematicEntity<?> schematicEntity = factory.supplier.get();
        NBTTagCompound data = schematicEntityTag.getCompoundTag("data");
        try {
            schematicEntity.deserializeNBT(data);
            return schematicEntity;
        } catch (InvalidInputDataException e) {
            throw new InvalidInputDataException("Failed to load the schematic from " + data, e);
        }
    }
}
