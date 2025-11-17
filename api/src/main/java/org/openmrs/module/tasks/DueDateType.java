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

/**
 * Enum representing the type of due date for a task.
 */
public enum DueDateType {
	
	/**
	 * Task is due during the current visit.
	 */
	THIS_VISIT,
	
	/**
	 * Task is due during the next visit.
	 */
	NEXT_VISIT,
	
	/**
	 * Task is due on a specific date.
	 */
	DATE
}
