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

    // POST /api/users/upload — upload a clinical file, extract data with Gemini, save
    @PostMapping("/upload")
    public ResponseEntity<User> uploadPatientFile(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "image", required = false) MultipartFile image
    ) throws IOException {
        MultipartFile uploaded = file != null ? file : image;
        if (uploaded == null || uploaded.isEmpty()) {
            throw new IllegalArgumentException("Upload a non-empty file using form-data key 'file'");
        }

        User saved = userService.extractAndSaveUser(uploaded);
        return ResponseEntity.ok(saved);
    }

    // GET /api/users — get all patients
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // GET /api/users/search?name=smith — search patients by name
    @GetMapping("/search")
    public ResponseEntity<List<User>> searchPatients(@RequestParam(value = "name", required = false) String name) {
        return ResponseEntity.ok(userService.searchPatientsByName(name));
    }

    // GET /api/users/{id} — get one patient by id
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
}
