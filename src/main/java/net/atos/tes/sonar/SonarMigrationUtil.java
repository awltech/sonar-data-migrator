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
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;


public class SonarMigrationUtil {
	
	/**
	 * @param args
	 * 
	 */
	
	static Logger log = Logger.getLogger(SonarMigrationUtil.class);
	
	public static void main(String[] args) {
		
		String migrationType="",projectKey = null;
		
		if(args.length>0)
		{
			migrationType = args[0];
			
			if("data".equalsIgnoreCase(migrationType) && args.length>1)
			{
				projectKey = args[1];
			}
			executeMigration(migrationType.toUpperCase(), projectKey);
		}
		else
		{
			printUsage();
			
		}
		
		
	}
	
	private static void executeMigration(String migrationType,String projectKey)
	{
		MigrationType migrationTypeEnum;
		try {
			migrationTypeEnum = MigrationType.valueOf(migrationType);
			switch (migrationTypeEnum) {
			case USERS:
				SonarUserMigration.migrateUsers();
				break;
			case DATA:
				if (isOldSonarVersion())
					SonarDataMigration.migrateData(projectKey);
				else
					UpgradedSonarDataMigration.migrateData(projectKey);
				break;
			}
			
		} catch (IllegalArgumentException e) {
			System.out.println("No valid migration Type option passed");
			log.error("No valid migration Type option passed");
			printUsage();
			return;
		}
		catch(SQLException ex)
		{
			log.fatal("Target and source sonar has incompatible versions");
			System.out.println("Target and Source sonar has incompatible versions");
			return;
		}
		
	}
	
	private static boolean isOldSonarVersion()throws SQLException
	{
		Connection targetCon = DatabaseUtil.getTargetConnection();		
		Connection sourceCon = DatabaseUtil.getSourceConnection();
		Statement sourceIssueStatement;
		boolean oldSourceSystem = false;
		boolean oldTargetSystem = false;
		try {
			sourceIssueStatement = sourceCon.createStatement();
			sourceIssueStatement.execute("select 1 from issues");	
		}		
		catch (SQLException e) {
			log.info("issues table doesn't exist in source");			
			oldSourceSystem = true;
		}		
		try {
			Statement targetIssueStatement = targetCon.createStatement();
			targetIssueStatement.execute("select 1 from issues");			
		}
		catch (SQLException e) {
			log.info("Issues table doesn't exist in target");			
			oldTargetSystem = true;
		}

		if(oldSourceSystem ^ oldTargetSystem)
			throw new SQLException("Old and Target system versions are mismatched");
		
		return oldSourceSystem && oldTargetSystem;
		
		
	}
	private enum MigrationType
	{
		USERS,
		DATA;
	}

	private static void printUsage()
	{
		System.out.println("Usage: java -classpath .;sonar-db-migration-<version>.jar net.atos.tes.sonar.SonarMigrationUtil <migration Type either users/data> <project key>");
		System.out.println("OR");
		System.out.println("Usage: ./script.sh <migration Type either users/data> <project key>");
	}
}
