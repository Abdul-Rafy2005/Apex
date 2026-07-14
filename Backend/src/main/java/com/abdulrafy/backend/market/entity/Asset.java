package com.abdulrafy.backend.market.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private Integer precision;

    @Column(name = "provider_source", nullable = false, length = 50)
    private String providerSource;

    @Column(nullable = false)
    private Boolean tradable;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (precision == null) precision = 8;
        if (tradable == null) tradable = true;
    }
}
