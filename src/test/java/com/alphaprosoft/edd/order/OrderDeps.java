package com.alphaprosoft.edd.order;

import com.alphaprosoft.edd.Dep;
import com.alphaprosoft.edd.order.query.GetCustomerQuery;
import com.alphaprosoft.edd.order.query.GetOrderQuery;
import com.alphaprosoft.edd.order.query.GetProductQuery;

public final class OrderDeps {

    public static final Dep<GetCustomerQuery, Customer> CUSTOMER = Dep.of("customer", QueryRegistry.GET_CUSTOMER);

    public static final Dep<GetProductQuery, Product> PRODUCT = Dep.of("product", QueryRegistry.GET_PRODUCT);

    public static final Dep<GetOrderQuery, OrderAggregate> CURRENT_ORDER = Dep.of("order", QueryRegistry.GET_ORDER);

    private OrderDeps() {}
}
