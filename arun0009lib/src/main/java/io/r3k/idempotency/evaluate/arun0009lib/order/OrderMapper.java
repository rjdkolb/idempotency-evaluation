package io.r3k.idempotency.evaluate.arun0009lib.order;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderMapper {

  OrderResponse toResponse(CustomerOrder order);
}
