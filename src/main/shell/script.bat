@REM
@REM Sonar Data Migrator, A tool to migrate sonar project data between to separate sonar instances.
@REM
@REM Copyright (C) 2013 Worldline or third-party contributors as
@REM indicated by the @author tags or express copyright attribution
@REM statements applied by the authors.
@REM
@REM This library is free software; you can redistribute it and/or
@REM modify it under the terms of the GNU Lesser General Public
@REM License as published by the Free Software Foundation; either
@REM version 2.1 of the License.
@REM
@REM This library is distributed in the hope that it will be useful,
@REM but WITHOUT ANY WARRANTY; without even the implied warranty of
@REM MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
@REM Lesser General Public License for more details.
@REM
@REM You should have received a copy of the GNU Lesser General Public
@REM License along with this library; if not, see <http://www.gnu.org/licenses/>
@REM

java -classpath .;sonar-db-migration-1.0.6.jar net.atos.tes.sonar.SonarMigrationUtil %1 %2