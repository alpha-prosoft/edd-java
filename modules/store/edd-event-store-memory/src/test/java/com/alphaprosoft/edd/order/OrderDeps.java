package com.alphaprosoft.edd.order;

import com.alphaprosoft.edd.order.query.GetCustomerQuery;
import com.alphaprosoft.edd.order.query.GetOrderQuery;
import com.alphaprosoft.edd.order.query.GetProductQuery;
import com.alphaprosoft.edd.query.Dep;

public final class OrderDeps {

  public static final Dep<GetCustomerQuery, Customer> CUSTOMER =
      Dep.of("customer", OrderModule.GET_CUSTOMER);

  public static final Dep<GetProductQuery, Product> PRODUCT =
      Dep.of("product", OrderModule.GET_PRODUCT);

  public static final Dep<GetOrderQuery, OrderAggregate> CURRENT_ORDER =
      Dep.of("order", OrderModule.GET_ORDER);

  private OrderDeps() {}
}
