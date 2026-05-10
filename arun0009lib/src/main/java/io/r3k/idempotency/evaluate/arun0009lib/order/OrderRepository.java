package io.r3k.idempotency.evaluate.arun0009lib.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

  Optional<CustomerOrder> findByCustomerOrderReference(String customerOrderReference);

  List<CustomerOrder> findAllByOrderByIdAsc();
}
