package me.botsko.prism.database;

import me.botsko.prism.Prism;
import me.botsko.prism.database.derby.DerbyPrismDataSource;
import me.botsko.prism.database.mysql.MySQLPrismDataSource;
import me.botsko.prism.database.sql.SQLPrismDataSourceUpdater;
import me.botsko.prism.database.sqlite.SQLitePrismDataSource;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Created for use for the Add5tar MC Minecraft server
 * Created by benjamincharlton on 4/04/2019.
 */
public class PrismDatabaseFactory {

    private static PrismDataSource database = null;

    public static void createDefaultConfig(Configuration configuration) {
        ConfigurationSection mysql;
        if(configuration.contains("prism.mysql"))
        {
            mysql = configuration.getConfigurationSection("prism.mysql");
        }else {
            mysql = configuration.createSection("prism.mysql");
        }
        MySQLPrismDataSource.updateDefaultConfig(mysql);
        addHikariDefaults(mysql);
        ConfigurationSection derby;
        if(configuration.contains("prism.derby"))
        {
            derby = configuration.getConfigurationSection("prism.derby");
        }else {
            derby = configuration.createSection("prism.derby");
        }
        DerbyPrismDataSource.updateDefaultConfig(derby);
        addHikariDefaults(derby);
        ConfigurationSection sqlite;
        if(configuration.contains("prism.sqlite"))
        {
            sqlite = configuration.getConfigurationSection("prism.sqlite");
        }else {
            sqlite = configuration.createSection("prism.sqlite");
        }
        SQLitePrismDataSource.updateDefaultConfig(sqlite);
        addHikariDefaults(sqlite);
    }

    private static void addHikariDefaults(ConfigurationSection section) {
        section.addDefault("database.max-pool-connections", 25);
        section.addDefault("database.min-idle-connections", 10);
        section.addDefault("database.max-wait", 30000);
        section.addDefault("database.max-failures-before-wait", 5);
        section.addDefault("database.actions-per-insert-batch", 300);
        // queue
        section.addDefault("database.force-write-queue-on-shutdown", true);
        section.addDefault("database.propertyfile","hikaricp.properties");
    }
    public static PrismDataSource createDataSource(Configuration  configuration) {
        if(configuration == null) return null;
        String dataSource = configuration.getString("datasource");
        if(dataSource == null) {
            Prism.log("NO DATASOURCE COULD BE FOUND !!! please adjust config.");
            return null;
        }
        switch (dataSource) {
            case "mysql":
                Prism.log("Attempting to configure datasource as " + dataSource);
                database = new MySQLPrismDataSource(configuration.getConfigurationSection("prism.mysql"));
                return database;
            case "derby":
                Prism.log("Attempting to configure datasource as " + dataSource);
                database = new DerbyPrismDataSource(configuration.getConfigurationSection("prism.derby"));
                return database;
            case "sqlite":
                Prism.log("Attempting to configure datasource as " + dataSource);
                database = new SQLitePrismDataSource(configuration.getConfigurationSection("prism.sqlite"));
                return database;
            default:
                Prism.log("Attempting to configure datasource as " + "!ERROR!");
                return null;
        }

    }
    public static PrismDataSourceUpdater createUpdater(Configuration configuration){
        if(configuration == null) return null;
        String dataSource = configuration.getString("dataSource","mysql");
        if(dataSource == null)return null;
        switch (dataSource) {
            case "mysql":
            case "derby":
            case "sqlite":
                return new SQLPrismDataSourceUpdater(database);
            default:
                return null;
        }
    }

    public static Connection getConnection() throws SQLException {
        return database.getConnection();
    }

}
