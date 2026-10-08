package com.account.serviceImpl.email;

import com.account.domain.Contact;
import com.account.domain.company.CompanyUnit;
import com.account.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Works out who receives a client email, using the same rules as the
 * estimate email:
 *   1. the contact chosen on the document
 *   2. the unit's primary contact, then secondary contact
 *   3. every other active contact of the unit (primary/secondary flags first)
 * Deleted contacts are skipped; emails are lower-cased, validated and de-duplicated.
 * The first address returned is the "primary" recipient.
 */
@Component
@RequiredArgsConstructor
public class ClientRecipientResolver {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+'-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    private final ContactRepository contactRepository;

    public List<String> resolve(Contact preferredContact, CompanyUnit unit) {
        List<Contact> ordered = new ArrayList<>();
        ordered.add(preferredContact);

        if (unit != null) {
            ordered.add(unit.getPrimaryContact());
            ordered.add(unit.getSecondaryContact());

            if (unit.getId() != null) {
                List<Contact> unitContacts =
                        contactRepository.findByCompanyUnitIdAndDeleteStatusFalse(unit.getId());
                if (unitContacts != null) {
                    unitContacts.stream()
                            .filter(Objects::nonNull)
                            .sorted(Comparator
                                    .comparing((Contact c) -> !c.isPrimaryForUnit())
                                    .thenComparing(c -> !c.isSecondaryForUnit()))
                            .forEach(ordered::add);
                }
            }
        }

        Set<String> emails = new LinkedHashSet<>();
        for (Contact contact : ordered) {
            if (contact == null || contact.isDeleted() || contact.isDeleteStatus()) {
                continue;
            }
            String raw = contact.getEmails();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String part : raw.replaceAll("[\\[\\]\"]", " ").split("[,;\\s]+")) {
                String email = part.trim().toLowerCase(Locale.ROOT);
                if (!email.isEmpty() && EMAIL_PATTERN.matcher(email).matches()) {
                    emails.add(email);
                }
            }
        }
        return new ArrayList<>(emails);
    }
}
