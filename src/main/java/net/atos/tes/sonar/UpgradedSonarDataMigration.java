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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.log4j.Logger;

public class UpgradedSonarDataMigration {

	static Logger log = Logger.getLogger(UpgradedSonarDataMigration.class);

	public static void migrateData(String projectKey) {

		long startTime = Calendar.getInstance().getTimeInMillis();
		log.info("start time " + new Date(startTime));

		Connection sourceCon = DatabaseUtil.getSourceConnection();
		Connection targetCon = DatabaseUtil.getTargetConnection();

		try {

			log.info("Does source DB supports retrival of auto generation of key? -" + sourceCon.getMetaData().supportsGetGeneratedKeys());
			log.info("Does target DB supports retrival of auto generation of key? -" + targetCon.getMetaData().supportsGetGeneratedKeys());
			log.info("Auto generated key retrival is required for knowing newly generated id to be used as foreign key");

			log.info("Preparing sql queries based on input");

			int rootProjectId = 0;

			if (projectKey != null && projectKey.trim().length() > 0) {
				projectKey= projectKey.trim();
				Statement st = sourceCon.createStatement();
				ResultSet projectRs = st.executeQuery("select id from projects where kee ='" + projectKey + "'");
				if (projectRs.next())
					rootProjectId = projectRs.getInt("id");
				
				log.info("Running migration for project key "+projectKey +" id "+rootProjectId);
			}
			else // Confirm if really wants to run for all projects
			{
				System.out.println("Are you sure you want to migrate ALL projects from source sonar? Y/N:");
				Scanner in = new Scanner(System.in);
				if ("N".equalsIgnoreCase(in.nextLine()))
					{
						log.info("Terminating application....");
						return;					
					}
					
				log.info("Running migration for ALL projects ");
			}

			// Queries for issues table

			String sourceIssuesSql = "select i.id,i.component_id,root_component_id,i.id,i.rule_id,i.line,plugin_rule_key,i.kee as issue_key,p.kee as project_key,effort_to_fix,i.status,"
					+ " resolution,assignee,severity,manual_severity,reporter,action_plan_key,issue_creation_date,issue_close_date,issue_update_date, i.created_at, i.updated_at from issues i,rules r,"
					+ "projects p where i.rule_id=r.id and i.component_id=p.id";

			if (projectKey != null && projectKey.trim().length() > 0)
				sourceIssuesSql += " and root_component_id = " + rootProjectId;

			PreparedStatement sourceIssueSt = sourceCon.prepareStatement(sourceIssuesSql);

			PreparedStatement targetIssueSt = targetCon
					.prepareStatement(
							"select kee,component_id,id,effort_to_fix,status, resolution,assignee,severity,manual_severity,reporter,action_plan_key,issue_creation_date,issue_close_date,issue_update_date,created_at,updated_at "
									+ "from issues where component_id=? and rule_id=? and line=? and kee not in (select new_kee from issues_map_temp where old_root_component_id = ?) ", ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_UPDATABLE);

			// Queries for issue_changes table
			PreparedStatement sourceIssueChangesSt = sourceCon
					.prepareStatement(
							"select id,kee,issue_key,user_login,change_type,change_data,created_at,updated_at from issue_changes where issue_key=?",
							ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			// Queries for issue_changes table
			PreparedStatement targetIssueChangesSt = targetCon
					.prepareStatement("insert into issue_changes (kee,issue_key,user_login,change_type,change_data,created_at,updated_at) values (?,?,?,?,?,?,?)"); // new
																																									// String[]

			log.info("######################## Gathering mapping information ########################");

			/*log.info("Getting users map from target DB");

			Map<Integer, Integer> userIdMap = new HashMap<Integer, Integer>();
			Statement targetUsersMapSt = targetCon.createStatement();
			ResultSet targetUserMapRs = targetUsersMapSt.executeQuery("select * from user_map_temp");

			while (targetUserMapRs.next()) {
				userIdMap.put(targetUserMapRs.getInt("old_id"), targetUserMapRs.getInt("new_id"));
			}

			targetUserMapRs.close();
			targetUsersMapSt.close();

			log.info("Old & New user map is ready");*/
			log.info("Getting rules map from target DB");

			Map<Integer, Integer> ruleIdMap = new HashMap<Integer, Integer>();

			PreparedStatement targetRulesSt = targetCon.prepareStatement("select id from rules where plugin_rule_key=?");
			Statement sourceRulesSt = sourceCon.createStatement();
			ResultSet sourceRulesRs = sourceRulesSt.executeQuery("select id,plugin_rule_key from rules");

			while (sourceRulesRs.next()) {

				int oldRuleId = sourceRulesRs.getInt("id");
				String plugin_rule_key = sourceRulesRs.getString("plugin_rule_key");
				targetRulesSt.setString(1, plugin_rule_key);
				ResultSet targetRulesRs = targetRulesSt.executeQuery();
				if (targetRulesRs.next()) {
					int newRuleId = targetRulesRs.getInt(1);
					ruleIdMap.put(oldRuleId, newRuleId);
				} else {
					log.warn("Rule " + plugin_rule_key + " doesn't exist in target system");
				}

				targetRulesSt.clearParameters();
				targetRulesRs.close();
			}

			sourceRulesRs.close();
			sourceRulesSt.close();
			targetRulesSt.close();

			log.info("Old & New rules map is ready");
			log.info("Getting root projects map from target DB");

			Map<Integer, Integer> rootProjectIdMap = new HashMap<Integer, Integer>();
			PreparedStatement targetProjectsSt = targetCon.prepareStatement("select id from projects where kee=?");
			Statement sourceProjectsSt = sourceCon.createStatement();
			String projectSql = "select id,kee from projects where scope='PRJ' and qualifier='TRK'";
			if (projectKey != null && projectKey.trim().length() > 0)
				projectSql += " and id = " + rootProjectId;

			ResultSet sourceProjectsRs = sourceProjectsSt.executeQuery(projectSql);

			while (sourceProjectsRs.next()) {

				int oldRootProjectId = sourceProjectsRs.getInt("id");
				String project_kee = sourceProjectsRs.getString("kee");
				targetProjectsSt.setString(1, project_kee);
				ResultSet targetRulesRs = targetProjectsSt.executeQuery();
				if (targetRulesRs.next()) {
					int newRootProjectId = targetRulesRs.getInt(1);
					rootProjectIdMap.put(oldRootProjectId, newRootProjectId);
				} else {
					log.warn("prject " + project_kee + " doesn't exist in target system");
					if(project_kee.equals(projectKey))
						printFinalMessage(startTime);
						System.exit(0);
				}

				targetProjectsSt.clearParameters();
				targetRulesRs.close();
			}

			sourceProjectsRs.close();
			sourceProjectsSt.close();
			targetProjectsSt.close();

			log.info("Old & New root project map is ready");
			log.info("Getting processed action plan from target DB");
			
			List<Integer> actionPlanIdList = new ArrayList<Integer>();

			Statement targetActionPlanMapSt = targetCon.createStatement();
			ResultSet targetActionPlanMapRs = targetActionPlanMapSt.executeQuery("select old_id from action_plans_temp");

			PreparedStatement targetActionPlanMapInsertSt = targetCon.prepareStatement("insert into action_plans_temp(old_id) values (?)");

			while (targetActionPlanMapRs.next()) {
				actionPlanIdList.add(targetActionPlanMapRs.getInt("old_id"));
			}

			targetActionPlanMapRs.close();
			targetActionPlanMapSt.close();

			log.info("Processed action plan data is ready");

			log.info("######################## Start working on action plan ########################");

			String sourceActionPlanSql = "select * from action_plans";
			if (projectKey != null && projectKey.trim().length() > 0) {
				sourceActionPlanSql += " where project_id=" + rootProjectId;
			}

			PreparedStatement sourceActionPlanSt = sourceCon.prepareStatement(sourceActionPlanSql);

			PreparedStatement targetActionPlanSt = targetCon
					.prepareStatement("insert into action_plans(created_at,updated_at,name,description,deadline,user_login,project_id,status,kee) "
							+ "values (?,?,?,?,?,?,?,?,?)");

			ResultSet sourceActionPlanRs = sourceActionPlanSt.executeQuery();
			while (sourceActionPlanRs.next()) {

				int oldActionPlanId = sourceActionPlanRs.getInt("id");
				if (actionPlanIdList.contains(oldActionPlanId)) {
					log.debug("Ignoring action plan " + oldActionPlanId + " as already migrated in target system");
					continue; // this action plan already added in target system
				}

				Integer newProjectId = rootProjectIdMap.get(sourceActionPlanRs.getInt("project_id"));

				if (newProjectId != null) {

					targetActionPlanSt.setTimestamp(1, sourceActionPlanRs.getTimestamp("created_at"));
					targetActionPlanSt.setTimestamp(2, sourceActionPlanRs.getTimestamp("updated_at"));
					targetActionPlanSt.setString(3, sourceActionPlanRs.getString("name"));
					targetActionPlanSt.setString(4, sourceActionPlanRs.getString("description"));
					targetActionPlanSt.setTimestamp(5, sourceActionPlanRs.getTimestamp("deadline"));
					targetActionPlanSt.setString(6, sourceActionPlanRs.getString("user_login"));
					targetActionPlanSt.setInt(7, newProjectId);
					targetActionPlanSt.setString(8, sourceActionPlanRs.getString("status"));
					targetActionPlanSt.setString(9, sourceActionPlanRs.getString("kee"));

					targetActionPlanSt.addBatch();

					targetActionPlanMapInsertSt.setInt(1, oldActionPlanId);
					targetActionPlanMapInsertSt.addBatch();

				} else {
					log.warn("Associated project for this action plan doesn't exist. skipping this action plan... Old project id "
							+ sourceActionPlanRs.getInt("project_id"));
				}
			}

			int[] updateCount = targetActionPlanSt.executeBatch();
			log.debug(updateCount.length + " rows inserted in action_plans in target DB.");

			targetActionPlanMapInsertSt.executeBatch();

			sourceActionPlanRs.close();
			sourceActionPlanSt.close();
			targetActionPlanSt.close();

			targetActionPlanMapInsertSt.close();

			log.info("######################## Action plans migrated successfuly ########################");
			
			log.info("############ Start working on Issues ######################");

			Map<Integer, String> issueIdKeeMap = new HashMap<Integer, String>();

			Statement targetIssuesMapSt = targetCon.createStatement();
			String issuesMapSql = "select old_id,old_root_component_id,new_kee from issues_map_temp";
			if (projectKey != null && projectKey.trim().length() > 0)
				issuesMapSql += " where old_root_component_id = " + rootProjectId;

			ResultSet targetIssuesMapRs = targetIssuesMapSt.executeQuery(issuesMapSql);

			PreparedStatement targetIssuesMapInsertSt = targetCon
					.prepareStatement("insert into issues_map_temp(old_id,old_root_component_id,new_kee) values (?,?,?)");

			while (targetIssuesMapRs.next()) {
				issueIdKeeMap.put(targetIssuesMapRs.getInt("old_id"), targetIssuesMapRs.getString("new_kee"));
			}

			targetIssuesMapRs.close();
			targetIssuesMapSt.close();
						
			List<Integer> issueChangesIdKeeList = new ArrayList<Integer>();

			Statement targetIssueChangesMapSt = targetCon.createStatement();
			String issueChangesMapSql = "select old_id,old_issue_kee from issue_changes_map_temp";			
			ResultSet targetIssueChangesMapRs = targetIssueChangesMapSt.executeQuery(issueChangesMapSql);

			PreparedStatement targetIssueChangesMapInsertSt = targetCon
					.prepareStatement("insert into issue_changes_map_temp(old_id,old_issue_kee) values (?,?)");

			while (targetIssueChangesMapRs.next()) {
				issueChangesIdKeeList.add(targetIssueChangesMapRs.getInt("old_id"));
			}

			targetIssueChangesMapRs.close();
			targetIssueChangesMapSt.close();


			PreparedStatement targetProjectSt = targetCon.prepareStatement("select id from projects where kee =?");

			ResultSet sourceIssueRs = sourceIssueSt.executeQuery();

			Map<String, Integer> keeIdMap = new HashMap<String, Integer>();

			while (sourceIssueRs.next()) {

				int issueId = sourceIssueRs.getInt("id");
				String issue_key = sourceIssueRs.getString("issue_key");
				String newIssue_key = null;
				
				log.info("Working on issue with id "+issueId);

				// Skip issue update step if this issue is already processed.

				if (issueIdKeeMap.containsKey(issueId)) 
				{
					newIssue_key = issueIdKeeMap.get(issueId);
					log.info("Issue with id "+issueId+ " already processed.");
				} 
				else 
				{
					String plugin_rule_key = sourceIssueRs.getString("plugin_rule_key");
					String project_kee = sourceIssueRs.getString("project_key");
					int lineNumber = sourceIssueRs.getInt("line");
					int oldProjectId = sourceIssueRs.getInt("component_id");
					int oldRootProjectId = sourceIssueRs.getInt("root_component_id");
					int oldRuleId = sourceIssueRs.getInt("rule_id");
					log.debug("Processing issue with plugin_rule_key " + plugin_rule_key + " line no=" + lineNumber + " oldProjectId "
							+ oldProjectId + " oldRootProjectId " + oldRootProjectId + " ruleid = " + oldRuleId + " kee=" + project_kee);
					int newProjectId = 0;
					if (keeIdMap.get(project_kee) != null) // check in local map first
					{
						newProjectId = keeIdMap.get(project_kee);
						log.debug("New project id " + newProjectId);
					} else {
						targetProjectSt.clearParameters();
						targetProjectSt.setString(1, project_kee);
						log.debug("Finding matching project id in target DB...");						
						ResultSet targetProjectRs = targetProjectSt.executeQuery();

						if (targetProjectRs.next()) {
							newProjectId = targetProjectRs.getInt(1);
							keeIdMap.put(project_kee, newProjectId);
							log.debug("New project id " + newProjectId);
						} else {
							log.warn("No project with " + project_kee + " in target system");
							continue; // continue for next violation
						}

						targetProjectRs.close();
					}
					
					if(ruleIdMap.get(oldRuleId) == null)
						{
							log.warn("Rule " + plugin_rule_key + " doesn't exist in target system. Issue can't migrate. Ignoring..");
							continue;
						}
					
					targetIssueSt.clearParameters();
					targetIssueSt.setInt(1, newProjectId);
					targetIssueSt.setInt(2, ruleIdMap.get(oldRuleId));
					targetIssueSt.setInt(3, lineNumber);
					// Also check if this is not exactly the same violation already processed.
					// Due to https://github.com/wenns/sonar-cxx/issues/110 , it is possible to have another duplicate violation on same line in same file.					
					targetIssueSt.setInt(4, oldRootProjectId);
					
					log.debug("Finding matching issue for this project in target DB...");
					ResultSet targetIssueRs = targetIssueSt.executeQuery();
					
					if (targetIssueRs.next()) {

						int effort_to_fix = sourceIssueRs.getInt("effort_to_fix");
						newIssue_key = targetIssueRs.getString("kee");
						// if not set via this method then it inserts 0 for null
						// column
						if (effort_to_fix != 0)
							targetIssueRs.updateInt("effort_to_fix", sourceIssueRs.getInt("effort_to_fix"));

						targetIssueRs.updateString("status", sourceIssueRs.getString("status"));
						targetIssueRs.updateString("resolution", sourceIssueRs.getString("resolution"));
						targetIssueRs.updateString("assignee", sourceIssueRs.getString("assignee"));
						targetIssueRs.updateString("action_plan_key", sourceIssueRs.getString("action_plan_key"));
						targetIssueRs.updateString("severity", sourceIssueRs.getString("severity"));
						targetIssueRs.updateBoolean("manual_severity", sourceIssueRs.getBoolean("manual_severity"));
						targetIssueRs.updateString("reporter", sourceIssueRs.getString("reporter"));
						

						// TODO check if all these dates need updates from old analysis ?
						targetIssueRs.updateTimestamp("issue_creation_date", sourceIssueRs.getTimestamp("issue_creation_date"));
						targetIssueRs.updateTimestamp("issue_close_date", sourceIssueRs.getTimestamp("issue_close_date"));
						targetIssueRs.updateTimestamp("issue_update_date", sourceIssueRs.getTimestamp("issue_update_date"));
						targetIssueRs.updateTimestamp("created_at", sourceIssueRs.getTimestamp("created_at"));
						targetIssueRs.updateTimestamp("updated_at", sourceIssueRs.getTimestamp("updated_at"));

						targetIssueRs.updateRow();

						targetIssuesMapInsertSt.setInt(1, issueId);
						targetIssuesMapInsertSt.setInt(2, oldRootProjectId);
						targetIssuesMapInsertSt.setString(3, newIssue_key);
						targetIssuesMapInsertSt.executeUpdate();
						
						log.info("Updated data in matching issue in target DB.");

					} else {
						log.warn("No matching issue found in target DB project id " + newProjectId + " ruleid "
								+ ruleIdMap.get(oldRuleId) + " linenumber " + lineNumber);
						continue; // continue for next violation
					}
					targetIssueSt.clearParameters();
					targetIssueRs.close();
				}
				
				log.debug("\t\tFetching all changes for this issue...");
				
				sourceIssueChangesSt.setString(1, issue_key);				
				ResultSet sourceIssueChangesRs = sourceIssueChangesSt.executeQuery();
				
				while (sourceIssueChangesRs.next()) {
					
					int oldIssueChangesId = sourceIssueChangesRs.getInt("id");
					
					// Skip issue changes insert if this issue change is already processed.
					if (issueChangesIdKeeList.contains(oldIssueChangesId)) 
					{	
						log.debug("\t\tIssue changes with id "+oldIssueChangesId+ " already processed.");
						continue;
					}

					targetIssueChangesSt.setString(1, sourceIssueChangesRs.getString("kee"));
					targetIssueChangesSt.setString(2, newIssue_key);
					targetIssueChangesSt.setString(3, sourceIssueChangesRs.getString("user_login"));
					targetIssueChangesSt.setString(4, sourceIssueChangesRs.getString("change_type"));
					targetIssueChangesSt.setString(5, sourceIssueChangesRs.getString("change_data"));
					targetIssueChangesSt.setTimestamp(6, sourceIssueChangesRs.getTimestamp("created_at"));
					targetIssueChangesSt.setTimestamp(7, sourceIssueChangesRs.getTimestamp("updated_at"));

					targetIssueChangesSt.addBatch();
					
					targetIssueChangesMapInsertSt.setInt(1, oldIssueChangesId);
					targetIssueChangesMapInsertSt.setString(2, issue_key);
					targetIssueChangesMapInsertSt.addBatch();

				}

				int[] updateCounts = targetIssueChangesSt.executeBatch();
				targetIssueChangesSt.clearBatch();
				
				targetIssueChangesMapInsertSt.executeBatch();
				targetIssueChangesMapInsertSt.clearBatch();
								
				if(updateCounts.length>0)
					log.debug("\t\t"+updateCounts.length + " rows inserted in issue_changes in target DB.");
				else
					log.debug("\t\tNo changes found for this issue.");
				
				sourceIssueChangesRs.close();
			}

			sourceIssueSt.close();
			sourceIssueRs.close();
			targetProjectSt.close();
			
			targetIssueChangesSt.close();
			sourceIssueChangesSt.close();
			targetIssueChangesMapInsertSt.close();

			log.info("############ Issues migrated Successfully ######################");
			
			printFinalMessage(startTime);

		} catch (Exception e) {
			log.error("Error while migrating data", e);
		}
	}

	private static void printFinalMessage(long startTime) {
		long endTime = Calendar.getInstance().getTimeInMillis();
		log.info("end time " + new Date(startTime));
		log.info("time taken " + (endTime - startTime) + " ms");
		log.info("Done");
	}
}
