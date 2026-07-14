package com.abdulrafy.backend.market.repository;

import com.abdulrafy.backend.market.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findByTradableTrue();

    List<Asset> findBySymbolIn(List<String> symbols);

    Optional<Asset> findBySymbol(String symbol);

    boolean existsBySymbol(String symbol);
}
