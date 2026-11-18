package de.savas.businessinsightapi.domain.organization;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "organization")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Organization() {
    }

    public Organization(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
