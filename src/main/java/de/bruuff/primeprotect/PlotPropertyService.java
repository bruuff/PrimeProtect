/*
 * This file is part of the plugin PrimeProtect for Sponge licensed under the MIT License (MIT).
 *
 * Copyright (c) 2015 Florian Brunzlaff
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bruuff.primeprotect;

import com.flowpowered.math.vector.Vector3d;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


public class PlotPropertyService implements PropertyService {

    private PrimeProtect plugin;

    private Database DB;

    public PlotPropertyService(PrimeProtect plugin) {
        this.plugin = plugin;
        DB = new Database(plugin.game);
        if( !DB.load() ) plugin.logger.info("Error loading Database.");
    }

    @Override
    public Plot getPlot(Location<World> location) {
        return getPlot(location.getPosition(), location.getExtent());
    }

    @Override
    public Plot getPlot(Vector3d position, World world) {
        final Connection conn = DB.getConnection();
        Plot plot = getPlot(conn, position, world);
        try {
            conn.close();
        } catch (SQLException e) { plugin.logger.info("Could not close connection"); }
        return plot;
    }

    @Override
    public Optional<Plot> getPlot(int id) {
        final Connection conn = DB.getConnection();
        Optional<Plot> optPlot = getPlot(conn, id);
        try {
            conn.close();
        } catch (SQLException e) { plugin.logger.info("Could not close connection"); }
        return optPlot;
    }

    @Override
    public boolean savePlot(Plot plot) {
        boolean success;
        final Connection conn = DB.getConnection();
        success = savePlot(conn, plot);
        try {
            conn.close();
        } catch (SQLException e) { plugin.logger.info("Could not close connection"); }
        return success;
    }

    private Plot getPlot(Connection conn, Vector3d position, World world){
        String sql = "SELECT * FROM primePlot WHERE (minX <= " + position.getFloorX() + ") " +
                " AND (minZ <= " + position.getFloorZ() + ") " +
                " AND (maxX >= " + position.getFloorX() + ") " +
                " AND (maxZ >= " + position.getFloorZ() + ");";
        PreparedStatement stmt = null;
        ResultSet resultSet = null;
        Plot plot = Plot.wilderness(world); // If everything else failes, wilderness.
        try {
            stmt = conn.prepareStatement(sql);
            resultSet = stmt.executeQuery();
            if(resultSet.isBeforeFirst()){

                Map<Integer, Plot> plotAndParents = new TreeMap<>();

                while (resultSet.next()){
                    Optional<PlotOwner> optPlotOwner = Optional.empty();
                    if(resultSet.getString("owner") != null) optPlotOwner = getOptPlotOwner(conn, resultSet.getString("owner"));

                    World plotWorld;
                    if(plugin.game.getServer().getWorld(UUID.fromString(resultSet.getString("world"))).isPresent()){
                        plotWorld = plugin.game.getServer().getWorld(UUID.fromString(resultSet.getString("world"))).get();
                    }else{
                        plotWorld = plugin.game.getServer().getWorlds().iterator().next();
                    }

                    //All plots in bounding box are first a possible plot
                    Plot possiblePlot = new Plot(resultSet.getInt("id"), optPlotOwner, plotWorld, resultSet.getString("vertices"), resultSet.getInt("depth"));

                    //Only put them in the list if we are really inside.
                    if(possiblePlot.contains(new PlotPoint(position.getFloorX(), position.getFloorZ()))){
                        plotAndParents.put(resultSet.getInt("depth"), possiblePlot);
                    }
                }
                for(Map.Entry<Integer,Plot> entry: plotAndParents.entrySet()){
                    if(entry.getKey() > 1){
                        if(plotAndParents.containsKey(entry.getKey() - 1)){
                            entry.getValue().setParent(plotAndParents.get(entry.getKey() - 1));
                        }else{
                            plugin.logger.warn("Parent plot missing in Database request.");
                        }
                    }
                    plot = entry.getValue(); //As they are sorted by depth, in the end this will be the deepest plot, containing all its parents
                }

            } //if not, then no Plot there.

        }catch (SQLException e) {
            plugin.logger.error("SQLException in load");
        }finally{
            try {
                if (resultSet != null) resultSet.close();
                if (stmt != null) stmt.close();
            }catch (SQLException e){
                plugin.logger.error("SQLException: Could not close ResultSet");
            }
        }
        return plot;
    }

    private Optional<PlotOwner> getOptPlotOwner(Connection conn, String ownerString){
        Optional<PlotOwner> optPlotOwner = Optional.empty();
        if(ownerString.startsWith("P:")){
            optPlotOwner = Optional.of( new PlotOwner(UUID.fromString(ownerString.substring(2))) );
        }else if(ownerString.startsWith("G:")){
            Optional<Group> optGroup = getGroup(conn, ownerString.substring(2));
            if(optGroup.isPresent()){
                optPlotOwner = Optional.of(new PlotOwner(optGroup.get()));
            }
        }
        return optPlotOwner;
    }

    private Optional<Plot> getPlot(Connection conn, int id){
        String sql = "SELECT * FROM plot WHERE id = " + id + ";";
        Optional<Plot> optPlot = Optional.empty();
        try {
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet resultSet = stmt.executeQuery();
            if(resultSet.isBeforeFirst()){
                if (resultSet.next()) {
                    Optional<PlotOwner> optPlotOwner = getOptPlotOwner(conn, resultSet.getString("owner"));

                    World plotWorld;
                    if(plugin.game.getServer().getWorld(UUID.fromString(resultSet.getString("world"))).isPresent()){
                        plotWorld = plugin.game.getServer().getWorld(UUID.fromString(resultSet.getString("world"))).get();
                    }else{
                        plotWorld = plugin.game.getServer().getWorlds().iterator().next();
                    }
                    Optional<Plot> optParentPlot = Optional.empty();
                    int parentId = resultSet.getInt("parent");
                    if(parentId > 0) optParentPlot = getPlot(conn, parentId);
                    optPlot = Optional.of(new Plot(id,
                            optPlotOwner,
                            plotWorld,
                            resultSet.getString("vertices"),
                            new PlotPoint(resultSet.getInt("centroidX"), resultSet.getInt("centroidZ")),
                            resultSet.getInt("depth"),
                            optParentPlot,
                            resultSet.getInt("minX"),
                            resultSet.getInt("minZ"),
                            resultSet.getInt("maxX"),
                            resultSet.getInt("maxZ")
                    ));
                }
            }
            resultSet.close();
            stmt.close();
        }catch (SQLException e) {
            plugin.logger.error("SQLException in load");
        }
        return optPlot;
    }


    public boolean savePlot(Connection conn, Plot plot){
        boolean success;
        String sql = "SELECT * FROM primePlot WHERE id = " + plot.getId() + " ;";
        try {
            PreparedStatement stmt = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
            ResultSet resultSet = stmt.executeQuery();
            Boolean insert = false;
            if(!resultSet.isBeforeFirst()) insert = true;

            if(insert){
                resultSet.moveToInsertRow();
            }else{
                resultSet.next();
            }
            resultSet.updateInt("id", plot.getId());

            if(plot.getOwner().isPresent()){
                resultSet.updateString("owner", plot.getOwner().get().serialize());
            }else{
                resultSet.updateNull("owner");
            }

            resultSet.updateString("world", plot.getWorld().getUniqueId().toString());
           // resultSet.updateString("actualVertices", plot.getVerticesString(plot.getActualPoints()));
            resultSet.updateString("vertices", plot.getVerticesString());

            resultSet.updateInt("centroidX", (int) Math.floor(plot.getCentroid().getX()));
            resultSet.updateInt("centroidZ", (int) Math.floor(plot.getCentroid().getZ()));
            if(plot.getParent().isPresent())
                resultSet.updateInt("parent", plot.getParent().get().getId());
            else
                resultSet.updateNull("parent");
            resultSet.updateInt("depth", plot.getDepth());

            if(plot.getVertices().size() > 0){
                resultSet.updateInt("minX", plot.getMinX());
                resultSet.updateInt("minZ", plot.getMinZ());
                resultSet.updateInt("maxX", plot.getMaxX());
                resultSet.updateInt("maxZ", plot.getMaxZ());
            }
            if(insert){
                resultSet.insertRow();
                resultSet.moveToCurrentRow();
            }else{
                resultSet.updateRow();
            }
            resultSet.close();
            stmt.close();
            success = true;
        }catch (SQLException e) {
            plugin.logger.error("SQLException in save");
            success = false;
        }
        return success;
    }

    private int getAutoIncrement(Connection conn, String table, String column) throws SQLException {
        String sql = "SELECT MAX(" + column + ") as max_val FROM " + table + ";";
        int autoIncrement = 0;
        PreparedStatement stmt = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
        ResultSet resultSet = stmt.executeQuery();
        if (resultSet.next()) {
            autoIncrement = resultSet.getInt("max_val") + 1;
        }
        resultSet.close();
        stmt.close();
        return autoIncrement;
    }


    public Plot createPlot(Optional<PlotOwner> optPlotOwner, World world, Plot parent){
        final Connection conn = DB.getConnection();
        Plot newPlot = null;
        int newId = 0;
        try {
            newId = getAutoIncrement(conn, "primePlot", "id");
        }catch (SQLException e) {
            plugin.logger.error("SQLException getting auto increment.");
        }
        if (newId > 0) {
            newPlot = new Plot(newId, optPlotOwner, world, parent);
            savePlot(conn, newPlot);
        }
        try {
            conn.close();
        } catch (SQLException e) { plugin.logger.info("Could not close connection"); }
        return newPlot;
    }

    @Override
    public Optional<Group> getGroup(String name) {
        final Connection conn = DB.getConnection();
        Optional<Group> optPlot = getGroup(conn, name);
        try {
            conn.close();
        } catch (SQLException e) { plugin.logger.info("Could not close connection"); }
        return optPlot;
    }

    private Optional<Group> getGroup(Connection conn, String name){
        String sql = "SELECT * FROM primeGroup WHERE name = '" + name + "';";
        Optional<Group> optGroup = Optional.empty();
        try {
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet resultSet = stmt.executeQuery();
            if(resultSet.isBeforeFirst()){
                if (resultSet.next()) {
                    Map<UUID, Rank> users = new HashMap<>();
                    String serializedGroup = resultSet.getString("users");
                    for (String part: serializedGroup.split("\\|")){
                        if(part.contains(",")){
                            users.put(UUID.fromString(part.split(",")[0]), Rank.valueOf(part.split(",")[1]));
                        }
                    }
                    optGroup = Optional.of(new Group(resultSet.getString("name"),
                            users,
                            Optional.of(TextColors.AQUA)
                    ));
                }
            }
            resultSet.close();
            stmt.close();
        }catch (SQLException e) {
            plugin.logger.error("SQLException in load");
        }
        return optGroup;
    }

    @Override
    public boolean deletePlot(int id) {
        boolean success;
        final Connection conn = DB.getConnection();
        success = deletePlot(conn, id);
        try {
            conn.close();
        } catch (SQLException e) { plugin.logger.info("Could not close connection"); }
        return success;
    }

    private boolean deletePlot(Connection conn, int id) {
        boolean success;
        String sql = "DELETE FROM primePlot WHERE id = " + id + " ;";
        try {
            PreparedStatement stmt = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
            stmt.executeUpdate();
            //ResultSet resultSet = stmt.executeQuery();
            //resultSet.deleteRow();
            //resultSet.close();
            stmt.close();
            success = true;
        }catch (SQLException e) {
            plugin.logger.error("SQLException in delete (" + sql + ")");
            success = false;
        }
        return success;
    }

    @Override
    public Group createGroup(String name, UUID founderUUID) {
        final Connection conn = DB.getConnection();
        Optional<Group> existingGroup = getGroup(conn, name); //This gets done twice (in command and here), revisit later.
        if(existingGroup.isPresent()){
            return null;
        }
        Group newGroup = new Group(name, founderUUID);
        saveGroup(conn, newGroup);
        try {
            conn.close();
        } catch (SQLException e) { plugin.logger.info("Could not close connection"); }
        return newGroup;
    }

    @Override
    public boolean deleteGroup(String name) {
        return false;
    }

    @Override
    public boolean saveGroup(Group group) {
        boolean success;
        final Connection conn = DB.getConnection();
        success = saveGroup(conn, group);
        try {
            conn.close();
        } catch (SQLException e) { plugin.logger.info("Could not close connection"); }
        return success;
    }

    private boolean saveGroup(Connection conn, Group group){
        boolean success;
        String sql = "SELECT * FROM primeGroup WHERE name = '" + group.getName() + "' ;";
        try {
            PreparedStatement stmt = conn.prepareStatement(sql, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
            ResultSet resultSet = stmt.executeQuery();
            Boolean insert = false;
            if(!resultSet.isBeforeFirst()) insert = true;
            if(insert){
                resultSet.moveToInsertRow();
            }else{
                resultSet.next();
            }
            resultSet.updateString("name", group.getName());
            resultSet.updateString("users", group.getSerializedUsers());

            if(group.getChatColor().isPresent()){
                resultSet.updateString("chatColor", group.getChatColor().get().toString());
            }else{
                resultSet.updateNull("chatColor");
            }

            if(insert){
                resultSet.insertRow();
                resultSet.moveToCurrentRow();
            }else{
                resultSet.updateRow();
            }
            resultSet.close();
            stmt.close();
            success = true;
        }catch (SQLException e) {
            plugin.logger.error("SQLException in save");
            e.printStackTrace();
            success = false;
        }
        return success;
    }
}
