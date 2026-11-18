package de.savas.businessinsightapi.domain.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findFirstByOrderByIdAsc();
}
