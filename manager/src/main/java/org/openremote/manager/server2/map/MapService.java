package org.openremote.manager.server2.map;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openremote.container.Container;
import org.openremote.container.ContainerService;
import org.openremote.manager.server2.ManagerWebService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.openremote.container.ContainerRuntime.JSON;
import static org.openremote.manager.server.Constants.*;

public class MapService implements ContainerService {

    private static final Logger LOG = Logger.getLogger(MapService.class.getName());

    public static final String MAP_TILES_PATH = "MAP_TILES_PATH";
    public static final String MAP_TILES_PATH_DEFAULT = "sample/mapdata.mbtiles";
    public static final String MAP_SETTINGS_PATH = "MAP_SETTINGS_PATH";
    public static final String MAP_SETTINGS_PATH_DEFAULT = "sample/mapsettings.json";

    // Shared SQL connection is fine concurrently in SQLite
    protected Connection connection;

    protected boolean devMode;
    protected Path mapTilesPath;
    protected Path mapSettingsPath;
    protected ObjectNode mapSettings;

    @Override
    public void prepare(Container container) {
        this.devMode = container.getConfigBoolean(DEV_MODE, DEV_MODE_DEFAULT);

        mapTilesPath = Paths.get(container.getConfig(MAP_TILES_PATH, MAP_TILES_PATH_DEFAULT));
        if (!Files.isRegularFile(mapTilesPath)) {
            throw new IllegalStateException(
                "MapWidget tiles data file not found: " + mapTilesPath.toAbsolutePath()
            );
        }

        mapSettingsPath = Paths.get(container.getConfig(MAP_SETTINGS_PATH, MAP_SETTINGS_PATH_DEFAULT));
        if (!Files.isRegularFile(mapSettingsPath)) {
            throw new IllegalStateException(
                "MapWidget settings file not found: " + mapSettingsPath.toAbsolutePath()
            );
        }

        container.getService(ManagerWebService.class).getApiSingletons().add(
            new MapResource(this)
        );
    }

    @Override
    public void start(Container container) {
        LOG.info("Starting map service with tile data: " + mapTilesPath.toAbsolutePath());
        try {
            Class.forName(org.sqlite.JDBC.class.getName());
            connection = DriverManager.getConnection("jdbc:sqlite:" + mapTilesPath.toAbsolutePath());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        readMapSettings();
    }

    @Override
    public void stop(Container container) {
        LOG.info("Stopping map service...");
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                LOG.warning("Error closing connection: " + ex);
            }
        }
    }

    public ObjectNode getMapSettings(String tileUrl) {

        // Refresh map settings for every request in dev mode, cache it in production
        if (devMode) {
            readMapSettings();
        }

        ObjectNode settingsCopy = mapSettings.deepCopy();
        ArrayNode tilesArray = JSON.createArrayNode();
        tilesArray.add(tileUrl);
        settingsCopy.with("style").with("sources").with("vector_tiles").set("tiles", tilesArray);
        return settingsCopy;
    }

    public byte[] getMapTile(int zoom, int column, int row) throws Exception {
        // Flip y, oh why
        row = new Double(Math.pow(2, zoom) - 1 - row).intValue();

        PreparedStatement query = null;
        ResultSet result = null;
        try {
            query = connection.prepareStatement(
                "select TILE_DATA from TILES where ZOOM_LEVEL = ? and TILE_COLUMN = ? and TILE_ROW = ?"
            );

            int index = 0;
            query.setInt(++index, zoom);
            query.setInt(++index, column);
            query.setInt(++index, row);

            result = query.executeQuery();

            if (result.next()) {
                return result.getBytes(1);
            } else {
                return null;
            }
        } finally {
            closeQuietly(query, result);
        }
    }

    protected void readMapSettings() {

        // Mix settings from file with database metadata, and some hardcoded magic
        try {
            String mapSettingsJson = new String(Files.readAllBytes(mapSettingsPath), "utf-8");
            mapSettings = JSON.readValue(mapSettingsJson, ObjectNode.class);
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing map settings: " + mapSettingsPath.toAbsolutePath(), ex);
        }

        ObjectNode style = mapSettings.with("style");

        style.put("version", 8);

        style.put("glyphs", STATIC_PATH + "/fonts/{fontstack}/{range}.pbf");

        ObjectNode sources = JSON.createObjectNode();
        style.set("sources", sources);

        ObjectNode vectorTiles = JSON.createObjectNode();
        sources.set("vector_tiles", vectorTiles);

        vectorTiles.put("type", "vector");

        PreparedStatement query = null;
        ResultSet result = null;
        try {
            query = connection.prepareStatement("select NAME, VALUE from METADATA");
            result = query.executeQuery();

            Map<String, String> resultMap = new HashMap<>();
            while (result.next()) {
                resultMap.put(result.getString(1), result.getString(2));
            }

            if (resultMap.size() == 0) {
                throw new RuntimeException("Missing JSON metadata in map database");
            }

            ObjectNode metadataJson = JSON.readValue(resultMap.get("json"), ObjectNode.class);
            vectorTiles.set("vector_layers", metadataJson.get("vector_layers"));
            vectorTiles.put("maxzoom", Integer.valueOf(resultMap.get("maxzoom")));
            vectorTiles.put("minzoom", Integer.valueOf(resultMap.get("minzoom")));
            vectorTiles.put("attribution", resultMap.get("attribution"));

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeQuietly(query, result);
        }
    }

    protected void closeQuietly(PreparedStatement query, ResultSet result) {
        try {
            if (result != null) {
                result.close();
            }
            if (query != null) {
                query.close();
            }
        } catch (Exception ex) {
            LOG.warning("Error closing query/result: " + ex);
        }
    }

}