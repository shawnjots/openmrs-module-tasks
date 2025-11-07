/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.api.dao;

import org.hl7.fhir.r4.model.CarePlan;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.tasks.Task;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.List;

/**
 * It is an integration test (extends BaseModuleContextSensitiveTest), which verifies DAO methods
 * against the in-memory H2 database. The database is initially loaded with data from
 * standardTestDataset.xml in openmrs-api. All test methods are executed in transactions, which are
 * rolled back by the end of each test method.
 */
public class TasksDaoTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	TasksDao dao;
	
	@Autowired
	PatientService patientService;
	
	@Autowired
	ProviderService providerService;
	
	@Test
	public void saveTask_shouldSaveAllPropertiesInDb() {
		//Given
		Task task = new Task();
		task.setDescription("some description");
		task.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		task.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		Patient patient = patientService.getPatient(2);
		task.setPatient(patient);
		task.setAssignee(providerService.getProvider(1));
		task.setAssigneeRoleUuid("role-uuid-1234");
		
		//When
		dao.saveTask(task);
		
		//Let's clean up the cache to be sure getTaskByUuid fetches from DB and not from cache
		Context.flushSession();
		Context.clearSession();
		
		//Then
		Task savedTask = dao.getTaskByUuid(task.getUuid());
		
		assertThat(savedTask, hasProperty("uuid", is(task.getUuid())));
		assertThat(savedTask, hasProperty("patient", is(task.getPatient())));
		assertThat(savedTask, hasProperty("description", is(task.getDescription())));
		assertThat(savedTask, hasProperty("status", is(task.getStatus())));
		assertThat(savedTask, hasProperty("kind", is(task.getKind())));
		assertThat(savedTask, hasProperty("assignee", is(task.getAssignee())));
		assertThat(savedTask, hasProperty("assigneeRoleUuid", is(task.getAssigneeRoleUuid())));
	}
	
	@Test
	public void getTasksByPatientId_shouldReturnTasksForPatient() {
		//Given
		Patient patient = patientService.getPatient(2);
		
		Task task1 = new Task();
		task1.setDescription("Task 1");
		task1.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		task1.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		task1.setPatient(patient);
		task1.setAssigneeRoleUuid("role-uuid-1");
		dao.saveTask(task1);
		
		Task task2 = new Task();
		task2.setDescription("Task 2");
		task2.setStatus(CarePlan.CarePlanActivityStatus.INPROGRESS);
		task2.setKind(CarePlan.CarePlanActivityKind.MEDICATIONREQUEST);
		task2.setPatient(patient);
		dao.saveTask(task2);
		
		Context.flushSession();
		Context.clearSession();
		
		//When
		List<Task> tasks = dao.getTasksByPatientId(patient.getId());
		
		//Then
		assertThat(tasks.size(), is(2));
		assertThat(tasks, hasItems(hasProperty("description", is("Task 1")), hasProperty("description", is("Task 2"))));
		assertThat(tasks, hasItem(hasProperty("assigneeRoleUuid", is("role-uuid-1"))));
	}
}
