package com.abdulrafy.backend.trading.repository;

import com.abdulrafy.backend.trading.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
