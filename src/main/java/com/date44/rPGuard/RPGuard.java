
package com.date44.rPGuard;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class RPGuard extends JavaPlugin implements Listener {

    private Connection connection;

    private long restoreSeconds;
    private int batchSize;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        restoreSeconds = getConfig().getLong(
                "restore-seconds", 300
        );
        batchSize = getConfig().getInt(
                "batch-size", 100
        );

        try {
            if (!getDataFolder().exists()
                    && !getDataFolder().mkdirs()) {
                throw new IllegalStateException(
                        "Could not create plugin directory"
                );
            }

            File dbFile = new File(
                    getDataFolder(), "changes.db"
            );

            connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + dbFile.getAbsolutePath()
            );

            try (Statement statement =
                         connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS block_changes (
                        id INTEGER PRIMARY KEY,
                        world_id TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        original_data TEXT NOT NULL,
                        restore_at INTEGER NOT NULL,
                        UNIQUE(world_id, x, y, z)
                    )
                    """);

                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_restore_at
                    ON block_changes(restore_at)
                    """);
            }
        } catch (SQLException | IllegalStateException e) {
            getLogger().severe(
                    "Could not initialize database: "
                            + e.getMessage()
            );
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(
                this,
                this::restoreDueBlocks,
                20L,
                20L
        );

        getLogger().info("RPGuard enabled.");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().hasPermission("rpguard.build")) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("rpguard.mine")) {
            event.setCancelled(true);
            return;
        }
        if (player.hasPermission("rpguard.mine.permanent")) {
            return;
        }

        Block block = event.getBlock();
        BlockData originalData = block.getBlockData();

        try {
            saveChange(
                    block.getWorld().getUID(),
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    originalData.getAsString(),
                    System.currentTimeMillis() / 1000
                            + restoreSeconds
            );
        } catch (SQLException e) {
            event.setCancelled(true);
            getLogger().severe(
                    "Could not save block change: "
                            + e.getMessage()
            );
        }
    }

    private void saveChange(
            UUID worldId,
            int x,
            int y,
            int z,
            String originalData,
            long restoreAt
    ) throws SQLException {

        String sql = """
            INSERT INTO block_changes
                (world_id, x, y, z, original_data, restore_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(world_id, x, y, z)
            DO NOTHING
            """;

        try (PreparedStatement ps =
                     connection.prepareStatement(sql)) {
            ps.setString(1, worldId.toString());
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.setString(5, originalData);
            ps.setLong(6, restoreAt);
            ps.executeUpdate();
        }
    }

    private void restoreDueBlocks() {
        long now = System.currentTimeMillis() / 1000;

        String sql = """
            SELECT id, world_id, x, y, z, original_data
            FROM block_changes
            WHERE restore_at <= ?
            ORDER BY restore_at
            LIMIT ?
            """;

        try (PreparedStatement ps =
                     connection.prepareStatement(sql)) {
            ps.setLong(1, now);
            ps.setInt(2, batchSize);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    UUID worldId = UUID.fromString(
                            rs.getString("world_id")
                    );

                    World world = Bukkit.getWorld(worldId);

                    if (world == null) {
                        continue;
                    }

                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");

                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }

                    Block block = world.getBlockAt(x, y, z);

                    if (block.getType().isAir()) {
                        BlockData original =
                                Bukkit.createBlockData(
                                        rs.getString("original_data")
                                );

                        block.setBlockData(original, false);
                    }

                    deleteChange(id);
                }
            }
        } catch (SQLException e) {
            getLogger().severe(
                    "Restore error: " + e.getMessage()
            );
        }
    }

    private void deleteChange(long id) throws SQLException {
        try (PreparedStatement ps =
                     connection.prepareStatement(
                             "DELETE FROM block_changes WHERE id = ?"
                     )) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public void onDisable() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                getLogger().warning(
                        "Database close failed: "
                                + e.getMessage()
                );
            }
        }
    }
}
