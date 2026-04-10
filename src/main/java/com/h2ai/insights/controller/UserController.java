package com.h2ai.insights.controller;

import com.h2ai.insights.entity.User;
import com.h2ai.insights.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // POST /api/users/upload — upload patient image, extract data, save
    @PostMapping("/upload")
    public ResponseEntity<User> uploadPatientImage(@RequestParam("image") MultipartFile image) throws IOException {
        User saved = userService.extractAndSaveUser(image);
        return ResponseEntity.ok(saved);
    }

    // GET /api/users — get all patients
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // GET /api/users/{id} — get one patient by id
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
}
