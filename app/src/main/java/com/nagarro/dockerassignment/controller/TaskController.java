package com.nagarro.dockerassignment.controller;

import com.nagarro.dockerassignment.model.Task;
import com.nagarro.dockerassignment.repository.TaskRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

// serves the Thymeleaf UI; the JSON API lives in TaskApiController
@Controller
public class TaskController {

    private final TaskRepository taskRepository;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("tasks", taskRepository.findAll());
        model.addAttribute("newTask", new Task());
        return "index";
    }

    @PostMapping("/tasks")
    public String create(@ModelAttribute("newTask") Task task) {
        // rebuild instead of saving the bound object directly so completed/createdAt
        // always start from defaults regardless of what the form posts
        taskRepository.save(new Task(task.getTitle(), task.getDescription()));
        return "redirect:/";
    }

    @PostMapping("/tasks/{id}/complete")
    public String complete(@PathVariable Long id) {
        taskRepository.findById(id).ifPresent(task -> {
            task.setCompleted(!task.isCompleted());
            taskRepository.save(task);
        });
        return "redirect:/";
    }

    @PostMapping("/tasks/{id}/delete")
    public String delete(@PathVariable Long id) {
        taskRepository.deleteById(id);
        return "redirect:/";
    }
}
