package net.nanaky.ultimate_minecarts.common.utils;

import com.google.common.collect.Maps;

import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.nanaky.ultimate_minecarts.mixin.EntityShapeContextAccessor;

import java.util.Map;
import java.util.Set;

import static net.minecraft.core.Direction.*;

public class MinecartHelper {

    public static final VoxelShape WALL_SHAPE = Shapes.box(0.48, 0.5, 0.48, 0.52, 1.2, 0.52);
    public static final Map<Set<VoxelShape>, VoxelShape> WALL_SHAPES_UNION = new Object2ReferenceOpenHashMap<>();

    public static final Map<Direction, VoxelShape> DIRECTION_2_SHAPE = Util.make(
            Maps.newEnumMap(Direction.class), map -> {
                map.put(EAST,  getShapeForDirection(EAST));
                map.put(WEST,  getShapeForDirection(WEST));
                map.put(NORTH, getShapeForDirection(NORTH));
                map.put(SOUTH, getShapeForDirection(SOUTH));
            });

    public static final Map<RailShape, Set<Direction>> DERAIL_FIX_WALLS = Util.make(
            Maps.newEnumMap(RailShape.class), map -> {
                map.put(RailShape.NORTH_WEST,      new ObjectArraySet<>(new Direction[]{SOUTH, EAST}));
                map.put(RailShape.NORTH_EAST,      new ObjectArraySet<>(new Direction[]{SOUTH, WEST}));
                map.put(RailShape.SOUTH_EAST,      new ObjectArraySet<>(new Direction[]{NORTH, WEST}));
                map.put(RailShape.SOUTH_WEST,      new ObjectArraySet<>(new Direction[]{NORTH, EAST}));
                map.put(RailShape.NORTH_SOUTH,     new ObjectArraySet<>(new Direction[]{WEST,  EAST}));
                map.put(RailShape.EAST_WEST,       new ObjectArraySet<>(new Direction[]{NORTH, SOUTH}));
                map.put(RailShape.ASCENDING_EAST,  new ObjectArraySet<>(new Direction[]{NORTH, SOUTH}));
                map.put(RailShape.ASCENDING_WEST,  new ObjectArraySet<>(new Direction[]{NORTH, SOUTH}));
                map.put(RailShape.ASCENDING_NORTH, new ObjectArraySet<>(new Direction[]{WEST,  EAST}));
                map.put(RailShape.ASCENDING_SOUTH, new ObjectArraySet<>(new Direction[]{WEST,  EAST}));
            });

    private static VoxelShape getShapeForDirection(Direction direction) {
        return WALL_SHAPE.move(
                direction.getStepX(),
                direction.getStepY(),
                direction.getStepZ());
    }

    private static VoxelShape getUnionShape(Set<VoxelShape> shapes) {
        VoxelShape total = Shapes.empty();
        for (VoxelShape shape : shapes)
            total = total.isEmpty() ? shape : Shapes.or(total, shape);
        return total;
    }

    public static VoxelShape getCollisionShape(VoxelShape railCollisionShape,
                                               RailShape railShape,
                                               BlockPos pos,
                                               CollisionContext context) {
        if (!(context instanceof EntityShapeContextAccessor entityContext))
            return railCollisionShape;

        Entity entity = entityContext.getEntity();
        if (!(entity instanceof AbstractMinecart cart))
            return railCollisionShape;

        if (!(cart instanceof MinecartPhysicsAccess physicsAccess) || !physicsAccess.isSelfMovingOnRail())
            return railCollisionShape;

        Set<Direction> derailFixWalls = DERAIL_FIX_WALLS.get(railShape);
        if (derailFixWalls == null)
            return railCollisionShape;

        AABB offsetCartBox = cart.getBoundingBox().move(
                -pos.getX(), -pos.getY(), -pos.getZ());

        Set<VoxelShape> selectedWalls = new ObjectArraySet<>();
        for (Direction direction : derailFixWalls) {
            VoxelShape wallShape = DIRECTION_2_SHAPE.get(direction);
            Direction.Axis axis = direction.getAxis();
            double distanceToHitbox = direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                    ? offsetCartBox.min(axis)  - wallShape.max(axis)
                    : wallShape.min(axis)      - offsetCartBox.max(axis);

            if (distanceToHitbox > 0)
                selectedWalls.add(wallShape);
        }

        if (selectedWalls.isEmpty())
            return railCollisionShape;

        if (!railCollisionShape.isEmpty())
            selectedWalls.add(railCollisionShape);

        return WALL_SHAPES_UNION.computeIfAbsent(selectedWalls, MinecartHelper::getUnionShape);
    }
}