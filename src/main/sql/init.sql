--
-- Sonar Data Migrator, A tool to migrate sonar project data between to separate sonar instances.
--
-- Copyright (C) 2013 Worldline or third-party contributors as
-- indicated by the @author tags or express copyright attribution
-- statements applied by the authors.
--
-- This library is free software; you can redistribute it and/or
-- modify it under the terms of the GNU Lesser General Public
-- License as published by the Free Software Foundation; either
-- version 2.1 of the License.
--
-- This library is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
-- Lesser General Public License for more details.
--
-- You should have received a copy of the GNU Lesser General Public
-- License along with this library; if not, see <http://www.gnu.org/licenses/>
--

create table user_map_temp (
"old_id" Serial UNIQUE, 
"new_id" Serial UNIQUE 
);

create table action_plans_temp (
"old_id" Serial UNIQUE
);

create table issues_map_temp (
"old_id" Serial UNIQUE, 
"old_root_component_id" Serial,
"new_kee" Varchar(50)
);

create table issue_changes_map_temp (
"old_id" Serial UNIQUE, 
"old_issue_kee" Varchar(50)
);