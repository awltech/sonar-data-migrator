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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

public class SonarUserMigration {

	static Logger log = Logger.getLogger(SonarUserMigration.class);
	
	public static void migrateUsers()	
	{
		long startTime = Calendar.getInstance().getTimeInMillis();
		log.info("start time "+new Date(startTime));
		
		Connection sourceCon = DatabaseUtil.getSourceConnection();
		Connection targetCon = DatabaseUtil.getTargetConnection();

		try {

			log.info("Does source DB supports retrival of auto generation of key? -"
					+ sourceCon.getMetaData().supportsGetGeneratedKeys());
			log.info("Does target DB supports retrival of auto generation of key? -"
					+ targetCon.getMetaData().supportsGetGeneratedKeys());
			log.info("Auto generated key retrival is required for knowing newly generated id to be used as foreign key");

			log.info("Preparing sql queries based on input");
			
			//Query for users
			PreparedStatement sourceUsersSt = sourceCon.prepareStatement("select * from users");			
			PreparedStatement targetUsersSt = targetCon.prepareStatement("select id,login from users");
			PreparedStatement targetNewUsersSt = targetCon.prepareStatement("insert into users(login,name,email,crypted_password,salt,created_at,updated_at,remember_token,remember_token_expires_at,active)" +
					" values (?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
			PreparedStatement targetUsersMapSt = targetCon.prepareStatement("insert into user_map_temp(old_id,new_id) values (?,?)");
			
			Map<String, Integer> targetUserLoginMap = new HashMap<String, Integer>();
			
			
			log.info("Getting existing users from target DB");
			ResultSet targetRs = targetUsersSt.executeQuery();
			
			while(targetRs.next())
			{
				targetUserLoginMap.put(targetRs.getString("login"),targetRs.getInt("id"));
			}
			
			targetRs.close();
			targetUsersSt.close();
			
			log.info("Checking default group detail from target DB");
			Statement targetGroupSt = targetCon.createStatement();
			ResultSet targetGroupRs = targetGroupSt.executeQuery("select id from groups where name='sonar-users'");
			int targetDefaultGroupId = 0;
			PreparedStatement targetGroupsUsersSt = null;
			
			if(targetGroupRs.next())
			{
				targetDefaultGroupId = targetGroupRs.getInt(1);
				targetGroupsUsersSt = targetCon.prepareStatement("insert into groups_users(user_id,group_id) values (?,"+targetDefaultGroupId+")");
			}
			
			targetGroupRs.close();
			targetGroupSt.close();
			
			
			log.info("Comparing users from source DB and adding missing in target");
			
			ResultSet sourceRs = sourceUsersSt.executeQuery();
			int newUsersCount =0,newUserId = 0,existingUsersCount = 0;
			
			while(sourceRs.next())
			{					
				String login = sourceRs.getString("login");
				int oldUserId = sourceRs.getInt("id");
				
				if (targetUserLoginMap.containsKey(login)) {
				
					existingUsersCount++;
					newUserId = targetUserLoginMap.get(login);
				}
				else
				{
					targetNewUsersSt.setString(1, login);
					targetNewUsersSt.setString(2, sourceRs.getString("name"));
					targetNewUsersSt.setString(3, sourceRs.getString("email"));
					targetNewUsersSt.setString(4,
							sourceRs.getString("crypted_password"));
					targetNewUsersSt.setString(5, sourceRs.getString("salt"));
					targetNewUsersSt.setTimestamp(6,
							sourceRs.getTimestamp("created_at"));
					targetNewUsersSt.setTimestamp(7,
							sourceRs.getTimestamp("updated_at"));
					targetNewUsersSt.setString(8,
							sourceRs.getString("remember_token"));
					targetNewUsersSt.setTimestamp(9,
							sourceRs.getTimestamp("remember_token_expires_at"));
					if (sourceRs.getShort("active") == 1)
						targetNewUsersSt.setBoolean(10, true);
					else
						targetNewUsersSt.setBoolean(10, false);
					
					targetNewUsersSt.executeUpdate();
					
					ResultSet userRs = targetNewUsersSt.getGeneratedKeys();					
					
					if (userRs.next()) {
						newUserId = userRs.getInt(1);						
					}
									
					userRs.close();					
					newUsersCount++;
					
					log.info("User "+login+" added.");
					 
					if(targetDefaultGroupId != 0)
						{
							targetGroupsUsersSt.setInt(1, newUserId);
							targetGroupsUsersSt.executeUpdate();
						}
					
					log.info("Deafult group 'sonar-users' assigned");
					
				}	
				
				targetUsersMapSt.setInt(1, oldUserId);
				targetUsersMapSt.setInt(2, newUserId);
				
				try {
					targetUsersMapSt.executeUpdate();
				}
				catch (SQLException sqle) {					
					log.info("User "+login+" already migrated" );
				}				
				
			}
						
			sourceRs.close();
			sourceUsersSt.close();
			targetNewUsersSt.close();
			targetUsersMapSt.close();
			targetGroupsUsersSt.close();
			
			long endTime = Calendar.getInstance().getTimeInMillis();
			log.info(newUsersCount +" users migrated, "+existingUsersCount+" users already exist");
			log.info("end time "+new Date(startTime));			
			log.info("Done time taken "+(endTime-startTime) + " ms");
			
		} catch (Exception e) {
			log.error("Error while processing users",e);
			
		}
	}

	
}
