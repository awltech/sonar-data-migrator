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
package com.worldline.awltech.sonar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

public class SonarDataMigration {

	static Logger log = Logger.getLogger(SonarDataMigration.class);

	public static void migrateData(String projectKey) {

		long startTime = Calendar.getInstance().getTimeInMillis();
		log.info("start time " + new Date(startTime));

		Connection sourceCon = DatabaseUtil.getSourceConnection();
		Connection targetCon = DatabaseUtil.getTargetConnection();

		try {

			log.info("Does source DB supports retrival of auto generation of key? -"
					+ sourceCon.getMetaData().supportsGetGeneratedKeys());
			log.info("Does target DB supports retrival of auto generation of key? -"
					+ targetCon.getMetaData().supportsGetGeneratedKeys());
			log.info("Auto generated key retrival is required for knowing newly generated id to be used as foreign key");

			log.info("Preparing sql queries based on input");

			int rootProjectId = 0;

			if (projectKey != null && projectKey.trim().length() > 0) {
				Statement st = sourceCon.createStatement();
				ResultSet projectRs = st
						.executeQuery("select id from projects where kee ='"
								+ projectKey + "'");
				if (projectRs.next())
					rootProjectId = projectRs.getInt("id");
			}

			// Queries for rule_failures table

			String sourceRuleFailureSql = "select s.project_id,s.root_project_id,rf.permanent_id,rf.id,snapshot_id,rf.rule_id,rv.resource_line,plugin_rule_key,kee,rf.switched_off from rule_failures rf,rules r,"
					+ "projects p,snapshots s,reviews rv where rf.rule_id=r.id and rf.snapshot_id=s.id and s.project_id=p.id and rv.rule_failure_permanent_id=rf.permanent_id"; // and
																																												// rf.switched_off=1";

			if (projectKey != null && projectKey.trim().length() > 0)
				sourceRuleFailureSql += " and root_project_id = "
						+ rootProjectId;

			PreparedStatement sourceRuleFailureSt = sourceCon
					.prepareStatement(sourceRuleFailureSql);

			PreparedStatement targetRuleFailuresSt = targetCon
			.prepareStatement(
					"select permanent_id,switched_off,id from rule_failures where snapshot_id=? and rule_id=? and line=?",
					ResultSet.TYPE_SCROLL_INSENSITIVE,
					ResultSet.CONCUR_UPDATABLE);
			
			PreparedStatement targetSnapshotsSt = targetCon
					.prepareStatement("select id,project_id,root_project_id from snapshots s where s.project_id = (select id from projects where kee=?) and islast=true");

			
			// Queries for reviews table
			PreparedStatement sourceReviewsSt = sourceCon
					.prepareStatement(
							"select id,created_at,updated_at,user_id,assignee_id,title,status,severity,rule_failure_permanent_id,project_id,resource_id,resource_line,"
									+ "resolution,rule_id,manual_violation,manual_severity,data from reviews where rule_failure_permanent_id=?",
							ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_UPDATABLE);
			
			PreparedStatement targetReviewsSt = targetCon
					.prepareStatement(
							"insert into reviews (created_at,updated_at,user_id,assignee_id,title,status,severity,rule_failure_permanent_id,project_id,"
									+ "resource_id,resource_line,resolution,rule_id,manual_violation,manual_severity,data) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", // REVIEWS_SEQ.NextVal
							Statement.RETURN_GENERATED_KEYS); // new String[] {"id"});

			// Queries for reviews_comments table
			PreparedStatement sourceReviewCommentsSt = sourceCon
					.prepareStatement(
							"select * from review_comments where review_id=?",
							ResultSet.TYPE_SCROLL_INSENSITIVE,
							ResultSet.CONCUR_UPDATABLE);

			PreparedStatement targetReviewCommentsSt = targetCon
					.prepareStatement("insert into review_comments(created_at,updated_at,review_id,user_id,review_text) values(?,?,?,?,?)"); // REVIEW_COMMENTS_SEQ.NextVal

			
			// Queries for action_plans_reviews table
			PreparedStatement sourceActionPlanReviewSt = sourceCon
					.prepareStatement("select * from action_plans_reviews where action_plan_id=?");
			PreparedStatement targetActionPlanReviewSt = targetCon
					.prepareStatement("insert into action_plans_reviews(action_plan_id,review_id) values (?,?)");

			
			Map<Integer, Integer> rootProjectIdMap = new HashMap<Integer, Integer>();
			Map<Integer, Integer> reviewIdMap = new HashMap<Integer, Integer>();
		
		
			log.info("######################## Gathering mapping information ########################");

			log.info("Getting users map from target DB");

			Map<Integer, Integer> userIdMap = new HashMap<Integer, Integer>();
			Statement targetUsersMapSt = targetCon.createStatement();
			ResultSet targetUserMapRs = targetUsersMapSt.executeQuery("select * from user_map_temp");

			while (targetUserMapRs.next()) {
				userIdMap.put(targetUserMapRs.getInt("old_id"),
						targetUserMapRs.getInt("new_id"));
					}

			targetUserMapRs.close();
			targetUsersMapSt.close();

			log.info("Old & New user map is ready");
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

			
			ResultSet sourceRuleFailureRs = sourceRuleFailureSt.executeQuery();

			log.info("############ Start working on Violations ######################");
			while (sourceRuleFailureRs.next()) {
				String plugin_rule_key = sourceRuleFailureRs.getString("plugin_rule_key");
				String kee = sourceRuleFailureRs.getString("kee");
				int lineNumber = sourceRuleFailureRs.getInt("resource_line");
				int switched_off = sourceRuleFailureRs.getInt("switched_off");
				int oldPermanentId = sourceRuleFailureRs.getInt("permanent_id");
				int oldProjectId = sourceRuleFailureRs.getInt("project_id");
				int oldRootProjectId = sourceRuleFailureRs.getInt("root_project_id");
				int oldRuleId = sourceRuleFailureRs.getInt("rule_id");

				log.debug("Violation found with plugin_rule_key "
						+ plugin_rule_key + " line no=" + lineNumber
						+ " oldPermanentId=" + oldPermanentId
						+ " oldProjectId " + oldProjectId
						+ " oldRootProjectId " + oldRootProjectId
						+ " ruleid = " + oldRuleId + " switched_off  "
						+ switched_off + " kee=" + kee);

				int newSnapshotId = 0, newProjectId = 0, newRootProjectId = 0, newPermanentId = 0;

				targetSnapshotsSt.clearParameters();
				targetSnapshotsSt.setString(1, kee);
				log.debug(" Fetching project id ");
				ResultSet targetSnapshotsRs = targetSnapshotsSt.executeQuery();
				if (targetSnapshotsRs.next()) {
					newSnapshotId = targetSnapshotsRs.getInt(1);
					newProjectId = targetSnapshotsRs.getInt("project_id");
					newRootProjectId = targetSnapshotsRs.getInt("root_project_id");

					rootProjectIdMap.put(oldRootProjectId, newRootProjectId);

					log.debug(" new snapshot id " + newSnapshotId
							+ " new project id " + newProjectId
							+ " new root projectid " + newRootProjectId);
				} else {
					log.warn("No Snapshot for this project " + kee
							+ " in system");
					continue; // continue for next violation
				}

				targetSnapshotsSt.clearParameters();
				targetSnapshotsRs.close();

				targetRuleFailuresSt.clearParameters();
				targetRuleFailuresSt.setInt(1, newSnapshotId);
				targetRuleFailuresSt.setInt(2, ruleIdMap.get(oldRuleId));
				targetRuleFailuresSt.setInt(3, lineNumber);

				log.debug(" Finding matching violation in target DB.");
				ResultSet targetRuleFailuresRs = targetRuleFailuresSt.executeQuery();
				if (targetRuleFailuresRs.next()) {
					newPermanentId = targetRuleFailuresRs.getInt(1);
					log.debug("Violation found with new permanent id ="
							+ newPermanentId);

					if (switched_off == 1) {
						// rs4.updateInt("switched_off", 1);
						targetRuleFailuresRs.updateBoolean("switched_off", true);
						targetRuleFailuresRs.updateRow();
						log.debug(" this violation is false positive , marked switched_off in targetimation");
					}

				} else {
					log.warn("No matching violation found in target DB snapshotid "
							+ newSnapshotId
							+ " ruleid "
							+ ruleIdMap.get(oldRuleId)
							+ " linenumber " + lineNumber);
					continue; // continue for next violation
				}

				targetRuleFailuresSt.clearParameters();
				targetRuleFailuresRs.close();

				sourceReviewsSt.setInt(1, oldPermanentId);

				int oldReviewId = 0, newReviewId = 0;

				log.debug("#################### Fetching reviews for this violations ####################");
				ResultSet sourceReviewsRs = sourceReviewsSt.executeQuery();
				while (sourceReviewsRs.next()) {

					oldReviewId = sourceReviewsRs.getInt("id");

					targetReviewsSt.setTimestamp(1, sourceReviewsRs.getTimestamp(2));
					targetReviewsSt.setTimestamp(2, sourceReviewsRs.getTimestamp(3));
					targetReviewsSt.setInt(3, userIdMap.get(sourceReviewsRs.getInt(4)));
					int assigneeId = sourceReviewsRs.getInt(5);
					if (assigneeId == 0)
						targetReviewsSt.setNull(4, Types.INTEGER); // if not set
																	// via this
																	// method
																	// then it
																	// inserts 0
																	// for null
																	// column
					else
						targetReviewsSt.setInt(4, userIdMap.get(assigneeId));

					targetReviewsSt.setString(5, sourceReviewsRs.getString(6));
					targetReviewsSt.setString(6, sourceReviewsRs.getString(7));
					targetReviewsSt.setString(7, sourceReviewsRs.getString(8));
					targetReviewsSt.setInt(8, newPermanentId); // rs5.getInt(9)
					targetReviewsSt.setInt(9, newRootProjectId); // rs5.getInt(10)
					targetReviewsSt.setInt(10, newProjectId);// rs5.getInt(11)
					targetReviewsSt.setInt(11, sourceReviewsRs.getInt(12));
					targetReviewsSt.setString(12, sourceReviewsRs.getString(13));
					targetReviewsSt.setInt(13, ruleIdMap.get(oldRuleId)); // rs5.getInt(14)

					if (sourceReviewsRs.getShort(15) == 1)
						targetReviewsSt.setBoolean(14, true);
					else
						targetReviewsSt.setBoolean(14, false);

					if (sourceReviewsRs.getShort(16) == 1)
						targetReviewsSt.setBoolean(15, true);
					else
						targetReviewsSt.setBoolean(15, false);

					targetReviewsSt.setString(16, sourceReviewsRs.getString(17));

					int updateCount = targetReviewsSt.executeUpdate();
					log.debug(updateCount
							+ " rows inserted in reviews in target DB.");

					ResultSet targetReviewsRs = targetReviewsSt.getGeneratedKeys();

					/*
					 * ResultSet rs6 = st8
					 * .executeQuery("select REVIEWS_SEQ.CurrVal from dual");
					 */
					if (targetReviewsRs.next()) {
						newReviewId = targetReviewsRs.getInt(1);

						log.debug("Checking review comments for Old review id "
								+ oldReviewId + " new reviewId=" + newReviewId);

						reviewIdMap.put(oldReviewId, newReviewId);

						sourceReviewCommentsSt.setInt(1, oldReviewId);
						ResultSet sourceReviewCommentsRs = sourceReviewCommentsSt.executeQuery();

						while (sourceReviewCommentsRs.next()) {

							targetReviewCommentsSt.setTimestamp(1,
									sourceReviewCommentsRs.getTimestamp("created_at"));
							targetReviewCommentsSt.setTimestamp(2,
									sourceReviewCommentsRs.getTimestamp("updated_at"));
							targetReviewCommentsSt.setInt(3, newReviewId); // rs7.getInt("review_id")
							targetReviewCommentsSt.setInt(4,
									userIdMap.get(sourceReviewCommentsRs.getInt("user_id")));
							targetReviewCommentsSt.setString(5,
									sourceReviewCommentsRs.getString("review_text"));

							targetReviewCommentsSt.addBatch();

						}

						int[] updateCounts = targetReviewCommentsSt
								.executeBatch();
						log.debug(updateCounts
								+ " rows inserted in review_comments in target DB.");
					}

				}

			}

			sourceRuleFailureSt.close();
			sourceRuleFailureRs.close();

			log.info("Getting processed action plan from target DB");
			
			List<Integer> actionPlanIdList = new ArrayList<Integer>();
			List<Integer> actionPlanReviewIdList = new ArrayList<Integer>();

			Statement targetActionPlanMapSt = targetCon.createStatement();
			ResultSet targetActionPlanMapRs = targetActionPlanMapSt.executeQuery("select old_id,old_review_id from action_plans_temp");

			PreparedStatement targetActionPlanMapInsertSt = targetCon.prepareStatement("insert into action_plans_temp(old_id,old_review_id) values (?)");

			while (targetActionPlanMapRs.next()) {
				actionPlanIdList.add(targetActionPlanMapRs.getInt("old_id"));
				actionPlanReviewIdList.add(targetActionPlanMapRs.getInt("old_review_id"));
			}

			targetActionPlanMapRs.close();
			targetActionPlanMapSt.close();

			log.info("Processed action plan data is ready");

			//@ToDo Need to insert in map_temp table for all insert in action plan
			// When action exist, continue for checking for action plan review and add missing review
			// at end of action plan review insert in temp_map for review old id.
			
			log.info("######################## Start Working on action plan ########################");
			
			String sourceActionPlanSql = "select * from action_plans";
			if (projectKey != null && projectKey.trim().length() > 0) {
				sourceActionPlanSql += " where project_id=" + rootProjectId;
			}
			
			PreparedStatement sourceActionPlanSt = sourceCon.prepareStatement(sourceActionPlanSql);

			PreparedStatement targetActionPlanSt = targetCon
					.prepareStatement(
							"insert into action_plans(created_at,updated_at,name,description,deadline,user_login,project_id,status) "
									+ "values (?,?,?,?,?,?,?,?)",
							Statement.RETURN_GENERATED_KEYS); // new String[] {"id" }); //ACTION_PLANS_SEQ.NextVal 

			ResultSet sourceActionPlanRs = sourceActionPlanSt.executeQuery();
			while (sourceActionPlanRs.next()) {

				int oldActionPlanId = sourceActionPlanRs.getInt("id");
				if (actionPlanIdList.contains(oldActionPlanId)) {
					log.debug("Ignoring action plan " + oldActionPlanId + " as already migrated in target system");
					continue; // this action plan already added in target system
				}

				Integer newProjectId = rootProjectIdMap.get(sourceActionPlanRs.getInt("project_id"));

				if (newProjectId != null) {
					
					targetActionPlanSt.setTimestamp(1,
							sourceActionPlanRs.getTimestamp("created_at"));
					targetActionPlanSt.setTimestamp(2,
							sourceActionPlanRs.getTimestamp("updated_at"));
					targetActionPlanSt.setString(3, sourceActionPlanRs.getString("name"));
					targetActionPlanSt.setString(4, sourceActionPlanRs.getString("description"));
					targetActionPlanSt.setTimestamp(5,
							sourceActionPlanRs.getTimestamp("deadline"));
					targetActionPlanSt.setString(6, sourceActionPlanRs.getString("user_login"));
					targetActionPlanSt.setInt(7, newProjectId);
					targetActionPlanSt.setString(8, sourceActionPlanRs.getString("status"));
					int updateCount = targetActionPlanSt.executeUpdate();
					log.debug(updateCount
							+ " rows inserted in action_plans in target DB.");
					ResultSet targetActionPlanRs = targetActionPlanSt.getGeneratedKeys();
					if (targetActionPlanRs.next()) {
						int newActionPlanId = targetActionPlanRs.getInt(1);

						sourceActionPlanReviewSt.setInt(1, oldActionPlanId);

						log.debug("Checking reviews for this action plan with new action plan id "
								+ newActionPlanId);

						ResultSet sourceActionPlanReviewRs = sourceActionPlanReviewSt.executeQuery();

						while (sourceActionPlanReviewRs.next()) {
							targetActionPlanReviewSt.setInt(1, newActionPlanId);
							targetActionPlanReviewSt.setInt(2,
									reviewIdMap.get(sourceActionPlanReviewRs.getInt("review_id")));

							targetActionPlanReviewSt.addBatch();
						}

						int[] updateCounts = targetActionPlanReviewSt
								.executeBatch();

						log.debug(updateCounts
								+ " rows inserted in action_plans_reviews in target table.");

					}
				} else {
					log.warn("Associated project for this action plan doesn't exist. skipping this action plan... Old project id "
							+ sourceActionPlanRs.getInt("project_id"));
				}
			}

			sourceActionPlanSt.clearParameters();
			sourceActionPlanRs.close();

			long endTime = Calendar.getInstance().getTimeInMillis();
			log.info("end time " + new Date(startTime));
			log.info("time taken " + (endTime - startTime) + " ms");
			log.info("Done");

		} catch (Exception e) {
			log.error("Error while processing users", e);
		}
	}
}
