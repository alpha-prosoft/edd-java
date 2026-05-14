package com.alphaprosoft.edd.order;

import com.alphaprosoft.edd.QueryId;
import com.alphaprosoft.edd.order.query.GetCustomerQuery;
import com.alphaprosoft.edd.order.query.GetOrderQuery;
import com.alphaprosoft.edd.order.query.GetProductQuery;

public final class QueryRegistry {

    public static final QueryId<GetOrderQuery, OrderAggregate> GET_ORDER =
            QueryId.of("get-order", GetOrderQuery.class, OrderAggregate.class);
    public static final QueryId<GetCustomerQuery, Customer> GET_CUSTOMER =
            QueryId.of("get-customer", GetCustomerQuery.class, Customer.class);
    public static final QueryId<GetProductQuery, Product> GET_PRODUCT =
            QueryId.of("get-product", GetProductQuery.class, Product.class);

    private QueryRegistry() {}
}
