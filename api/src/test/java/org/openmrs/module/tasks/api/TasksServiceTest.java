/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.api;

import org.hl7.fhir.r4.model.CarePlan;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.api.dao.TasksDao;
import org.openmrs.module.tasks.api.impl.TasksServiceImpl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a unit test, which verifies logic in TasksService. It doesn't extend
 * BaseModuleContextSensitiveTest, thus it is run without the in-memory DB and Spring context.
 */
public class TasksServiceTest {
	
	@InjectMocks
	TasksServiceImpl tasksService;
	
	@Mock
	TasksDao dao;
	
	@Before
	public void setupMocks() {
		MockitoAnnotations.initMocks(this);
	}
	
	@Test
	public void saveTask_shouldDelegateToDao() {
		//Given
		Task task = new Task();
		task.setDescription("some description");
		task.setStatus(CarePlan.CarePlanActivityStatus.NOTSTARTED);
		task.setKind(CarePlan.CarePlanActivityKind.APPOINTMENT);
		
		when(dao.saveTask(task)).thenReturn(task);
		
		//When
		Task savedTask = tasksService.saveTask(task);
		
		//Then
		verify(dao).saveTask(task);
		assertThat(savedTask, is(task));
	}
	
	@Test
	public void getTaskByUuid_shouldDelegateToDao() {
		//Given
		String uuid = "test-uuid";
		Task task = new Task();
		task.setUuid(uuid);
		
		when(dao.getTaskByUuid(uuid)).thenReturn(task);
		
		//When
		Task foundTask = tasksService.getTaskByUuid(uuid);
		
		//Then
		verify(dao).getTaskByUuid(uuid);
		assertThat(foundTask, is(task));
	}
	
	@Test
	public void getTasksByPatientId_shouldDelegateToDao() {
		//Given
		Integer patientId = 2;
		List<Task> tasks = new ArrayList<>();
		Task task1 = new Task();
		task1.setDescription("Task 1");
		tasks.add(task1);
		
		when(dao.getTasksByPatientId(patientId)).thenReturn(tasks);
		
		//When
		List<Task> foundTasks = tasksService.getTasksByPatientId(patientId);
		
		//Then
		verify(dao).getTasksByPatientId(patientId);
		assertThat(foundTasks.size(), is(1));
		assertThat(foundTasks.get(0).getDescription(), is("Task 1"));
	}
	
	@Test
	public void voidTask_shouldMarkTaskVoidedAndDelegateToDao() {
		//Given
		Task task = new Task();
		when(dao.saveTask(task)).thenReturn(task);
		String reason = "Test reason";
		
		//When
		tasksService.voidTask(task, reason);
		
		//Then
		assertThat(task.getVoided(), is(true));
		assertThat(task.getVoidReason(), is(reason));
		verify(dao).saveTask(task);
	}
	
	@Test
	public void purgeTask_shouldDelegateToDao() {
		//Given
		Task task = new Task();
		
		//When
		tasksService.purgeTask(task);
		
		//Then
		verify(dao).deleteTask(task);
	}
}
