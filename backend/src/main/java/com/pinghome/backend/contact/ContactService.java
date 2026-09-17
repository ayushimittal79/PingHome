package com.pinghome.backend.contact;

import com.pinghome.backend.common.exception.ForbiddenException;
import com.pinghome.backend.common.exception.ResourceNotFoundException;
import com.pinghome.backend.contact.dto.ContactRequest;
import com.pinghome.backend.contact.dto.ContactResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContactService {

    private final ContactRepository contactRepository;

    public ContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    public List<ContactResponse> list(Long ownerUserId) {
        return contactRepository.findByOwnerUserIdOrderByNameAsc(ownerUserId).stream()
                .map(ContactResponse::from)
                .toList();
    }

    @Transactional
    public ContactResponse create(Long ownerUserId, ContactRequest request) {
        Contact contact = new Contact();
        contact.setOwnerUserId(ownerUserId);
        applyRequest(contact, request);
        contactRepository.save(contact);
        return ContactResponse.from(contact);
    }

    @Transactional
    public ContactResponse update(Long ownerUserId, Long contactId, ContactRequest request) {
        Contact contact = getOwned(ownerUserId, contactId);
        applyRequest(contact, request);
        return ContactResponse.from(contact);
    }

    @Transactional
    public void delete(Long ownerUserId, Long contactId) {
        Contact contact = getOwned(ownerUserId, contactId);
        contactRepository.delete(contact);
    }

    /** Used by TripService when attaching watchers to verify the contact belongs to the traveller. */
    public Contact getOwned(Long ownerUserId, Long contactId) {
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found"));
        if (!contact.getOwnerUserId().equals(ownerUserId)) {
            throw new ForbiddenException("This contact does not belong to you");
        }
        return contact;
    }

    private void applyRequest(Contact contact, ContactRequest request) {
        contact.setName(request.name());
        contact.setPhone(request.phone());
        contact.setEmail(request.email() == null ? null : request.email().toLowerCase());
        contact.setRelationship(request.relationship());
    }
}
