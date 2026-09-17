package com.pinghome.backend.contact;

import com.pinghome.backend.contact.dto.ContactRequest;
import com.pinghome.backend.contact.dto.ContactResponse;
import com.pinghome.backend.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public ResponseEntity<List<ContactResponse>> list() {
        return ResponseEntity.ok(contactService.list(CurrentUser.id()));
    }

    @PostMapping
    public ResponseEntity<ContactResponse> create(@Valid @RequestBody ContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contactService.create(CurrentUser.id(), request));
    }

    @PutMapping("/{contactId}")
    public ResponseEntity<ContactResponse> update(@PathVariable Long contactId, @Valid @RequestBody ContactRequest request) {
        return ResponseEntity.ok(contactService.update(CurrentUser.id(), contactId, request));
    }

    @DeleteMapping("/{contactId}")
    public ResponseEntity<Void> delete(@PathVariable Long contactId) {
        contactService.delete(CurrentUser.id(), contactId);
        return ResponseEntity.noContent().build();
    }
}
