package io.r3k.idempotency.evaluate.arun0009lib.order;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrder {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  private String tenant;

  private String customerOrderReference;

  private String supplierOrderReference;

  public CustomerOrder(
      String tenant, String customerOrderReference, String supplierOrderReference) {
    this.tenant = tenant;
    this.customerOrderReference = customerOrderReference;
    this.supplierOrderReference = supplierOrderReference;
  }
}
