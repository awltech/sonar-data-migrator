sonar-data-migrator
===================

Utility to migrate data from one Sonar instance to other ones.

To use it, follow below steps.

1.	Make sure that sonar analysis is done on source and target sonar with same code.
2.	configure source and target db details in database.properties file
3.	run init.sql on target sonar db, it will create temporary tables to store migrated data details
4.	set proper java path in script.bat file (you can use script.sh for executing on linux) .

Usage

* run the bat/sh script passing users as argument for migration of users
* run the bat/sh script passing data as first argument and project key as per "Key" value in sonar report as second argument. If key is not passed, it will work for all projects in db.

Note that script is idempotent meaning that running it multiple times doesn't create any issue as data already migrated won't migrate again. This script will migrate below things

1	Users (default sonar-users role is assigned to all migrated users)
2	Action plans associated to the project
3   All changes to issue like assignee,severity,status,action plan and resolution
4	All reviews/comments added to issue 
5	All false positive data