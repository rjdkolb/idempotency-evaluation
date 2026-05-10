package io.r3k.idempotency.evaluate.arun0009lib.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Getter
public class CustomerOrder {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false)
  private String customerOrderReference;

  @Column(nullable = false)
  private String supplierOrderReference;

  protected CustomerOrder() {}

  public CustomerOrder(String customerOrderReference, String supplierOrderReference) {
    this.customerOrderReference = customerOrderReference;
    this.supplierOrderReference = supplierOrderReference;
  }
}
