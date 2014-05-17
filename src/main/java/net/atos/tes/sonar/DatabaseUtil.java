/**
 * Sonar Data Migrator, A tool to migrate sonar project data between to separate sonar instances.
 *
 * Copyright (C) 2013 Worldline or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see <http://www.gnu.org/licenses/>
 */
package net.atos.tes.sonar;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class DatabaseUtil {

	static ResourceBundle dbResourceBundle = null;
	
	static
	{
	try {
		dbResourceBundle = ResourceBundle.getBundle("database");		
		
	} catch (MissingResourceException e) {
		e.printStackTrace();
		System.out.println("Database property file missing" );		
	} 
	}
	
	static Connection getConnection(String driver, String connectionString,
			String userName, String password) {
		Connection con = null;
		try {
			Class.forName(driver);
			con = DriverManager.getConnection(connectionString, userName,
					password);

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return con;
	}
	
	static Connection getSourceConnection()
	{
		
		return getConnection(dbResourceBundle.getString("sourceDriver"), dbResourceBundle.getString("sourceConnectionString"),
				dbResourceBundle.getString("sourceUserName"), dbResourceBundle.getString("sourcePassword"));
	}

	static Connection getTargetConnection()
	{
		return getConnection(dbResourceBundle.getString("targetDriver"), dbResourceBundle.getString("targetConnectionString"),
				dbResourceBundle.getString("targetUserName"), dbResourceBundle.getString("targetPassword"));
	}
	
	static void checkColumnProperties(Connection con2, String tableName)
			throws SQLException {

		PreparedStatement st4 = con2.prepareStatement("select * from "
				+ tableName);
		ResultSet rs0 = st4.executeQuery();

		ResultSetMetaData rsmd = rs0.getMetaData();
		for (int i = 1; i <= rsmd.getColumnCount(); i++) {
			System.out
					.println("name=" + rsmd.getColumnLabel(i) + " auto "
							+ rsmd.isAutoIncrement(i) + " "
							+ rsmd.getColumnTypeName(i));
		}
	}
	
	//@ToDo to keep all prepared statements at single place
	static void initPreparedStatements(){
		
	}
	
}
