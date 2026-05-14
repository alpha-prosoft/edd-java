package com.alphaprosoft.edd.order;

import com.alphaprosoft.edd.Dep;
import com.alphaprosoft.edd.order.query.GetCustomer;
import com.alphaprosoft.edd.order.query.GetOrder;
import com.alphaprosoft.edd.order.query.GetProduct;

public final class OrderDeps {

    public static final Dep<GetCustomer, Customer> CUSTOMER =
            Dep.remote("customer", Services.CUSTOMER_SVC, OrderIds.GET_CUSTOMER);

    public static final Dep<GetProduct, Product> PRODUCT =
            Dep.remote("product", Services.CATALOG_SVC, OrderIds.GET_PRODUCT);

    public static final Dep<GetOrder, OrderAggregate> CURRENT_ORDER = Dep.local("order", OrderIds.GET_ORDER);

    private OrderDeps() {}
}
