/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.tasks.api;

import org.openmrs.module.tasks.TaskKind;
import org.openmrs.module.tasks.TaskStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.api.APIException;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.Task;
import org.openmrs.module.tasks.api.dao.TasksDao;
import org.openmrs.module.tasks.api.impl.TasksServiceImpl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
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
		task.setStatus(TaskStatus.NOTSTARTED);
		task.setKind(TaskKind.APPOINTMENT);
		
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
	public void getActiveTasksByPatientId_shouldDelegateToDao() {
		Integer patientId = 2;
		List<Task> tasks = new ArrayList<>();
		Task task1 = new Task();
		task1.setDescription("Active");
		tasks.add(task1);
		
		when(dao.getActiveTasksByPatientId(patientId)).thenReturn(tasks);
		
		List<Task> foundTasks = tasksService.getActiveTasksByPatientId(patientId);
		
		verify(dao).getActiveTasksByPatientId(patientId);
		assertThat(foundTasks.size(), is(1));
		assertThat(foundTasks.get(0).getDescription(), is("Active"));
	}
	
	@Test
	public void getTasksByPatientId_withIncludeVoided_shouldDelegateToDao() {
		Integer patientId = 2;
		List<Task> tasks = new ArrayList<>();
		Task task1 = new Task();
		task1.setDescription("Task 1");
		tasks.add(task1);
		
		when(dao.getTasksByPatientId(patientId, true)).thenReturn(tasks);
		
		List<Task> foundTasks = tasksService.getTasksByPatientId(patientId, true);
		
		verify(dao).getTasksByPatientId(patientId, true);
		assertThat(foundTasks.size(), is(1));
		assertThat(foundTasks.get(0).getDescription(), is("Task 1"));
	}
	
	@Test
	public void voidTask_shouldDelegateToDao() {
		// Audit fields are populated by OpenMRS's RequiredDataAdvice in the real Spring wiring;
		// this unit test only asserts the service's delegation to the DAO.
		Task task = new Task();
		
		tasksService.voidTask(task, "Test reason");
		
		verify(dao).saveTask(task);
	}
	
	@Test(expected = APIException.class)
	public void voidTask_withNullTask_shouldThrow() {
		tasksService.voidTask(null, "reason");
	}
	
	@Test(expected = APIException.class)
	public void voidTask_withNullReason_shouldThrow() {
		tasksService.voidTask(new Task(), null);
	}
	
	@Test(expected = APIException.class)
	public void voidTask_withEmptyReason_shouldThrow() {
		tasksService.voidTask(new Task(), "");
	}
	
	@Test(expected = APIException.class)
	public void voidTask_withWhitespaceReason_shouldThrow() {
		tasksService.voidTask(new Task(), "   ");
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
	
	@Test(expected = APIException.class)
	public void purgeTask_withNullTask_shouldThrow() {
		tasksService.purgeTask(null);
	}
	
	@Test
	public void saveSystemTask_shouldDelegateToDao() {
		SystemTask systemTask = new SystemTask();
		systemTask.setName("a-task");
		when(dao.saveSystemTask(systemTask)).thenReturn(systemTask);
		
		SystemTask saved = tasksService.saveSystemTask(systemTask);
		
		verify(dao).saveSystemTask(systemTask);
		assertThat(saved, is(sameInstance(systemTask)));
	}
	
	@Test
	public void getSystemTaskByUuid_shouldDelegateToDao() {
		String uuid = "system-task-uuid";
		SystemTask systemTask = new SystemTask();
		systemTask.setUuid(uuid);
		when(dao.getSystemTaskByUuid(uuid)).thenReturn(systemTask);
		
		SystemTask found = tasksService.getSystemTaskByUuid(uuid);
		
		verify(dao).getSystemTaskByUuid(uuid);
		assertThat(found, is(sameInstance(systemTask)));
	}
	
	@Test
	public void getAllSystemTasks_shouldDelegateToDaoWithIncludeRetiredFlag() {
		SystemTask active = new SystemTask();
		SystemTask retired = new SystemTask();
		when(dao.getAllSystemTasks(false)).thenReturn(Arrays.asList(active));
		when(dao.getAllSystemTasks(true)).thenReturn(Arrays.asList(active, retired));
		
		assertThat(tasksService.getAllSystemTasks(false).size(), is(1));
		assertThat(tasksService.getAllSystemTasks(true).size(), is(2));
		verify(dao).getAllSystemTasks(false);
		verify(dao).getAllSystemTasks(true);
	}
	
	@Test
	public void retireSystemTask_shouldDelegateToDao() {
		// Audit fields are populated by OpenMRS's RequiredDataAdvice in the real Spring wiring;
		// this unit test only asserts the service's delegation to the DAO.
		SystemTask systemTask = new SystemTask();
		
		tasksService.retireSystemTask(systemTask, "no longer needed");
		
		verify(dao).saveSystemTask(systemTask);
	}
	
	@Test(expected = APIException.class)
	public void retireSystemTask_withNullSystemTask_shouldThrow() {
		tasksService.retireSystemTask(null, "reason");
	}
	
	@Test(expected = APIException.class)
	public void retireSystemTask_withNullReason_shouldThrow() {
		tasksService.retireSystemTask(new SystemTask(), null);
	}
	
	@Test(expected = APIException.class)
	public void retireSystemTask_withEmptyReason_shouldThrow() {
		tasksService.retireSystemTask(new SystemTask(), "");
	}
	
	@Test(expected = APIException.class)
	public void retireSystemTask_withWhitespaceReason_shouldThrow() {
		tasksService.retireSystemTask(new SystemTask(), "   ");
	}
}
