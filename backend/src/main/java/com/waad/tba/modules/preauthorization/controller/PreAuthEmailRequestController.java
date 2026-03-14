package com.waad.tba.modules.preauthorization.controller;

import com.waad.tba.modules.preauthorization.entity.PreAuthEmailRequest;
import com.waad.tba.modules.preauthorization.repository.PreAuthEmailRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preauthorization/email-requests")
@RequiredArgsConstructor
public class PreAuthEmailRequestController {

    private final PreAuthEmailRequestRepository repository;

    @GetMapping
    public ResponseEntity<Page<PreAuthEmailRequest>> listRequests(
            @PageableDefault(size = 20, sort = "receivedAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) Boolean processed) {
        
        if (processed != null) {
            return ResponseEntity.ok(repository.findByProcessed(processed, pageable));
        }
        return ResponseEntity.ok(repository.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PreAuthEmailRequest> getRequest(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRequest(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
