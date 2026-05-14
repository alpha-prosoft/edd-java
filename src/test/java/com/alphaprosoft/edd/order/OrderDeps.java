package com.alphaprosoft.edd.order;

import com.alphaprosoft.edd.Dep;
import com.alphaprosoft.edd.order.query.GetCustomerQuery;
import com.alphaprosoft.edd.order.query.GetOrderQuery;
import com.alphaprosoft.edd.order.query.GetProductQuery;

public final class OrderDeps {

    public static final Dep<GetCustomerQuery, Customer> CUSTOMER =
            Dep.remote("customer", Services.CUSTOMER_SVC, OrderIds.GET_CUSTOMER);

    public static final Dep<GetProductQuery, Product> PRODUCT =
            Dep.remote("product", Services.CATALOG_SVC, OrderIds.GET_PRODUCT);

    public static final Dep<GetOrderQuery, OrderAggregate> CURRENT_ORDER = Dep.local("order", OrderIds.GET_ORDER);

    private OrderDeps() {}
}
