import sse from 'k6/x/sse';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL_MVC = __ENV.BASE_URL_MVC || 'http://localhost:8080';
const BASE_URL_WEBFLUX = __ENV.BASE_URL_WEBFLUX || 'http://localhost:8081';

const TARGET_VUS = Number(__ENV.TARGET_VUS || 500);

export default function customerOrder() {
    const userId = `load-test-user-${__VU}`;
    const startedAt = Date.now();

    // make real customer order 

    // imitate the real pos handling: group as one bundle 
        // group 1. 
        // 1. /api/pos/{storeId}/hall/orders/{orderId}/confirm
        // 2. /api/pos/{storeId}/kitchen/order-items/{itemId}/complete
        // 3. /api/pos/{storeId}/hall/orders/{orderId}/items/{itemId}/serve

        // group 2. (heavy query) usually when the network loss so call to use fetch
        // /api/pos/{storeId}/orders
        // /api/pos/{storeId}/hall/orders/pending-payments

        // group 3. SSE reconnection for not temporary loss
        // 

    const function group1() {

    }

    const function group2() {

    }

    const function group3() {

    }

    
}

