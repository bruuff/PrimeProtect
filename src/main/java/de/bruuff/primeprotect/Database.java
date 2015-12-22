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

import org.spongepowered.api.Game;
import org.spongepowered.api.service.sql.SqlService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Database {

    public static final String JDBC_URL = "jdbc:h2:./mods/PrimeProtect/data.db";
    private SqlService sql;
    private Game game;

    public Database(Game game) {
        this.game = game;
    }

    public boolean load(){
        Connection conn = getConnection();
        if (conn == null) return false;

        String sql = "CREATE TABLE IF NOT EXISTS primePlot (" +
                "id INT(11) UNSIGNED AUTO_INCREMENT PRIMARY KEY, " +
                "owner VARCHAR(100) NULL, " +
                "world VARCHAR(100) NOT NULL, " +
                "vertices TEXT NOT NULL DEFAULT ''," +
                "centroidX INT(11) NULL," +
                "centroidZ INT(11) NULL," +
                "parent INT(11) NULL  DEFAULT NULL," +
                "depth INT(11) NOT NULL," +
                "settings TEXT NOT NULL DEFAULT ''," +
                "minX INT(11) NULL," +
                "minZ INT(11) NULL," +
                "maxX INT(11) NULL," +
                "maxZ INT(11) NULL" +
                "); " +
                "CREATE TABLE IF NOT EXISTS primeGroup (" +
                "name VARCHAR(32) PRIMARY KEY, " +
                "users TEXT NOT NULL," +
                "chatColor VARCHAR(20) NULL" +
                "); ";
        boolean worked = query(conn, sql);

        try {
            conn.close();
        }  catch (SQLException e) {
            e.printStackTrace();
            worked = false;
        }
        return worked;
    }

    public boolean query(Connection conn, String query){
        boolean worked = true;
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(query);
            stmt.execute();
        } catch (SQLException e) {e.printStackTrace(); worked = false;} finally {
            try {
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                e.printStackTrace();
                worked = false;
            }
        }
        return worked;
    }

    public Connection getConnection(){
        try {
            DataSource dataSource = getDataSource(JDBC_URL);
            if (dataSource != null ) return dataSource.getConnection();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public DataSource getDataSource(String jdbcUrl){
        if (sql == null) {
            sql = game.getServiceManager().provide(SqlService.class).get();
        }
        try {
            return sql.getDataSource(jdbcUrl);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

}
