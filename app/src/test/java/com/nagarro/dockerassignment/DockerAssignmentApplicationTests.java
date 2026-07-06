package com.nagarro.dockerassignment;

import com.nagarro.dockerassignment.model.Task;
import com.nagarro.dockerassignment.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DockerAssignmentApplicationTests {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void savesAndReadsBackATask() {
        Task saved = taskRepository.save(new Task("Write assignment", "Docker + Spring Boot"));

        assertThat(saved.getId()).isNotNull();
        assertThat(taskRepository.findById(saved.getId()))
                .isPresent()
                .get()
                .extracting(Task::getTitle)
                .isEqualTo("Write assignment");
    }
}
