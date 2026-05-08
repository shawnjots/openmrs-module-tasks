/*
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
 * This internal enum mirrors {@code org.hl7.fhir.r4.model.CarePlan.CarePlanActivityKind} and is
 * meant to protect against future regressions, largely if HL7 changes the underlying value set.
 */
public enum TaskKind {
	APPOINTMENT,
	COMMUNICATIONREQUEST,
	DEVICEREQUEST,
	MEDICATIONREQUEST,
	NUTRITIONORDER,
	SERVICEREQUEST,
	TASK,
	VISIONPRESCRIPTION
}
