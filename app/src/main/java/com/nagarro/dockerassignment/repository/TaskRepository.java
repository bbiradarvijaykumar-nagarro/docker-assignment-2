package com.nagarro.dockerassignment.repository;

import com.nagarro.dockerassignment.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
}
