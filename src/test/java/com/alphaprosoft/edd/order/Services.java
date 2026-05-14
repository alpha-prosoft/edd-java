package com.alphaprosoft.edd.order;

import com.alphaprosoft.edd.Service;

public final class Services {
    public static final Service CUSTOMER_SVC = Service.of("customer-svc");
    public static final Service CATALOG_SVC = Service.of("catalog-svc");
    public static final Service NOTIFICATION_SVC = Service.of("notification-svc");

    private Services() {}
}
