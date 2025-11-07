/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks;

import org.springframework.stereotype.Component;

/**
 * Contains module's config.
 */
@Component("tasks.TasksConfig")
public class TasksConfig {
	
	public static final String TASKS_VIEW_PRIVILEGE = "View Tasks";
	
	public static final String TASKS_MANAGE_PRIVILEGE = "Manage Tasks";
	
	public static final String TASKS_DELETE_PRIVILEGE = "Delete Tasks";
	
	/**
	 * @deprecated use {@link #TASKS_MANAGE_PRIVILEGE} instead.
	 */
	@Deprecated
	public static final String MODULE_PRIVILEGE = TASKS_MANAGE_PRIVILEGE;
}
