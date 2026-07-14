package com.abdulrafy.backend.market.mapper;

import com.abdulrafy.backend.market.dto.AssetResponse;
import com.abdulrafy.backend.market.entity.Asset;

public final class AssetMapper {

    private AssetMapper() {}

    public static AssetResponse toResponse(Asset asset) {
        return new AssetResponse(
            asset.getId(),
            asset.getSymbol(),
            asset.getName(),
            asset.getPrecision(),
            asset.getProviderSource(),
            asset.getTradable(),
            asset.getCreatedAt()
        );
    }
}
